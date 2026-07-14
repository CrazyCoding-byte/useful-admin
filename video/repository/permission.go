// Package repository 中的用户权限数据访问层。
//
// 职责：
//
//	判断用户是否有权限观看完整视频。权限来源有两种：
//	1. 用户购买了某个课程；
//	2. 用户开通了平台会员且在有效期内。
//	另外提供开通会员、创建购买记录的管理接口。
package repository

import (
	"time"
	"video/model"

	"gorm.io/gorm"
)

// PermissionRepository 用户权限仓库，操作 user_vip 和 user_course_purchase 表。
type PermissionRepository struct {
	db *gorm.DB
}

// NewPermissionRepository 创建权限仓库实例。
func NewPermissionRepository(db *gorm.DB) *PermissionRepository {
	return &PermissionRepository{db: db}
}

// IsVip 判断用户是否在会员有效期内。
//
// 逻辑：
//  1. 先查 status=1 的会员记录；
//  2. 如果存在且 EndTime 晚于当前时间，则判定为有效会员。
func (r *PermissionRepository) IsVip(userID uint64) (bool, error) {
	var vip model.UserVip
	err := r.db.Where("user_id = ? AND status = 1", userID).First(&vip).Error
	if err != nil {
		if err == gorm.ErrRecordNotFound {
			return false, nil
		}
		return false, err
	}
	return vip.EndTime.After(time.Now()), nil
}

// HasPurchasedCourse 判断用户是否已购买某课程，且购买仍在有效期内。
//
// 说明：
//   - 找不到记录 -> 未购买；
//   - 记录存在但 ExpireTime 不为零且已过期 -> 购买失效；
//   - 记录存在且 ExpireTime 为零或未来时间 -> 已购买。
func (r *PermissionRepository) HasPurchasedCourse(userID, courseID uint64) (bool, error) {
	var record model.UserCoursePurchase
	err := r.db.Where("user_id = ? AND course_id = ? AND status = 1", userID, courseID).First(&record).Error
	if err != nil {
		if err == gorm.ErrRecordNotFound {
			return false, nil
		}
		return false, err
	}
	if !record.ExpireTime.IsZero() && record.ExpireTime.Before(time.Now()) {
		return false, nil
	}
	return true, nil
}

// CanWatchFull 综合判断用户是否能观看某课程的完整视频。
//
// 优先级：
//  1. 课程本身免费 -> 直接放行；
//  2. 用户是有效会员 -> 放行；
//  3. 用户已购买该课程 -> 放行；
//  4. 否则只能试看。
func (r *PermissionRepository) CanWatchFull(userID uint64, course *model.Course) (bool, error) {
	if course.IsFree {
		return true, nil
	}
	isVip, err := r.IsVip(userID)
	if err != nil {
		return false, err
	}
	if isVip {
		return true, nil
	}
	return r.HasPurchasedCourse(userID, course.ID)
}

// CreateVip 创建一条会员记录。
func (r *PermissionRepository) CreateVip(vip *model.UserVip) error {
	return r.db.Create(vip).Error
}

// UpsertVip 给用户开通或续费会员。
//
// 逻辑：
//   - 如果用户没有会员记录，则创建一条新记录；
//   - 如果已有记录，则更新开始时间、结束时间和状态为有效。
//     调用方需要先计算好 startTime 和 endTime（例如 endTime = now + months）。
func (r *PermissionRepository) UpsertVip(userID uint64, startTime, endTime time.Time) error {
	var vip model.UserVip
	err := r.db.Where("user_id = ?", userID).First(&vip).Error
	if err == gorm.ErrRecordNotFound {
		vip = model.UserVip{
			UserID:    userID,
			StartTime: startTime,
			EndTime:   endTime,
			Status:    1,
		}
		return r.db.Create(&vip).Error
	}
	if err != nil {
		return err
	}
	vip.StartTime = startTime
	vip.EndTime = endTime
	vip.Status = 1
	return r.db.Save(&vip).Error
}

// CreatePurchase 创建一条课程购买记录。
func (r *PermissionRepository) CreatePurchase(record *model.UserCoursePurchase) error {
	return r.db.Create(record).Error
}
