package repository

import (
	"fmt"
	"github.com/google/uuid"
	"gorm.io/gorm"
	"local/im/src/model"
	"time"
)

type MessageService struct {
	Db *gorm.DB
}

/*
 场景1:单聊消息存储+查询(对齐你的业务规则)
*/

// saveUserMessage 存储单聊消息(用户->用户/客服,生成有序SessionId)
func SaveUserMessage(m *MessageService, fromUserId string, receiveUserId string, content string) error {
	sessionId := buildSingleSessionId(fromUserId, receiveUserId)
	message := model.Message{
		FromUserID: fromUserId,
		ToUserID:   receiveUserId,
		Content:    content,
		Type:       1,
		Status:     1,
		SessionID:  sessionId,
		SendTime:   time.Now(),
		IsRead:     false,
	}
	if err := m.Db.Create(&message).Error; err != nil {
		return fmt.Errorf("保存消息失败：%w", err)
	}
	return nil
}
func buildSingleSessionId(fromUserId string, toUserId string) string {
	return fromUserId + "_" + toUserId
}

// 拉取当前用户与目标用户的双向聊天数据（修正拼写：ReciveUserId → ReceiveUserId）
func GetUserMessage(m *MessageService, currentUserID string, targetUserID string, page, size int) ([]model.Message, error) {
	var messages []model.Message
	sessionId := buildSingleSessionId(currentUserID, targetUserID)
	// 条件：(当前用户发给目标用户) OR (目标用户发给当前用户)，确保双向消息都包含
	result := m.Db.Where("session_id=? AND type=1", sessionId).Order("send_time DESC").Limit(size).Offset((page - 1) * size).Find(&messages)
	if result.Error != nil {
		return nil, fmt.Errorf("查询单聊消息失败%w", result.Error)
	}
	if result.Error != nil {
		return nil, result.Error
	}
	return messages, nil
}

/*
*
场景2:群聊相关
*/
func SaveGroup(m *MessageService, CreateUserId, GroupName string) (string, error) {
	//1.生成唯一群id(规则:g+数字,这里用日期+UUID保证唯一)
	groupId := "g" + time.Now().Format("20060102") + "_" + uuid.New().String()[:8]
	//2.写入group表
	group := model.Group{
		GroupID:   groupId,
		GroupName: GroupName,
		OwnerID:   CreateUserId,
		MaxMember: 50,
		CreatedAt: time.Now(),
		UpdatedAt: time.Now(),
	}
	if err := m.Db.Create(&group).Error; err != nil {
		return "", fmt.Errorf("保存群失败：%w", err)
	}
	return groupId, nil
}

// AddGroupMembers 群主拉人入群
func AddGroupMembers(m *MessageService, groupId, operatorId string, memberIds []string) error {
	if !m.IsGroupOwner(groupId, operatorId) {
		return fmt.Errorf("非群主无权限操作")
	}
	//2.校验群成员数量是否超限
	currentCount, err := m.GetGroupMembers(groupId)
	if err != nil {
		return fmt.Errorf("查询群成员数量失败：%w", err)
	}
	var maxCount int64
	if err := m.Db.Model(&model.Group{}).Where("group_id=?", groupId).Pluck("max_member", &maxCount).Error; err != nil {
		return fmt.Errorf("查询群成员数量失败：%w", err)
	}

	if currentCount+int64(len(memberIds)) > maxCount {
		return fmt.Errorf("群成员数量超限,当前:%d,最大:%d", currentCount, maxCount)
	}

	//3.批量添加成员
	members := make([]model.GroupMember, 0, len(memberIds))
	for _, mid := range memberIds {
		if m.IsGroupMember(groupId, mid) {
			continue //已存在则跳过
		}
		members = append(members, model.GroupMember{
			GroupID:  groupId,
			MemberID: mid,
			Role:     3,
			IsQuit:   false,
			JoinTime: time.Now(),
		})
	}

	if len(members) == 0 {
		return nil // 无新成员需要添加
	}

	// 4. 批量写入群成员表
	if err := m.Db.CreateInBatches(&members, 10).Error; err != nil { // 分批写入，避免SQL过长
		return fmt.Errorf("添加群成员失败：%w", err)
	}
	return nil
}

// RemoveGroupMember 群主踢人出群（核心新增功能）
func RemoveGroupMember(svc *MessageService, groupID, operatorID, memberID string) error {
	// 1. 校验操作人是否是群主
	if !svc.IsGroupOwner(groupID, operatorID) {
		return fmt.Errorf("非群主无权踢人")
	}

	// 2. 不能踢自己（群主）
	if operatorID == memberID {
		return fmt.Errorf("群主不能踢自己，请先转让群主或解散群")
	}

	// 3. 标记成员为退出（软删除，保留记录）
	result := svc.Db.Model(&model.GroupMember{}).
		Where("group_id = ? AND member_id = ? AND is_quit = false", groupID, memberID).
		Updates(map[string]interface{}{
			"is_quit":    true,
			"quit_time":  time.Now(),
			"updated_at": time.Now(),
		})

	if result.Error != nil {
		return fmt.Errorf("踢人失败：%w", result.Error)
	}
	if result.RowsAffected == 0 {
		return fmt.Errorf("该用户不在群里或已退出")
	}
	return nil
}

