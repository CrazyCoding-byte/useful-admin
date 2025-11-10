package model

import (
	"nhooyr.io/websocket"
	"sync"
	"time"
)

type MessageReq struct {
	FromUserID string `json:"from_user_id"` // 发送者ID
	ToType     int    `json:"to_type"`      // 接收类型：1-用户；2-群；3-客服
	ToID       string `json:"to_id"`        // 接收者ID：用户ID/群ID/客服ID
	Content    string `json:"content"`      // 消息内容
	Type       int    `json:"type"`         // 消息类型：文本/图片等
}

// Session 单个WebSocket连接会话
type Session struct {
	UserID     string          // 关联的用户ID（普通用户：user_xxx；客服：kf_xxx）
	Conn       *websocket.Conn // WebSocket连接实例
	LastActive time.Time       // 最后活跃时间（用于心跳检测）
	mu         sync.Mutex      // 保证写连接的线程安全
}

// 一个用户对于多个不同操作系统的 session
type SessionManager struct {
	session map[string][]*Session // key: UserID
	mu      sync.RWMutex
}

// Send 线程安全地发送消息
func (s *Session) Send(message []byte) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.Conn.Write(websocket.MessageText, message)
}
func NewSessionManager() *SessionManager {
	return &SessionManager{
		session: make(map[string][]*Session),
	}
}

// 添加用户session
func (m *SessionManager) add(UserId string, session *Session) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.session[UserId] = append(m.session[UserId], session)
}

func (m *SessionManager) remove(UserId string, session *Session) {
	m.mu.Lock()
	defer m.mu.Unlock()
	if sessions, ok := m.session[UserId]; ok {
		// 从切片中删除指定session
		for i, s := range sessions {
			if s == session {
				m.session[UserId] = append(sessions[:i], sessions[i+1:]...)
				break
			}
		}
		// 如果用户没有在线Session了，删除map的key
		if len(m.session[UserId]) == 0 {
			delete(m.session, UserId)
		}
	}
}

// Get 获取用户的所有的session
func (m *SessionManager) Get(UserId string) []*Session {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.session[UserId]
}

// GetAll 获取所有用户的session
func (m *SessionManager) GetAll() map[string][]*Session {
	m.mu.RLock()
	defer m.mu.RUnlock()
	copySession := make(map[string][]*Session, len(m.session))
	for k, v := range m.session {
		copySession[k] = v
	}
	return copySession
}
