// Package handler 中的视频相关 HTTP 接口。
package handler

import (
	"io"
	"net/http"
	"strconv"
	"video/middleware"
	"video/model"
	"video/service"

	"github.com/gin-gonic/gin"
)

// VideoHandler 视频 HTTP 处理器。
type VideoHandler struct {
	service *service.VideoService
}

// NewVideoHandler 创建视频处理器实例。
func NewVideoHandler(s *service.VideoService) *VideoHandler {
	return &VideoHandler{service: s}
}

// UploadVideo 上传视频文件。
//
// 请求方式：multipart/form-data
// 请求字段：
//   - courseId：课程 ID（必填）；
//   - chapterId：章节 ID（可选）；
//   - title：视频标题（必填）；
//   - trialSeconds：试看秒数（可选，默认使用配置值）；
//   - file：视频文件（必填）。
func (h *VideoHandler) UploadVideo(c *gin.Context) {
	courseID, err := strconv.ParseUint(c.PostForm("courseId"), 10, 64)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail("courseId 错误"))
		return
	}
	chapterID, _ := strconv.ParseUint(c.PostForm("chapterId"), 10, 64)
	title := c.PostForm("title")
	if title == "" {
		c.JSON(http.StatusOK, model.Fail("视频标题不能为空"))
		return
	}
	trialSeconds, _ := strconv.Atoi(c.PostForm("trialSeconds"))

	file, header, err := c.Request.FormFile("file")
	if err != nil {
		c.JSON(http.StatusOK, model.Fail("请上传视频文件"))
		return
	}
	defer file.Close()

	video, err := h.service.UploadVideo(courseID, chapterID, title, trialSeconds, file, header.Size, header.Header.Get("Content-Type"))
	if err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(video))
}

// UpdateVideo 更新视频信息。
func (h *VideoHandler) UpdateVideo(c *gin.Context) {
	var req model.CourseVideo
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusOK, model.Fail("参数错误"))
		return
	}
	if err := h.service.Update(&req); err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(nil))
}

// DeleteVideo 删除视频。
func (h *VideoHandler) DeleteVideo(c *gin.Context) {
	id, err := strconv.ParseUint(c.Param("id"), 10, 64)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail("ID 错误"))
		return
	}
	if err := h.service.Delete(id); err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(nil))
}

// GetVideo 查询视频详情。
func (h *VideoHandler) GetVideo(c *gin.Context) {
	id, err := strconv.ParseUint(c.Param("id"), 10, 64)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail("ID 错误"))
		return
	}
	video, err := h.service.GetByID(id)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(video))
}

// ListByCourse 查询课程下的所有视频。
func (h *VideoHandler) ListByCourse(c *gin.Context) {
	courseID, err := strconv.ParseUint(c.Param("courseId"), 10, 64)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail("课程ID 错误"))
		return
	}
	list, err := h.service.ListByCourse(courseID)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(list))
}

// PlayInfo 获取播放地址（支持未登录试看）。
//
// 说明：
//
//	该接口使用可选鉴权中间件。未登录用户 userID=0，只能试看；
//	已登录用户根据会员/购买情况返回完整版或试看版 m3u8 预签名 URL。
//	m3u8Url 是 video 服务自己的代理地址（/api/video/m3u8/:id/:kind），
//	不走 MinIO presigned，因为 m3u8 内 ts 是相对路径，浏览器解析不到签名会 AccessDenied。
func (h *VideoHandler) PlayInfo(c *gin.Context) {
	videoID, err := strconv.ParseUint(c.Param("id"), 10, 64)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail("ID 错误"))
		return
	}
	userID, _ := middleware.GetUserID(c)
	isAdmin := middleware.IsAdmin(c)
	// 管理后台的“播放”和“VIP 播放”共用此接口，通过 mode 明确请求试看或完整版。
	// 普通用户即使传 full 也仍受后端权限判断约束，不能绕过购买校验。
	mode := c.Query("mode")
	info, err := h.service.GetPlayInfo(videoID, userID, isAdmin, mode)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(info))
}

// ProxyM3U8 代理 m3u8 请求：拉 MinIO 原始 m3u8，把每个 .ts / .m3u8 相对路径替换成带签名的绝对 URL 后返回。
// 路由：GET /api/video/m3u8/:id/:kind   kind = full | trial
func (h *VideoHandler) ProxyM3U8(c *gin.Context) {
	videoID, err := strconv.ParseUint(c.Param("id"), 10, 64)
	if err != nil {
		c.String(http.StatusBadRequest, "ID 错误")
		return
	}
	kind := c.Param("kind")
	body, err := h.service.ProxyM3U8(videoID, kind)
	if err != nil {
		c.String(http.StatusInternalServerError, err.Error())
		return
	}
	c.Header("Content-Type", "application/vnd.apple.mpegurl")
	c.Header("Cache-Control", "no-store")
	c.String(http.StatusOK, body)
}

