// Package service 中的直播业务与实时聊天模块。
//
// 整体链路：
//  1. 管理后台调用 CreateRoom 创建直播间，系统生成 stream_id 和推流地址；
//  2. 主播用 OBS 向 ZLMediaKit 推流（rtmp://host/live/{stream_id}）；
//  3. ZLMediaKit 触发 on_publish webhook，LiveService 校验 stream_id 并把直播间状态改为直播中；
//  4. 观众进入直播间，拿到 HTTP-FLV/HLS/WebRTC 播放地址；
//  5. 观众通过 WebSocket 加入 LiveHub 发送实时聊天/弹幕；
//  6. 主播/管理员可以禁言、踢人、结束直播。
//
// 高并发：
//
//	LiveHub 采用与 DanmakuHub 相同的 Room + Redis Pub/Sub 架构，
//	支持多 video 实例水平扩展，单房间可承载上千并发。
package service

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"strconv"
	"sync"
	"time"
	"video/config"
	"video/model"
	"video/repository"
	"video/zlm"

	"github.com/google/uuid"
	"github.com/redis/go-redis/v9"
)

// LiveClient 表示一个直播间内的 WebSocket 连接。
type LiveClient struct {
	RoomID   uint64      // 所属直播间 ID
	UserID   uint64      // 用户 ID，未登录为 0
	Username string      // 用户昵称
	Send     chan []byte // 发送缓冲区，容量 256
}

// LiveRoomHub 表示一个直播间内的在线连接集合。
type LiveRoomHub struct {
	RoomID    uint64                 // 直播间 ID
	Clients   map[*LiveClient]bool   // 所有在线连接
	UserIndex map[uint64]*LiveClient // 按 userID 索引，方便踢人/禁言时快速定位
	Mutex     sync.RWMutex           // 保护 Clients 和 UserIndex
}

// Add 把客户端加入房间，并更新 userID 索引。
func (r *LiveRoomHub) Add(c *LiveClient) {
	r.Mutex.Lock()
	defer r.Mutex.Unlock()
	r.Clients[c] = true
	if c.UserID > 0 {
		r.UserIndex[c.UserID] = c
	}
}

// Remove 把客户端移出房间，并清理 userID 索引。
func (r *LiveRoomHub) Remove(c *LiveClient) {
	r.Mutex.Lock()
	defer r.Mutex.Unlock()
	delete(r.Clients, c)
	if c.UserID > 0 {
		if r.UserIndex[c.UserID] == c {
			delete(r.UserIndex, c.UserID)
		}
	}
}

// Broadcast 向房间内所有在线连接广播消息。
// 如果某个客户端发送缓冲区满，说明是慢连接，直接关闭并移除，避免阻塞其他用户。
func (r *LiveRoomHub) Broadcast(msg []byte) {
	r.Mutex.RLock()
	defer r.Mutex.RUnlock()
	for client := range r.Clients {
		select {
		case client.Send <- msg:
		default:
			close(client.Send)
			delete(r.Clients, client)
			if client.UserID > 0 && r.UserIndex[client.UserID] == client {
				delete(r.UserIndex, client.UserID)
			}
		}
	}
}

// KickUser 把指定用户从房间踢出（关闭其 WebSocket 连接）。
// 返回是否成功找到并踢出。
func (r *LiveRoomHub) KickUser(userID uint64) bool {
	r.Mutex.Lock()
	defer r.Mutex.Unlock()
	client, ok := r.UserIndex[userID]
	if !ok {
		return false
	}
	close(client.Send)
	delete(r.Clients, client)
	delete(r.UserIndex, userID)
	return true
}

// Count 返回房间当前在线人数。
func (r *LiveRoomHub) Count() int {
	r.Mutex.RLock()
	defer r.Mutex.RUnlock()
	return len(r.Clients)
}

// LiveHub 直播聊天总控，管理所有直播间。
type LiveHub struct {
	repos      *repository.Repositories // 数据仓库
	redis      *redis.Client            // Redis 客户端，用于多实例同步
	cfg        *config.VideoConfig      // 直播相关配置
	rooms      map[uint64]*LiveRoomHub  // 所有直播间
	roomsMutex sync.RWMutex             // 保护 rooms
}

