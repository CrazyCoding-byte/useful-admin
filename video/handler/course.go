// Package handler 中的课程相关 HTTP 接口。
//
// 职责：
//
//	解析 HTTP 请求参数、调用 service 层处理业务、返回统一 JSON 响应。
//	所有接口通过 model.Result 结构返回 {code, message, data} 格式。
package handler

import (
	"net/http"
	"strconv"
	"video/model"
	"video/service"

	"github.com/gin-gonic/gin"
)

// CourseHandler 课程 HTTP 处理器。
type CourseHandler struct {
	service *service.CourseService
}

// NewCourseHandler 创建课程处理器实例。
func NewCourseHandler(s *service.CourseService) *CourseHandler {
	return &CourseHandler{service: s}
}

// CreateCourse 创建课程。
// 请求体：JSON，必填字段 title。
func (h *CourseHandler) CreateCourse(c *gin.Context) {
	var req model.Course
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusOK, model.Fail("参数错误"))
		return
	}
	if req.Title == "" {
		c.JSON(http.StatusOK, model.Fail("课程标题不能为空"))
		return
	}
	if err := h.service.Create(&req); err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(req))
}

// UpdateCourse 更新课程。
func (h *CourseHandler) UpdateCourse(c *gin.Context) {
	var req model.Course
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

// DeleteCourse 删除课程。
// 路由参数：id。
func (h *CourseHandler) DeleteCourse(c *gin.Context) {
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

// GetCourse 查询课程详情。
func (h *CourseHandler) GetCourse(c *gin.Context) {
	id, err := strconv.ParseUint(c.Param("id"), 10, 64)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail("ID 错误"))
		return
	}
	course, err := h.service.GetByID(id)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(course))
}

// ListCourse 分页查询课程列表。
// 查询参数：page, pageSize, keyword。
func (h *CourseHandler) ListCourse(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("pageSize", "10"))
	keyword := c.Query("keyword")
	list, total, err := h.service.List(page, pageSize, keyword)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(gin.H{
		"list":     list,
		"total":    total,
		"page":     page,
		"pageSize": pageSize,
	}))
}

// CourseDetail 查询课程详情（含章节和视频）。
func (h *CourseHandler) CourseDetail(c *gin.Context) {
	id, err := strconv.ParseUint(c.Param("id"), 10, 64)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail("ID 错误"))
		return
	}
	detail, err := h.service.CourseDetail(id)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(detail))
}

// CreateChapter 创建章节。
// 请求体：JSON，必填字段 title 和 courseId。
func (h *CourseHandler) CreateChapter(c *gin.Context) {
	var req model.CourseChapter
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusOK, model.Fail("参数错误"))
		return
	}
	if req.Title == "" || req.CourseID == 0 {
		c.JSON(http.StatusOK, model.Fail("章节标题和课程ID不能为空"))
		return
	}
	if err := h.service.CreateChapter(&req); err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(req))
}

// UpdateChapter 更新章节。
func (h *CourseHandler) UpdateChapter(c *gin.Context) {
	var req model.CourseChapter
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusOK, model.Fail("参数错误"))
		return
	}
	if err := h.service.UpdateChapter(&req); err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(nil))
}

// DeleteChapter 删除章节。
func (h *CourseHandler) DeleteChapter(c *gin.Context) {
	id, err := strconv.ParseUint(c.Param("id"), 10, 64)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail("ID 错误"))
		return
	}
	if err := h.service.DeleteChapter(id); err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(nil))
}

// ListChapter 查询某课程下指定父章节的子章节。
// 查询参数：parentId（默认 0，表示顶层章节）。
func (h *CourseHandler) ListChapter(c *gin.Context) {
	courseID, err := strconv.ParseUint(c.Param("courseId"), 10, 64)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail("课程ID 错误"))
		return
	}
	parentID, _ := strconv.ParseUint(c.DefaultQuery("parentId", "0"), 10, 64)
	list, err := h.service.ListChapters(courseID, parentID)
	if err != nil {
		c.JSON(http.StatusOK, model.Fail(err.Error()))
		return
	}
	c.JSON(http.StatusOK, model.Success(list))
}

// BindUserPermission 给用户绑定会员或课程购买权限（管理接口）。
//
// 请求体字段：
//   - userId：用户 ID；
//   - isVip：是否开通会员；
//   - vipMonths：开通会员月数；
//   - courseId：课程 ID（>0 时开通课程购买）；
//   - days：课程有效期天数（<=0 表示永久）。
func (h *CourseHandler) BindUserPermission(c *gin.Context) {
	var req struct {
		UserID    uint64 `json:"userId"`
		CourseID  uint64 `json:"courseId"`
		Days      int    `json:"days"`
		IsVip     bool   `json:"isVip"`
		VipMonths int    `json:"vipMonths"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusOK, model.Fail("参数错误"))
		return
	}

	if req.IsVip {
		if err := h.service.BindVip(req.UserID, req.VipMonths); err != nil {
			c.JSON(http.StatusOK, model.Fail(err.Error()))
			return
		}
	}
	if req.CourseID > 0 {
		if err := h.service.BindCoursePurchase(req.UserID, req.CourseID, req.Days); err != nil {
			c.JSON(http.StatusOK, model.Fail(err.Error()))
			return
		}
	}
	c.JSON(http.StatusOK, model.Success(nil))
}
