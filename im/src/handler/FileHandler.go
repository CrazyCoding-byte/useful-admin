package handler

import "C"
import (
	"context"
	"fmt"
	"github.com/gin-gonic/gin"
	"github.com/minio/minio-go/v7"
	"github.com/redis/go-redis/v9"
	"io"
	"local/im/src/repository"
	"strconv"
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

// 分片上传
type InitChunkUploadRequest struct {
	FileName string `json:"fileName"binding:"required"`
	MimeType string `json:"mimeType"binding:"required"`
	FileSize int64  `json:"fileSize"binding:"required"`
	FileHash string `json:"fileHash"binding:"required"`
}

// CompleteChunkUploadRequest 完成分片上传请求
type CompleteChunkUploadRequest struct {
	UploadId string `json:"uploadId"binding:"required"`
}
type AbortChunkUploadRequest struct {
	UploadID string `json:"uploadID"binding:"required"`
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

// InitChunkUpload 初始化分片上传
func (h *FileHandler) InitChunkUpload(c *gin.Context) {
	var req InitChunkUploadRequest
	if err := c.ShouldBind(&req); err != nil {
		c.JSON(400, gin.H{"code": 400, "msg": "参数错误", "error": err.Error()})
	}
	uploadID, err := h.chunkUploader.InitUpload(
		context.Background(),
		req.FileName,
		req.MimeType,
		req.FileSize,
		req.FileHash,
	)
	if err != nil {
		// 秒传判断
		errStr := err.Error()
		if len(errStr) > 12 && errStr[:12] == "file_exists|" {
			c.JSON(200, gin.H{
				"code": 200,
				"msg":  "文件已存在(秒传)",
				"data": gin.H{"filePath": errStr[12:]},
			})
			return
			c.JSON(500, gin.H{"code": 500, "msg": "初始化分片上传失败", "error": err.Error()})
		}
		return
	}
	c.JSON(200, gin.H{
		"code": 200,
		"msg":  "初始化分片上传成功",
		"data": gin.H{"uploadID": uploadID},
	})
}

// UploadChunk 上传单个分片
func (h *FileHandler) UploadChunk(c *gin.Context) {
	uploadID := c.PostForm("uploadId")
	chunkIndexStr := c.PostForm("chunkIndex")
	if uploadID == "" || chunkIndexStr == "" {
		c.JSON(400, gin.H{"code": 400, "msg": "参数错误"})
		return
	}
	chunkIndex, err := strconv.Atoi(chunkIndexStr)
	if err != nil {
		c.JSON(400, gin.H{"code": 400, "msg": "chunkIndex 格式错误"})
		return
	}
	//获取分片文件
	file, _, err := c.Request.FormFile("file")
	if err != nil {
		c.JSON(400, gin.H{"code": 400, "msg": "获取分片文件失败", "error": err.Error()})
		return
	}
	defer file.Close()
	//读取分片数据
	chunkData, err := io.ReadAll(file)
	if err != nil {
		c.JSON(500, gin.H{"code": 500, "msg": "读取分片数据失败", "error": err.Error()})
		return
	}
	//上传分片
	if err := h.chunkUploader.UploadChunk(context.Background(), uploadID, chunkIndex, chunkData); err != nil {
		c.JSON(500, gin.H{"code": 500, "msg": "上传分片失败", "error": err.Error()})
		return
	}
	c.JSON(200, gin.H{
		"code": 200,
		"msg":  "上传分片成功",
	})
}

// QueryProgress 查询上传进度
func (h *FileHandler) QueryProgress(c *gin.Context) {
	uploadID := c.Query("upload")
	if uploadID == "" {
		c.JSON(400, gin.H{"code": 400, "msg": "缺少参数 uploadId"})
		return
	}
	progress, uploadedChunks, totalChunks, err := h.chunkUploader.QueryProgress(context.Background(), uploadID)
	if err != nil {
		c.JSON(500, gin.H{"code": 500, "msg": "查询进度失败", "error": err.Error()})
		return
	}
	c.JSON(200, gin.H{"code": 200, "msg": "查询成功", "data": gin.H{"progress": progress, "uploadedChunks": uploadedChunks, "totalChunks": totalChunks}})
}

// CompleteChunkUpload 完成分片合并
func (h *FileHandler) CompleteChunkUpload(c *gin.Context) {
	var req CompleteChunkUploadRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(400, gin.H{"code": 400, "msg": "参数错误", "error": err.Error()})
		return
	}
	objectKey, err := h.chunkUploader.CompleteUpload(context.Background(), req.UploadId)
	if err != nil {
		c.JSON(500, gin.H{"code": 500, "msg": "合并失败", "error": err.Error()})
		return
	}
	c.JSON(200, gin.H{"code": 200, "msg": "合并成功", "data": gin.H{"filePath": objectKey}})

}
func (h *FileHandler) AbortChunkUpload(c *gin.Context) {
	var req AbortChunkUploadRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(400, gin.H{"code": 400, "msg": "参数错误", "error": err.Error()})
		return
	}
	c.JSON(200, gin.H{"code": 200, "msg": "取消上传成功"})
}
func (h *FileHandler) DownloadFile(c *gin.Context) {
	filePath := c.Query("filePath")
	if filePath == "" {
		c.JSON(400, gin.H{"code": 400, "msg": "缺少参数 filePath"})
		return
	}
	//从minio获取文件
	ctx := context.Background()
	object, err := h.fileService.GetMinioClient().GetObject(ctx, h.fileService.GetBucketName(), filePath, minio.GetObjectOptions{})
	if err != nil {
		c.JSON(500, gin.H{"code": 500, "msg": "获取文件失败", "error": err.Error()})
		return
	}
	defer object.Close()

	//获取文件信息
	stat, err := object.Stat()
	if err != nil {
		c.JSON(404, gin.H{"code": 404, "msg": "文件不存在"})
		return
	}
	//设置响应头
	c.Header("Context-Type", stat.ContentType)
	c.Header("Content-Disposition", fmt.Sprintf("attachment; filename=%s", stat.Key))
	c.Header("Content-Length", fmt.Sprintf("%d", stat.Size))

	//将文件内容写入响应
	_, err = io.Copy(c.Writer, object)
	if err != nil {
		c.JSON(500, gin.H{"code": 500, "msg": "下载文件失败", "error": err.Error()})
		return
	}
}
