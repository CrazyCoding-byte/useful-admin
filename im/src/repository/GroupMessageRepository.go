package repository

import (
	"fmt"
	"local/im/src/model"
	"time"
)

// 创建一个组
// 创建一个组（修正拼写：saveGoup → saveGroup）
func saveGroup(service *MessageService, createUserId string, groupName string) error {
	// 1. 生成唯一群ID
	group := model.Group{
		GroupID:   "G" + time.Now().Format("20060102"), // 唯一ID
		GroupName: groupName,
		OwnerID:   createUserId,
	}

	// 2. 先创建群（确保群存在）
	if err := service.db.Create(&group).Error; err != nil {
		return fmt.Errorf("创建群失败：%w", err)
	}

	// 3. 再创建群主的群成员记录（群主自动加入）
	groupMember := model.GroupMember{
		GroupID:  group.GroupID,
		MemberID: createUserId,
		Role:     1, // 1=群主
	}
	if err := service.db.Create(&groupMember).Error; err != nil {
		// 可选：如果成员创建失败，删除已创建的群（保证数据一致性）
		service.db.Delete(&group)
		return fmt.Errorf("添加群主到群失败：%w", err)
	}

	return nil
}

// 查询群聊天信息(组信息有几种 一种是全是用户组信息,一种是用户和客服信息)
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

// 根据用户ID查询所在的所有群
func getGroupsByUserId(service *MessageService, userId string) ([]model.Group, error) {
	var groups []model.Group
	// 关联查询：通过group_members表找到用户所在的群ID，再查询群信息
	// 步骤：1. 查group_members表，找到userId对应的group_id；2. 用group_id查groups表
	result := service.db.
		Table("groups").                                                                // 主表：groups
		Joins("JOIN group_members ON groups.group_id = group_members.group_id").        // 关联群成员表
		Where("group_members.member_id = ? AND group_members.is_quit = false", userId). // 用户是成员且未退出
		Find(&groups)

	if result.Error != nil {
		return nil, fmt.Errorf("查询用户所在群失败：%w", result.Error)
	}
	return groups, nil
}
