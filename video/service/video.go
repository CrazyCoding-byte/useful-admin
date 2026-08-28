// Package service 提供视频业务的核心能力：
//  1. 视频上传：把原始视频文件上传到 MinIO；
//  2. 视频转码：异步调用 ffmpeg 生成 HLS 切片（完整版 + 试看版），再上传回 MinIO；
//  3. 播放鉴权：根据用户是否购买/会员/免费，返回完整版或试看版的 m3u8 预签名 URL。
//
// 设计说明：
//
//	转码是 CPU/IO 密集型操作，不能在 HTTP 请求协程中同步执行，
//	因此 UploadVideo 在保存元数据后会启动一个后台 goroutine 调用 transcode。
package service

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"io"
	"log/slog"
	"math"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"time"
	"video/config"
	"video/model"
	"video/repository"

	"github.com/google/uuid"
)

// VideoService 视频业务服务。
type VideoService struct {
	repos    *repository.Repositories // 数据仓库集合
	cfg      *config.VideoConfig      // 视频相关配置（切片时长、试看秒数、工作目录等）
	server   *config.ServerConfig     // 服务地址配置（用于拼接回调/播放地址）
	uploader *repository.ChunkUploader // 分片上传器（复用 IM 已验证的 Multipart 方案）
}

// NewVideoService 创建视频业务服务实例。
func NewVideoService(repos *repository.Repositories, cfg *config.Config, uploader *repository.ChunkUploader) *VideoService {
	return &VideoService{
		repos:    repos,
		cfg:      &cfg.Video,
		server:   &cfg.Server,
		uploader: uploader,
	}
}

// Create 创建视频记录（仅写库，不上传文件）。
func (s *VideoService) Create(video *model.CourseVideo) error {
	return s.repos.VideoRepo.Create(video)
}

// Update 更新视频记录。
func (s *VideoService) Update(video *model.CourseVideo) error {
	return s.repos.VideoRepo.Update(video)
}

// Delete 删除视频记录，并清理 MinIO 上的原始视频和 HLS 切片。
func (s *VideoService) Delete(id uint64) error {
	video, err := s.repos.VideoRepo.GetByID(id)
	if err != nil {
		return err
	}
	// 如果已经生成 HLS，则删除该视频下的所有切片和 m3u8
	if video.HlsPath != "" {
		ctx := context.Background()
		_ = s.repos.MinioRepo.RemovePrefix(ctx, video.HlsPath)
		if video.OriginalObject != "" {
			_ = s.repos.MinioRepo.RemoveObject(ctx, video.OriginalObject)
		}
	}
	return s.repos.VideoRepo.Delete(id)
}

// GetByID 根据 ID 查询视频。
func (s *VideoService) GetByID(id uint64) (*model.CourseVideo, error) {
	return s.repos.VideoRepo.GetByID(id)
}

// ListByCourse 查询课程下的所有视频。
func (s *VideoService) ListByCourse(courseID uint64) ([]model.CourseVideo, error) {
	return s.repos.VideoRepo.ListByCourse(courseID)
}

// UploadVideo 上传原始视频到 MinIO，并创建视频记录。
// 上传成功后，会启动一个后台 goroutine 调用 ffmpeg 进行切片转码。
func (s *VideoService) UploadVideo(courseID uint64, chapterID uint64, title string, trialSeconds int, fileReader io.Reader, size int64, contentType string) (*model.CourseVideo, error) {
	// 1. 先写数据库，拿到自增 ID，后续用这个 ID 作为 MinIO 对象路径的一部分
	video := &model.CourseVideo{
		CourseID:     courseID,
		ChapterID:    chapterID,
		Title:        title,
		TrialSeconds: trialSeconds,
		Status:       0, // 0=待转码
	}
	if err := s.repos.VideoRepo.Create(video); err != nil {
		return nil, err
	}

	// 2. 构造 MinIO 对象名：course/{courseId}/video/{videoId}/original/{videoId}.mp4
	ext := ".mp4"
	if contentType == "video/x-matroska" {
		ext = ".mkv"
	}
	objectName := fmt.Sprintf("%soriginal/%d%s", repository.BuildHlsPrefix(courseID, video.ID), video.ID, ext)

	// 3. 上传到 MinIO
	ctx := context.Background()
	if err := s.repos.MinioRepo.Upload(ctx, objectName, fileReader, size, contentType); err != nil {
		return nil, err
	}

	// 4. 更新数据库中的原始视频对象名
	video.OriginalObject = objectName
	if err := s.repos.VideoRepo.Update(video); err != nil {
		return nil, err
	}

	// 5. 异步转码：避免上传接口阻塞
	go s.transcode(video)

	return video, nil
}

