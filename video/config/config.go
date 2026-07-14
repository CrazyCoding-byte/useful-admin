// Package config 负责 video 模块的专属配置管理。
//
// 设计说明：
//
//	数据库、MinIO、Redis、日志、AES 鉴权等中间件的初始化逻辑已经抽取到公共模块 pkg 中。
//	video 模块的配置直接内嵌 pkg/config.Config，并只扩展自己独有的 server、video、zlmediakit 节点。
//
//	内嵌（embedded）后，video.Config 拥有 pkg/config.Config 的所有方法和字段，
//	因此 main.go 中仍然可以像之前一样调用 cfg.Log.InitLogger()、cfg.Database.InitDatabase() 等。
package config

import (
	"fmt"
	"os"

	"gopkg.in/yaml.v3"

	// 使用别名 pkgconfig 导入公共配置包，避免与当前包的 config 命名冲突
	pkgconfig "local/pkg/config"
)

// Config 是 video 模块的总配置。
// 通过 yaml:",inline" 把公共配置节点（database/minio/redis/aseKey/log）展平到当前层级，
// 这样 application.yml 里不需要多套嵌一层，仍然保持原来的写法。
type Config struct {
	// 内嵌公共配置，复用其已经实现好的初始化方法（InitLogger / InitDatabase / InitMinIO / InitRedisClient）
	pkgconfig.Config `yaml:",inline"`

	// Server HTTP 服务配置：监听端口、外网 base-url（用于拼接播放/回调地址）
	Server ServerConfig `yaml:"server"`

	// Video 视频业务配置：ffmpeg 切片参数、试看秒数、弹幕参数等
	Video VideoConfig `yaml:"video"`

	// ZLMediaKit 直播服务器配置：推流地址、播放地址、webhook 密钥等
	ZLMediaKit ZLMediaKitConfig `yaml:"zlmediakit"`
}

// ServerConfig HTTP 服务配置。
// Port 是服务监听的端口；BaseURL 用于生成给前端/小程序的完整播放地址和 ZLMediaKit webhook 回调地址。
type ServerConfig struct {
	Port    int    `yaml:"port"`     // 监听端口，例如 8890
	BaseURL string `yaml:"base-url"` // 服务外网地址，例如 http://localhost:8890
}

// VideoConfig 视频业务专属配置。
// 这些参数控制上传后的转码行为、试看策略以及弹幕系统的缓存策略。
type VideoConfig struct {
	// WorkDir 是 ffmpeg 切片时的本地临时目录。每个视频会在这个目录下创建独立子目录，
	// 转码完成后自动删除，避免磁盘无限增长。
	WorkDir string `yaml:"work-dir"`

	// HlsSegmentTime 是 HLS 每个 ts 切片的时长（秒）。值越小，直播/点播延迟越低，但切片文件越多；
	// 值越大，缓存效率越高，但seek精度下降。教学视频推荐 6~10 秒。
	HlsSegmentTime int `yaml:"hls-segment-time"`

	// DefaultTrialSeconds 是课程未购买、非会员且课程不免费时，默认允许试看的秒数。
	// 单个视频可覆盖此值；若视频自身 TrialSeconds <= 0，则 fallback 到该默认值。
	DefaultTrialSeconds int `yaml:"default-trial-seconds"`

	// DanmakuHistoryLimit 是用户进入视频房间时，一次性推送的历史弹幕条数上限。
	// 防止进入热门视频时一次性推送过多消息导致前端卡顿。
	DanmakuHistoryLimit int `yaml:"danmaku-history-limit"`

	// DanmakuRedisChannel 是 Redis Pub/Sub 频道前缀。video 服务多实例部署时，
	// 实例 A 收到的弹幕会发布到 "前缀+videoId" 频道，其他实例订阅后本地广播。
	DanmakuRedisChannel string `yaml:"danmaku-redis-channel"`
}

// ZLMediaKitConfig 直播服务器配置。
// ZLMediaKit 是一款开源流媒体服务器，支持 RTMP/RTSP/HTTP-FLV/HLS/WebRTC 协议。
// 直播链路：OBS（推流） -> ZLMediaKit（收流/分发） -> 小程序/H5（拉流）。
// 我们的 video 服务只负责“信令”：创建直播间、生成推流地址、校验推流、提供拉流地址。
type ZLMediaKitConfig struct {
	// Host 是 ZLMediaKit 的 HTTP API 地址，用于调用 RESTful 管理接口，例如 localhost:6080
	Host string `yaml:"host"`

	// Secret 是 ZLMediaKit 配置文件中的 adminParamsSecret，调用 /index/api/* 接口时需要携带。
	Secret string `yaml:"secret"`

	// RTMPPushHost 是 OBS 推流的基础 RTMP 地址，例如 rtmp://localhost/live
	// 实际推流 URL 为：rtmp://localhost/live/{streamId}
	RTMPPushHost string `yaml:"rtmp-push-host"`

	// HTTPPlaybackURL 是 HTTP 播放基础地址，例如 http://localhost:6080
	// 播放 URL 会根据此地址拼接成 /live/{streamId}.live.flv 或 /live/{streamId}/hls.m3u8
	HTTPPlaybackURL string `yaml:"http-playback-url"`

	// EnableWebhook 控制是否启用 ZLMediaKit webhook 回调。
	// 开启后，ZLMediaKit 在推流开始/结束、播放开始等事件时会主动通知 video 服务。
	EnableWebhook bool `yaml:"enable-webhook"`

	// WebhookSecret 用于校验 ZLMediaKit 回调请求的合法性，防止第三方伪造推流事件。
	WebhookSecret string `yaml:"webhook-secret"`
}

// LoadConfig 从指定路径读取 YAML 配置文件并解析成 Config 结构体。
//
// 解析规则：
//   - 由于 Config 内嵌了 pkgconfig.Config 并使用 yaml:",inline"，
//     YAML 中的 database、minio、redis、log、aseKey 等节点会被解析到公共配置里；
//   - server、video、zlmediakit 节点由本包定义的结构体接收。
//
// 这种设计让我们既能复用公共模块的初始化能力，又能保持 video 业务配置的独立性。
func LoadConfig(path string) (*Config, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("读取配置文件 %s 失败: %w", path, err)
	}
	var cfg Config
	if err := yaml.Unmarshal(data, &cfg); err != nil {
		return nil, fmt.Errorf("解析配置文件 %s 失败: %w", path, err)
	}
	return &cfg, nil
}
