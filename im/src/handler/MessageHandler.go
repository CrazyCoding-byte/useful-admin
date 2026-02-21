package handler

import "C"
import (
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
	return c.MustGet("user_id").(string)
}

func getCurrentUserName(c *gin.Context) string {
	return c.MustGet("username").(string)
}

func (h *MessageHandler) GetChatList(c *gin.Context) {
	userId := getCurrentUserId(c)
	singleSession, groupSession, err := h.msgRepo.GetUserAllSessions(userId)
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
	chatType, err := strconv.Atoi(c.Query("chat_type"))
	if err != nil {
		chatType = 1
	}
	targetId := c.Query("target_id")
	if targetId == "" {
		c.JSON(http.StatusBadRequest, gin.H{"code": 400, "msg": "target_id不能为空"})
		return
	}
	cursor, err := strconv.ParseInt(c.DefaultQuery("cursor", "0"), 10, 64)
	if err != nil {
		cursor = 0
	}
	//每页条数,限制最大50条
	size, err := strconv.Atoi(c.DefaultQuery("size", "20"))
	if err != nil || size < 1 || size > 50 {
		size = 20
	}
	var PageResult *model.CursorPageResult
	var repoErr error
	if chatType == 1 {
		PageResult, repoErr = h.msgRepo.GetUserMessageByCursor(userId, targetId, cursor, size)
	} else {
		PageResult, repoErr = h.msgRepo.GetGroupMessageByCursor(targetId, cursor, size)
	}
	if repoErr != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"code": 500,
			"msg":  fmt.Sprintf("获取聊天记录失败：%v", repoErr),
		})
		return
	}
	c.JSON(http.StatusOK, gin.H{"code": 200, "data": PageResult})
}
