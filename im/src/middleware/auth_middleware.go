package middleware

import (
	"fmt"
	"github.com/gin-gonic/gin"
	"local/im/src/config"
	"net/http"
	"strings"
	"sync"
)

type AuthMiddleware struct {
	aeskey     string
	tokenCache sync.Map
}
type UserClaims struct {
	UserId   string
	UserName string
}

func NewAuthMiddleWare(cfg *config.Config) *AuthMiddleware {
	return &AuthMiddleware{
		aeskey: cfg.AES.Key,
	}
}
func (m *AuthMiddleware) Auth(token string) (*UserClaims, error) {

	return func(c *gin.Context) {
		token, err := m.extraToken(c)
		if err != nil {
			c.JSON(http.StatusUnauthorized, gin.H{"code": 401, "message": "未授权"})
			c.Abort() //终止请求
			return
		}
		//先从缓冲获取
		if claim, ok := m.tokenCache.Load(token); ok {
			userClaims := claim.(*UserClaims)

		}
	}, nil
}
func (m *AuthMiddleware) extraToken(c *gin.Context) (string, error) {
	//从参数获取
	token := c.Query("token")
	if token != "" {
		return token, nil
	}
	//从header获取
	authHeader := c.GetHeader("Authorization")
	if authHeader != "" {
		parts := strings.Split(authHeader, " ")
		if len(parts) == 2 && parts[0] == "Bearer" {
			return parts[1], nil
		}
		return "", fmt.Errorf("无效的 Authorization header 格式")
	}
	return "", fmt.Errorf("未找到 token")
}
