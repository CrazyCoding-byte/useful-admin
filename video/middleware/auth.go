// Package middleware 提供 video 模块常用的 Gin 中间件辅助函数。
//
// 关于鉴权：
//
//	鉴权逻辑已经由公共模块 pkg（local/pkg/middleware）实现，video 模块直接使用 pkg 的 AuthMiddleware。
//	本文件只保留 CORS、上下文取值等与业务相关的辅助函数，避免在 video 里重复写 Token 解析。
package middleware

import (
	"net/http"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"
	// 复用公共模块的 Token 解析工具，避免在 video 中重复实现 RSA+AES 鉴权
	pkgutils "local/pkg/utils"
)

// GetUserID 从 Gin 上下文中获取当前登录用户的数字 ID。
//
// 说明：
//
//	pkg 的 AuthMiddleware 解析 Token 后，会把 user_id 以 string 类型写入 Gin 上下文。
//	这里尝试按 string -> uint64 转换；如果转换失败或不存在，返回 0 和 false。
//	未登录时 userID 为 0，业务层可以据此判断是游客。
func GetUserID(c *gin.Context) (uint64, bool) {
	v, ok := c.Get("user_id")
	if !ok {
		return 0, false
	}

	// IM 写入的是字符串，先按 string 处理
	if s, ok := v.(string); ok {
		uid, err := strconv.ParseUint(s, 10, 64)
		if err != nil {
			return 0, false
		}
		return uid, true
	}

	// 兼容可能直接写入 uint64 的场景
	if uid, ok := v.(uint64); ok {
		return uid, true
	}

	return 0, false
}

// GetUsername 从 Gin 上下文中获取当前登录用户的昵称。
// pkg 的 AuthMiddleware 会把 username 以 string 类型写入上下文；不存在时返回空字符串。
func GetUsername(c *gin.Context) string {
	v, ok := c.Get("username")
	if !ok {
		return ""
	}
	if s, ok := v.(string); ok {
		return s
	}
	return ""
}

// Cors 返回一个 Gin 中间件，用于处理跨域预检请求和跨域响应头。
// 开发阶段允许所有来源；生产环境建议根据实际域名进行限制。
func Cors() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Writer.Header().Set("Access-Control-Allow-Origin", "*")
		c.Writer.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		c.Writer.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With")
		if c.Request.Method == "OPTIONS" {
			c.AbortWithStatus(http.StatusNoContent)
			return
		}
		c.Next()
	}
}

// IsWebsocketRequest 判断当前请求是否为 WebSocket 升级请求。
// 用于在需要时区分 HTTP 接口和 WebSocket 连接。
func IsWebsocketRequest(c *gin.Context) bool {
	return strings.Contains(strings.ToLower(c.GetHeader("Upgrade")), "websocket")
}

// OptionalAuth 返回一个“可选鉴权”中间件。
//
// 使用场景：
//
//	视频播放、直播观看等接口允许游客访问，但如果用户已登录，
//	则解析 Token 并把 user_id/username 写入上下文，用于后续个性化推荐或权限判断。
//
// 实现：
//
//	直接调用公共模块的 ParseAndVerifyToken 解析 RSA+AES Token；
//	解析失败时不中断请求，只是不设置用户信息。
func OptionalAuth(aesKey string) gin.HandlerFunc {
	return func(c *gin.Context) {
		tokenStr := c.Query("token")
		if tokenStr == "" {
			authHeader := c.GetHeader("Authorization")
			if authHeader != "" {
				parts := strings.SplitN(authHeader, " ", 2)
				if len(parts) == 2 && parts[0] == "Bearer" {
					tokenStr = parts[1]
				}
			}
		}
		if tokenStr == "" {
			c.Next()
			return
		}

		// 调用公共模块的解析方法：RSA 验签 + AES 解密 user_id/username
		userID, username, err := pkgutils.ParseAndVerifyToken(tokenStr, aesKey)
		if err != nil {
			// 可选鉴权：解析失败也放行，只是当成游客
			c.Next()
			return
		}

		c.Set("user_id", userID)
		c.Set("username", username)
		c.Next()
	}
}
