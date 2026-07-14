// Package handler 中的直播相关 HTTP/WebSocket 接口。
//
// 接口分组：
//  1. 管理后台接口（需要登录）：/api/video/live/admin/*
//     - 创建、修改、删除、查询直播间
//     - 开始直播、结束直播、封禁直播间
//     - 禁言/踢出用户、撤销处罚、查询处罚列表
//  2. 观众端接口（可选登录）：/api/video/live/*
//     - 获取播放地址
//     - WebSocket 实时聊天/弹幕
//  3. ZLMediaKit webhook 回调：/api/video/live/webhook/*
//     - on_publish：OBS 开始推流，video 校验后允许收流
//     - on_unpublish：OBS 停止推流，video 标记直播结束
//     - on_play：观众开始播放，video 累计观看人数
//
// 直播完整链路：
//  1. 管理员调用 POST /admin/room 创建直播间，系统生成 stream_id 和推流地址；
//  2. 主播用 OBS 向 rtmp://host/live/{stream_id} 推流；
//  3. ZLMediaKit 收到推流前调用 /webhook/publish，video 校验 stream_id 合法性；
//  4. 校验通过后 ZLMediaKit 开始收流，观众拿到播放地址观看；
//  5. 观众通过 WebSocket 加入房间发送实时弹幕；
//  6. 管理员可随时结束直播或处罚违规用户。
package handler

import (
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"strconv"
	"time"
	"video/middleware"
	"video/model"
	"video/service"

	"github.com/gin-gonic/gin"
	"nhooyr.io/websocket"
)

// LiveHandler 直播 HTTP/WebSocket 处理器。
// 聚合 LiveService（业务逻辑）和 LiveHub（实时聊天）。
type LiveHandler struct {
	svc *service.LiveService
	hub *service.LiveHub
}

// NewLiveHandler 创建直播处理器实例。
func NewLiveHandler(svc *service.LiveService, hub *service.LiveHub) *LiveHandler {
	return &LiveHandler{svc: svc, hub: hub}
}

// parseCurrentUser 从 Gin 上下文中解析当前登录用户。
//
// 说明：
//   - 已登录：返回 user_id 和 username；
//   - 未登录：返回 0 和 "游客"，业务层据此视为游客。
func parseCurrentUser(c *gin.Context) (uint64, string) {
	userID, ok := middleware.GetUserID(c)
	if !ok {
		return 0, "游客"
	}
	return userID, middleware.GetUsername(c)
}

// ==================== 管理后台接口 ====================

