package model

import (
	"context"
	"nhooyr.io/websocket"
	"nhooyr.io/websocket/wsjson"
	"sync"
	"time"
)

// -------------------------- 完善后的会话结构体 --------------------------
// UserSession 用户WebSocket连接会话（关联用户和连接）
type UserSession struct {
	UserID     string          `json:"user_id"`     // 关联用户ID
	Username   string          `json:"username"`    // 用户名
	Conn       *websocket.Conn `json:"-"`           // WebSocket连接（不序列化）
	LoginTime  time.Time       `json:"login_time"`  // 登录时间
	LastActive time.Time       `json:"last_active"` // 最后活跃时间
	DeviceInfo string          `json:"device_info"` // 设备信息（如web/ios/android）
	IsOnline   bool            `json:"is_online"`   // 是否在线
	mu         sync.Mutex      // 保证写连接的线程安全（新增：原Session里的锁移过来）
}

// SessionManager 连接管理器（适配新的UserSession）
type SessionManager struct {
	userSession map[string][]*UserSession // userId->多个设备连接
	mu          sync.RWMutex
}

// 全局连接管理器（单例）
var GlobalSessionManager = NewSessionManager()

// NewSessionManager 初始化管理器
func NewSessionManager() *SessionManager {
	return &SessionManager{
		userSession: make(map[string][]*UserSession),
	}
}

// Add 添加用户连接
func (m *SessionManager) Add(userId string, s *UserSession) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.userSession[userId] = append(m.userSession[userId], s)
}

// Remove 删除用户连接
func (m *SessionManager) Remove(userId string, s *UserSession) {
	m.mu.Lock()
	defer m.mu.Unlock()
	list := m.userSession[userId]
	for i, item := range list {
		if item == s {
			m.userSession[userId] = append(list[:i], list[i+1:]...)
			break
		}
	}
	if len(m.userSession[userId]) == 0 {
		delete(m.userSession, userId)
	}
}

// GetUserSession 获取用户所有连接
func (m *SessionManager) GetUserSession(userId string) []*UserSession {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.userSession[userId]
}

// SendJSON 线程安全发送JSON消息（适配新的UserSession）
func (s *UserSession) SendJSON(v interface{}) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	return wsjson.Write(context.Background(), s.Conn, v)
}