// hlsRendition 一档清晰度（多码率转码的产物）。
type hlsRendition struct {
	Name      string // 档位标识，如 full_1080
	Height    int    // 目标高度
	Bandwidth int    // master.m3u8 里的 BANDWIDTH（用于播放器自适应选档）
}

// 多码率档位：从高到低，超过原片分辨率的档会被跳过。
var hlsRenditions = []hlsRendition{
	{Name: "full_1080", Height: 1080, Bandwidth: 2800000},
	{Name: "full_720", Height: 720, Bandwidth: 1400000},
	{Name: "full_480", Height: 480, Bandwidth: 600000},
}

// transcode 调用 ffmpeg 对原始视频做多码率 HLS 切片（AES-128 加密），并上传回 MinIO。
//
// 产物结构（HlsPath 前缀下）：
//
//	master.m3u8             多码率主清单（播放器入口）
//	full_1080.m3u8/.ts      1080p 档（加密）
//	full_720.m3u8/.ts       720p 档（加密）
//	full_480.m3u8/.ts       480p 档（加密，原片分辨率不足则跳过）
//	trial.m3u8/.ts          试看版（单档 720p，只含前 N 秒，独立密钥）
//	keys/{keyId}.key        16 字节 AES-128 密钥（私有，仅供密钥接口读取）
//
// 这是一个耗时操作，必须在后台 goroutine 中执行。
func (s *VideoService) transcode(video *model.CourseVideo) {
	ctx := context.Background()
	workDir := filepath.Join(s.cfg.WorkDir, fmt.Sprintf("%d_%d", video.CourseID, video.ID))
	// 转成绝对路径：keyinfo 文件里的密钥路径会被 ffmpeg 直接读取，
	// 相对路径会受 ffmpeg 启动目录影响，绝对路径最稳妥（跨平台）。
	if abs, err := filepath.Abs(workDir); err == nil {
		workDir = abs
	}
	_ = os.MkdirAll(workDir, 0755)
	defer os.RemoveAll(workDir)

	fail := func(err error) {
		slog.Error("转码失败", "error", err, "videoId", video.ID)
		_ = s.repos.VideoRepo.UpdateStatus(video.ID, 2)
	}

	// 1. 从 MinIO 下载原始视频到本地
	localRaw := filepath.Join(workDir, "raw"+filepath.Ext(video.OriginalObject))
	obj, err := s.repos.MinioRepo.Download(ctx, video.OriginalObject)
	if err != nil {
		fail(fmt.Errorf("下载原始视频失败: %w", err))
		return
	}
	out, err := os.Create(localRaw)
	if err != nil {
		obj.Close()
		fail(fmt.Errorf("创建本地文件失败: %w", err))
		return
	}
	_, err = io.Copy(out, obj)
	out.Close()
	obj.Close()
	if err != nil {
		fail(fmt.Errorf("写入本地文件失败: %w", err))
		return
	}

	// 2. ffprobe 获取时长与分辨率
	duration, _ := s.getVideoDuration(localRaw)
	_, srcHeight := s.getVideoResolution(localRaw)
	segmentTime := strconv.Itoa(s.cfg.HlsSegmentTime)

	// 3. 生成完整版/试看版的 AES-128 密钥（16 字节，keyId 用 UUID 保证不可枚举）
	fullKeyID := uuid.NewString()
	trialKeyID := uuid.NewString()
	keyDir := filepath.Join(workDir, "keys")
	_ = os.MkdirAll(keyDir, 0755)
	fullKeyPath := filepath.Join(keyDir, fullKeyID+".key")
	trialKeyPath := filepath.Join(keyDir, trialKeyID+".key")
	fullKey, err := generateAESKey(fullKeyPath)
	if err != nil {
		fail(fmt.Errorf("生成完整版密钥失败: %w", err))
		return
	}
	trialKey, err := generateAESKey(trialKeyPath)
	if err != nil {
		fail(fmt.Errorf("生成试看版密钥失败: %w", err))
		return
	}
	_ = fullKey
	_ = trialKey

	// 4. keyinfo 文件：ffmpeg 据此给切片加密，并把 key URI 写进 m3u8
	keyURIPrefix := strings.TrimRight(s.server.BaseURL, "/") + "/api/video/key/"
	fullKeyInfo := filepath.Join(workDir, "keyinfo_full")
	if err := writeKeyInfo(fullKeyInfo, keyURIPrefix+fullKeyID, fullKeyPath); err != nil {
		fail(fmt.Errorf("生成完整版 keyinfo 失败: %w", err))
		return
	}
	trialKeyInfo := filepath.Join(workDir, "keyinfo_trial")
	if err := writeKeyInfo(trialKeyInfo, keyURIPrefix+trialKeyID, trialKeyPath); err != nil {
		fail(fmt.Errorf("生成试看版 keyinfo 失败: %w", err))
		return
	}

	// 5. 逐档重编码完整版（跳过超过原片分辨率的档位）
	renditions := make([]hlsRendition, 0, len(hlsRenditions))
	for _, r := range hlsRenditions {
		if srcHeight > 0 && srcHeight < r.Height {
			slog.Info("原片分辨率低于目标档，跳过", "videoId", video.ID, "target", r.Height, "src", srcHeight)
			continue
		}
		renditions = append(renditions, r)
	}
	if len(renditions) == 0 {
		renditions = []hlsRendition{{Name: "full_480", Height: 480, Bandwidth: 600000}}
	}

	for _, r := range renditions {
		m3u8Path := filepath.Join(workDir, r.Name+".m3u8")
		cmd := exec.Command("ffmpeg", "-y", "-i", localRaw,
			"-vf", fmt.Sprintf("scale=-2:%d", r.Height),
			"-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
			"-c:a", "aac", "-b:a", "128k",
			"-start_number", "0", "-hls_time", segmentTime, "-hls_list_size", "0",
			"-hls_key_info_file", fullKeyInfo,
			"-hls_segment_filename", filepath.Join(workDir, r.Name+"_%05d.ts"),
			"-f", "hls", m3u8Path)
		if output, err := cmd.CombinedOutput(); err != nil {
			fail(fmt.Errorf("ffmpeg 转码档 %s 失败: %w, output=%s", r.Name, err, string(output)))
			return
		}
		slog.Info("完整版档位转码完成", "videoId", video.ID, "rendition", r.Name)
	}

	// 6. 生成多码率主清单 master.m3u8
	masterPath := filepath.Join(workDir, "master.m3u8")
	if err := os.WriteFile(masterPath, []byte(buildMasterPlaylist(renditions)), 0644); err != nil {
		fail(fmt.Errorf("生成 master.m3u8 失败: %w", err))
		return
	}

	// 7. 试看版：前 N 秒，720p 单档，独立密钥
	trialSeconds := video.TrialSeconds
	if trialSeconds <= 0 {
		trialSeconds = s.cfg.DefaultTrialSeconds
	}
	trialM3u8Path := filepath.Join(workDir, "trial.m3u8")
	cmd := exec.Command("ffmpeg", "-y", "-i", localRaw, "-t", strconv.Itoa(trialSeconds),
		"-vf", "scale=-2:720",
		"-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
		"-c:a", "aac", "-b:a", "128k",
		"-start_number", "0", "-hls_time", segmentTime, "-hls_list_size", "0",
		"-hls_key_info_file", trialKeyInfo,
		"-hls_segment_filename", filepath.Join(workDir, "trial_%05d.ts"),
		"-f", "hls", trialM3u8Path)
	if output, err := cmd.CombinedOutput(); err != nil {
		fail(fmt.Errorf("ffmpeg 试看版切片失败: %w, output=%s", err, string(output)))
		return
	}

	// 8. 递归上传 workDir 下所有文件（含 keys/ 子目录）到 MinIO
	prefix := repository.BuildHlsPrefix(video.CourseID, video.ID)
	if err := filepath.Walk(workDir, func(path string, info os.FileInfo, err error) error {
		if err != nil || info.IsDir() {
			return err
		}
		rel, err := filepath.Rel(workDir, path)
		if err != nil {
			return err
		}
		objectName := prefix + filepath.ToSlash(rel)
		contentType := "video/MP2T"
		switch {
		case strings.HasSuffix(rel, ".m3u8"):
			contentType = "application/vnd.apple.mpegurl"
		case strings.HasSuffix(rel, ".key"):
			contentType = "application/octet-stream"
		}
		if err := s.repos.MinioRepo.UploadFile(ctx, objectName, path, contentType); err != nil {
			return fmt.Errorf("上传 %s 失败: %w", objectName, err)
		}
		return nil
	}); err != nil {
		fail(err)
		return
	}

	// 9. 更新数据库：时长、HLS 路径、密钥 ID、状态=已转码
	video.Duration = int(duration)
	video.HlsPath = prefix
	video.FullM3u8 = "master.m3u8"
	video.TrialM3u8 = "trial.m3u8"
	video.FullKeyID = fullKeyID
	video.TrialKeyID = trialKeyID
	video.Status = 1
	if err := s.repos.VideoRepo.Update(video); err != nil {
		fail(fmt.Errorf("更新视频状态失败: %w", err))
		return
	}
	slog.Info("视频转码完成", "videoId", video.ID, "duration", duration,
		"renditions", len(renditions), "encrypted", true)
}

