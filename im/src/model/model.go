package model

import (
	"nhooyr.io/websocket"
	"time"
)

// -------------------------- 核心数据库表结构体 --------------------------
// User 用户表（简化版，可根据你的实际需求扩展）
type User struct {
	UserID    string    `gorm:"primaryKey;column:user_id"` // 用户ID（普通用户：user_xxx；客服：kf_xxx）
	Username  string    `gorm:"column:username"`           // 昵称
	UserType  int       `gorm:"column:user_type"`          // 1-普通用户；2-客服
	Avatar    string    `gorm:"column:avatar"`             // 头像
	CreatedAt time.Time `gorm:"column:created_at"`
	UpdatedAt time.Time `gorm:"column:updated_at"`
}

// Message 消息表（单聊/群聊/客服消息都存在这里）
type Message struct {
	ID         uint64    `gorm:"primaryKey;autoIncrement;column:id"` // 自增ID
	SessionID  string    `gorm:"column:session_id;index"`            // 会话ID（单聊：有序用户ID拼接；群聊：群ID）
	FromUserID string    `gorm:"column:from_user_id;index"`          // 发送者ID
	ToUserID   string    `gorm:"column:to_user_id"`                  // 接收者ID（单聊：对方ID；群聊：群ID）
	Content    string    `gorm:"column:content"`                     // 消息内容
	Type       int       `gorm:"column:type;index"`                  // 1-单聊；2-群聊
	SendTime   time.Time `gorm:"column:send_time;index"`             // 发送时间
	IsRead     bool      `gorm:"column:is_read"`                     // 已读状态（单聊有效；群聊需单独表）
	Status     int       `gorm:"column:status;default:1"`            // 新增：消息状态（1-成功；2-失败；0-发送中
}

// Group 群表
type Group struct {
	GroupID   string    `gorm:"primaryKey;column:group_id"` // 群ID（如g1001）
	GroupName string    `gorm:"column:group_name"`          // 群名
	OwnerID   string    `gorm:"column:owner_id;index"`      // 群主ID（可是用户/客服）
	MaxMember int       `gorm:"column:max_member"`          // 最大成员数
	Desc      string    `gorm:"column:desc"`                // 群描述
	CreatedAt time.Time `gorm:"column:created_at"`
	UpdatedAt time.Time `gorm:"column:updated_at"`
}

// GroupMember 群成员表
type GroupMember struct {
	ID       uint64    `gorm:"primaryKey;autoIncrement;column:id"`
	GroupID  string    `gorm:"column:group_id;index"`  // 群ID
	MemberID string    `gorm:"column:member_id;index"` // 成员ID（用户/客服）
	Role     int       `gorm:"column:role"`            // 1-群主；2-管理员；3-普通成员
	IsQuit   bool      `gorm:"column:is_quit"`         // 是否退出群
	JoinTime time.Time `gorm:"column:join_time"`
	QuitTime time.Time `gorm:"column:quit_time;null"`
}

// -------------------------- 完善已有会话结构体 --------------------------
// UserSession 用户WebSocket连接会话（关联用户和连接）
type UserSession struct {
	UserID     string          `json:"user_id"`     // 关联用户ID
	Username   string          `json:"username"`    // 用户名
	Conn       *websocket.Conn `json:"-"`           // WebSocket连接（不序列化）
	LoginTime  time.Time       `json:"login_time"`  // 登录时间
	LastActive time.Time       `json:"last_active"` // 最后活跃时间
	DeviceInfo string          `json:"device_info"` // 设备信息（如web/ios/android）
	IsOnline   bool            `json:"is_online"`   // 是否在线
}

// ChatSession 聊天会话（单聊/群聊的会话元信息）
type ChatSession struct {
	SessionID    string    `json:"session_id"`   // 会话ID
	Type         int       `json:"type"`         // 1-单聊；2-群聊
	Participants []string  `json:"participants"` // 参与者ID（单聊：[u1,u2]；群聊：群成员列表）
	CreatedAt    time.Time `json:"created_at"`   // 会话创建时间
	LastMessage  time.Time `json:"last_message"` // 最后一条消息时间
}

// GroupMsgRead 群消息已读表（可选，群聊已读状态专用）
type GroupMsgRead struct {
	ID       uint64    `gorm:"primaryKey;autoIncrement;column:id"`
	MsgID    uint64    `gorm:"column:msg_id;index"`    // 消息ID
	GroupID  string    `gorm:"column:group_id;index"`  // 群ID
	MemberID string    `gorm:"column:member_id;index"` // 成员ID
	ReadTime time.Time `gorm:"column:read_time"`       // 已读时间
}

// -------------------------- 原有会话管理结构体（完善） --------------------------
// MessageReq 前端消息请求体
type MessageReq struct {
	FromUserID string `json:"from_user_id"` // 发送者ID
	ToType     int    `json:"to_type"`      // 接收类型：1-用户；2-群；3-客服（最终归为单聊）
	ToID       string `json:"to_id"`        // 接收者ID：用户ID/群ID/客服ID
	Content    string `json:"content"`      // 消息内容
	Type       int    `json:"type"`         // 消息类型：1-文本；2-图片等（和会话类型区分）
}
