// Package handler 中的弹幕相关 HTTP/WebSocket 接口。
//
// 接口说明：
//   - GET /api/video/danmaku/:videoId        拉取历史弹幕；
//   - GET /api/video/danmaku/ws/:videoId     WebSocket 实时弹幕。
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

// DanmakuHandler 弹幕 HTTP 处理器。
type DanmakuHandler struct {
	hub *service.DanmakuHub
}

// NewDanmakuHandler 创建弹幕处理器实例。
func NewDanmakuHandler(hub *service.DanmakuHub) *DanmakuHandler {
	return &DanmakuHandler{hub: hub}
}

// WebSocket 弹幕 WebSocket 连接。
//
// 连接流程：
//  1. 解析 videoId；
//  2. 尝试从 Token 获取用户信息（未登录则为游客）；
//  3. 升级 WebSocket；
//  4. 加入弹幕房间并推送历史弹幕；
//  5. 启动写协程，把 Hub 中的消息发送给客户端；
//  6. 读循环：接收客户端发来的弹幕，交给 Hub 处理。
func (h *DanmakuHandler) WebSocket(c *gin.Context) {
	videoID, err := strconv.ParseUint(c.Param("videoId"), 10, 64)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail("videoId 错误"))
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
		slog.Error("WebSocket 连接失败", "error", err)
		return
	}
	defer conn.Close(websocket.StatusNormalClosure, "")

	client, history, err := h.hub.Join(videoID, userID, username)
	if err != nil {
		slog.Error("加入弹幕房间失败", "error", err)
		return
	}
	defer h.hub.Leave(client)

	// 发送历史弹幕
	if len(history) > 0 {
		data, _ := json.Marshal(map[string]any{
			"type": "history",
			"data": history,
		})
		_ = conn.Write(c.Request.Context(), websocket.MessageText, data)
	}

	// 启动写协程：从 client.Send 通道读取消息并写入 WebSocket
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

	// 读循环：读取客户端发来的弹幕
	for {
		_, data, err := conn.Read(c.Request.Context())
		if err != nil {
			slog.Info("WebSocket 读取结束", "error", err)
			break
		}
		var msg struct {
			Content string  `json:"content"`
			TimeAt  float64 `json:"timeAt"`
			Color   string  `json:"color"`
			Type    int     `json:"type"`
		}
		if err := json.Unmarshal(data, &msg); err != nil {
			continue
		}
		if msg.Content == "" {
			continue
		}
		if _, err := h.hub.SendDanmaku(client, msg.Content, msg.TimeAt, msg.Color, msg.Type); err != nil {
			slog.Error("发送弹幕失败", "error", err)
		}
	}

	<-done
}

// ListDanmaku HTTP 方式拉取历史弹幕。
// 查询参数：limit，默认 100。
func (h *DanmakuHandler) ListDanmaku(c *gin.Context) {
	videoID, err := strconv.ParseUint(c.Param("videoId"), 10, 64)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail("videoId 错误"))
		return
	}
	limit, _ := strconv.Atoi(c.DefaultQuery("limit", "100"))
	list, err := h.hub.ListHistory(videoID, limit)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(list))
}