// CreateRoom 创建直播间。
//
// 请求体字段：
//   - title：直播间标题（必填）；
//   - coverUrl：封面图地址（可选）。
//
// 响应：返回直播间信息 + pushUrl（OBS 推流地址）。
func (h *LiveHandler) CreateRoom(c *gin.Context) {
	var req struct {
		Title    string `json:"title"`
		CoverUrl string `json:"coverUrl"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusOK, model.Fail("参数错误"))
		return
	}
	if req.Title == "" {
		c.JSON(http.StatusOK, model.Fail("直播间标题不能为空"))
		return
	}

	userID, username := parseCurrentUser(c)
	room, pushURL, err := h.svc.CreateRoom(req.Title, req.CoverUrl, userID, username)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}

	c.JSON(http.StatusOK, model.Success(gin.H{
		"room":    room,
		"pushUrl": pushURL,
	}))
}

// UpdateRoom 更新直播间信息。
//
// 请求体字段：
//   - id：直播间 ID（必填）；
//   - title：标题（可选，非空才更新）；
//   - coverUrl：封面（可选）。
func (h *LiveHandler) UpdateRoom(c *gin.Context) {
	var req model.LiveRoom
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusOK, model.Fail("参数错误"))
		return
	}
	if req.ID == 0 {
		c.JSON(http.StatusOK, model.Fail("直播间ID不能为空"))
		return
	}
	if err := h.svc.UpdateRoom(&req); err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(nil))
}

// DeleteRoom 删除直播间。
// 如果直播间正在推流，会先通知 ZLMediaKit 断流，再删除数据库记录。
func (h *LiveHandler) DeleteRoom(c *gin.Context) {
	id, err := strconv.ParseUint(c.Param("id"), 10, 64)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail("ID 错误"))
		return
	}
	if err := h.svc.DeleteRoom(id); err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(nil))
}

// GetRoom 查询直播间详情。
func (h *LiveHandler) GetRoom(c *gin.Context) {
	id, err := strconv.ParseUint(c.Param("id"), 10, 64)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail("ID 错误"))
		return
	}
	room, err := h.svc.GetRoom(id)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(room))
}

// ListRooms 分页查询直播间列表。
//
// 查询参数：
//   - page：页码，默认 1；
//   - pageSize：每页条数，默认 10；
//   - status：状态筛选，-1 表示全部（默认），0=未开播，1=直播中，2=封禁；
//   - keyword：标题模糊搜索。
func (h *LiveHandler) ListRooms(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("pageSize", "10"))
	status, _ := strconv.Atoi(c.DefaultQuery("status", "-1"))
	keyword := c.Query("keyword")

	list, total, err := h.svc.ListRooms(page, pageSize, status, keyword)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(gin.H{
		"list":     list,
		"total":    total,
		"page":     page,
		"pageSize": pageSize,
	}))
}

// StartLive 开始直播。
//
// 说明：
//
//	管理后台点击“开始直播”后，返回 OBS 推流地址；
//	主播需要把该地址填入 OBS 的推流设置里。
func (h *LiveHandler) StartLive(c *gin.Context) {
	id, err := strconv.ParseUint(c.Param("id"), 10, 64)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail("ID 错误"))
		return
	}
	room, pushURL, err := h.svc.StartLive(id)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(gin.H{
		"room":    room,
		"pushUrl": pushURL,
	}))
}

// EndLive 结束直播。
// 会把直播间状态改为未开播，并通知 ZLMediaKit 断开推流。
func (h *LiveHandler) EndLive(c *gin.Context) {
	id, err := strconv.ParseUint(c.Param("id"), 10, 64)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail("ID 错误"))
		return
	}
	if err := h.svc.EndLive(id); err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(nil))
}

// BanRoom 封禁直播间。
// 封禁后不允许推流，正在直播的会被强制断流。
func (h *LiveHandler) BanRoom(c *gin.Context) {
	id, err := strconv.ParseUint(c.Param("id"), 10, 64)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail("ID 错误"))
		return
	}
	if err := h.svc.BanRoom(id); err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(nil))
}

// ==================== 用户处罚接口 ====================

// BanUser 对直播间内用户执行禁言或踢出处罚。
//
// 请求体字段：
//   - roomId：直播间 ID（必填）；
//   - userId：被处罚用户 ID（必填）；
//   - type：处罚类型，0=禁言，1=踢出并禁言（必填）；
//   - minutes：处罚时长（分钟），0 或负数表示永久（可选，默认永久）；
//   - reason：处罚原因（可选）。
func (h *LiveHandler) BanUser(c *gin.Context) {
	var req struct {
		RoomID  uint64 `json:"roomId"`
		UserID  uint64 `json:"userId"`
		Type    int    `json:"type"`
		Minutes int    `json:"minutes"`
		Reason  string `json:"reason"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusOK, model.Fail("参数错误"))
		return
	}
	if req.RoomID == 0 || req.UserID == 0 {
		c.JSON(http.StatusOK, model.Fail("直播间ID和用户ID不能为空"))
		return
	}
	opUserID, _ := parseCurrentUser(c)
	if err := h.svc.BanUser(req.RoomID, req.UserID, req.Type, req.Minutes, req.Reason, opUserID); err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(nil))
}

