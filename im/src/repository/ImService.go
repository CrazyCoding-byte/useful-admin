package repository

import (
	"gorm.io/gorm"
)

// MessageService 依赖 GORM 的 *gorm.DB
type MessageService struct {
	db *gorm.DB
}

func newMessageService(db *gorm.DB) *MessageService {
	return &MessageService{db: db}
}

// 创建群聊
func (m *MessageService) CreateGroup(groupName string, creatorId string) error {
	return saveGoup(m, groupName, creatorId)
}

// 添加群成员
func (m *MessageService) AddGroupMember(groupId string, userIds string) error {

}

//消息
