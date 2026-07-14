// Package handler 中的视频相关 HTTP 接口。
package handler

import (
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
func (h *VideoHandler) PlayInfo(c *gin.Context) {
	videoID, err := strconv.ParseUint(c.Param("id"), 10, 64)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail("ID 错误"))
		return
	}
	userID, _ := middleware.GetUserID(c)
	info, err := h.service.GetPlayInfo(videoID, userID)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(info))
}
