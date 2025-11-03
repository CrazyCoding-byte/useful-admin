package websocket

import (
	"encoding/json"
	"github.com/gin-gonic/gin"
	"github.com/go-resty/resty/v2"
	"github.com/gorilla/websocket"
	"local/im/src/model"
	"net/http"
)

var upGrader = websocket.Upgrader{
	CheckOrigin: func(r *http.Request) bool {
		return true
	},
}

func handleWebSocket(c *gin.Context) {
	// 获取token
	var token = c.Query("token")
	if token == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "token is required"})
		return
	}
	// 验证token是否有效
	client := resty.New()
	resp, err := client.R().
		SetHeader("Content-type", "application/json").
		SetBody(map[string]string{"token": token}).
		Post("http://localhost:8080/user/getUserByToken")
	if err != nil || resp.StatusCode() != http.StatusOK {
		c.JSON(http.StatusBadRequest, gin.H{"error": "token is invalid"})
	}

	var Result model.Result
	//解析Auth服务器返回的用户数据
	if err := json.Unmarshal(resp.Body(), &Result); err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "解析用户信息失败"})
		return
	}
	if Result.Success != 200 {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "token is invalid"})
		return
	}
}