// NewLiveHub 创建直播聊天 Hub，并启动 Redis 订阅协程。
func NewLiveHub(repos *repository.Repositories, redisClient *redis.Client, cfg *config.Config) *LiveHub {
	hub := &LiveHub{
		repos: repos,
		redis: redisClient,
		cfg:   &cfg.Video,
		rooms: make(map[uint64]*LiveRoomHub),
	}
	go hub.startRedisSubscriber()
	return hub
}

// getRoom 获取或创建一个直播间（懒加载）。
func (h *LiveHub) getRoom(roomID uint64) *LiveRoomHub {
	h.roomsMutex.Lock()
	defer h.roomsMutex.Unlock()
	room, ok := h.rooms[roomID]
	if !ok {
		room = &LiveRoomHub{
			RoomID:    roomID,
			Clients:   make(map[*LiveClient]bool),
			UserIndex: make(map[uint64]*LiveClient),
		}
		h.rooms[roomID] = room
	}
	return room
}

// Join 用户加入直播间。
// 如果用户被禁言/踢出且在处罚有效期内，则返回错误，禁止进入。
func (h *LiveHub) Join(roomID uint64, userID uint64, username string) (*LiveClient, error) {
	if userID > 0 {
		banned, err := h.repos.LiveBanRepo.IsBanned(roomID, userID)
		if err != nil {
			return nil, err
		}
		if banned {
			return nil, fmt.Errorf("您已被禁言或踢出该直播间")
		}
	}

	client := &LiveClient{
		RoomID:   roomID,
		UserID:   userID,
		Username: username,
		Send:     make(chan []byte, 256),
	}
	room := h.getRoom(roomID)
	room.Add(client)
	return client, nil
}

// Leave 用户离开直播间，清理资源。
func (h *LiveHub) Leave(client *LiveClient) {
	if client == nil {
		return
	}
	room := h.getRoom(client.RoomID)
	room.Remove(client)
	close(client.Send)
}

// SendChat 发送一条直播间消息（聊天/弹幕）。
// 先检查用户是否被禁言，再本地广播，最后通过 Redis 同步到其他实例。
func (h *LiveHub) SendChat(client *LiveClient, content string, msgType string, color string) (*model.LiveMessage, error) {
	if client.UserID > 0 {
		banned, err := h.repos.LiveBanRepo.IsBanned(client.RoomID, client.UserID)
		if err != nil {
			return nil, err
		}
		if banned {
			return nil, fmt.Errorf("您已被禁言")
		}
	}

	msg := &model.LiveMessage{
		Type:     msgType,
		RoomID:   client.RoomID,
		UserID:   client.UserID,
		Username: client.Username,
		Content:  content,
		Color:    color,
		Ts:       time.Now().UnixMilli(),
	}
	data, err := json.Marshal(msg)
	if err != nil {
		return nil, err
	}

	room := h.getRoom(client.RoomID)
	room.Broadcast(data)

	// Redis 多实例同步
	channel := h.cfg.DanmakuRedisChannel + "live:" + strconv.FormatUint(client.RoomID, 10)
	go func() {
		ctx := context.Background()
		if err := h.redis.Publish(ctx, channel, data).Err(); err != nil {
			slog.Error("Redis 发布直播消息失败", "error", err)
		}
	}()

	return msg, nil
}

// KickUser 从直播间踢出指定用户。
func (h *LiveHub) KickUser(roomID, userID uint64) bool {
	room := h.getRoom(roomID)
	return room.KickUser(userID)
}

// OnlineCount 返回直播间当前在线人数。
func (h *LiveHub) OnlineCount(roomID uint64) int {
	room := h.getRoom(roomID)
	return room.Count()
}

// startRedisSubscriber 订阅 Redis 直播消息频道，接收其他 video 实例广播的消息。
func (h *LiveHub) startRedisSubscriber() {
	ctx := context.Background()
	pubsub := h.redis.PSubscribe(ctx, h.cfg.DanmakuRedisChannel+"live:*")
	defer pubsub.Close()
	ch := pubsub.Channel()
	for msg := range ch {
		var m model.LiveMessage
		if err := json.Unmarshal([]byte(msg.Payload), &m); err != nil {
			continue
		}
		room := h.getRoom(m.RoomID)
		room.Broadcast([]byte(msg.Payload))
	}
}

// LiveService 直播业务服务。
type LiveService struct {
	repos *repository.Repositories // 数据仓库
	zlm   *zlm.Client              // ZLMediaKit 管理客户端
	cfg   *config.Config           // video 总配置
	hub   *LiveHub                 // 直播聊天 Hub
}