// GetPlayKey 下发 HLS AES-128 解密密钥（16 字节原始数据）。
//
// 说明：
//   - m3u8 的 EXT-X-KEY URI 指向本接口，播放器解密切片时自动请求；
//   - 播放器原生请求不带 Authorization header，故本接口不强制 Bearer 鉴权，
//     安全依赖 keyId 随机不可枚举 + m3u8 预签名 URL 双重保护；
//   - 路由：GET /api/video/key/:keyId（注意在鉴权组之外注册）。
func (h *VideoHandler) GetPlayKey(c *gin.Context) {
	keyID := c.Param("keyId")
	if keyID == "" {
		c.JSON(http.StatusOK, model.Fail("缺少 keyId"))
		return
	}
	key, err := h.service.GetPlayKey(keyID)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.Data(http.StatusOK, "application/octet-stream", key)
}

// InitChunkUpload 初始化分片上传（切片上传第一步）。
//
// 请求体：JSON
//   - courseId：课程 ID（必填）
//   - chapterId：章节 ID（可选，0 表示不归属章节）
//   - title：视频标题（必填）
//   - trialSeconds：试看秒数（可选）
//   - fileName：原始文件名（必填，用于推断扩展名）
//   - mimeType：文件 MIME 类型（必填）
//   - fileSize：文件总大小（字节，必填）
//   - fileHash：文件 SHA-256 哈希（必填，用于秒传）
//
// 返回 data 字段：
//   - videoId / uploadID / objectKey / totalChunks / chunkSize / instant
//   - instant=true 表示秒传命中，无需继续上传。
func (h *VideoHandler) InitChunkUpload(c *gin.Context) {
	var req struct {
		CourseID     uint64 `json:"courseId" binding:"required"`
		ChapterID    uint64 `json:"chapterId"`
		Title        string `json:"title" binding:"required"`
		TrialSeconds int    `json:"trialSeconds"`
		FileName     string `json:"fileName" binding:"required"`
		MimeType     string `json:"mimeType" binding:"required"`
		FileSize     int64  `json:"fileSize" binding:"required"`
		FileHash     string `json:"fileHash" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusOK, model.Fail("参数错误: "+err.Error()))
		return
	}
	result, err := h.service.InitChunkUpload(req.CourseID, req.ChapterID, req.Title, req.TrialSeconds,
		req.FileName, req.MimeType, req.FileSize, req.FileHash)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(result))
}

// UploadChunk 上传单个分片。
//
// 请求方式：multipart/form-data
//   - uploadId：InitChunkUpload 返回的 uploadID（必填）
//   - chunkIndex：分片索引，从 0 开始（必填）
//   - file：分片二进制数据（必填）
//
// 断点续传：同一 uploadId + chunkIndex 重复上传且内容一致时，服务端直接跳过并返回成功。
func (h *VideoHandler) UploadChunk(c *gin.Context) {
	uploadID := c.PostForm("uploadId")
	chunkIndexStr := c.PostForm("chunkIndex")
	if uploadID == "" || chunkIndexStr == "" {
		c.JSON(http.StatusOK, model.Fail("参数错误: uploadId 和 chunkIndex 不能为空"))
		return
	}
	chunkIndex, err := strconv.Atoi(chunkIndexStr)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail("参数错误: chunkIndex 格式错误"))
		return
	}
	file, _, err := c.Request.FormFile("file")
	if err != nil {
		c.JSON(http.StatusOK, model.Fail("请上传分片文件"))
		return
	}
	defer file.Close()
	chunkData, err := io.ReadAll(file)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail("读取分片数据失败: "+err.Error()))
		return
	}
	if err := h.service.UploadChunk(uploadID, chunkIndex, chunkData); err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(nil))
}

// ChunkProgress 查询分片上传进度（断点续传第二步）。
// 查询参数：upload=<uploadID>
// 返回 data：{progress, uploadedChunks, totalChunks}
func (h *VideoHandler) ChunkProgress(c *gin.Context) {
	uploadID := c.Query("upload")
	if uploadID == "" {
		c.JSON(http.StatusOK, model.Fail("缺少参数 upload"))
		return
	}
	progress, uploadedChunks, totalChunks, err := h.service.ChunkProgress(uploadID)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(gin.H{
		"progress":       progress,
		"uploadedChunks": uploadedChunks,
		"totalChunks":    totalChunks,
	}))
}

// CompleteChunkUpload 完成分片上传（合并分片并触发转码）。
// 请求体：JSON {uploadId}
func (h *VideoHandler) CompleteChunkUpload(c *gin.Context) {
	var req struct {
		UploadID string `json:"uploadId" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusOK, model.Fail("参数错误: "+err.Error()))
		return
	}
	video, err := h.service.CompleteChunkUpload(req.UploadID)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(video))
}

// AbortChunkUpload 取消分片上传（清理 Multipart 任务和视频记录）。
// 请求体：JSON {uploadID}
func (h *VideoHandler) AbortChunkUpload(c *gin.Context) {
	var req struct {
		UploadID string `json:"uploadID" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusOK, model.Fail("参数错误: "+err.Error()))
		return
	}
	if err := h.service.AbortChunkUpload(req.UploadID); err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(nil))
}
