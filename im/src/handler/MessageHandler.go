package handler

import (
	"encoding/json"
	"fmt"
	"local/im/src/model"
	"local/im/src/repository"
)

// 消息处理器
type MessageHandler struct {
	sessionManage *model.SessionManager
	imService     *repository.MessageService
}

func NewMessageHandler(s *model.SessionManager, imService *repository.MessageService) *MessageHandler {
	return &MessageHandler{
		sessionManage: s,
		imService:     imService,
	}
}
func (m *MessageHandler) Handle(session *model.Session, message []byte) error {
	// 步骤1：解析前端消息
	var req model.MessageReq
	if err := json.Unmarshal(message, &req); err != nil {
		return fmt.Errorf("消息格式错误：%w", err)
	}

	// 步骤2：校验必填参数
	if req.FromUserID == "" || req.ToType == 0 || req.ToID == "" {
		return fmt.Errorf("缺少必填参数")
	}
	return nil
}