// NewLiveService 创建直播业务服务实例。
func NewLiveService(repos *repository.Repositories, cfg *config.Config, hub *LiveHub) *LiveService {
	return &LiveService{
		repos: repos,
		zlm:   zlm.NewClient(cfg.ZLMediaKit),
		cfg:   cfg,
		hub:   hub,
	}
}

// generateStreamID 生成全局唯一的 stream_id。
// 使用 UUID 并去掉横杠，作为 OBS 推流时的流名称。
func (s *LiveService) generateStreamID() string {
	return uuid.New().String()
}

// CreateRoom 创建直播间。
// 生成 stream_id，保存数据库，并返回直播间信息和推流地址。
func (s *LiveService) CreateRoom(title, coverUrl string, userID uint64, username string) (*model.LiveRoom, string, error) {
	room := &model.LiveRoom{
		Title:    title,
		CoverUrl: coverUrl,
		StreamID: s.generateStreamID(),
		UserID:   userID,
		Username: username,
		Status:   model.LiveRoomStatusOffline,
	}
	if err := s.repos.LiveRoomRepo.CreateRoom(room); err != nil {
		return nil, "", err
	}
	pushURL := s.zlm.BuildRTMPPushURL(room.StreamID)
	return room, pushURL, nil
}

// UpdateRoom 更新直播间信息（标题、封面等）。
func (s *LiveService) UpdateRoom(room *model.LiveRoom) error {
	return s.repos.LiveRoomRepo.UpdateRoom(room)
}

// DeleteRoom 删除直播间。
// 删除前会先结束直播并断开 ZLMediaKit 上的推流，再删除数据库记录。
func (s *LiveService) DeleteRoom(id uint64) error {
	room, err := s.repos.LiveRoomRepo.GetRoomByID(id)
	if err != nil {
		return err
	}
	// 如果正在直播，先通知 ZLMediaKit 断流
	if room.Status == model.LiveRoomStatusLive {
		_ = s.zlm.CloseStreams(room.StreamID)
	}
	return s.repos.LiveRoomRepo.DeleteRoom(id)
}

// GetRoom 根据 ID 查询直播间。
func (s *LiveService) GetRoom(id uint64) (*model.LiveRoom, error) {
	return s.repos.LiveRoomRepo.GetRoomByID(id)
}

// ListRooms 分页查询直播间列表。
func (s *LiveService) ListRooms(page, pageSize int, status int, keyword string) ([]model.LiveRoom, int64, error) {
	return s.repos.LiveRoomRepo.ListRooms(page, pageSize, status, keyword)
}

// StartLive 开始直播。
// 把直播间状态改为直播中，并返回 OBS 推流地址。
func (s *LiveService) StartLive(id uint64) (*model.LiveRoom, string, error) {
	room, err := s.repos.LiveRoomRepo.GetRoomByID(id)
	if err != nil {
		return nil, "", err
	}
	if room.Status == model.LiveRoomStatusBanned {
		return nil, "", fmt.Errorf("直播间已被封禁，无法开播")
	}
	if err := s.repos.LiveRoomRepo.StartLive(id); err != nil {
		return nil, "", err
	}
	room.Status = model.LiveRoomStatusLive
	pushURL := s.zlm.BuildRTMPPushURL(room.StreamID)
	return room, pushURL, nil
}

// EndLive 结束直播。
// 更新数据库状态为未开播，并调用 ZLMediaKit 关闭推流。
func (s *LiveService) EndLive(id uint64) error {
	room, err := s.repos.LiveRoomRepo.GetRoomByID(id)
	if err != nil {
		return err
	}
	if err := s.repos.LiveRoomRepo.EndLive(id); err != nil {
		return err
	}
	// 通知 ZLMediaKit 断开该流
	if err := s.zlm.CloseStreams(room.StreamID); err != nil {
		slog.Error("结束直播时关闭 ZLMediaKit 流失败", "error", err, "streamId", room.StreamID)
	}
	return nil
}

// BanRoom 封禁直播间。
func (s *LiveService) BanRoom(id uint64) error {
	room, err := s.repos.LiveRoomRepo.GetRoomByID(id)
	if err != nil {
		return err
	}
	if err := s.repos.LiveRoomRepo.BanRoom(id); err != nil {
		return err
	}
	// 如果正在直播，强制断流
	if room.Status == model.LiveRoomStatusLive {
		_ = s.zlm.CloseStreams(room.StreamID)
	}
	return nil
}