// getVideoDuration 调用 ffprobe 获取视频时长（秒）。
func (s *VideoService) getVideoDuration(filePath string) (float64, error) {
	cmd := exec.Command("ffprobe", "-v", "error", "-show_entries", "format=duration",
		"-of", "default=noprint_wrappers=1:nokey=1", filePath)
	output, err := cmd.Output()
	if err != nil {
		return 0, err
	}
	return strconv.ParseFloat(strings.TrimSpace(string(output)), 64)
}

// GetPlayInfo 获取视频播放信息。
// GetPlayInfo 获取播放信息。
// 如果课程免费、用户已购买、用户是有效会员，或用户是管理员角色，返回完整版 m3u8；否则返回试看版。
//
// 管理员 bypass 规则：isAdmin=true 时无视课程付费/会员/购买状态，直接 canWatchFull=true。
// 这样 useful-admin 后台点播放永远拿到完整版（方便验证多码率 + 加密 + 转码全链路），
// 而普通用户在小程序/web 端走原有权限链路。
func (s *VideoService) GetPlayInfo(videoID uint64, userID uint64, isAdmin bool) (map[string]any, error) {
	video, err := s.repos.VideoRepo.GetByID(videoID)
	if err != nil {
		return nil, err
	}
	course, err := s.repos.CourseRepo.GetByID(video.CourseID)
	if err != nil {
		return nil, err
	}

	// 管理员或免费课程：直接放行完整版
	canWatchFull := course.IsFree || isAdmin
	// 已登录且非免费课程，再判断会员/购买
	if !isAdmin && !canWatchFull && userID > 0 {
		canWatchFull, err = s.repos.PermissionRepo.CanWatchFull(userID, course)
		if err != nil {
			return nil, err
		}
	}

	// 根据权限选择 m3u8 文件（完整版 = 多码率 master.m3u8；试看版 = trial.m3u8）
	var m3u8Object, keyID string
	if canWatchFull {
		m3u8Object = video.HlsPath + video.FullM3u8
		keyID = video.FullKeyID
	} else {
		m3u8Object = video.HlsPath + video.TrialM3u8
		keyID = video.TrialKeyID
	}

	// m3u8Url 走 video 服务的代理端点（不走 MinIO presigned），
	// 因为 m3u8 里的 ts 切片是 ffmpeg 生成的相对路径，浏览器用 m3u8 base URL
	// 解析出来的 ts 没带 X-Amz- 签名，MinIO 会拒绝。
	// 代理端点会把每个 ts 替换成独立 presigned URL 后返回给浏览器。
	return map[string]any{
		"videoId":      video.ID,
		"title":        video.Title,
		"duration":     video.Duration,
		"trialSeconds": video.TrialSeconds,
		"canWatchFull": canWatchFull,
		"m3u8Url":      s.m3u8ProxyURL(video.ID, m3u8Object),
		"keyId":        keyID,
	}, nil
}

