package handler

import (
	"context"
	"github.com/gin-gonic/gin"
	"github.com/redis/go-redis/v9"
	"local/im/src/repository"
)

// FileHandler 文件上传处理器
type FileHandler struct {
	fileService   *repository.FileStorageService
	chunkUploader *repository.MinioCoreChunkUploader
	redisClient   *redis.Client
}

// NewFileHandler 创建文件处理器
func NewFileHandler(
	fileService *repository.FileStorageService,
	chunkUploader *repository.MinioCoreChunkUploader,
	redisClient *redis.Client,
) *FileHandler {
	return &FileHandler{
		fileService:   fileService,
		chunkUploader: chunkUploader,
		redisClient:   redisClient,
	}
}

// 普通文件上传
func (h *FileHandler) UploadFile(c *gin.Context) {
	file, header, err := c.Request.FormFile("file")
	if err != nil {
		c.JSON(400, gin.H{"code": 400, "msg": "获取文件失败", "error": err.Error()})
		return
	}
	defer file.Close()
	//计算文件哈希
	fileHash, err := repository.CalculateFileHash(file)
	if err != nil {
		c.JSON(500, gin.H{"code": 500, "msg": "计算文件哈希失败", "error": err.Error()})
		return
	}
	//重新定位文件指针
	file.Seek(0, 0)
	//上传文件
	result, err := h.fileService.UploadFile(context.Background(),
		h.redisClient,
		"minio",
		file,
		header.Filename,
		header.Header.Get("Content-Type"),
		fileHash,
		header.Size)

	if err != nil {
		c.JSON(500, gin.H{"code": 500, "msg": "上传失败", "error": err.Error()})
		return
	}
	c.JSON(200, gin.H{
		"code": 200,
		"msg":  "上传成功",
		"data": result,
	})
}
