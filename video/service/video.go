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
	"fmt"
	"io"
	"log/slog"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"time"
	"video/config"
	"video/model"
	"video/repository"
)

// VideoService 视频业务服务。
type VideoService struct {
	repos  *repository.Repositories // 数据仓库集合
	cfg    *config.VideoConfig      // 视频相关配置（切片时长、试看秒数、工作目录等）
	server *config.ServerConfig     // 服务地址配置（用于拼接回调/播放地址）
}

// NewVideoService 创建视频业务服务实例。
func NewVideoService(repos *repository.Repositories, cfg *config.Config) *VideoService {
	return &VideoService{
		repos:  repos,
		cfg:    &cfg.Video,
		server: &cfg.Server,
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

// transcode 调用 ffmpeg 对原始视频切片，生成完整版和试看版 HLS，并上传回 MinIO。
// 这是一个耗时操作，必须在后台 goroutine 中执行。
func (s *VideoService) transcode(video *model.CourseVideo) {
	ctx := context.Background()
	// 每个视频一个独立工作目录，转码完成后删除
	workDir := filepath.Join(s.cfg.WorkDir, fmt.Sprintf("%d_%d", video.CourseID, video.ID))
	_ = os.MkdirAll(workDir, 0755)
	defer os.RemoveAll(workDir)

	// 1. 从 MinIO 下载原始视频到本地
	localRaw := filepath.Join(workDir, "raw"+filepath.Ext(video.OriginalObject))
	obj, err := s.repos.MinioRepo.Download(ctx, video.OriginalObject)
	if err != nil {
		slog.Error("下载原始视频失败", "error", err, "videoId", video.ID)
		_ = s.repos.VideoRepo.UpdateStatus(video.ID, 2) // 2=转码失败
		return
	}
	defer obj.Close()

	out, err := os.Create(localRaw)
	if err != nil {
		slog.Error("创建本地文件失败", "error", err)
		_ = s.repos.VideoRepo.UpdateStatus(video.ID, 2)
		return
	}
	_, err = io.Copy(out, obj)
	out.Close()
	if err != nil {
		slog.Error("写入本地文件失败", "error", err)
		_ = s.repos.VideoRepo.UpdateStatus(video.ID, 2)
		return
	}

	// 2. 使用 ffprobe 获取视频总时长
	duration, err := s.getVideoDuration(localRaw)
	if err != nil {
		slog.Error("获取视频时长失败", "error", err)
		duration = 0
	}

	// 3. ffmpeg 切完整版 HLS
	fullM3u8Name := "full.m3u8"
	fullM3u8Path := filepath.Join(workDir, fullM3u8Name)
	segmentTime := strconv.Itoa(s.cfg.HlsSegmentTime)
	// -codec: copy 表示不重新编码，速度最快；如果是特殊格式可能需要重新编码
	cmd := exec.Command("ffmpeg", "-i", localRaw, "-codec:", "copy", "-start_number", "0",
		"-hls_time", segmentTime, "-hls_list_size", "0", "-f", "hls", fullM3u8Path)
	if output, err := cmd.CombinedOutput(); err != nil {
		slog.Error("ffmpeg 完整版切片失败", "error", err, "output", string(output))
		_ = s.repos.VideoRepo.UpdateStatus(video.ID, 2)
		return
	}

	// 4. ffmpeg 切试看版 HLS：只取前 trialSeconds 秒
	trialSeconds := video.TrialSeconds
	if trialSeconds <= 0 {
		trialSeconds = s.cfg.DefaultTrialSeconds
	}
	trialM3u8Name := "trial.m3u8"
	trialM3u8Path := filepath.Join(workDir, trialM3u8Name)
	cmd = exec.Command("ffmpeg", "-i", localRaw, "-t", strconv.Itoa(trialSeconds), "-codec:", "copy",
		"-start_number", "0", "-hls_time", segmentTime, "-hls_list_size", "0", "-f", "hls", trialM3u8Path)
	if output, err := cmd.CombinedOutput(); err != nil {
		slog.Error("ffmpeg 试看版切片失败", "error", err, "output", string(output))
		_ = s.repos.VideoRepo.UpdateStatus(video.ID, 2)
		return
	}

	// 5. 把切片和 m3u8 上传到 MinIO
	prefix := repository.BuildHlsPrefix(video.CourseID, video.ID)
	files, err := os.ReadDir(workDir)
	if err != nil {
		slog.Error("读取工作目录失败", "error", err)
		_ = s.repos.VideoRepo.UpdateStatus(video.ID, 2)
		return
	}
	for _, f := range files {
		if f.IsDir() {
			continue
		}
		localPath := filepath.Join(workDir, f.Name())
		objectName := prefix + f.Name()
		contentType := "video/MP2T"
		if strings.HasSuffix(f.Name(), ".m3u8") {
			contentType = "application/vnd.apple.mpegurl"
		}
		if err := s.repos.MinioRepo.UploadFile(ctx, objectName, localPath, contentType); err != nil {
			slog.Error("上传切片失败", "error", err, "object", objectName)
			_ = s.repos.VideoRepo.UpdateStatus(video.ID, 2)
			return
		}
	}

	// 6. 更新数据库：时长、HLS 路径、状态=已转码
	video.Duration = int(duration)
	video.HlsPath = prefix
	video.FullM3u8 = fullM3u8Name
	video.TrialM3u8 = trialM3u8Name
	video.Status = 1
	if err := s.repos.VideoRepo.Update(video); err != nil {
		slog.Error("更新视频状态失败", "error", err)
		_ = s.repos.VideoRepo.UpdateStatus(video.ID, 2)
		return
	}
	slog.Info("视频转码完成", "videoId", video.ID, "duration", duration)
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
// 如果课程免费、用户已购买、或用户是有效会员，返回完整版 m3u8；否则返回试看版 m3u8。
func (s *VideoService) GetPlayInfo(videoID uint64, userID uint64) (map[string]any, error) {
	video, err := s.repos.VideoRepo.GetByID(videoID)
	if err != nil {
		return nil, err
	}
	course, err := s.repos.CourseRepo.GetByID(video.CourseID)
	if err != nil {
		return nil, err
	}

	// 免费课程直接放行
	canWatchFull := course.IsFree
	// 已登录且非免费课程，再判断会员/购买
	if userID > 0 && !canWatchFull {
		canWatchFull, err = s.repos.PermissionRepo.CanWatchFull(userID, course)
		if err != nil {
			return nil, err
		}
	}

	// 根据权限选择 m3u8 文件
	var m3u8Object string
	if canWatchFull {
		m3u8Object = video.HlsPath + video.FullM3u8
	} else {
		m3u8Object = video.HlsPath + video.TrialM3u8
	}

	// 生成 MinIO 预签名 URL，有效期 2 小时
	ctx := context.Background()
	m3u8URL, err := s.repos.MinioRepo.PresignedGetURL(ctx, m3u8Object, 2*time.Hour)
	if err != nil {
		return nil, err
	}

	return map[string]any{
		"videoId":      video.ID,
		"title":        video.Title,
		"duration":     video.Duration,
		"trialSeconds": video.TrialSeconds,
		"canWatchFull": canWatchFull,
		"m3u8Url":      m3u8URL,
	}, nil
}

// GetTsUrl 获取 ts 切片的预签名 URL（可选，用于更严格鉴权场景）。
func (s *VideoService) GetTsUrl(objectName string) (string, error) {
	ctx := context.Background()
	return s.repos.MinioRepo.PresignedGetURL(ctx, objectName, 2*time.Hour)
}