// m3u8ProxyURL 构造 video 服务自己的 m3u8 代理 URL（不走 MinIO presigned）。
// kind 是 m3u8 文件前缀（不含 .m3u8），例如 "master" / "trial" / "full_1080"。
func (s *VideoService) m3u8ProxyURL(videoID uint64, m3u8Object string) string {
	// 从完整 objectKey 提取文件名前缀（如 "course/1/video/17/master.m3u8" → "master"）
	name := m3u8Object[strings.LastIndex(m3u8Object, "/")+1:]
	prefix := strings.TrimSuffix(name, ".m3u8")
	return fmt.Sprintf("%s/api/video/m3u8/%d/%s", s.server.BaseURL, videoID, prefix)
}

// GetTsUrl 获取 ts 切片的预签名 URL（可选，用于更严格鉴权场景）。
func (s *VideoService) GetTsUrl(objectName string) (string, error) {
	ctx := context.Background()
	return s.repos.MinioRepo.PresignedGetURL(ctx, objectName, 2*time.Hour)
}

// ProxyM3U8 读取 m3u8 文件内容，把里面每个 .ts 切片替换成独立的 presigned URL，
// 把每个 .m3u8 子清单（master.m3u8 里的 full_1080.m3u8 之类）替换为 video 服务的代理 URL（递归）。
//
// 为什么需要这个代理：ffmpeg 生成的 m3u8 内部 ts 用相对路径，浏览器解析时不会继承
// m3u8 URL 的查询参数（X-Amz- 签名），导致 ts 请求被 MinIO 拒绝（AccessDenied）。
// 改为由 video 服务代理：拉 m3u8 内容 → 正则替换每个 .ts 为带签名的绝对 URL，
// 把每个子 m3u8 也指向 video 代理（递归）→ 返回给浏览器。
//
// kind 是 m3u8 文件名前缀（不含 .m3u8），允许值：master / trial / full_1080 / full_720 / full_480。
func (s *VideoService) ProxyM3U8(videoID uint64, kind string) (string, error) {
	video, err := s.repos.VideoRepo.GetByID(videoID)
	if err != nil {
		return "", fmt.Errorf("视频不存在: %w", err)
	}
	if video.Status != 1 {
		return "", fmt.Errorf("视频尚未转码完成")
	}

	// 白名单校验，避免任意路径拼接
	allowed := map[string]bool{
		"master":     true,
		"trial":      true,
		"full_1080":  true,
		"full_720":   true,
		"full_480":   true,
	}
	if !allowed[kind] {
		return "", fmt.Errorf("不支持的 m3u8 类型: %s", kind)
	}
	m3u8Object := video.HlsPath + kind + ".m3u8"

	ctx := context.Background()
	body, err := s.repos.MinioRepo.DownloadObject(ctx, m3u8Object)
	if err != nil {
		return "", fmt.Errorf("读取 m3u8 失败: %w", err)
	}

	prefix := video.HlsPath
	// 子 m3u8 路径 → 指向 video 服务代理（递归），保证下一级 m3u8 也走 video 注入 ts 签名
	re := regexp.MustCompile(`(?m)^([^#\s]+\.m3u8)\s*$`)
	bodyStr := re.ReplaceAllStringFunc(string(body), func(line string) string {
		rel := strings.TrimSpace(line)
		childName := strings.TrimSuffix(rel, ".m3u8")
		return fmt.Sprintf("%s/api/video/m3u8/%d/%s", s.server.BaseURL, videoID, childName)
	})

	// ts 切片 → MinIO presigned URL（2 小时）
	tsRe := regexp.MustCompile(`(?m)^([^#\s]+\.ts)\s*$`)
	signed := tsRe.ReplaceAllStringFunc(bodyStr, func(line string) string {
		rel := strings.TrimSpace(line)
		fullObj := prefix + rel
		u, err := s.repos.MinioRepo.PresignedGetURL(ctx, fullObj, 2*time.Hour)
		if err != nil {
			return line
		}
		return u
	})
	return signed, nil
}

