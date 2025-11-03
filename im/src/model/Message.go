package model

import (
	"gorm.io/gorm"
	"time"
)

// 消息模型（优化版）
type Message struct {
	gorm.Model
	SessionID  string    `gorm:"column:session_id;index:idx_session_time"` // 会话ID（核心！标识一个聊天会话）
	FromUserID string    `gorm:"column:from_user_id;index"`                // 发送者ID（用户/系统）
	ToUserID   string    `gorm:"column:to_user_id"`                        // 接收者ID（单聊：用户ID；群聊：群ID）
	Content    string    `gorm:"column:content;type:text"`                 // 消息内容
	Type       int       `gorm:"column:type"`                              // 消息类型：1-单聊；2-群聊；3-系统消息
	Status     int       `gorm:"column:status;default:1"`                  // 消息状态：1-正常；2-撤回；3-删除
	SendTime   time.Time `gorm:"column:send_time;index:idx_session_time"`  // 发送时间（与SessionID联合索引）
	IsRead     bool      `gorm:"column:is_read;default:false"`             // 是否已读（单聊/群聊中对接收者的标记）
}

func (Message) TableName() string {
	return "messages"
}
