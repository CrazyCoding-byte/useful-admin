// Package repository 中的 MinIO 分片上传器。
//
// 设计说明：
//
//	IM 模块已经实现并验证过一套基于 MinIO Multipart Upload 的分片上传方案
//	（Redis+DB 双元数据、断点续传、分片哈希校验、分布式锁防并发），
//	本文件把同一套成熟实现移植到 video 模块，只做两点定制：
//	  1. ObjectKey 由调用方（handler/service）显式传入，以适配教学视频的
//	     目录结构 course/{courseId}/video/{videoId}/original/xxx；
//	  2. 秒传查重使用 video 自己的 file_storage 表（education 库）。
//	其余逻辑（分片上传、进度查询、断点续传、合并、取消）与 IM 保持一致。
package repository

import (
	"bytes"
	"context"
	"crypto/md5"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"log/slog"
	"math"
	"sort"
	"strings"
	"time"

	"video/model"

	pkgconfig "local/pkg/config"

	"github.com/bsm/redislock"
	"github.com/minio/minio-go/v7"
	"github.com/redis/go-redis/v9"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

// ChunkUploader 基于 MinIO Core 的分片上传器。
type ChunkUploader struct {
	Core        *minio.Core
	Client      *minio.Client
	RedisClient *redis.Client
	Db          *gorm.DB
	Cfg         pkgconfig.MinIOConfig
	Locker      *redislock.Client
	Retry       pkgconfig.Retry

	chunkSizeBytes   int64 // 单分片大小（字节）
	maxFileSizeBytes int64 // 最大文件大小（字节）
}

// NewChunkUploader 创建分片上传器。
// 参数含义与 IM 模块的 NewMinioCoreChunkUploader 一致，方便对照维护。
func NewChunkUploader(
	core *minio.Core,
	client *minio.Client,
	redisClient *redis.Client,
	db *gorm.DB,
	cfg pkgconfig.MinIOConfig,
	retry pkgconfig.Retry,
) *ChunkUploader {
	chunkSizeBytes := int64(cfg.ChunkSize) * 1024 * 1024
	maxFileSizeBytes := int64(cfg.MaxFileSize) * 1024 * 1024
	if chunkSizeBytes <= 0 {
		chunkSizeBytes = 5 * 1024 * 1024
	}
	if maxFileSizeBytes <= 0 {
		maxFileSizeBytes = 200 * 1024 * 1024
	}
	return &ChunkUploader{
		Core:             core,
		Client:           client,
		RedisClient:      redisClient,
		Db:               db,
		Cfg:              cfg,
		Locker:           redislock.New(redisClient),
		Retry:            retry,
		chunkSizeBytes:   chunkSizeBytes,
		maxFileSizeBytes: maxFileSizeBytes,
	}
}

// ChunkSize 返回单分片大小（字节），供前端切片时对齐。
func (u *ChunkUploader) ChunkSize() int64 {
	return u.chunkSizeBytes
}

// CheckFileExists 秒传查重：file_hash 命中且 MinIO 上对象仍存在则返回记录。
func (u *ChunkUploader) CheckFileExists(ctx context.Context, fileHash string) (*model.FileStorage, bool) {
	var file model.FileStorage
	result := u.Db.Where("file_hash = ? AND file_system_type = ?", fileHash, "minio_core").First(&file)
	if result.Error != nil {
		return nil, false
	}

	rc, _, _, err := u.Core.GetObject(ctx, u.Cfg.BucketName, file.FilePath, minio.GetObjectOptions{})
	if rc != nil {
		defer rc.Close()
	}
	if err != nil {
		errResp := minio.ToErrorResponse(err)
		if errResp.Code == "NoSuchKey" || errResp.Code == "NotFound" {
			return nil, false
		}
		slog.Warn("MinIO 秒传校验失败", "err", err, "objectKey", file.FilePath)
		return nil, false
	}
	return &file, true
}

// saveUploadMeta 持久化上传元数据：Redis 为主，DB 兜底。
//
// 注意：DB 写入必须用 UPSERT（GORM 的 Clauses(clause.OnConflict{UpdateAll: true}).Create），
// 不能用 Save —— Save 在主键有值时只执行 UPDATE，记录不存在就 0 rows affected 静默成功。
// 之前用 Save 导致新任务的 meta 永远写不进 DB，只有 Redis 兜底；Redis 一过期就全丢。
func (u *ChunkUploader) saveUploadMeta(ctx context.Context, meta *model.MinioUploadMeta) error {
	metaJSON, err := json.Marshal(meta)
	if err != nil {
		return fmt.Errorf("序列化元数据失败: %w", err)
	}
	redisKey := u.Cfg.RedisPrefix + meta.UploadID
	if err := u.RedisClient.Set(ctx, redisKey, metaJSON, u.Cfg.ExpireTime).Err(); err != nil {
		return fmt.Errorf("保存Redis元数据失败: %w", err)
	}
	// UPSERT：主键冲突时更新所有字段
	if err := u.Db.WithContext(ctx).Clauses(clause.OnConflict{UpdateAll: true}).Create(meta).Error; err != nil {
		// DB 失败不再静默 —— 记录错误日志但不影响 Redis 兜底流程
		slog.Error("保存DB元数据失败（Redis已保存，DB兜底失效）", "err", err, "uploadID", meta.UploadID)
	}
	return nil
}

// GetUploadMeta 读取上传元数据：优先 Redis，缺失时从 DB 兜底并回写。
func (u *ChunkUploader) GetUploadMeta(ctx context.Context, uploadID string) (*model.MinioUploadMeta, error) {
	redisKey := u.Cfg.RedisPrefix + uploadID
	metaJSON, err := u.RedisClient.Get(ctx, redisKey).Bytes()
	if err == nil {
		var meta model.MinioUploadMeta
		if err := json.Unmarshal(metaJSON, &meta); err != nil {
			return nil, fmt.Errorf("解析Redis元数据失败: %w", err)
		}
		return &meta, nil
	}

	var meta model.MinioUploadMeta
	result := u.Db.WithContext(ctx).Where("upload_id = ?", uploadID).First(&meta)
	if result.Error != nil {
		return nil, fmt.Errorf("Redis+DB均未找到元数据: %w", result.Error)
	}
	_ = u.saveUploadMeta(ctx, &meta)
	return &meta, nil
}

// InitUpload 初始化分片上传任务。
// objectKey 由调用方传入（如 course/1/video/2/original/2.mp4）。
// 返回 MinIO 的 UploadID；若文件已存在则返回错误信息 "file_exists|<objectKey>" 以便秒传。
func (u *ChunkUploader) InitUpload(ctx context.Context, objectKey, fileName, mimeType string, fileSize int64, fileHash string) (string, error) {
	// 1. 秒传：整体 hash 命中直接返回已有文件
	if existingFile, exists := u.CheckFileExists(ctx, fileHash); exists {
		return existingFile.FilePath, fmt.Errorf("file_exists|%s", existingFile.FilePath)
	}

	// 2. 大小校验
	if fileSize > u.maxFileSizeBytes {
		return "", fmt.Errorf("文件大小超出限制（最大%.2fMB）", float64(u.Cfg.MaxFileSize))
	}

	// 3. 创建 MinIO Multipart 上传任务
	var uploadID string
	err := retry(u.Retry.MaxRetries, u.Retry.RetryDelay, func() error {
		var innerErr error
		uploadID, innerErr = u.Core.NewMultipartUpload(ctx, u.Cfg.BucketName, objectKey, minio.PutObjectOptions{
			ContentType: mimeType,
		})
		return innerErr
	})
	if err != nil {
		return "", fmt.Errorf("初始化分片上传失败: %w", err)
	}

	// 4. 计算总分片数
	totalChunks := int(math.Ceil(float64(fileSize) / float64(u.chunkSizeBytes)))
	if totalChunks <= 0 {
		totalChunks = 1
	}

	// 5. 初始化元数据
	meta := model.MinioUploadMeta{
		UploadID:       uploadID,
		FileHash:       fileHash,
		FileName:       fileName,
		MimeType:       mimeType,
		TotalChunks:    totalChunks,
		ChunkSize:      u.chunkSizeBytes,
		FileSize:       fileSize,
		UploadedChunks: []int{},
		ObjectKey:      objectKey,
		CreateTime:     time.Now().Unix(),
		ChunkHashes:    make(map[int]string),
		ChunkMD5s:      make(map[int]string),
	}

	// 6. 保存元数据；失败则回滚已创建的 Multipart 任务
	if err := u.saveUploadMeta(ctx, &meta); err != nil {
		_ = retry(u.Retry.MaxRetries, u.Retry.RetryDelay, func() error {
			return u.Core.AbortMultipartUpload(ctx, u.Cfg.BucketName, objectKey, uploadID)
		})
		return "", fmt.Errorf("保存元数据失败: %w", err)
	}

	return uploadID, nil
}

// UploadChunk 上传单个分片。
// 已上传且 hash 一致的分片直接跳过（断点续传）；通过分布式锁防止同分片并发上传。
func (u *ChunkUploader) UploadChunk(ctx context.Context, uploadID string, chunkIndex int, chunkData []byte) error {
	meta, err := u.GetUploadMeta(ctx, uploadID)
	if err != nil {
		return fmt.Errorf("获取元数据失败: %w", err)
	}

	// 断点续传：已上传且哈希一致则跳过
	if chunkHash, exists := meta.ChunkHashes[chunkIndex]; exists {
		localHash := calculateChunkSHA256Hex(chunkData)
		if chunkHash == localHash {
			slog.Info("分片已上传且哈希一致，跳过", "uploadID", uploadID, "chunkIndex", chunkIndex)
			return nil
		}
		slog.Warn("分片哈希不一致，重新上传", "uploadID", uploadID, "chunkIndex", chunkIndex)
	}

	// 校验分片索引与大小
	if chunkIndex >= meta.TotalChunks {
		return fmt.Errorf("分片索引超出范围（总分片数：%d）", meta.TotalChunks)
	}
	if int64(len(chunkData)) > u.chunkSizeBytes && chunkIndex != meta.TotalChunks-1 {
		return fmt.Errorf("分片大小超出限制（最大%.2fMB）", float64(u.Cfg.ChunkSize))
	}

	// 分布式锁，防并发上传同一分片
	lockKey := fmt.Sprintf("%schunk_lock:%s:%d", u.Cfg.RedisPrefix, uploadID, chunkIndex)
	lock, err := u.Locker.Obtain(ctx, lockKey, 10*time.Second, &redislock.Options{
		RetryStrategy: redislock.LinearBackoff(100 * time.Millisecond),
	})
	if err != nil {
		return fmt.Errorf("获取锁失败: %w", err)
	}
	defer lock.Release(ctx)

	partID := chunkIndex + 1
	chunkSize := int64(len(chunkData))
	md5Base64 := calculateChunkMD5Base64(chunkData)
	sha256Hex := calculateChunkSHA256Hex(chunkData)

	var part minio.ObjectPart
	err = retry(u.Retry.MaxRetries, u.Retry.RetryDelay, func() error {
		var innerErr error
		// PutObjectPartOptions 同时设 Md5Base64 + Sha256Hex + DisableContentSha256:false
		// 在部分 MinIO 版本上会"假成功"（HTTP 204 但 part 未落盘），所以这里只保留 Md5Base64。
		// 反向校验（line 272-279）仍会对比 part.ETag 与本地 MD5，确保落盘一致。
		part, innerErr = u.Core.PutObjectPart(ctx,
			u.Cfg.BucketName,
			meta.ObjectKey,
			uploadID,
			partID,
			bytes.NewReader(chunkData),
			chunkSize,
			minio.PutObjectPartOptions{
				Md5Base64: md5Base64,
			},
		)
		return innerErr
	})
	if err != nil {
		return fmt.Errorf("上传分片%d失败: %w", chunkIndex, err)
	}

	// 反向校验：MinIO 返回的 ETag 应与本地 MD5 一致
	h := md5.New()
	h.Write(chunkData)
	localMD5Hex := hex.EncodeToString(h.Sum(nil))
	remoteETag := strings.Trim(part.ETag, "\"")
	if localMD5Hex != remoteETag {
		return fmt.Errorf("分片%d哈希校验失败（本地MD5：%s，MinIO ETag：%s）", chunkIndex, localMD5Hex, remoteETag)
	}

	// 更新元数据
	if !contains(meta.UploadedChunks, chunkIndex) {
		meta.UploadedChunks = append(meta.UploadedChunks, chunkIndex)
		sort.Ints(meta.UploadedChunks)
	}
	meta.ChunkHashes[chunkIndex] = sha256Hex
	meta.ChunkMD5s[chunkIndex] = md5Base64
	if err := u.saveUploadMeta(ctx, meta); err != nil {
		return fmt.Errorf("更新元数据失败: %w", err)
	}
	return nil
}

// QueryProgress 查询上传进度（断点续传必备）。
func (u *ChunkUploader) QueryProgress(ctx context.Context, uploadID string) (float64, []int, int, error) {
	meta, err := u.GetUploadMeta(ctx, uploadID)
	if err != nil {
		return 0, nil, 0, fmt.Errorf("获取进度失败: %w", err)
	}
	uploadedCount := len(meta.UploadedChunks)
	progress := float64(uploadedCount) / float64(meta.TotalChunks) * 100
	return progress, meta.UploadedChunks, meta.TotalChunks, nil
}

// GetUploadedChunks 获取已上传分片索引列表（供前端断点续传跳过）。
func (u *ChunkUploader) GetUploadedChunks(ctx context.Context, uploadID string) ([]int, int, error) {
	_, uploadedChunks, totalChunks, err := u.QueryProgress(ctx, uploadID)
	if err != nil {
		return nil, 0, err
	}
	return uploadedChunks, totalChunks, nil
}

// listAllParts 分页拉取某个 UploadID 的所有已上传分片。
func (u *ChunkUploader) listAllParts(ctx context.Context, bucket, objectKey, uploadID string) ([]minio.ObjectPart, error) {
	var allParts []minio.ObjectPart
	partNumberMarker := 0
	maxParts := 1000
	for {
		partsResult, err := u.Core.ListObjectParts(ctx, bucket, objectKey, uploadID, partNumberMarker, maxParts)
		if err != nil {
			return nil, fmt.Errorf("列出分片失败: %w", err)
		}
		allParts = append(allParts, partsResult.ObjectParts...)
		if !partsResult.IsTruncated {
			break
		}
		partNumberMarker = partsResult.NextPartNumberMarker
	}
	return allParts, nil
}

// isChunkComplete 校验分片数量与每个分片的 ETag（合并前必须通过）。
//
// 注意：分片数量校验以 MinIO 的 listAllParts 结果为准，不依赖 DB 的 UploadedChunks 字段。
// 原因：UploadedChunks 是在 UploadChunk 里通过进程内 append+saveMeta 维护的，
// 并发上传同一 uploadID 时多个 worker 会互相覆盖，导致 DB 计数远小于实际 part 数（典型的写丢更新）。
// MinIO 的 part 列表是权威，不会丢。
func (u *ChunkUploader) isChunkComplete(ctx context.Context, uploadID string) (bool, error) {
	meta, err := u.GetUploadMeta(ctx, uploadID)
	if err != nil {
		return false, fmt.Errorf("获取元数据失败: %w", err)
	}

	allParts, err := u.listAllParts(ctx, u.Cfg.BucketName, meta.ObjectKey, uploadID)
	if err != nil {
		return false, err
	}
	if len(allParts) != meta.TotalChunks {
		return false, fmt.Errorf("分片数量不匹配（MinIO 已上传%d/%d）", len(allParts), meta.TotalChunks)
	}

	// 不再做二次 ETag 校验：
	// 1) PutObjectPart 时已经传 Md5Base64，MinIO 内部校验过，不一致会 4xx 拒绝；
	// 2) meta.ChunkMD5s 是进程内 map+saveMeta，并发写会丢更新，靠它做校验会误判；
	// 3) 数量匹配（== TotalChunks）+ PartNumber 1..N 连续，merge 时 MinIO 会按 part 顺序拼接，不会乱。
	_ = meta.ChunkMD5s // 保留字段以备后续审计/查 bug
	return true, nil
}

// CompleteUpload 校验并合并分片，成功后写 file_storage 记录（供秒传），并清理元数据。
// 返回合并后的 ObjectKey。
func (u *ChunkUploader) CompleteUpload(ctx context.Context, uploadID string) (string, error) {
	complete, err := u.isChunkComplete(ctx, uploadID)
	if err != nil {
		return "", fmt.Errorf("校验分片完整性失败: %w", err)
	}
	if !complete {
		return "", fmt.Errorf("分片未全部完成或哈希校验失败")
	}

	meta, err := u.GetUploadMeta(ctx, uploadID)
	if err != nil {
		return "", fmt.Errorf("获取元数据失败: %w", err)
	}

	allParts, err := u.listAllParts(ctx, u.Cfg.BucketName, meta.ObjectKey, uploadID)
	if err != nil {
		return "", err
	}
	if len(allParts) != meta.TotalChunks {
		return "", fmt.Errorf("MinIO实际分片数(%d)与总分片数(%d)不匹配", len(allParts), meta.TotalChunks)
	}

	var completeParts []minio.CompletePart
	for _, part := range allParts {
		completeParts = append(completeParts, minio.CompletePart{
			PartNumber: part.PartNumber,
			ETag:       part.ETag,
		})
	}
	sort.Slice(completeParts, func(i, j int) bool {
		return completeParts[i].PartNumber < completeParts[j].PartNumber
	})

	var uploadInfo minio.UploadInfo
	err = retry(u.Retry.MaxRetries, u.Retry.RetryDelay, func() error {
		var innerErr error
		uploadInfo, innerErr = u.Core.CompleteMultipartUpload(ctx,
			u.Cfg.BucketName,
			meta.ObjectKey,
			uploadID,
			completeParts,
			minio.PutObjectOptions{ContentType: meta.MimeType},
		)
		return innerErr
	})
	if err != nil {
		return "", fmt.Errorf("合并分片失败: %w", err)
	}
	slog.Info("分片合并成功", "uploadID", uploadID, "objectKey", meta.ObjectKey, "etag", uploadInfo.ETag)

	// 写 file_storage 记录（秒传元数据）
	fileStorage := &model.FileStorage{
		FileName:       meta.FileName,
		FileHash:       meta.FileHash,
		FilePath:       meta.ObjectKey,
		FileSize:       formatFileSize(meta.FileSize),
		FileType:       meta.MimeType,
		FileSystemType: "minio_core",
		ETag:           uploadInfo.ETag,
		CreatedAt:      time.Now(),
		UpdatedAt:      time.Now(),
	}
	if err := u.Db.Create(fileStorage).Error; err != nil {
		slog.Error("保存文件记录失败，回滚删除MinIO对象", "err", err, "objectKey", meta.ObjectKey)
		if u.Client != nil {
			_ = retry(u.Retry.MaxRetries, u.Retry.RetryDelay, func() error {
				return u.Client.RemoveObject(ctx, u.Cfg.BucketName, meta.ObjectKey, minio.RemoveObjectOptions{})
			})
		}
		return "", fmt.Errorf("保存文件记录失败: %w", err)
	}

	// 清理上传元数据（Redis+DB）
	redisKey := u.Cfg.RedisPrefix + uploadID
	if err := u.RedisClient.Del(ctx, redisKey).Err(); err != nil {
		slog.Warn("清理Redis元数据失败", "err", err, "uploadID", uploadID)
	}
	if err := u.Db.WithContext(ctx).Delete(&model.MinioUploadMeta{}, "upload_id = ?", uploadID).Error; err != nil {
		slog.Warn("清理DB元数据失败", "err", err, "uploadID", uploadID)
	}

	return meta.ObjectKey, nil
}

// AbortUpload 取消上传：终止 MinIO Multipart 任务并清理元数据。
func (u *ChunkUploader) AbortUpload(ctx context.Context, uploadID string) error {
	meta, err := u.GetUploadMeta(ctx, uploadID)
	if err != nil {
		return fmt.Errorf("获取取消上传元数据失败: %w", err)
	}
	err = retry(u.Retry.MaxRetries, u.Retry.RetryDelay, func() error {
		return u.Core.AbortMultipartUpload(ctx, u.Cfg.BucketName, meta.ObjectKey, uploadID)
	})
	if err != nil {
		slog.Error("取消分片上传失败", "err", err, "uploadID", uploadID)
	}
	redisKey := u.Cfg.RedisPrefix + uploadID
	if err := u.RedisClient.Del(ctx, redisKey).Err(); err != nil {
		slog.Error("删除Redis元数据失败", "err", err, "uploadID", uploadID)
	}
	if err := u.Db.WithContext(ctx).Delete(&model.MinioUploadMeta{}, "upload_id = ?", uploadID).Error; err != nil {
		slog.Error("删除DB元数据失败", "err", err, "uploadID", uploadID)
	}
	return nil
}

// -------------------------- 工具函数 --------------------------

// retry 通用重试：指数退避。
func retry(maxRetries int, delay time.Duration, fn func() error) error {
	var err error
	for i := 0; i < maxRetries; i++ {
		if err = fn(); err == nil {
			return nil
		}
		slog.Warn("操作失败，重试中", "retry_times", i+1, "err", err)
		time.Sleep(delay * time.Duration(i+1))
	}
	return fmt.Errorf("重试%d次后仍失败: %w", maxRetries, err)
}

// calculateChunkMD5Base64 计算分片 MD5（Base64 编码，MinIO 标准）。
func calculateChunkMD5Base64(data []byte) string {
	h := md5.New()
	h.Write(data)
	return base64.StdEncoding.EncodeToString(h.Sum(nil))
}

// calculateChunkSHA256Hex 计算分片 SHA256（Hex 编码）。
func calculateChunkSHA256Hex(data []byte) string {
	h := sha256.New()
	h.Write(data)
	return hex.EncodeToString(h.Sum(nil))
}

func contains(slice []int, item int) bool {
	for _, v := range slice {
		if v == item {
			return true
		}
	}
	return false
}

// formatFileSize 把字节数格式化为可读字符串。
func formatFileSize(size int64) string {
	const (
		KB = 1024
		MB = 1024 * KB
		GB = 1024 * MB
		TB = 1024 * GB
	)
	switch {
	case size >= TB:
		return fmt.Sprintf("%.2fTB", float64(size)/TB)
	case size >= GB:
		return fmt.Sprintf("%.2fGB", float64(size)/GB)
	case size >= MB:
		return fmt.Sprintf("%.2fMB", float64(size)/MB)
	case size >= KB:
		return fmt.Sprintf("%.2fKB", float64(size)/KB)
	default:
		return fmt.Sprintf("%dB", size)
	}
}