// -------------------------- HLS AES-128 加密密钥 --------------------------

// GetPlayKey 根据 keyId 返回 16 字节 AES-128 解密密钥。
//
// 说明：
//   - m3u8 里的 EXT-X-KEY URI 指向 GET /api/video/key/{keyId}；
//   - 播放器（小程序 video 组件/H5 播放器）拉取该 URI 时不会带 Authorization header，
//     因此该接口不强制 Bearer 鉴权，安全依赖 keyId 的随机不可枚举性 + m3u8 预签名 URL；
//   - keyId 必须属于某个视频（full_key_id 或 trial_key_id），否则 404。
func (s *VideoService) GetPlayKey(keyID string) ([]byte, error) {
	video, err := s.repos.VideoRepo.GetByKeyID(keyID)
	if err != nil {
		return nil, fmt.Errorf("密钥不存在: %w", err)
	}
	objectName := video.HlsPath + "keys/" + keyID + ".key"
	ctx := context.Background()
	obj, err := s.repos.MinioRepo.Download(ctx, objectName)
	if err != nil {
		return nil, err
	}
	defer obj.Close()
	return io.ReadAll(obj)
}

// getVideoResolution 调用 ffprobe 获取视频分辨率（宽、高）。
// 探测失败时返回 0,0，调用方据此跳过档位判断。
func (s *VideoService) getVideoResolution(filePath string) (int, int) {
	cmd := exec.Command("ffprobe", "-v", "error", "-select_streams", "v:0",
		"-show_entries", "stream=width,height", "-of", "csv=p=0", filePath)
	output, err := cmd.Output()
	if err != nil {
		slog.Warn("探测视频分辨率失败", "err", err, "file", filePath)
		return 0, 0
	}
	parts := strings.Split(strings.TrimSpace(string(output)), ",")
	if len(parts) < 2 {
		return 0, 0
	}
	w, errW := strconv.Atoi(strings.TrimSpace(parts[0]))
	h, errH := strconv.Atoi(strings.TrimSpace(parts[1]))
	if errW != nil || errH != nil {
		return 0, 0
	}
	return w, h
}

