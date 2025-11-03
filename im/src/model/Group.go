package model

import (
	"gorm.io/gorm"
	"time"
)

type Group struct {
	gorm.Model
	GroupID      string    `gorm:"column:group_id;uniqueIndex;size:64"` // 群唯一ID（自定义，如G1001）
	GroupName    string    `gorm:"column:group_name;size:128"`          // 群名
	OwnerID      string    `gorm:"column:owner_id;size:64"`             // 群主ID（谁创建的群）
	Announcement string    `gorm:"column:announcement;type:text"`       // 群公告（可选）
	MaxMember    int       `gorm:"column:max_member;default:200"`       // 最大成员数（可选）
	CreateTime   time.Time `gorm:"column:create_time;autoCreateTime"`   // 群创建时间
}

func (Group) TableName() string {
	return "groups"
}
