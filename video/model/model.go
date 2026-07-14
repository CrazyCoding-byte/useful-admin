// Package model 定义 video 模块所需的所有数据库实体（GORM 模型）以及通用响应结构。
//
// 业务概览：
//  1. Course（课程）：平台售卖/展示的最小单元，可设置价格、是否免费、上下架状态；
//  2. CourseChapter（课程章节）：一个课程下可包含多个章节，用于组织视频；
//  3. CourseVideo（课程视频）：归属于某个课程/章节，存储 MinIO 上的原始视频和 HLS 切片路径；
//  4. UserVip（用户会员）：记录用户的会员有效期，会员有效期内可免费观看会员课程；
//  5. UserCoursePurchase（课程购买记录）：记录用户购买某个课程的时间与有效期；
//  6. Danmaku（弹幕）：记录视频弹幕，支持按视频 ID 查询历史弹幕。
package model

import (
	"time"

	"gorm.io/gorm"
)

// Course 课程实体。
// 课程是平台对外售卖/展示的最小单位：免费课程可直接观看，付费课程需要用户购买或开通会员。
type Course struct {
	// ID 主键，自增。使用自增 ID 而非雪花 ID，与现有 education 数据库风格保持一致。
	ID uint64 `gorm:"primaryKey;autoIncrement" json:"id"`

	// Title 课程标题，必填，最大 200 字符。
	Title string `gorm:"size:200;not null" json:"title"`

	// Description 课程详细介绍，存储长文本。
	Description string `gorm:"type:text" json:"description"`

	// CoverUrl 课程封面图在 MinIO 上的访问地址或外网 URL。
	CoverUrl string `gorm:"size:500" json:"coverUrl"`

	// Price 课程价格，单位：分。例如 19900 表示 199.00 元。
	Price int64 `gorm:"default:0" json:"price"`

	// IsFree 是否免费。true 表示所有用户（含游客）都能观看完整视频。
	IsFree bool `gorm:"default:false" json:"isFree"`

	// Status 课程状态：0=下架，1=上架。下架后前端不再展示。
	Status int `gorm:"default:1" json:"status"`

	// CreatedAt / UpdatedAt / DeletedAt 由 GORM 自动维护。
	// DeletedAt 配合 gorm.DeletedAt 实现软删除。
	CreatedAt time.Time      `json:"createdAt"`
	UpdatedAt time.Time      `json:"updatedAt"`
	DeletedAt gorm.DeletedAt `gorm:"index" json:"-"`
}

// CourseChapter 课程章节实体。
// 一个课程下可以有多个章节，章节下再挂视频，形成“课程 -> 章节 -> 视频”三级结构。
type CourseChapter struct {
	// ID 主键，自增。
	ID uint64 `gorm:"primaryKey;autoIncrement" json:"id"`

	// CourseID 所属课程 ID，建立索引加速按课程查询章节。
	CourseID uint64 `gorm:"not null;index" json:"courseId"`

	// Title 章节标题，必填。
	Title string `gorm:"size:200;not null" json:"title"`

	// SortOrder 排序号，数字越小越靠前。
	SortOrder int `gorm:"default:0" json:"sortOrder"`
}

// CourseVideo 课程视频实体。
// 视频文件本身存储在 MinIO 对象存储中；本表只保存元数据、转码状态、播放路径等信息。
type CourseVideo struct {
	// ID 主键，自增。
	ID uint64 `gorm:"primaryKey;autoIncrement" json:"id"`

	// CourseID 所属课程 ID。
	CourseID uint64 `gorm:"not null;index" json:"courseId"`

	// ChapterID 所属章节 ID，0 表示未分章节（直接挂在课程下）。
	ChapterID uint64 `gorm:"index" json:"chapterId"`

	// Title 视频标题，必填。
	Title string `gorm:"size:200;not null" json:"title"`

	// SortOrder 同章节/课程内的排序号。
	SortOrder int `json:"sortOrder"`

	// Duration 视频总时长（秒），由 ffmpeg 转码时解析得到。
	Duration int `json:"duration"`

	// TrialSeconds 可试看秒数。0 表示按课程/用户权限策略决定（如课程免费则不限制）。
	TrialSeconds int `json:"trialSeconds"`

	// OriginalObject 原始上传视频在 MinIO 中的对象名。
	// 路径格式：course/{courseId}/video/{videoId}/original/{videoId}.mp4
	OriginalObject string `gorm:"size:500" json:"originalObject"`

	// HlsPath HLS 切片和 m3u8 索引文件在 MinIO 中的公共前缀。
	// 路径格式：course/{courseId}/video/{videoId}/
	HlsPath string `gorm:"size:500" json:"hlsPath"`

	// FullM3u8 完整版 m3u8 文件名，例如 full.m3u8。
	FullM3u8 string `gorm:"size:200" json:"fullM3u8"`

	// TrialM3u8 试看版 m3u8 文件名，例如 trial.m3u8。
	TrialM3u8 string `gorm:"size:200" json:"trialM3u8"`

	// Status 转码状态：0=待转码，1=已转码，2=转码失败。
	Status int `gorm:"default:0" json:"status"`

	CreatedAt time.Time      `json:"createdAt"`
	UpdatedAt time.Time      `json:"updatedAt"`
	DeletedAt gorm.DeletedAt `gorm:"index" json:"-"`
}