// generateAESKey 生成 16 字节 AES-128 密钥并写入本地文件，返回密钥字节。
func generateAESKey(filePath string) ([]byte, error) {
	key := make([]byte, 16)
	if _, err := rand.Read(key); err != nil {
		return nil, fmt.Errorf("生成随机密钥失败: %w", err)
	}
	if err := os.WriteFile(filePath, key, 0600); err != nil {
		return nil, err
	}
	return key, nil
}

// writeKeyInfo 生成 ffmpeg -hls_key_info_file 所需的密钥信息文件。
//
//	格式（每行一个）：
//	  第 1 行：key URI（原样写入 m3u8 的 EXT-X-KEY），播放器据此请求密钥；
//	  第 2 行：本地密钥文件路径；
//	  第 3 行：IV（32 位 hex），不写则由 ffmpeg 按分片序号派生。
func writeKeyInfo(infoPath, keyURI, keyFilePath string) error {
	iv := make([]byte, 16)
	if _, err := rand.Read(iv); err != nil {
		return fmt.Errorf("生成随机 IV 失败: %w", err)
	}
	content := fmt.Sprintf("%s\n%s\n%s\n", keyURI, keyFilePath, hex.EncodeToString(iv))
	return os.WriteFile(infoPath, []byte(content), 0600)
}

