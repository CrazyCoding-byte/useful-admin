package model

import (
	"context"
	"nhooyr.io/websocket"
	"nhooyr.io/websocket/wsjson"
	"sync"
	"time"
)

type Session struct {
	// Session 单个WebSocket连接会话
	UserID     string          // 关联的用户ID（普通用户：user_xxx；客服：kf_xxx）
	Conn       *websocket.Conn // WebSocket连接实例
	LastActive time.Time       // 最后活跃时间（用于心跳检测）
	mu         sync.Mutex      // 保证写连接的线程安全
}
type SessionManager struct {
	userSession map[string][]*Session //userId->多个设备连接
	mu          sync.RWMutex
}

var GlobalSessionManager = NewSessionManager()

func NewSessionManager() *SessionManager {
	return &SessionManager{
		userSession: make(map[string][]*Session),
	}
}

func (m *SessionManager) Add(userId string, s *Session) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.userSession[userId] = append(m.userSession[userId], s)
}

// 删除用户连接
func (m *SessionManager) Remove(userId string, s *Session) {
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
func (m *SessionManager) GetUserSession(userId string) []*Session {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.userSession[userId]
}

// sendJson 线程安全发json
func (s *Session) SendJSON(v interface{}) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	return wsjson.Write(context.Background(), s.Conn, v)
}