// UnbanUser 撤销对某用户的处罚。
//
// 请求体字段：
//   - roomId：直播间 ID（必填）；
//   - userId：用户 ID（必填）。
func (h *LiveHandler) UnbanUser(c *gin.Context) {
	var req struct {
		RoomID uint64 `json:"roomId"`
		UserID uint64 `json:"userId"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusOK, model.Fail("参数错误"))
		return
	}
	if req.RoomID == 0 || req.UserID == 0 {
		c.JSON(http.StatusOK, model.Fail("直播间ID和用户ID不能为空"))
		return
	}
	if err := h.svc.UnbanUser(req.RoomID, req.UserID); err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(nil))
}

// ListBans 查询某直播间的处罚列表。
func (h *LiveHandler) ListBans(c *gin.Context) {
	roomID, err := strconv.ParseUint(c.Param("roomId"), 10, 64)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail("直播间ID 错误"))
		return
	}
	list, err := h.svc.ListBans(roomID)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(list))
}

// ==================== 观众端接口 ====================

// PlayInfo 获取直播间播放地址。
//
// 说明：
//
//	直播间必须处于直播中状态；
//	返回 flv、hls、webrtc 等多种协议地址，方便小程序/H5 按需选择。
func (h *LiveHandler) PlayInfo(c *gin.Context) {
	roomID, err := strconv.ParseUint(c.Param("id"), 10, 64)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail("ID 错误"))
		return
	}
	urls, err := h.svc.GetPlayURLs(roomID)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(gin.H{
		"roomId": roomID,
		"urls":   urls,
	}))
}

// WebSocket 直播间实时聊天/弹幕。
//
// 连接流程：
//  1. 解析 roomId；
//  2. 可选鉴权，获取用户信息；
//  3. 升级 WebSocket；
//  4. 加入房间；
//  5. 启动写协程，把 Hub 消息推给客户端；
//  6. 读循环：解析客户端消息并广播。
func (h *LiveHandler) WebSocket(c *gin.Context) {
	roomID, err := strconv.ParseUint(c.Param("id"), 10, 64)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail("直播间ID 错误"))
		return
	}

	userID, username := uint64(0), "游客"
	if id, ok := middleware.GetUserID(c); ok {
		userID = id
		username = middleware.GetUsername(c)
	}

	conn, err := websocket.Accept(c.Writer, c.Request, &websocket.AcceptOptions{
		InsecureSkipVerify: true,
	})
	if err != nil {
		slog.Error("直播 WebSocket 连接失败", "error", err)
		return
	}
	defer conn.Close(websocket.StatusNormalClosure, "")

	client, err := h.hub.Join(roomID, userID, username)
	if err != nil {
		slog.Error("加入直播间失败", "error", err)
		return
	}
	defer h.hub.Leave(client)

	// 写协程：从 Hub 接收消息并写入 WebSocket
	done := make(chan struct{})
	go func() {
		defer close(done)
		for msg := range client.Send {
			ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
			if err := conn.Write(ctx, websocket.MessageText, msg); err != nil {
				cancel()
				return
			}
			cancel()
		}
	}()

	// 读循环：读取客户端发来的聊天/弹幕消息
	for {
		_, data, err := conn.Read(c.Request.Context())
		if err != nil {
			slog.Info("直播 WebSocket 读取结束", "error", err)
			break
		}
		var msg struct {
			Type    string `json:"type"`    // chat / danmaku
			Content string `json:"content"` // 消息内容
			Color   string `json:"color"`   // 弹幕颜色
		}
		if err := json.Unmarshal(data, &msg); err != nil {
			continue
		}
		if msg.Content == "" {
			continue
		}
		if msg.Type == "" {
			msg.Type = "chat"
		}
		if _, err := h.hub.SendChat(client, msg.Content, msg.Type, msg.Color); err != nil {
			slog.Error("发送直播消息失败", "error", err)
		}
	}

	<-done
}

// ==================== ZLMediaKit Webhook ====================

// webhookSuccess 返回 ZLMediaKit 期望的成功响应。
// code=0 表示允许继续；其他值会拒绝推流/播放。
func webhookSuccess(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{"code": 0})
}

// webhookFail 返回 ZLMediaKit 期望的失败响应。
func webhookFail(c *gin.Context, msg string) {
	c.JSON(http.StatusOK, gin.H{"code": -1, "msg": msg})
}

// verifyWebhookSecret 校验 ZLMediaKit webhook 请求的 secret。
//
// 支持从以下位置获取：
//  1. URL 查询参数 ?secret=xxx；
//  2. x-www-form-urlencoded 表单字段 secret；
//  3. HTTP Header X-ZLM-Secret。
func (h *LiveHandler) verifyWebhookSecret(c *gin.Context) bool {
	secret := c.Query("secret")
	if secret == "" {
		secret = c.PostForm("secret")
	}
	if secret == "" {
		secret = c.GetHeader("X-ZLM-Secret")
	}
	return h.svc.VerifyWebhookSecret(secret)
}

// extractStreamID 从 ZLMediaKit webhook 请求中解析 stream_id。
//
// ZLMediaKit 默认以 application/x-www-form-urlencoded 发送事件，字段名为 stream；
// 部分版本或自定义配置可能以 JSON 发送，这里做兼容处理。
func extractStreamID(c *gin.Context) string {
	// 1. 优先从表单字段读取
	stream := c.PostForm("stream")
	if stream != "" {
		return stream
	}
	// 2. 尝试从 URL 查询参数读取
	stream = c.Query("stream")
	if stream != "" {
		return stream
	}
	// 3. 尝试解析 JSON body
	var body struct {
		Stream string `json:"stream"`
	}
	// ShouldBindBodyWith 会消耗 body，这里用 ShouldBindJSON 即可；
	// 如果前面已经读取了 form，body 可能为空，因此放在最后兜底。
	_ = c.ShouldBindJSON(&body)
	return body.Stream
}

// WebhookPublish 处理 ZLMediaKit on_publish 回调。
//
// 触发时机：OBS 开始向 ZLMediaKit 推流时。
// 业务逻辑：
//  1. 校验 webhook 密钥；
//  2. 根据 stream_id 找到直播间；
//  3. 如果直播间正常，标记为直播中并返回 code=0，允许收流；
//  4. 如果直播间不存在或被封禁，返回 code=-1，拒绝收流。
func (h *LiveHandler) WebhookPublish(c *gin.Context) {
	if !h.verifyWebhookSecret(c) {
		webhookFail(c, "webhook 密钥校验失败")
		return
	}
	streamID := extractStreamID(c)
	if streamID == "" {
		webhookFail(c, "缺少 stream_id")
		return
	}
	if err := h.svc.HandlePublishWebhook(streamID); err != nil {
		webhookFail(c, err.Error())
		return
	}
	webhookSuccess(c)
}

// WebhookUnpublish 处理 ZLMediaKit on_unpublish 回调。
//
// 触发时机：OBS 停止推流或网络断开时。
// 业务逻辑：把对应直播间状态改为未开播。
func (h *LiveHandler) WebhookUnpublish(c *gin.Context) {
	if !h.verifyWebhookSecret(c) {
		webhookFail(c, "webhook 密钥校验失败")
		return
	}
	streamID := extractStreamID(c)
	if streamID == "" {
		webhookFail(c, "缺少 stream_id")
		return
	}
	if err := h.svc.HandleUnpublishWebhook(streamID); err != nil {
		webhookFail(c, err.Error())
		return
	}
	webhookSuccess(c)
}

// WebhookPlay 处理 ZLMediaKit on_play 回调。
//
// 触发时机：观众成功开始拉流时。
// 业务逻辑：累计观看人数 +1。
func (h *LiveHandler) WebhookPlay(c *gin.Context) {
	if !h.verifyWebhookSecret(c) {
		webhookFail(c, "webhook 密钥校验失败")
		return
	}
	streamID := extractStreamID(c)
	if streamID == "" {
		webhookFail(c, "缺少 stream_id")
		return
	}
	if err := h.svc.HandlePlayWebhook(streamID); err != nil {
		webhookFail(c, err.Error())
		return
	}
	webhookSuccess(c)
}
