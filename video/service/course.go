// Package service 中的课程与权限业务层。
//
// 职责：
//
//	课程管理（增删改查、章节管理）以及用户观看权限绑定（开通会员、购买课程）。
//	service 层负责编排 repository 调用、处理业务规则，不直接操作 HTTP 请求和响应。
package service

import (
	"time"
	"video/model"
	"video/repository"
)

// CourseService 课程业务服务。
type CourseService struct {
	repos *repository.Repositories
}

// NewCourseService 创建课程业务服务实例。
func NewCourseService(repos *repository.Repositories) *CourseService {
	return &CourseService{repos: repos}
}

// Create 创建课程。
func (s *CourseService) Create(course *model.Course) error {
	return s.repos.CourseRepo.Create(course)
}

// Update 更新课程。
func (s *CourseService) Update(course *model.Course) error {
	return s.repos.CourseRepo.Update(course)
}

// Delete 删除课程。
func (s *CourseService) Delete(id uint64) error {
	return s.repos.CourseRepo.Delete(id)
}

// GetByID 根据 ID 查询课程。
func (s *CourseService) GetByID(id uint64) (*model.Course, error) {
	return s.repos.CourseRepo.GetByID(id)
}

// List 分页查询课程列表。
func (s *CourseService) List(page, pageSize int, keyword string) ([]model.Course, int64, error) {
	return s.repos.CourseRepo.List(page, pageSize, keyword)
}

// CreateChapter 创建章节。
func (s *CourseService) CreateChapter(chapter *model.CourseChapter) error {
	return s.repos.ChapterRepo.Create(chapter)
}

// UpdateChapter 更新章节。
func (s *CourseService) UpdateChapter(chapter *model.CourseChapter) error {
	return s.repos.ChapterRepo.Update(chapter)
}

// DeleteChapter 删除章节。
func (s *CourseService) DeleteChapter(id uint64) error {
	return s.repos.ChapterRepo.Delete(id)
}

// ListChapters 查询某课程下指定父章节的子章节（parentID=0 表示顶层章节）。
func (s *CourseService) ListChapters(courseID, parentID uint64) ([]model.CourseChapter, error) {
	return s.repos.ChapterRepo.ListByCourseAndParent(courseID, parentID)
}

// CourseDetail 查询课程详情，包含课程基本信息、章节树和视频列表。
//
// 返回结构：
//
//	{
//	  "course": { ... },
//	  "chapters": [
//	    {
//	      "id": 1,
//	      "title": "第一章",
//	      "children": [ { "id": 2, "title": "1.1 节", "children": [], "videos": [ ... ] } ],
//	      "videos": [ ... ]
//	    }
//	  ]
//	}
//
// 章节支持 parent_id 自引用，这里按 parent_id 组织成树；parent_id=0 的节点为顶层。
func (s *CourseService) CourseDetail(courseID uint64) (map[string]any, error) {
	course, err := s.repos.CourseRepo.GetByID(courseID)
	if err != nil {
		return nil, err
	}
	chapters, err := s.repos.ChapterRepo.ListByCourse(courseID)
	if err != nil {
		return nil, err
	}
	videos, err := s.repos.VideoRepo.ListByCourse(courseID)
	if err != nil {
		return nil, err
	}

	// 按章节 ID 把视频分组
	videoMap := make(map[uint64][]model.CourseVideo)
	for _, v := range videos {
		videoMap[v.ChapterID] = append(videoMap[v.ChapterID], v)
	}

	// 把章节列表构建成树
	tree := buildChapterTree(chapters, videoMap)

	return map[string]any{
		"course":   course,
		"chapters": tree,
	}, nil
}

// buildChapterTree 把扁平的章节列表按 parent_id 组织成树。
func buildChapterTree(chapters []model.CourseChapter, videoMap map[uint64][]model.CourseVideo) []map[string]any {
	nodeMap := make(map[uint64]map[string]any, len(chapters))
	for _, ch := range chapters {
		nodeMap[ch.ID] = map[string]any{
			"id":        ch.ID,
			"courseId":  ch.CourseID,
			"parentId":  ch.ParentID,
			"title":     ch.Title,
			"sortOrder": ch.SortOrder,
			"children":  make([]map[string]any, 0),
			"videos":    videoMap[ch.ID],
		}
	}

	var roots []map[string]any
	for _, ch := range chapters {
		node := nodeMap[ch.ID]
		if ch.ParentID == 0 {
			roots = append(roots, node)
			continue
		}
		if parent, ok := nodeMap[ch.ParentID]; ok {
			parent["children"] = append(parent["children"].([]map[string]any), node)
		} else {
			// 父节点不存在时降级为顶层，避免数据异常导致丢失
			roots = append(roots, node)
		}
	}
	return roots
}

// BindVip 给用户开通会员。
//
// 逻辑：
//
//	从当前时间开始，持续 months 个月。例如 months=1 表示开通 1 个月会员。
func (s *CourseService) BindVip(userID uint64, months int) error {
	start := time.Now()
	end := start.AddDate(0, months, 0)
	return s.repos.PermissionRepo.UpsertVip(userID, start, end)
}

// BindCoursePurchase 给用户开通课程购买权限。
//
// 参数 days：
//   - days > 0：课程在 days 天后过期；
//   - days <= 0：课程永久有效（ExpireTime 为零值）。
func (s *CourseService) BindCoursePurchase(userID, courseID uint64, days int) error {
	expireTime := time.Time{}
	if days > 0 {
		expireTime = time.Now().AddDate(0, 0, days)
	}
	record := &model.UserCoursePurchase{
		UserID:       userID,
		CourseID:     courseID,
		PurchaseTime: time.Now(),
		ExpireTime:   expireTime,
		Status:       1,
	}
	return s.repos.PermissionRepo.CreatePurchase(record)
}