// GetPlayURLs 获取直播间播放地址。
// 同时累计观看人数 +1（实际可由 on_play webhook 触发更精确）。
func (s *LiveService) GetPlayURLs(roomID uint64) (map[string]string, error) {
	room, err := s.repos.LiveRoomRepo.GetRoomByID(roomID)
	if err != nil {
		return nil, err
	}
	if room.Status != model.LiveRoomStatusLive {
		return nil, fmt.Errorf("直播间未开播")
	}
	// 异步累计观看人数
	go func() {
		_ = s.repos.LiveRoomRepo.IncrViewCount(roomID)
	}()
	return s.zlm.PlaybackURLs(room.StreamID), nil
}

// BanUser 对用户执行禁言或踢出处罚。
//
// 参数：
//   - banType: 0=禁言，1=踢出并禁言；
//   - minutes: 处罚时长（分钟），0 表示永久；
//   - reason: 处罚原因；
//   - opUserID: 操作人 ID。
func (s *LiveService) BanUser(roomID, userID uint64, banType int, minutes int, reason string, opUserID uint64) error {
	expireAt := time.Now().AddDate(100, 0, 0) // 默认永久
	if minutes > 0 {
		expireAt = time.Now().Add(time.Duration(minutes) * time.Minute)
	}
	ban := &model.LiveBan{
		RoomID:   roomID,
		UserID:   userID,
		Type:     model.LiveBanType(banType),
		ExpireAt: expireAt,
		Reason:   reason,
		OpUserID: opUserID,
	}
	if err := s.repos.LiveBanRepo.CreateBan(ban); err != nil {
		return err
	}
	// 如果是踢出，立即断开该用户的 WebSocket
	if ban.Type == model.LiveBanTypeKick {
		s.hub.KickUser(roomID, userID)
	}
	return nil
}

// UnbanUser 撤销对用户的处罚。
func (s *LiveService) UnbanUser(roomID, userID uint64) error {
	return s.repos.LiveBanRepo.DeleteBan(roomID, userID)
}

// ListBans 查询直播间处罚列表。
func (s *LiveService) ListBans(roomID uint64) ([]model.LiveBan, error) {
	return s.repos.LiveBanRepo.ListBans(roomID)
}

// VerifyWebhookSecret 校验 ZLMediaKit webhook 请求的密钥。
func (s *LiveService) VerifyWebhookSecret(secret string) bool {
	return s.cfg.ZLMediaKit.WebhookSecret != "" && s.cfg.ZLMediaKit.WebhookSecret == secret
}

// HandlePublishWebhook 处理 ZLMediaKit on_publish 回调。
//
// 说明：
//
//	OBS 开始向 ZLMediaKit 推流时，ZLMediaKit 会先调用此接口。我们校验 stream_id 是否存在、
//	直播间是否未被封禁，然后标记为直播中，返回成功让 ZLMediaKit 继续收流。
func (s *LiveService) HandlePublishWebhook(streamID string) error {
	room, err := s.repos.LiveRoomRepo.GetRoomByStreamID(streamID)
	if err != nil {
		return fmt.Errorf("stream_id 不存在: %s", streamID)
	}
	if room.Status == model.LiveRoomStatusBanned {
		return fmt.Errorf("直播间已被封禁")
	}
	return s.repos.LiveRoomRepo.StartLive(room.ID)
}

// HandleUnpublishWebhook 处理 ZLMediaKit on_unpublish 回调。
// OBS 停止推流或网络断开时触发，把直播间状态改为未开播。
func (s *LiveService) HandleUnpublishWebhook(streamID string) error {
	room, err := s.repos.LiveRoomRepo.GetRoomByStreamID(streamID)
	if err != nil {
		return err
	}
	return s.repos.LiveRoomRepo.EndLive(room.ID)
}

// HandlePlayWebhook 处理 ZLMediaKit on_play 回调。
// 观众开始拉流时触发，累计观看人数 +1。
func (s *LiveService) HandlePlayWebhook(streamID string) error {
	room, err := s.repos.LiveRoomRepo.GetRoomByStreamID(streamID)
	if err != nil {
		return err
	}
	return s.repos.LiveRoomRepo.IncrViewCount(room.ID)
}
