// Package model 中的直播相关实体。
//
// 直播业务流程：
//  1. 主播/管理员在管理后台创建直播间，系统生成唯一的 stream_id；
//  2. 主播用 OBS 等推流软件向 ZLMediaKit 推流：rtmp://host/live/{stream_id}；
//  3. ZLMediaKit 通过 webhook 通知 video 服务“流已上线”，video 服务把直播间状态改为直播中；
//  4. 观众通过 HTTP-FLV / HLS / WebRTC 拉流观看；
//  5. 观众同时可通过 WebSocket 发送实时弹幕/聊天；
//  6. 主播/管理员可以禁言、踢人、结束直播。
package model

import (
	"time"

	"gorm.io/gorm"
)

// LiveRoomStatus 直播间状态枚举。
// 使用 int 类型便于数据库存储和索引查询。
type LiveRoomStatus int

const (
	// LiveRoomStatusOffline 未开播或已结束。
	// 刚创建直播间时默认处于此状态。
	LiveRoomStatusOffline LiveRoomStatus = 0

	// LiveRoomStatusLive 直播中。
	// ZLMediaKit 回调 on_publish 且校验通过后，状态会切换为此值。
	LiveRoomStatusLive LiveRoomStatus = 1

	// LiveRoomStatusBanned 直播间被封禁。
	// 被封禁后不允许推流，正在推流的会被 ZLMediaKit 主动断开。
	LiveRoomStatusBanned LiveRoomStatus = 2
)

// LiveRoom 直播间实体。
// 直播间与 ZLMediaKit 的 stream_id 一一对应，stream_id 同时也是 OBS 推流时的流名称。
type LiveRoom struct {
	// ID 主键，自增。
	ID uint64 `gorm:"primaryKey;autoIncrement" json:"id"`

	// Title 直播间标题，展示在小程序/管理后台列表中。
	Title string `gorm:"size:200;not null" json:"title"`

	// CoverUrl 直播间封面图地址。
	CoverUrl string `gorm:"size:500" json:"coverUrl"`

	// StreamID ZLMediaKit 流 ID，全局唯一。
	// 推流地址：rtmp://host/live/{streamID}
	// 播放地址：http://host/live/{streamID}.live.flv
	StreamID string `gorm:"size:100;uniqueIndex" json:"streamId"`

	// UserID 主播用户 ID。创建直播间时由 Token 解析得到。
	UserID uint64 `gorm:"index" json:"userId"`

	// Username 主播昵称。
	Username string `gorm:"size:50" json:"username"`

	// Status 直播间状态：0=未开播/已结束，1=直播中，2=被封禁。
	Status LiveRoomStatus `gorm:"default:0" json:"status"`

	// ViewCount 累计观看人次。每次有观众成功拉流时 +1（由 on_play webhook 触发）。
	ViewCount int `gorm:"default:0" json:"viewCount"`

	// OnlineCount 当前在线人数。由直播聊天 Hub 定时写回数据库，或 ZLMediaKit 统计。
	OnlineCount int `gorm:"default:0" json:"onlineCount"`

	// StartedAt 最近一次开播时间，nil 表示尚未开播。
	StartedAt *time.Time `json:"startedAt"`

	// EndedAt 最近一次结束时间，nil 表示尚未结束。
	EndedAt *time.Time `json:"endedAt"`

	CreatedAt time.Time      `json:"createdAt"`
	UpdatedAt time.Time      `json:"updatedAt"`
	DeletedAt gorm.DeletedAt `gorm:"index" json:"-"`
}

// LiveBanType 直播间处罚类型枚举。
type LiveBanType int

const (
	// LiveBanTypeMute 禁言：用户仍可观看直播，但不能发送弹幕/聊天。
	LiveBanTypeMute LiveBanType = 0

	// LiveBanTypeKick 踢出并禁言：用户被强制断开观看连接，并在一定时间内禁止再次进入。
	LiveBanTypeKick LiveBanType = 1
)

// LiveBan 直播间处罚记录（禁言/踢出）。
// 通过 expire_at 控制处罚时长，支持临时禁言和永久封禁。
type LiveBan struct {
	// ID 主键，自增。
	ID uint64 `gorm:"primaryKey;autoIncrement" json:"id"`

	// RoomID 被处罚用户所在的直播间 ID。
	RoomID uint64 `gorm:"index;not null" json:"roomId"`

	// UserID 被处罚用户 ID。
	UserID uint64 `gorm:"index;not null" json:"userId"`

	// Type 处罚类型：0=禁言，1=踢出并禁言。
	Type LiveBanType `gorm:"default:0" json:"type"`

	// ExpireAt 处罚过期时间。当前时间 < ExpireAt 时表示处罚仍然有效。
	ExpireAt time.Time `json:"expireAt"`

	// Reason 处罚原因，方便管理后台查看。
	Reason string `gorm:"size:200" json:"reason"`

	// OpUserID 执行处罚的管理员/主播用户 ID。
	OpUserID uint64 `json:"opUserId"`

	CreatedAt time.Time `json:"createdAt"`
}

// LiveMessage 直播间实时消息（聊天/弹幕）。
// 该结构用于 WebSocket 广播和 Redis Pub/Sub，不直接落库（除非需要审计）。
type LiveMessage struct {
	// Type 消息类型：chat=普通聊天，danmaku=弹幕，gift=礼物，system=系统通知等。
	Type string `json:"type"`

	// RoomID 直播间 ID。
	RoomID uint64 `json:"roomId"`

	// UserID 发送者用户 ID，未登录游客为 0。
	UserID uint64 `json:"userId"`

	// Username 发送者昵称。
	Username string `json:"username"`

	// Content 消息内容。
	Content string `json:"content"`

	// Color 弹幕颜色（仅弹幕类型使用）。
	Color string `json:"color,omitempty"`

	// Ts 消息发送时间戳（毫秒），用于前端排序和去重。
	Ts int64 `json:"ts"`
}
