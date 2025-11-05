package repository

import "local/im/src/model"

// 添加用户之间消息
func saveUserMessage(m *MessageService, fromUserId string, receiveUserId string, content string) {
	message := model.Message{
		FromUserID: fromUserId,
		ToUserID:   receiveUserId,
		Content:    content,
		Type:       1,
		Status:     1,
		SessionID:  fromUserId + receiveUserId,
	}
	m.db.Create(&message)
}

// 拉取当前用户与目标用户的双向聊天数据（修正拼写：ReciveUserId → ReceiveUserId）
func getUserMessage(m *MessageService, currentUserID string, targetUserID string) ([]model.Message, error) {
	var messages []model.Message
	// 条件：(当前用户发给目标用户) OR (目标用户发给当前用户)，确保双向消息都包含
	result := m.db.Where(
		"(from_user_id = ? AND to_user_id = ?) OR (from_user_id = ? AND to_user_id = ?)",
		currentUserID, targetUserID, // 方向1：我→对方
		targetUserID, currentUserID, // 方向2：对方→我
	).Order("send_time ASC"). // 按发送时间正序排列（先发生的在前）
					Find(&messages)

	if result.Error != nil {
		return nil, result.Error
	}
	return messages, nil
}

// 显示现有的聊天
// 获取用户的聊天列表（单聊+群聊，每个会话展示最新一条消息）
func getChatList(m *MessageService, userId string) ([]model.Message, error) {
	var chatList []model.Message

	// 步骤1：查询“用户参与的所有会话”（单聊+群聊）
	// 单聊：(from=用户 OR to=用户) 且 类型=1（单聊）
	// 群聊：类型=2（群聊） 且 用户是该群的成员（需关联group_members表）
	// 这里先简化群聊逻辑，仅通过消息表的session_id和类型筛选，后续再完善群成员校验
	result := m.db.
		// 条件：用户是发送者 或 接收者（单聊），或者是群聊且用户在群中
		Where("(from_user_id = ? OR to_user_id = ?) AND (type = 1 OR (type = 2 AND session_id IN (SELECT group_id FROM group_members WHERE member_id = ? AND is_quit = false)))",
			userId, userId, userId).
		// 按会话分组，取每个会话的最新一条消息
		Group("session_id").
		Order("send_time DESC").
		Find(&chatList)

	if result.Error != nil {
		return nil, result.Error
	}
	return chatList, nil
}
