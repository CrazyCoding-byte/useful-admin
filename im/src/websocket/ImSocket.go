package websocket

import (
	"context"
	"fmt"
	"local/im/src/config"
	"local/im/src/utils"
	"log"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"nhooyr.io/websocket"
	"nhooyr.io/websocket/wsjson"
)

type WebSocketServer struct {
	aesKey string
}

// 加载客户端必须要从oauth2服务校验
func NewWebSocketServer() *WebSocketServer {
	// 从配置文件加载 aseKey.key
	// 尝试多个可能的配置文件路径
	configPaths := []string{
		"src/application.yml",    // 从 im 目录运行
		"../src/application.yml", // 从其他目录运行
		"E:\\studyoauth2\\springcloud-oauth2\\im\\src\\application.yml", // 绝对路径（兼容 main.go）
	}

	var cfg *config.Config
	var err error
	for _, configPath := range configPaths {
		cfg, err = config.LoadConfig[config.Config](configPath)
		if err == nil {
			slog.Info("成功加载配置文件", "path", configPath)
			break
		}
	}

	if err != nil {
		slog.Error("加载配置失败，所有路径尝试均失败", "error", err)
		return &WebSocketServer{aesKey: ""}
	}

	return &WebSocketServer{
		aesKey: cfg.AES.Key,
	}
}

func (s *WebSocketServer) Start(addr string) error {
	http.HandleFunc("/ws", s.handleWebSocket)
	http.HandleFunc("/auth/verify", s.handleAuthVerify)

	slog.Info("WebSocket 服务器启动", "addr", addr)
	return http.ListenAndServe(addr, nil)
}

func (s *WebSocketServer) handleWebSocket(w http.ResponseWriter, r *http.Request) {
	// 从请求中获取 Token
	token, err := s.extractToken(r)
	if err != nil {
		log.Printf("Token 提取失败: %v", err)
		http.Error(w, "Unauthorized: "+err.Error(), http.StatusUnauthorized)
		return
	}

	// 验证并解析 Token
	userID, username, err := utils.ParseAndVerifyToken(token, s.aesKey)
	if err != nil {
		log.Printf("Token 验证失败: %v", err)
		http.Error(w, "Unauthorized: "+err.Error(), http.StatusUnauthorized)
		return
	}

	// 升级到 WebSocket 连接
	conn, err := websocket.Accept(w, r, &websocket.AcceptOptions{
		InsecureSkipVerify: true,
	})
	if err != nil {
		log.Printf("WebSocket 升级失败: %v", err)
		return
	}
	defer conn.Close(websocket.StatusInternalError, "内部错误")

	log.Printf("用户连接成功 - ID: %s, 用户名: %s", userID, username)

	// 处理 WebSocket 连接 - 使用新的 context，不需要传递用户信息
	if err := s.handleConnection(conn, userID, username); err != nil {
		log.Printf("连接处理错误: %v", err)
	}
}

