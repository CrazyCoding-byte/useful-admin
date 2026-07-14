// Package repository 中的仓库聚合入口。
//
// 设计说明：
//
//	所有数据访问对象（Repository）都通过 Repositories 结构体统一管理和初始化。
//	这样可以避免在 service 层手动传递多个仓库实例，也便于后续做单元测试时整体替换。
package repository

import (
	"video/model"

	"gorm.io/gorm"
)

// Repositories 聚合所有数据仓库，同时持有 *gorm.DB 实例，方便跨仓库事务（未来扩展）。
type Repositories struct {
	DB *gorm.DB // 数据库连接，service 层如需事务可直接使用

	CourseRepo     *CourseRepository     // 课程仓库
	ChapterRepo    *ChapterRepository    // 章节仓库
	VideoRepo      *VideoRepository      // 视频仓库
	DanmakuRepo    *DanmakuRepository    // 弹幕仓库
	PermissionRepo *PermissionRepository // 用户权限（会员/购买）仓库
	MinioRepo      *MinioRepository      // MinIO 文件仓库
	LiveRoomRepo   *LiveRepository       // 直播间仓库
	LiveBanRepo    *LiveBanRepository    // 直播间处罚仓库
}

// NewRepositories 初始化所有仓库并自动建表。
//
// 说明：
//  1. 通过 db.AutoMigrate 自动创建/更新表结构，适合开发阶段快速迭代；
//  2. 生产环境建议由 DBA 管理表结构，或仅首次启动时调用 AutoMigrate。
func NewRepositories(db *gorm.DB, minioRepo *MinioRepository) (*Repositories, error) {
	if err := db.AutoMigrate(
		&model.Course{},
		&model.CourseChapter{},
		&model.CourseVideo{},
		&model.UserVip{},
		&model.UserCoursePurchase{},
		&model.Danmaku{},
		&model.LiveRoom{},
		&model.LiveBan{},
	); err != nil {
		return nil, err
	}
	return &Repositories{
		DB:             db,
		CourseRepo:     NewCourseRepository(db),
		ChapterRepo:    NewChapterRepository(db),
		VideoRepo:      NewVideoRepository(db),
		DanmakuRepo:    NewDanmakuRepository(db),
		PermissionRepo: NewPermissionRepository(db),
		MinioRepo:      minioRepo,
		LiveRoomRepo:   NewLiveRepository(db),
		LiveBanRepo:    NewLiveBanRepository(db),
	}, nil
}
