// Package service 中的弹幕模块。
//
// 设计目标：支撑单房间上千并发，支持多 video 服务实例水平扩展。
// 核心思路：
//   - 每个视频一个 Room，房间内维护该视频下所有在线 WebSocket 连接；
//   - 新弹幕先保存到 MySQL，再本地广播，最后通过 Redis Pub/Sub 同步到其他实例；
//   - 用户进入房间时，先推送最近 N 条历史弹幕。
package service

import (
	"context"
	"encoding/json"
	"log/slog"
	"strconv"
	"sync"
	"video/config"
	"video/model"
	"video/repository"

	"github.com/redis/go-redis/v9"
)

// DanmakuMessage 通过 WebSocket 广播的弹幕消息结构。
type DanmakuMessage struct {
	ID       uint64  `json:"id"`       // 弹幕记录 ID
	VideoID  uint64  `json:"videoId"`  // 所属视频
	UserID   uint64  `json:"userId"`   // 发送者用户 ID
	Username string  `json:"username"` // 发送者昵称
	Content  string  `json:"content"`  // 弹幕内容
	TimeAt   float64 `json:"timeAt"`   // 弹幕在视频中的出现时间（秒）
	Color    string  `json:"color"`    // 弹幕颜色
	Type     int     `json:"type"`     // 0=滚动 1=顶部 2=底部
}

// Client 表示一个弹幕房间的 WebSocket 连接。
type Client struct {
	VideoID  uint64      // 所属视频/房间 ID
	UserID   uint64      // 用户 ID，未登录为 0
	Username string      // 用户昵称
	Send     chan []byte // 发送缓冲区，容量 256，防止慢连接拖垮服务
}

// Room 表示一个视频房间，管理该视频下所有在线连接。
type Room struct {
	VideoID uint64           // 房间 ID（即视频 ID）
	Clients map[*Client]bool // 在线连接集合
	Mutex   sync.RWMutex     // 保护 Clients 的读写锁
}

// Add 把客户端加入房间。
func (r *Room) Add(c *Client) {
	r.Mutex.Lock()
	defer r.Mutex.Unlock()
	r.Clients[c] = true
}

// Remove 把客户端移出房间。
func (r *Room) Remove(c *Client) {
	r.Mutex.Lock()
	defer r.Mutex.Unlock()
	delete(r.Clients, c)
}

// Broadcast 向房间内所有在线连接广播消息。
// 如果某个客户端发送缓冲区满，说明是慢连接，直接关闭并移除，避免阻塞其他用户。
func (r *Room) Broadcast(msg []byte) {
	r.Mutex.RLock()
	defer r.Mutex.RUnlock()
	for client := range r.Clients {
		select {
		case client.Send <- msg:
		default:
			close(client.Send)
			delete(r.Clients, client)
		}
	}
}

// DanmakuHub 弹幕总控，管理所有房间。
type DanmakuHub struct {
	repos      *repository.Repositories // 数据仓库
	redis      *redis.Client            // Redis 客户端，用于多实例同步
	cfg        *config.VideoConfig      // 弹幕相关配置
	rooms      map[uint64]*Room         // 所有房间
	roomsMutex sync.RWMutex             // 保护 rooms 的读写锁
}

// NewDanmakuHub 创建弹幕 Hub，并启动 Redis 订阅协程。
func NewDanmakuHub(repos *repository.Repositories, redisClient *redis.Client, cfg *config.Config) *DanmakuHub {
	hub := &DanmakuHub{
		repos: repos,
		redis: redisClient,
		cfg:   &cfg.Video,
		rooms: make(map[uint64]*Room),
	}
	// 在后台启动 Redis 订阅，接收其他 video 实例发来的弹幕
	go hub.startRedisSubscriber()
	return hub
}

// getRoom 获取或创建一个房间（懒加载）。
func (h *DanmakuHub) getRoom(videoID uint64) *Room {
	h.roomsMutex.Lock()
	defer h.roomsMutex.Unlock()
	room, ok := h.rooms[videoID]
	if !ok {
		room = &Room{VideoID: videoID, Clients: make(map[*Client]bool)}
		h.rooms[videoID] = room
	}
	return room
}

