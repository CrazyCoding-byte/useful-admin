package repository

import (
	"local/im/src/model"
	"time"
)

// 创建一个组
func saveGoup(service *MessageService, createUserId string, groupName string) error {
	group := model.Group{
		GroupID:   "G" + time.Now().Format("20060102"),
		GroupName: groupName,
		OwnerID:   createUserId,
	}
	groupMember := model.GroupMember{
		GroupID:  group.GroupID,
		MemberID: createUserId,
		Role:     1,
	}
	service.db.Create(&groupMember)
	return service.db.Create(&group).Error
}

// 查询组信息(组信息有几种 一种是全是用户组信息,一种是用户和客服信息)
func getGroupInfo(service *MessageService, groupId string) ([]model.Message, error) {
	var messages []model.Message
	result := service.db.Where("session_id = ? AND type=2", groupId).
		Order("send_time Desc").
		Limit(20).
		Find(&messages)
	if result.Error != nil {
		return nil, result.Error
	}
	return messages, nil
}

// 根据当前的用户id查询所在群
func getGroupByUserId(service *MessageService, userId string) ([]model.Group, error) {
	var group []model.Group
	result := service.db.Where("select group_id from group_member where member_id = ?", userId).Find(&group)
	if result.Error != nil {
		return nil, result.Error
	}
	return group, nil
}
