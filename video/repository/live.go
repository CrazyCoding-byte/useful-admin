// Package repository 中的直播间与处罚数据访问层。
package repository

import (
	"time"
	"video/model"

	"gorm.io/gorm"
)

// LiveRepository 直播间表数据仓库。
type LiveRepository struct {
	db *gorm.DB
}

// NewLiveRepository 创建直播间仓库实例。
func NewLiveRepository(db *gorm.DB) *LiveRepository {
	return &LiveRepository{db: db}
}

// CreateRoom 创建直播间记录。
func (r *LiveRepository) CreateRoom(room *model.LiveRoom) error {
	return r.db.Create(room).Error
}

// UpdateRoom 更新直播间记录。
func (r *LiveRepository) UpdateRoom(room *model.LiveRoom) error {
	return r.db.Save(room).Error
}

// DeleteRoom 根据 ID 删除直播间记录。
// 当前使用 GORM 默认软删除（依赖 model.DeletedAt 字段）。
func (r *LiveRepository) DeleteRoom(id uint64) error {
	return r.db.Delete(&model.LiveRoom{}, id).Error
}

// GetRoomByID 根据 ID 查询直播间。
func (r *LiveRepository) GetRoomByID(id uint64) (*model.LiveRoom, error) {
	var room model.LiveRoom
	err := r.db.First(&room, id).Error
	return &room, err
}

// GetRoomByStreamID 根据流 ID 查询直播间。
// ZLMediaKit webhook 只会告诉我们 stream_id，因此需要通过该字段定位直播间。
func (r *LiveRepository) GetRoomByStreamID(streamID string) (*model.LiveRoom, error) {
	var room model.LiveRoom
	err := r.db.Where("stream_id = ?", streamID).First(&room).Error
	return &room, err
}

// ListRooms 分页查询直播间列表，支持按状态和标题搜索。
// status 为 -1 时表示不限制状态。
func (r *LiveRepository) ListRooms(page, pageSize int, status int, keyword string) ([]model.LiveRoom, int64, error) {
	var list []model.LiveRoom
	var total int64

	query := r.db.Model(&model.LiveRoom{})
	if status >= 0 {
		query = query.Where("status = ?", status)
	}
	if keyword != "" {
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

// StartLive 标记直播间开始直播。
// 同时记录开播时间，并把结束时间清空（支持多次开播）。
func (r *LiveRepository) StartLive(id uint64) error {
	now := time.Now()
	return r.db.Model(&model.LiveRoom{}).Where("id = ?", id).Updates(map[string]any{
		"status":     model.LiveRoomStatusLive,
		"started_at": now,
		"ended_at":   nil,
	}).Error
}

// EndLive 标记直播间结束直播。
func (r *LiveRepository) EndLive(id uint64) error {
	now := time.Now()
	return r.db.Model(&model.LiveRoom{}).Where("id = ?", id).Updates(map[string]any{
		"status":   model.LiveRoomStatusOffline,
		"ended_at": now,
	}).Error
}

// BanRoom 标记直播间被封禁。
func (r *LiveRepository) BanRoom(id uint64) error {
	return r.db.Model(&model.LiveRoom{}).Where("id = ?", id).
		Update("status", model.LiveRoomStatusBanned).Error
}

// UpdateOnlineCount 更新直播间当前在线人数。
// 该字段可由直播聊天 Hub 或 ZLMediaKit 回调定时更新。
func (r *LiveRepository) UpdateOnlineCount(id uint64, count int) error {
	return r.db.Model(&model.LiveRoom{}).Where("id = ?", id).
		Update("online_count", count).Error
}

// IncrViewCount 累计观看人数 +1。
// 由 ZLMediaKit on_play webhook 触发，观众每成功拉流一次就加 1。
func (r *LiveRepository) IncrViewCount(id uint64) error {
	return r.db.Model(&model.LiveRoom{}).Where("id = ?", id).
		Update("view_count", gorm.Expr("view_count + 1")).Error
}

// LiveBanRepository 直播间处罚记录仓库。
type LiveBanRepository struct {
	db *gorm.DB
}

// NewLiveBanRepository 创建处罚仓库实例。
func NewLiveBanRepository(db *gorm.DB) *LiveBanRepository {
	return &LiveBanRepository{db: db}
}

// CreateBan 创建一条处罚记录（禁言/踢出）。
func (r *LiveBanRepository) CreateBan(ban *model.LiveBan) error {
	return r.db.Create(ban).Error
}

// IsBanned 判断用户在指定直播间是否仍处于处罚有效期。
// 查询条件：room_id、user_id 匹配，且 expire_at 晚于当前时间。
func (r *LiveBanRepository) IsBanned(roomID, userID uint64) (bool, error) {
	var count int64
	err := r.db.Model(&model.LiveBan{}).
		Where("room_id = ? AND user_id = ? AND expire_at > ?", roomID, userID, time.Now()).
		Count(&count).Error
	return count > 0, err
}

// ListBans 查询直播间的处罚列表，按创建时间倒序。
func (r *LiveBanRepository) ListBans(roomID uint64) ([]model.LiveBan, error) {
	var list []model.LiveBan
	err := r.db.Where("room_id = ?", roomID).Order("created_at DESC").Find(&list).Error
	return list, err
}

// DeleteBan 撤销对某用户的处罚。
func (r *LiveBanRepository) DeleteBan(roomID, userID uint64) error {
	return r.db.Where("room_id = ? AND user_id = ?", roomID, userID).
		Delete(&model.LiveBan{}).Error
}