func (s *WebSocketServer) handleAuthVerify(w http.ResponseWriter, r *http.Request) {
	if r.Method != "POST" {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	token, err := s.extractToken(r)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	userID, username, err := utils.ParseAndVerifyToken(token, s.aesKey)
	if err != nil {
		http.Error(w, err.Error(), http.StatusUnauthorized)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.Write([]byte(fmt.Sprintf(`{"status": "success", "user_id": "%s", "username": "%s"}`, userID, username)))
}

func (s *WebSocketServer) extractToken(r *http.Request) (string, error) {
	// 1. 尝试从查询参数获取
	token := r.URL.Query().Get("token")
	if token != "" {
		return token, nil
	}

	// 2. 尝试从 Authorization Header 获取
	authHeader := r.Header.Get("Authorization")
	if authHeader != "" {
		parts := strings.Split(authHeader, " ")
		if len(parts) == 2 && parts[0] == "Bearer" {
			return parts[1], nil
		}
		return "", fmt.Errorf("无效的 Authorization header 格式")
	}

	// 3. 尝试从 Cookie 获取
	cookie, err := r.Cookie("auth_token")
	if err == nil && cookie.Value != "" {
		return cookie.Value, nil
	}

	return "", fmt.Errorf("未找到 Token")
}

func (s *WebSocketServer) handleConnection(conn *websocket.Conn, userID, username string) error {
	// 连接建立事件
	s.onConnect(conn, userID, username)

	defer func() {
		// 连接断开事件
		s.onDisconnect(userID, username)
	}()

	// 使用 background context，因为不需要从 HTTP 请求继承上下文
	ctx := context.Background()

	for {
		var message map[string]interface{}
		err := wsjson.Read(ctx, conn, &message)
		if err != nil {
			return err
		}

		// 消息接收事件
		s.onMessage(conn, userID, username, message)

		// 处理不同类型的消息
		if err := s.handleMessage(ctx, conn, userID, username, message); err != nil {
			return err
		}
	}
}

func (s *WebSocketServer) onConnect(conn *websocket.Conn, userID, username string) {
	log.Printf("用户上线 - ID: %s, 用户名: %s", userID, username)

	welcomeMsg := map[string]interface{}{
		"type":      "welcome",
		"message":   "连接成功",
		"user_id":   userID,
		"username":  username,
		"timestamp": time.Now().Unix(),
	}
	wsjson.Write(context.Background(), conn, welcomeMsg)
}

func (s *WebSocketServer) onMessage(conn *websocket.Conn, userID, username string, message interface{}) {
	log.Printf("收到消息 - 用户: %s, 内容: %v", username, message)
}

func (s *WebSocketServer) onDisconnect(userID, username string) {
	log.Printf("用户下线 - ID: %s, 用户名: %s", userID, username)
}

func (s *WebSocketServer) handleMessage(ctx context.Context, conn *websocket.Conn, userID, username string, message map[string]interface{}) error {
	msgType, ok := message["type"].(string)
	if !ok {
		return fmt.Errorf("消息类型缺失")
	}

	switch msgType {
	case "ping":
		return s.handlePing(ctx, conn)
	case "chat":
		return s.handleChat(ctx, conn, username, message)
	case "broadcast":
		return s.handleBroadcast(ctx, conn, username, message)
	default:
		return s.handleUnknown(ctx, conn, message)
	}
}

func (s *WebSocketServer) handlePing(ctx context.Context, conn *websocket.Conn) error {
	response := map[string]interface{}{
		"type":      "pong",
		"timestamp": time.Now().Unix(),
	}
	return wsjson.Write(ctx, conn, response)
}

func (s *WebSocketServer) handleChat(ctx context.Context, conn *websocket.Conn, username string, message map[string]interface{}) error {
	content, ok := message["content"].(string)
	if !ok {
		return fmt.Errorf("聊天内容缺失")
	}

	response := map[string]interface{}{
		"type":      "chat",
		"from":      username,
		"content":   content,
		"timestamp": time.Now().Unix(),
	}
	return wsjson.Write(ctx, conn, response)
}

func (s *WebSocketServer) handleBroadcast(ctx context.Context, conn *websocket.Conn, username string, message map[string]interface{}) error {
	content, ok := message["content"].(string)
	if !ok {
		return fmt.Errorf("广播内容缺失")
	}

	// 这里可以实现广播逻辑
	log.Printf("广播消息 - 用户: %s, 内容: %s", username, content)

	response := map[string]interface{}{
		"type":      "broadcast_ack",
		"status":    "sent",
		"timestamp": time.Now().Unix(),
	}
	return wsjson.Write(ctx, conn, response)
}

func (s *WebSocketServer) handleUnknown(ctx context.Context, conn *websocket.Conn, message map[string]interface{}) error {
	response := map[string]interface{}{
		"type":      "error",
		"message":   "未知的消息类型",
		"timestamp": time.Now().Unix(),
	}
	return wsjson.Write(ctx, conn, response)
}