// UserVip 用户会员实体。
// 会员有效期内，用户可免费观看平台所有非免费但标记为“会员免费”的课程（本实现中所有付费课程对会员开放）。
type UserVip struct {
	// ID 主键，自增。
	ID uint64 `gorm:"primaryKey;autoIncrement" json:"id"`

	// UserID 用户 ID，唯一索引，一个用户只有一条会员记录。
	UserID uint64 `gorm:"uniqueIndex;not null" json:"userId"`

	// StartTime 会员开始时间。
	StartTime time.Time `json:"startTime"`

	// EndTime 会员结束时间。判断会员是否有效时，用 EndTime.After(time.Now())。
	EndTime time.Time `json:"endTime"`

	// Status 会员状态：0=失效，1=有效。续费/开通时更新为 1。
	Status int `gorm:"default:1" json:"status"`
}

// UserCoursePurchase 用户课程购买记录实体。
// 用户单独购买某个课程后，在有效期内可观看该课程下所有视频。
type UserCoursePurchase struct {
	// ID 主键，自增。
	ID uint64 `gorm:"primaryKey;autoIncrement" json:"id"`

	// UserID 购买用户 ID。
	UserID uint64 `gorm:"index;not null" json:"userId"`

	// CourseID 被购买的课程 ID。
	CourseID uint64 `gorm:"index;not null" json:"courseId"`

	// PurchaseTime 购买时间。
	PurchaseTime time.Time `json:"purchaseTime"`

	// ExpireTime 课程有效期。零值 time.Time{} 表示永久有效。
	ExpireTime time.Time `json:"expireTime"`

	// Status 购买状态：0=失效/退款，1=有效。
	Status int `gorm:"default:1" json:"status"`
}

// Danmaku 弹幕实体。
// 弹幕关联到具体视频，并记录发送者、内容、出现时间、颜色、类型等信息。
type Danmaku struct {
	// ID 主键，自增。
	ID uint64 `gorm:"primaryKey;autoIncrement" json:"id"`

	// VideoID 所属视频 ID。
	VideoID uint64 `gorm:"index;not null" json:"videoId"`

	// UserID 发送者用户 ID，未登录游客可留 0。
	UserID uint64 `gorm:"index" json:"userId"`

	// Username 发送者昵称，未登录显示“游客”。
	Username string `gorm:"size:50" json:"username"`

	// Content 弹幕内容，必填，最大 200 字符（防止超长刷屏）。
	Content string `gorm:"size:200;not null" json:"content"`

	// TimeAt 弹幕在视频中的出现时间（秒），用于前端在正确时间点展示弹幕。
	TimeAt float64 `gorm:"index" json:"timeAt"`

	// Color 弹幕颜色，默认白色 #ffffff。
	Color string `gorm:"size:20;default:'#ffffff'" json:"color"`

	// Type 弹幕类型：0=滚动，1=顶部固定，2=底部固定。
	Type int `gorm:"default:0" json:"type"`

	CreatedAt time.Time `json:"createdAt"`
}

// Result 通用 API 响应结构。
// Code 为业务状态码：200 表示成功，500 表示失败，其他值可自定义。
type Result struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
	Data    any    `json:"data,omitempty"`
}

// Success 构造成功响应，data 为任意类型，会序列化到 JSON 的 data 字段。
func Success(data any) Result {
	return Result{Code: 200, Message: "success", Data: data}
}

// Fail 构造失败响应，message 会展示给用户。
func Fail(message string) Result {
	return Result{Code: 500, Message: message}
}

// FailCode 构造带自定义状态码的失败响应。
func FailCode(code int, message string) Result {
	return Result{Code: code, Message: message}
}