// buildMasterPlaylist 生成多码率主清单 master.m3u8 内容。
// 子清单用相对路径（与 master.m3u8 同目录），播放器自动按带宽/分辨率自适应。
func buildMasterPlaylist(renditions []hlsRendition) string {
	var b strings.Builder
	b.WriteString("#EXTM3U\n")
	b.WriteString("#EXT-X-VERSION:3\n")
	for _, r := range renditions {
		height := r.Height
		width := 0
		switch height {
		case 1080:
			width = 1920
		case 720:
			width = 1280
		case 480:
			width = 854
		}
		fmt.Fprintf(&b, "#EXT-X-STREAM-INF:BANDWIDTH=%d,RESOLUTION=%dx%d\n", r.Bandwidth, width, height)
		b.WriteString(r.Name + ".m3u8\n")
	}
	return b.String()
}

// -------------------------- 分片上传（切片上传 + 断点续传） --------------------------

// InitChunkUploadResult InitChunkUpload 的返回结果。
type InitChunkUploadResult struct {
	VideoID     uint64 `json:"videoId"`     // 视频记录 ID
	UploadID    string `json:"uploadID"`    // MinIO Multipart UploadID（秒传时为空）
	ObjectKey   string `json:"objectKey"`   // MinIO 对象名
	TotalChunks int    `json:"totalChunks"` // 总分片数
	ChunkSize   int64  `json:"chunkSize"`   // 单分片大小（字节），前端按此切片
	Instant     bool   `json:"instant"`     // true 表示秒传命中，无需再上传
}