// Join 用户加入房间，返回客户端对象和历史弹幕。
func (h *DanmakuHub) Join(videoID uint64, userID uint64, username string) (*Client, []model.Danmaku, error) {
	client := &Client{
		VideoID:  videoID,
		UserID:   userID,
		Username: username,
		Send:     make(chan []byte, 256), // 256 条消息缓冲
	}
	room := h.getRoom(videoID)
	room.Add(client)

	// 查询最近 N 条历史弹幕，进入房间时推送
	history, err := h.repos.DanmakuRepo.ListByVideo(videoID, h.cfg.DanmakuHistoryLimit)
	if err != nil {
		return nil, nil, err
	}
	return client, history, nil
}

// ListHistory 查询历史弹幕（HTTP 接口用）。
func (h *DanmakuHub) ListHistory(videoID uint64, limit int) ([]model.Danmaku, error) {
	if limit <= 0 {
		limit = h.cfg.DanmakuHistoryLimit
	}
	return h.repos.DanmakuRepo.ListByVideo(videoID, limit)
}

// Leave 用户离开房间，清理资源。
func (h *DanmakuHub) Leave(client *Client) {
	if client == nil {
		return
	}
	room := h.getRoom(client.VideoID)
	room.Remove(client)
	close(client.Send)
}

// SendDanmaku 发送一条弹幕：先保存到 MySQL，再本地广播，最后 Redis 发布供其他实例同步。
func (h *DanmakuHub) SendDanmaku(client *Client, content string, timeAt float64, color string, dType int) (*DanmakuMessage, error) {
	// 1. 构造弹幕数据库记录
	danmaku := &model.Danmaku{
		VideoID:  client.VideoID,
		UserID:   client.UserID,
		Username: client.Username,
		Content:  content,
		TimeAt:   timeAt,
		Color:    color,
		Type:     dType,
	}
	// 2. 同步保存到数据库，获取自增 ID（必须先保存，否则广播出去 id 为 0）
	if err := h.repos.DanmakuRepo.Create(danmaku); err != nil {
		return nil, err
	}

	// 3. 构造广播消息
	msg := &DanmakuMessage{
		ID:       danmaku.ID,
		VideoID:  danmaku.VideoID,
		UserID:   danmaku.UserID,
		Username: danmaku.Username,
		Content:  danmaku.Content,
		TimeAt:   danmaku.TimeAt,
		Color:    danmaku.Color,
		Type:     danmaku.Type,
	}
	data, err := json.Marshal(msg)
	if err != nil {
		return nil, err
	}

	// 4. 本地广播：把弹幕发给当前实例该房间内的所有用户
	room := h.getRoom(client.VideoID)
	room.Broadcast(data)

	// 5. Redis 发布：让部署在其他机器上的 video 实例也能收到这条弹幕
	channel := h.cfg.DanmakuRedisChannel + strconv.FormatUint(client.VideoID, 10)
	go func() {
		ctx := context.Background()
		if err := h.redis.Publish(ctx, channel, data).Err(); err != nil {
			slog.Error("Redis 发布弹幕失败", "error", err)
		}
	}()

	return msg, nil
}

// startRedisSubscriber 订阅 Redis 弹幕频道，接收其他 video 实例广播的弹幕。
// 使用 PSubscribe 通配符订阅所有视频房间，避免为每个房间单独开一个订阅。
func (h *DanmakuHub) startRedisSubscriber() {
	ctx := context.Background()
	pubsub := h.redis.PSubscribe(ctx, h.cfg.DanmakuRedisChannel+"*")
	defer pubsub.Close()
	ch := pubsub.Channel()
	for msg := range ch {
		var dm DanmakuMessage
		if err := json.Unmarshal([]byte(msg.Payload), &dm); err != nil {
			continue
		}
		room := h.getRoom(dm.VideoID)
		room.Broadcast([]byte(msg.Payload))
	}
}
