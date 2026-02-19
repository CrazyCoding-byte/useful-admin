package websocket

import (
	"context"
	"fmt"
	"local/im/src/config"
	"local/im/src/model"
	"local/im/src/repository"
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
	aesKey  string
	msgRepo *repository.MessageService
}

// 加载客户端必须要从oauth2服务校验
func NewWebSocketServer(msgRepo *repository.MessageService) *WebSocketServer {
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
		aesKey:  cfg.AES.Key,
		msgRepo: msgRepo,
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
	//1.创建UserSession实例
	userSession := model.UserSession{
		UserID:     userID,
		Username:   username,
		Conn:       conn,
		LoginTime:  time.Now(),
		LastActive: time.Now(),
		DeviceInfo: "web",
		IsOnline:   true,
	}
	// 连接建立事件
	s.onConnect(&userSession)

	defer func() {
		// 连接断开事件
		s.onDisconnect(&userSession)
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
		s.onMessage(&userSession, message)

		// 处理不同类型的消息
		if err := s.handleMessage(ctx, &userSession, message); err != nil {
			return err
		}
	}
}

func (s *WebSocketServer) onConnect(userSession *model.UserSession) {
	log.Printf("用户上线 - ID: %s, 用户名: %s", userSession.UserID, userSession.Username)
	//加入全局Session管理器
	model.GlobalSessionManager.Add(userSession.UserID, userSession)
	welcomeMsg := map[string]interface{}{
		"type":      "welcome",
		"message":   "连接成功",
		"user_id":   userSession.UserID,
		"username":  userSession.Username,
		"timestamp": time.Now().Unix(),
	}
	if err := userSession.SendJSON(welcomeMsg); err != nil {
		log.Printf("发送欢迎消息失败: %v", err)
	}
}

func (s *WebSocketServer) onDisconnect(userSession *model.UserSession) {
	log.Printf("用户下线 - ID: %s, 用户名: %s", userSession.UserID, userSession.Username)
	model.GlobalSessionManager.Remove(userSession.UserID, userSession)
	userSession.IsOnline = false
}
func (s *WebSocketServer) onMessage(userSession *model.UserSession, message interface{}) {
	userSession.LastActive = time.Now()
	log.Printf("收到消息 - ID: %s, 用户名: %s, 消息内容: %v", userSession.UserID, userSession.Username, message)
}
func (s *WebSocketServer) handleMessage(ctx context.Context, userSession *model.UserSession, message map[string]interface{}) error {
	msgType, ok := message["type"].(string)
	if !ok {
		return fmt.Errorf("消息类型缺失")
	}

	switch msgType {
	case "ping":
		return s.handlePing(ctx, userSession)
	case "chat":
		return s.handleChat(ctx, userSession, message)
	case "group_chat":
		return s.handleGroupChat(ctx, userSession, message)
	}
}

func (s *WebSocketServer) handlePing(ctx context.Context, userSession *model.UserSession) error {
	response := map[string]interface{}{
		"type":      "pong",
		"timestamp": time.Now().Unix(),
	}
	return userSession.SendJSON(response)
}

func (s *WebSocketServer) handleChat(ctx context.Context, fromSession *model.UserSession, message map[string]interface{}) error {
	content, ok := message["content"].(string)
	if !ok {
		return fmt.Errorf("聊天内容缺失")
	}

	toUserId, ok := message["to_user_id"].(string)
	if !ok {
		return fmt.Errorf("接收用户ID缺失")
	}
	//保存
	err := s.msgRepo.SaveUserMessage(fromSession.UserID, toUserId, content)
	if err != nil {
		return err
	}
	//构造推送消息
	pushMsg := map[string]interface{}{
		"type":      "chat",
		"from":      fromSession.UserID,
		"from_name": fromSession.Username,
		"to":        toUserId,
		"content":   content,
		"timestamp": time.Now().Unix(),
	}
	//推送接受方
	toSessions := model.GlobalSessionManager.GetUserSession(toUserId)
	for _, toSession := range toSessions {
		go func(s *model.UserSession) {
			if err := s.SendJSON(pushMsg); err != nil {
				slog.Info("推送单聊消息失败,移除无效连接:%v", err)
				model.GlobalSessionManager.Remove(toUserId, toSession)
			}
		}(toSession)
	}
	//回显
	return fromSession.SendJSON(pushMsg)
}

func (s *WebSocketServer) handleGroupChat(ctx context.Context, fromSession *model.UserSession, message map[string]interface{}) error {
	content, ok := message["content"].(string)
	if !ok {
		return s.sendError(fromSession, "聊天内容缺失")
	}
	groupId, ok := message["group_id"].(string)
	if !ok {
		return s.sendError(fromSession, "群组ID缺失")
	}
	//保存
	err := s.msgRepo.SaveGroupMessage(fromSession.UserID, groupId, content)
	if err != nil {
		return s.sendError(fromSession, "保存群聊消息失败")
	}
}

func (s *WebSocketServer) handleUnknown(ctx context.Context, conn *websocket.Conn, message map[string]interface{}) error {
	response := map[string]interface{}{
		"type":      "error",
		"message":   "未知的消息类型",
		"timestamp": time.Now().Unix(),
	}
	return wsjson.Write(ctx, conn, response)
}
func (s *WebSocketServer) sendError(userSession *model.UserSession, errMsg string) error {
	return userSession.SendJSON(map[string]interface{}{
		"type":      "error",
		"message":   errMsg,
		"timestamp": time.Now().Unix(),
	})
}
