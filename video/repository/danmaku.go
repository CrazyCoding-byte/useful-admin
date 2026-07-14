// Package repository 中的弹幕数据访问层。
package repository

import (
	"video/model"

	"gorm.io/gorm"
)

// DanmakuRepository 弹幕表数据仓库。
type DanmakuRepository struct {
	db *gorm.DB
}

// NewDanmakuRepository 创建弹幕仓库实例。
func NewDanmakuRepository(db *gorm.DB) *DanmakuRepository {
	return &DanmakuRepository{db: db}
}

// Create 插入一条弹幕记录。
// 保存后 danmaku.ID 会被赋值为自增主键，WebSocket 广播时需要带上真实 ID。
func (r *DanmakuRepository) Create(d *model.Danmaku) error {
	return r.db.Create(d).Error
}

// ListByVideo 查询某视频的历史弹幕，按出现时间和创建时间升序排列，最多返回 limit 条。
//
// 说明：
//
//	进入视频房间时，前端需要快速展示最近 N 条弹幕，避免房间看起来冷清。
//	按 time_at 排序可让前端直接按视频时间线渲染。
func (r *DanmakuRepository) ListByVideo(videoID uint64, limit int) ([]model.Danmaku, error) {
	var list []model.Danmaku
	err := r.db.Where("video_id = ?", videoID).
		Order("time_at ASC, created_at ASC").
		Limit(limit).
		Find(&list).Error
	return list, err
}
