package repository

import (
	"context"
	"fmt"
	"io"
	"local/im/src/client"
	"local/im/src/model"
	"strings"

	"gorm.io/gorm"
)

// MessageService 消息服务层，聚合 GroupMessageRepository 和 UserMessageRepository 的所有方法
type MessageService struct {
	db                *gorm.DB
	fileStorageClient *client.FileStorageClient // 新增
}

// NewMessageService 创建消息服务实例
func NewMessageService(db *gorm.DB, fileStorageAddr string) (*MessageService, error) {
	// 创建文件存储客户端
	fileClient, err := client.NewFileStorageClient(fileStorageAddr)
	if err != nil {
		return nil, fmt.Errorf("创建文件存储客户端失败: %w", err)
	}

	return &MessageService{
		db:                db,
		fileStorageClient: fileClient,
	}, nil
}

// SaveVideoMessage 保存视频消息（上传视频并保存消息记录）
func (m *MessageService) SaveVideoMessage(fromUserId string, receiveUserId string, videoFile io.Reader, fileName string, mimeType string) (string, error) {
	ctx := context.Background()

	// 上传视频到文件存储服务
	fileDetail, err := m.fileStorageClient.UploadVideo(ctx, "minio", fileName, mimeType, videoFile)
	if err != nil {
		return "", fmt.Errorf("上传视频失败: %w", err)
	}

	// 保存消息记录（将视频URL或fileHash存入消息内容）
	message := model.Message{
		FromUserID: fromUserId,
		ToUserID:   receiveUserId,
		Content:    fileDetail.FilePath, // 或 fileDetail.FileHash
		Type:       1,                   // 单聊
		Status:     1,
		SessionID:  fromUserId + receiveUserId,
	}

	if err := m.db.Create(&message).Error; err != nil {
		return "", fmt.Errorf("保存消息失败: %w", err)
	}

	return fileDetail.FileHash, nil
}

// ==================== 群消息相关方法（GroupMessageRepository） ====================

// CreateGroup 创建群聊
func (m *MessageService) CreateGroup(groupName string, creatorId string) error {
	return saveGroup(m, creatorId, groupName)
}

// GetGroupInfo 查询群聊天信息
func (m *MessageService) GetGroupInfo(groupId string) ([]model.Message, error) {
	return getGroupInfo(m, groupId)
}

// GetGroupsByUserId 根据用户ID查询所在的所有群
func (m *MessageService) GetGroupsByUserId(userId string) ([]model.Group, error) {
	return getGroupsByUserId(m, userId)
}

// AddGroupMember 添加群成员（支持批量添加）
func (m *MessageService) AddGroupMember(groupId string, userIds string) error {
	// 解析用户ID列表（假设用逗号分隔）
	userIDList := strings.Split(userIds, ",")

	// 验证群是否存在
	var group model.Group
	if err := m.db.Where("group_id = ?", groupId).First(&group).Error; err != nil {
		return fmt.Errorf("群不存在：%w", err)
	}

	// 批量添加成员
	for _, userId := range userIDList {
		userId = strings.TrimSpace(userId)
		if userId == "" {
			continue
		}

		// 检查成员是否已存在
		var existingMember model.GroupMember
		result := m.db.Where("group_id = ? AND member_id = ?", groupId, userId).First(&existingMember)

		if result.Error == nil {
			// 如果成员已存在且已退出，则恢复
			if existingMember.IsQuit {
				existingMember.IsQuit = false
				if err := m.db.Save(&existingMember).Error; err != nil {
					return fmt.Errorf("恢复群成员失败：%w", err)
				}
			}
			// 如果成员已存在且未退出，跳过
			continue
		}

		// 创建新成员记录
		groupMember := model.GroupMember{
			GroupID:  groupId,
			MemberID: userId,
			Role:     3, // 默认普通成员
		}
		if err := m.db.Create(&groupMember).Error; err != nil {
			return fmt.Errorf("添加群成员失败（用户ID: %s）：%w", userId, err)
		}
	}

	return nil
}

// RemoveGroupMember 移除群成员（软删除）
func (m *MessageService) RemoveGroupMember(groupId string, userId string) error {
	var member model.GroupMember
	if err := m.db.Where("group_id = ? AND member_id = ? AND is_quit = false", groupId, userId).First(&member).Error; err != nil {
		return fmt.Errorf("群成员不存在：%w", err)
	}

	member.IsQuit = true
	if err := m.db.Save(&member).Error; err != nil {
		return fmt.Errorf("移除群成员失败：%w", err)
	}

	return nil
}

// ==================== 用户消息相关方法（UserMessageRepository） ====================

// SaveUserMessage 添加用户之间消息
func (m *MessageService) SaveUserMessage(fromUserId string, receiveUserId string, content string) error {
	message := model.Message{
		FromUserID: fromUserId,
		ToUserID:   receiveUserId,
		Content:    content,
		Type:       1, // 单聊
		Status:     1, // 正常
		SessionID:  fromUserId + receiveUserId,
	}
	if err := m.db.Create(&message).Error; err != nil {
		return fmt.Errorf("保存用户消息失败：%w", err)
	}
	return nil
}

// GetUserMessage 拉取当前用户与目标用户的双向聊天数据
func (m *MessageService) GetUserMessage(currentUserID string, targetUserID string) ([]model.Message, error) {
	return getUserMessage(m, currentUserID, targetUserID)
}

// GetChatList 获取用户的聊天列表（单聊+群聊，每个会话展示最新一条消息）
func (m *MessageService) GetChatList(userId string) ([]model.Message, error) {
	return getChatList(m, userId)
}

// SaveGroupMessage 保存群消息
func (m *MessageService) SaveGroupMessage(fromUserId string, groupId string, content string) error {
	message := model.Message{
		FromUserID: fromUserId,
		ToUserID:   groupId,
		Content:    content,
		Type:       2, // 群聊
		Status:     1, // 正常
		SessionID:  groupId,
	}
	if err := m.db.Create(&message).Error; err != nil {
		return fmt.Errorf("保存群消息失败：%w", err)
	}
	return nil
}

//====================== 文件存储相关方法（FileStorageRepository） ======================
