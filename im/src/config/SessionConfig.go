package config

import (
	"fmt"
	"local/im/src/model"
	"log"
	"nhooyr.io/websocket"
	"sync"
	"time"
)

type Manager struct {
	userSessionManager *UserSessionManager
	chatSessionManager *ChatSessionManager
	groupManager       *GroupManager
}

func NewManager() *Manager {
	return &Manager{
		userSessionManager: NewUserSessionManager(),
		chatSessionManager: NewChatSessionManager(),
		groupManager:       NewGroupManager(),
	}
}

// UserSessionManager 用户连接Session管理
type UserSessionManager struct {
	userSessions map[string][]*model.UserSession        // userID -> sessions
	connSessions map[*websocket.Conn]*model.UserSession // conn -> session
	mutex        sync.RWMutex
}

func NewUserSessionManager() *UserSessionManager {
	return &UserSessionManager{
		userSessions: make(map[string][]*model.UserSession), //key是stirng value是*model.UserSession
		connSessions: make(map[*websocket.Conn]*model.UserSession),
	}
}

// AddSession 添加用户Session
func (sm *UserSessionManager) AddSession(userID, username string, conn *websocket.Conn, deviceInfo string) {
	sm.mutex.Lock()
	defer sm.mutex.Unlock()

	session := &model.UserSession{
		UserID:     userID,
		Username:   username,
		Conn:       conn,
		LoginTime:  time.Now(),
		LastActive: time.Now(),
		DeviceInfo: deviceInfo,
		IsOnline:   true,
	}

	// 添加到用户Session列表
	sm.userSessions[userID] = append(sm.userSessions[userID], session)
	// 添加到连接映射
	sm.connSessions[conn] = session

	log.Printf("用户Session创建 - 用户: %s, 设备: %s, 总连接数: %d",
		username, deviceInfo, len(sm.userSessions[userID]))
}

// RemoveSession 移除用户Session
func (sm *UserSessionManager) RemoveSession(conn *websocket.Conn) {
	sm.mutex.Lock()
	defer sm.mutex.Unlock()

	session, exists := sm.connSessions[conn]
	if !exists {
		return
	}

	// 从连接映射中移除
	delete(sm.connSessions, conn)

	// 从用户Session列表中移除
	sessions := sm.userSessions[session.UserID]
	for i, s := range sessions {
		if s.Conn == conn {
			sm.userSessions[session.UserID] = append(sessions[:i], sessions[i+1:]...)
			break
		}
	}

	// 如果用户没有Session了，清理用户条目
	if len(sm.userSessions[session.UserID]) == 0 {
		delete(sm.userSessions, session.UserID)
	}

	log.Printf("用户Session移除 - 用户: %s, 设备: %s",
		session.Username, session.DeviceInfo)
}

// GetUserSessions 获取用户的所有Session
func (sm *UserSessionManager) GetUserSessions(userID string) []*model.UserSession {
	sm.mutex.RLock()
	defer sm.mutex.RUnlock()

	return sm.userSessions[userID]
}

// IsUserOnline 检查用户是否在线
func (sm *UserSessionManager) IsUserOnline(userID string) bool {
	sm.mutex.RLock()
	defer sm.mutex.RUnlock()

	sessions, exists := sm.userSessions[userID]
	if !exists {
		return false
	}

	for _, session := range sessions {
		if session.IsOnline {
			return true
		}
	}
	return false
}

// UpdateActivity 更新用户活动时间
func (sm *UserSessionManager) UpdateActivity(conn *websocket.Conn) {
	sm.mutex.Lock()
	defer sm.mutex.Unlock()

	if session, exists := sm.connSessions[conn]; exists {
		session.LastActive = time.Now()
	}
}

// ChatSessionManager 聊天会话管理
type ChatSessionManager struct {
	sessions     map[string]*model.ChatSession // sessionID -> chatSession
	userSessions map[string][]string           // userID -> sessionIDs
	mutex        sync.RWMutex
}

func NewChatSessionManager() *ChatSessionManager {
	return &ChatSessionManager{
		sessions:     make(map[string]*model.ChatSession),
		userSessions: make(map[string][]string),
	}
}

// GetOrCreateSingleChat 获取或创建单聊会话
func (csm *ChatSessionManager) GetOrCreateSingleChat(userID1, userID2 string) string {
	csm.mutex.Lock()
	defer csm.mutex.Unlock()

	// 生成会话ID（确保顺序一致）
	sessionID := generateSingleChatID(userID1, userID2)

	// 检查是否已存在
	if session, exists := csm.sessions[sessionID]; exists {
		return session.SessionID
	}

	// 创建新会话
	session := &model.ChatSession{
		SessionID:    sessionID,
		Type:         1, // 单聊
		Participants: []string{userID1, userID2},
		CreatedAt:    time.Now(),
		LastMessage:  time.Now(),
	}

	csm.sessions[sessionID] = session

	// 更新用户会话映射
	csm.userSessions[userID1] = append(csm.userSessions[userID1], sessionID)
	csm.userSessions[userID2] = append(csm.userSessions[userID2], sessionID)

	return sessionID
}

// GetOrCreateGroupChat 获取或创建群聊会话
func (csm *ChatSessionManager) GetOrCreateGroupChat(groupID string, members []string) string {
	csm.mutex.Lock()
	defer csm.mutex.Unlock()

	sessionID := "group_" + groupID

	// 检查是否已存在
	if session, exists := csm.sessions[sessionID]; exists {
		return session.SessionID
	}

	// 创建新会话
	session := &model.ChatSession{
		SessionID:    sessionID,
		Type:         2, // 群聊
		Participants: members,
		CreatedAt:    time.Now(),
		LastMessage:  time.Now(),
	}

	csm.sessions[sessionID] = session

	// 更新所有成员的会话映射
	for _, member := range members {
		csm.userSessions[member] = append(csm.userSessions[member], sessionID)
	}

	return sessionID
}

// GetUserChatSessions 获取用户的所有聊天会话
func (csm *ChatSessionManager) GetUserChatSessions(userID string) []*model.ChatSession {
	csm.mutex.RLock()
	defer csm.mutex.RUnlock()

	var userSessions []*model.ChatSession
	sessionIDs, exists := csm.userSessions[userID]
	if !exists {
		return userSessions
	}

	for _, sessionID := range sessionIDs {
		if session, exists := csm.sessions[sessionID]; exists {
			userSessions = append(userSessions, session)
		}
	}

	return userSessions
}

// GroupManager 群组管理
type GroupManager struct {
	groups  map[string]*model.Group    // groupID -> group
	members map[string]map[string]bool // groupID -> memberIDs
	mutex   sync.RWMutex
}

func NewGroupManager() *GroupManager {
	return &GroupManager{
		groups:  make(map[string]*model.Group),
		members: make(map[string]map[string]bool),
	}
}

// 生成单聊会话ID（确保userID1和userID2顺序无关）
func generateSingleChatID(userID1, userID2 string) string {
	if userID1 < userID2 {
		return fmt.Sprintf("single_%s_%s", userID1, userID2)
	}
	return fmt.Sprintf("single_%s_%s", userID2, userID1)
}
