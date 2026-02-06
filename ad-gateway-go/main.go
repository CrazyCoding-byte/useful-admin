package main

import (
	"github.com/gin-gonic/gin"
)

func main() {
	r := gin.Default()
	r.GET("/api/ad/hello", func(c *gin.Context) {
		c.JSON(200, gin.H{"msg": "go gateway ok"})
	})
	r.Run(":8082") // Go网关监听8082端口
}
