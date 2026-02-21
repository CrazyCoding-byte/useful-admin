package handler

import "C"
import (
	"encoding/json"
	"fmt"
	"github.com/gin-gonic/gin"
	"local/im/src/model"
	"local/im/src/repository"
	"net/http"
	"strconv"
)

// 消息处理器
type MessageHandler struct {
	msgRepo *repository.MessageService
}

func NewMessageHandler(s *model.SessionManager, imService *repository.MessageService) *MessageHandler {
	return &MessageHandler{
		msgRepo: imService,
	}
}
func getCurrentUserId(c *gin.Context) string {
	return c.MustGet("userId").(string)
}
func getCurrentUserName(c *gin.Context) string {
	return c.MustGet("userName").(string)
}

func (h *MessageHandler) GetChatList(c *gin.Context) {
	userId := getCurrentUserId(c)
	//解析参数
	chatType, err := strconv.Atoi(c.Query("chat_type"))
	if err != nil {
		chatType = 1
	}
	targetID := C.Query("target_id")
	if targetID == "" {
		c.JSON(500, gin.H{
			"code": 400,
			"msg":  "target_id 不能为空",
		})
		return
	}
	cursor, err := strconv.ParseInt(c.DefaultQuery("cursor", "0"), 10, 64)
	if err != nil {
		cursor = 0
	}
	//每页条款,限制最大50条
	size, err := strconv.ParseInt(c.DefaultQuery("cursor", "0"), 10, 64)
	if err != nil {
		cursor = 0
	}
	if err != nil {
		c.JSON(500, gin.H{
			"code": 500,
			"msg":  "获取聊天列表失败",
		})
	}
	c.JSON(http.StatusOK, gin.H{"code": 200, "single_session": singleSession, "group_session": groupSession})
}
func (h *MessageHandler) GetHistory(c *gin.Context) {
	userId := getCurrentUserId(c)
	chatType, _ := strconv.Atoi(c.Query("chat_type"))
	targetId := c.Query("target_id")
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	size, _ := strconv.Atoi(c.DefaultQuery("size", "10"))
	var messages interface{}
	var err error
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
