// Package zlm 封装 ZLMediaKit 的 RESTful 管理 API 调用。
//
// 说明：
//
//	ZLMediaKit 是一款开源流媒体服务器，负责接收 OBS 的 RTMP 推流并把流分发给观众。
//	video 服务作为“信令服务器”，通过本包调用 ZLMediaKit 的管理接口，
//	实现关闭流、查询在线观众数等操作；推流/播放地址的拼接也在这里完成。
package zlm

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"time"

	"video/config"
)

// Client ZLMediaKit 管理 API 客户端。
type Client struct {
	cfg config.ZLMediaKitConfig
	hc  *http.Client
}

// NewClient 创建 ZLMediaKit 客户端。
func NewClient(cfg config.ZLMediaKitConfig) *Client {
	return &Client{
		cfg: cfg,
		hc: &http.Client{
			Timeout: 5 * time.Second,
		},
	}
}

// baseURL 返回 ZLMediaKit HTTP API 基础地址。
func (c *Client) baseURL() string {
	return "http://" + c.cfg.Host
}

// CloseStreams 强制关闭指定流。
// 当主播在管理后台点击“结束直播”时，video 服务可以调用此接口断开 ZLMediaKit 上的推流。
func (c *Client) CloseStreams(streamID string) error {
	params := url.Values{}
	params.Set("secret", c.cfg.Secret)
	params.Set("schema", "rtmp")
	params.Set("vhost", "__defaultVhost__")
	params.Set("app", "live")
	params.Set("stream", streamID)
	params.Set("force", "1")

	apiURL := fmt.Sprintf("%s/index/api/closeStreams?%s", c.baseURL(), params.Encode())
	resp, err := c.hc.Get(apiURL)
	if err != nil {
		return fmt.Errorf("调用 ZLMediaKit closeStreams 失败: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("ZLMediaKit closeStreams 返回状态码: %d", resp.StatusCode)
	}
	return nil
}

// GetMediaList 查询当前活跃的媒体流列表。
// 可用于统计当前有多少观众正在观看某个流（ZLMediaKit 会返回 readerCount）。
func (c *Client) GetMediaList(streamID string) (*MediaListResponse, error) {
	params := url.Values{}
	params.Set("secret", c.cfg.Secret)
	if streamID != "" {
		params.Set("stream", streamID)
	}

	apiURL := fmt.Sprintf("%s/index/api/getMediaList?%s", c.baseURL(), params.Encode())
	resp, err := c.hc.Get(apiURL)
	if err != nil {
		return nil, fmt.Errorf("调用 ZLMediaKit getMediaList 失败: %w", err)
	}
	defer resp.Body.Close()

	var result MediaListResponse
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, fmt.Errorf("解析 ZLMediaKit getMediaList 响应失败: %w", err)
	}
	return &result, nil
}

// BuildRTMPPushURL 构造 OBS 推流 URL。
// 例如：rtmp://localhost/live/{streamID}
func (c *Client) BuildRTMPPushURL(streamID string) string {
	return fmt.Sprintf("%s/%s", c.cfg.RTMPPushHost, streamID)
}

// BuildFLVPlaybackURL 构造 HTTP-FLV 播放地址。
// 例如：http://localhost:6080/live/{streamID}.live.flv
func (c *Client) BuildFLVPlaybackURL(streamID string) string {
	return fmt.Sprintf("%s/live/%s.live.flv", c.cfg.HTTPPlaybackURL, streamID)
}

// BuildHLSPlaybackURL 构造 HLS 播放地址。
// 例如：http://localhost:6080/live/{streamID}/hls.m3u8
func (c *Client) BuildHLSPlaybackURL(streamID string) string {
	return fmt.Sprintf("%s/live/%s/hls.m3u8", c.cfg.HTTPPlaybackURL, streamID)
}

// BuildWebRTCPlayURL 构造 WebRTC 播放地址。
// 注意：WebRTC 需要 ZLMediaKit 开启 webrtc 插件并配置 ssl 证书，测试环境可能无法使用。
func (c *Client) BuildWebRTCPlayURL(streamID string) string {
	return fmt.Sprintf("%s/index/api/webrtc?app=live&stream=%s&type=play", c.baseURL(), streamID)
}

// PlaybackURLs 返回一个流的所有播放地址集合，方便前端/小程序按需选择。
func (c *Client) PlaybackURLs(streamID string) map[string]string {
	return map[string]string{
		"rtmp":   fmt.Sprintf("%s/live/%s", c.cfg.RTMPPushHost, streamID),
		"flv":    c.BuildFLVPlaybackURL(streamID),
		"hls":    c.BuildHLSPlaybackURL(streamID),
		"webrtc": c.BuildWebRTCPlayURL(streamID),
	}
}

// MediaListResponse ZLMediaKit getMediaList 接口响应。
type MediaListResponse struct {
	Code int         `json:"code"` // 0 表示成功
	Msg  string      `json:"msg"`
	Data []MediaInfo `json:"data"`
}

// MediaInfo 单个媒体流信息。
type MediaInfo struct {
	App              string `json:"app"`
	Stream           string `json:"stream"`
	Schema           string `json:"schema"`
	ReaderCount      int    `json:"readerCount"`      // 当前观看人数
	TotalReaderCount int    `json:"totalReaderCount"` // 总观看人数（可能包含已断开）
	OriginURL        string `json:"originUrl"`
	OriginType       int    `json:"originType"`
	Status           int    `json:"status"`
}