// QuitGroup 成员主动退出群聊（核心新增功能）
func QuitGroup(svc *MessageService, groupID, userID string) error {
	// 1. 群主不能直接退群（需先转让群主）
	if svc.IsGroupOwner(groupID, userID) {
		return fmt.Errorf("群主不能直接退出群，请先转让群主或解散群")
	}

	// 2. 标记为退出
	result := svc.Db.Model(&model.GroupMember{}).
		Where("group_id = ? AND member_id = ? AND is_quit = false", groupID, userID).
		Updates(map[string]interface{}{
			"is_quit":    true,
			"quit_time":  time.Now(),
			"updated_at": time.Now(),
		})

	if result.Error != nil {
		return fmt.Errorf("退群失败：%w", result.Error)
	}
	if result.RowsAffected == 0 {
		return fmt.Errorf("你不在该群里或已退出")
	}
	return nil
}

// SaveGroupMessage 存储群聊消息（发送前校验是否是群成员）
func SaveGroupMessage(svc *MessageService, fromUserID, groupID, content string) error {
	// 新增：发送群消息前校验用户是否在群里
	if !svc.IsGroupMember(groupID, fromUserID) {
		return fmt.Errorf("非群成员无法发送群消息")
	}

	msg := model.Message{
		SessionID:  groupID, // 你的规则：群聊SessionID=群ID
		FromUserID: fromUserID,
		ToUserID:   groupID, // 你的规则：ToUserID填群ID
		Content:    content,
		Type:       2, // 你的规则：群聊Type=2
		SendTime:   time.Now(),
		IsRead:     false,
		Status:     1,
	}
	if err := svc.Db.Create(&msg).Error; err != nil {
		return fmt.Errorf("存储群消息失败：%w", err)
	}
	return nil
}

// GetGroupMessage 拉取群聊历史记录（你的规则）
func GetGroupMessage(svc *MessageService, groupID string, page, size int) ([]model.Message, error) {
	var messages []model.Message
	result := svc.Db.Where("session_id = ? AND type = 2", groupID).
		Order("send_time DESC").
		Limit(size).
		Offset((page - 1) * size).
		Find(&messages)

	if result.Error != nil {
		return nil, fmt.Errorf("查询群消息失败：%w", result.Error)
	}
	return messages, nil
}

// GetGroupMemberCount 获取当前群成员数量（仅统计未退出的）
func (svc *MessageService) GetGroupMemberCount(groupID string) (int64, error) {
	var count int64
	err := svc.Db.Model(&model.GroupMember{}).
		Where("group_id = ? AND is_quit = false", groupID).
		Count(&count).Error
	return count, err
}

// ------------------------------
// 场景：用户登录后初始化会话列表（你的核心逻辑）
// ------------------------------

// GetUserAllSessions 获取用户所有会话（单聊+群聊，你的表操作逻辑）
func GetUserAllSessions(svc *MessageService, userID string) (
	singleSessions []string, // 单聊SessionID列表
	groupSessions []string, // 群聊SessionID列表
	err error,
) {
	// 1. 你的规则：查所有单聊会话
	err = svc.Db.Model(&model.Message{}).
		Where("(from_user_id = ? OR to_user_id = ?) AND type = 1", userID, userID).
		Distinct("session_id").
		Find(&singleSessions).Error
	if err != nil {
		return nil, nil, err
	}

	// 2. 你的规则：查所有群聊会话（用户加入的群）
	err = svc.Db.Model(&model.GroupMember{}).
		Where("member_id = ? AND is_quit = false", userID).
		Pluck("group_id", &groupSessions).Error
	return
}

// IsGroupMember 校验用户是否在群里（群聊发消息前校验）
func (svc *MessageService) IsGroupMember(groupID, userID string) bool {
	var count int64
	svc.Db.Model(&model.GroupMember{}).
		Where("group_id = ? AND member_id = ? AND is_quit = false", groupID, userID).
		Count(&count)
	return count > 0
}

// IsGroupOwner 校验用户是否是群主
func (svc *MessageService) IsGroupOwner(groupID, userID string) bool {
	var count int64
	svc.Db.Model(&model.Group{}).
		Where("group_id = ? AND owner_id = ?", groupID, userID).
		Count(&count)
	return count > 0
}

// GetGroupMemberIDs 获取群所有成员ID（群消息推送用）
func (svc *MessageService) GetGroupMemberIDs(groupID string) ([]string, error) {
	var ids []string
	err := svc.Db.Model(&model.GroupMember{}).
		Where("group_id = ? AND is_quit = false", groupID).
		Pluck("member_id", &ids).Error
	return ids, err
}