// InitChunkUpload 初始化分片上传。
// 流程：
//  1. 先创建 CourseVideo 记录（状态=待转码），拿到自增 videoID；
//  2. 用 videoID 构造 MinIO 对象名 course/{courseId}/video/{videoId}/original/{videoId}.mp4；
//  3. 调用分片上传器创建 Multipart 任务（内部会先做秒传查重）。
//
// 秒传命中时 Instant=true，此时 UploadID 为空，ObjectKey 指向已存在的文件，
// 前端可直接提示"文件已存在"，或由后端直接进入转码流程。
func (s *VideoService) InitChunkUpload(courseID, chapterID uint64, title string, trialSeconds int, fileName, mimeType string, fileSize int64, fileHash string) (*InitChunkUploadResult, error) {
	// 1. 先建视频记录（分片上传阶段状态为 0=待转码）
	video := &model.CourseVideo{
		CourseID:     courseID,
		ChapterID:    chapterID,
		Title:        title,
		TrialSeconds: trialSeconds,
		Status:       0,
	}
	if err := s.repos.VideoRepo.Create(video); err != nil {
		return nil, err
	}

	// 2. 构造 MinIO 对象名（保留原始扩展名，默认 .mp4）
	ext := filepath.Ext(fileName)
	if ext == "" || len(ext) > 10 {
		ext = ".mp4"
	}
	objectKey := fmt.Sprintf("%soriginal/%d%s", repository.BuildHlsPrefix(courseID, video.ID), video.ID, ext)

	// 3. 创建 Multipart 任务（秒传命中时返回 file_exists|<objectKey>）
	uploadID, err := s.uploader.InitUpload(context.Background(), objectKey, fileName, mimeType, fileSize, fileHash)
	if err != nil {
		// 秒传：文件已存在，直接使用已有对象，并触发转码
		if strings.HasPrefix(err.Error(), "file_exists|") {
			existingKey := strings.TrimPrefix(err.Error(), "file_exists|")
			video.OriginalObject = existingKey
			_ = s.repos.VideoRepo.Update(video)
			// 异步转码（会为这个新 videoId 生成独立的 HLS 目录）
			go s.transcode(video)
			return &InitChunkUploadResult{
				VideoID:     video.ID,
				ObjectKey:   existingKey,
				TotalChunks: 0,
				ChunkSize:   s.uploader.ChunkSize(),
				Instant:     true,
			}, nil
		}
		// 失败：删除刚创建的记录，避免残留脏数据
		_ = s.repos.VideoRepo.Delete(video.ID)
		return nil, err
	}

	// 4. 把 objectKey 写回 video 记录（之前漏掉，导致 CompleteChunkUpload 时 GetByObjectKey 找不到）
	//    注意：必须 Update，否则后续 CompleteChunkUpload 调 GetByObjectKey(objectKey) 会失败。
	video.OriginalObject = objectKey
	if err := s.repos.VideoRepo.Update(video); err != nil {
		// 写库失败也要继续 —— 前端有 uploadID 还能传，complete 时再补救
		slog.Error("InitChunkUpload: 写 video.OriginalObject 失败", "err", err, "videoID", video.ID)
	}

	// 4. 计算总分片数（与 uploader 内部算法一致）
	totalChunks := int(math.Ceil(float64(fileSize) / float64(s.uploader.ChunkSize())))
	if totalChunks <= 0 {
		totalChunks = 1
	}
	return &InitChunkUploadResult{
		VideoID:     video.ID,
		UploadID:    uploadID,
		ObjectKey:   objectKey,
		TotalChunks: totalChunks,
		ChunkSize:   s.uploader.ChunkSize(),
		Instant:     false,
	}, nil
}

// UploadChunk 上传单个分片（断点续传：已上传且 hash 一致的分片会跳过）。
func (s *VideoService) UploadChunk(uploadID string, chunkIndex int, chunkData []byte) error {
	return s.uploader.UploadChunk(context.Background(), uploadID, chunkIndex, chunkData)
}

// ChunkProgress 查询分片上传进度。
func (s *VideoService) ChunkProgress(uploadID string) (float64, []int, int, error) {
	return s.uploader.QueryProgress(context.Background(), uploadID)
}

// CompleteChunkUpload 合并分片，更新视频记录并触发异步转码。
// 返回合并后的视频记录。
func (s *VideoService) CompleteChunkUpload(uploadID string) (*model.CourseVideo, error) {
	// 1. 合并分片（内部校验分片完整性 + 写秒传元数据）
	objectKey, err := s.uploader.CompleteUpload(context.Background(), uploadID)
	if err != nil {
		return nil, err
	}

	// 2. 用 objectKey 反查 InitChunkUpload 阶段创建的记录
	video, err := s.repos.VideoRepo.GetByObjectKey(objectKey)
	if err != nil {
		return nil, fmt.Errorf("找不到对应的视频记录: %w", err)
	}

	// 3. 更新记录并异步转码
	video.OriginalObject = objectKey
	if err := s.repos.VideoRepo.Update(video); err != nil {
		return nil, err
	}
	go s.transcode(video)
	return video, nil
}

// AbortChunkUpload 取消分片上传，并清理 InitChunkUpload 阶段创建的视频记录。
func (s *VideoService) AbortChunkUpload(uploadID string) error {
	// 先取元数据拿到 objectKey，用于反查并删除视频记录
	meta, err := s.uploader.GetUploadMeta(context.Background(), uploadID)
	if err == nil && meta.ObjectKey != "" {
		if video, err := s.repos.VideoRepo.GetByObjectKey(meta.ObjectKey); err == nil {
			_ = s.repos.VideoRepo.Delete(video.ID)
		}
	}
	return s.uploader.AbortUpload(context.Background(), uploadID)
}
