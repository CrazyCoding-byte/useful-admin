package model

import (
	"gorm.io/gorm"
	"time"
)

type GroupMember struct {
	gorm.Model
	GroupID  string    `gorm:"column:group_id;index:idx_group_member"`  // 关联群ID
	MemberID string    `gorm:"column:member_id;index:idx_group_member"` // 群成员ID（用户ID）
	Role     int       `gorm:"column:role;default:2"`                   // 成员角色：1=群主，2=管理员，3=普通成员
	JoinTime time.Time `gorm:"column:join_time;autoCreateTime"`         // 加入群的时间
	IsQuit   bool      `gorm:"column:is_quit;default:false"`            // 是否退出群（软删除，保留历史）
}

func (GroupMember) TableName() string {
	return "group_members"
}
