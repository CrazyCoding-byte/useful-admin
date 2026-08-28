package middleware

import (
	"fmt"
	"net/http"
	"strings"

	"local/pkg/config"
	"local/pkg/utils"
	"log"

	"github.com/gin-gonic/gin"
)

// AuthMiddleware 基于 Token 的鉴权中间件。
// 支持从 URL 参数或 Authorization Header 提取 Token，并缓存解析结果。
type AuthMiddleware struct {
	aeskey string
}

// UserClaims 解析后的用户信息缓存。
type UserClaims struct {
	UserId   string
	UserName string
	ExpireAt int64 // 过期时间戳
}

// NewAuthMiddleWare 创建鉴权中间件实例。
func NewAuthMiddleWare(cfg *config.Config) *AuthMiddleware {
	return &AuthMiddleware{
		aeskey: cfg.AES.Key,
	}
}

// HandlerFunc 返回 Gin 中间件函数。
// token 参数当前保留兼容旧接口，实际从请求中提取。
func (m *AuthMiddleware) HandlerFunc(token string) gin.HandlerFunc {
	return func(c *gin.Context) {
		token, err := m.extraToken(c)
		if err != nil {
			log.Printf("Token提取失败: %v, Authorization=%q", err, c.GetHeader("Authorization"))
			c.JSON(http.StatusUnauthorized, gin.H{"code": 401, "message": err.Error()})
			c.Abort() // 终止请求
			return
		}

		userId, username, _, err := utils.ParseAndVerifyTokenWithAuthorities(token, m.aeskey)
		if err != nil {
			log.Printf("Token解析失败: %v", err)
			c.JSON(http.StatusUnauthorized, gin.H{"code": 401, "message": err.Error()})
			c.Abort()
			return
		}
		log.Printf("Token解析成功: user_id=%s, username=%s", userId, username)

		c.Set("user_id", userId)
		c.Set("username", username)
		// 暂存 authorities（handler 内会再次解析拿 authorities；这里为兼容老调用方暂不存）
		c.Next()
	}
}

// extraToken 从请求中提取 Token。
// 优先从 URL 参数 token 获取，其次从 Authorization: Bearer <token> 获取。
func (m *AuthMiddleware) extraToken(c *gin.Context) (string, error) {
	// 从参数获取
	token := c.Query("token")
	if token != "" {
		return token, nil
	}
	// 从 header 获取
	authHeader := c.GetHeader("Authorization")
	if authHeader != "" {
		parts := strings.Fields(authHeader)
		if len(parts) == 2 && strings.EqualFold(parts[0], "Bearer") {
			return parts[1], nil
		}
		return "", fmt.Errorf("无效的 Authorization header 格式")
	}
	return "", fmt.Errorf("未找到 token")
}

// GetUserID 从 Gin 上下文中获取当前用户 ID。
// 返回 userID 字符串和是否存在的标志。
func GetUserID(c *gin.Context) (string, bool) {
	uid, exists := c.Get("user_id")
	if !exists {
		return "", false
	}
	s, ok := uid.(string)
	return s, ok
}

// GetUsername 从 Gin 上下文中获取当前用户名。
func GetUsername(c *gin.Context) string {
	name, exists := c.Get("username")
	if !exists {
		return ""
	}
	s, ok := name.(string)
	if !ok {
		return ""
	}
	return s
}

// GetAuthorities 从 Gin 上下文中获取当前用户角色列表（OptionalAuth 中间件设置）。
// 用于服务按角色做权限 bypass，例如 admin 角色直接给完整版视频。
func GetAuthorities(c *gin.Context) []string {
	v, exists := c.Get("authorities")
	if !exists {
		return nil
	}
	arr, ok := v.([]string)
	if !ok {
		return nil
	}
	return arr
}

// IsAdmin 判定当前用户是否包含 admin 角色（兼容 "admin" 和 "ROLE_admin"）。
func IsAdmin(c *gin.Context) bool {
	for _, a := range GetAuthorities(c) {
		if a == "admin" || a == "ROLE_admin" {
			return true
		}
	}
	return false
}

// OptionalAuth 可选鉴权中间件。
// 如果请求带了合法 Token，则解析并设置用户信息；未携带或非法也不拦截，继续执行。
func OptionalAuth(aesKey string) gin.HandlerFunc {
	return func(c *gin.Context) {
		token := c.Query("token")
		if token == "" {
			authHeader := c.GetHeader("Authorization")
			if authHeader != "" {
				parts := strings.Split(authHeader, " ")
				if len(parts) == 2 && parts[0] == "Bearer" {
					token = parts[1]
				}
			}
		}
		if token != "" {
			userId, username, authorities, err := utils.ParseAndVerifyTokenWithAuthorities(token, aesKey)
			if err == nil {
				c.Set("user_id", userId)
				c.Set("username", username)
				c.Set("authorities", authorities)
			}
		}
		c.Next()
	}
}
