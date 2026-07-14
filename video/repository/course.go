// Package repository 中的课程与章节数据访问层。
//
// 职责：
//
//	封装对 course、course_chapter 表的 CRUD 操作，把 SQL 细节隐藏在仓库内部，
//	service 层只面向业务语义调用。
package repository

import (
	"video/model"

	"gorm.io/gorm"
)

// CourseRepository 课程表数据仓库。
type CourseRepository struct {
	db *gorm.DB
}

// NewCourseRepository 创建课程仓库实例。
func NewCourseRepository(db *gorm.DB) *CourseRepository {
	return &CourseRepository{db: db}
}

// Create 插入一条课程记录。
// 由于 Course 使用自增主键，保存后 course.ID 会被 GORM 自动赋值为新主键。
func (r *CourseRepository) Create(course *model.Course) error {
	return r.db.Create(course).Error
}

// Update 更新课程记录。
// 注意：Save 会根据主键更新所有非零字段，调用前请确保 course.ID 已设置。
func (r *CourseRepository) Update(course *model.Course) error {
	return r.db.Save(course).Error
}

// Delete 根据 ID 软删除课程记录（GORM 默认软删除）。
func (r *CourseRepository) Delete(id uint64) error {
	return r.db.Delete(&model.Course{}, id).Error
}

// GetByID 根据 ID 查询课程详情。
func (r *CourseRepository) GetByID(id uint64) (*model.Course, error) {
	var course model.Course
	err := r.db.First(&course, id).Error
	return &course, err
}

// List 分页查询课程列表，支持按标题模糊搜索。
// 返回课程列表、总条数以及可能的错误。
func (r *CourseRepository) List(page, pageSize int, keyword string) ([]model.Course, int64, error) {
	var list []model.Course
	var total int64

	query := r.db.Model(&model.Course{})
	if keyword != "" {
		// 标题模糊匹配，MySQL 下 LIKE 会利用索引前缀（若有）
		query = query.Where("title LIKE ?", "%"+keyword+"%")
	}

	if err := query.Count(&total).Error; err != nil {
		return nil, 0, err
	}

	err := query.Order("created_at DESC").
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Find(&list).Error
	return list, total, err
}

// ChapterRepository 章节表数据仓库。
type ChapterRepository struct {
	db *gorm.DB
}

// NewChapterRepository 创建章节仓库实例。
func NewChapterRepository(db *gorm.DB) *ChapterRepository {
	return &ChapterRepository{db: db}
}

// Create 插入一条章节记录。
func (r *ChapterRepository) Create(chapter *model.CourseChapter) error {
	return r.db.Create(chapter).Error
}

// Update 更新章节记录。
func (r *ChapterRepository) Update(chapter *model.CourseChapter) error {
	return r.db.Save(chapter).Error
}

// Delete 根据 ID 删除章节记录。
func (r *ChapterRepository) Delete(id uint64) error {
	return r.db.Delete(&model.CourseChapter{}, id).Error
}

// ListByCourse 查询某课程下的所有章节，按 sort_order 和 id 升序排列。
// 返回的章节列表可用于前端渲染课程大纲。
func (r *ChapterRepository) ListByCourse(courseID uint64) ([]model.CourseChapter, error) {
	var list []model.CourseChapter
	err := r.db.Where("course_id = ?", courseID).
		Order("sort_order ASC, id ASC").
		Find(&list).Error
	return list, err
}
