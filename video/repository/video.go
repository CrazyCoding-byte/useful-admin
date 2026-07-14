// Package repository 中的课程视频数据访问层。
package repository

import (
	"video/model"

	"gorm.io/gorm"
)

// VideoRepository 课程视频表数据仓库。
type VideoRepository struct {
	db *gorm.DB
}

// NewVideoRepository 创建视频仓库实例。
func NewVideoRepository(db *gorm.DB) *VideoRepository {
	return &VideoRepository{db: db}
}

// Create 插入一条视频记录。
// 插入后 video.ID 会被赋值为自增主键，用于构造 MinIO 对象路径。
func (r *VideoRepository) Create(video *model.CourseVideo) error {
	return r.db.Create(video).Error
}

// Update 更新视频记录。
func (r *VideoRepository) Update(video *model.CourseVideo) error {
	return r.db.Save(video).Error
}

// Delete 根据 ID 软删除视频记录。
func (r *VideoRepository) Delete(id uint64) error {
	return r.db.Delete(&model.CourseVideo{}, id).Error
}

// GetByID 根据 ID 查询视频详情。
func (r *VideoRepository) GetByID(id uint64) (*model.CourseVideo, error) {
	var video model.CourseVideo
	err := r.db.First(&video, id).Error
	return &video, err
}

// ListByCourse 查询某课程下的所有视频，按章节、排序号、ID 升序排列。
func (r *VideoRepository) ListByCourse(courseID uint64) ([]model.CourseVideo, error) {
	var list []model.CourseVideo
	err := r.db.Where("course_id = ?", courseID).
		Order("chapter_id ASC, sort_order ASC, id ASC").
		Find(&list).Error
	return list, err
}

// ListByChapter 查询某章节下的所有视频。
func (r *VideoRepository) ListByChapter(chapterID uint64) ([]model.CourseVideo, error) {
	var list []model.CourseVideo
	err := r.db.Where("chapter_id = ?", chapterID).
		Order("sort_order ASC, id ASC").
		Find(&list).Error
	return list, err
}

// UpdateStatus 只更新视频转码状态。
// 转码是异步操作，后台 goroutine 通过此方法快速标记成功或失败，避免使用 Save 覆盖其他字段。
func (r *VideoRepository) UpdateStatus(id uint64, status int) error {
	return r.db.Model(&model.CourseVideo{}).Where("id = ?", id).Update("status", status).Error
}
