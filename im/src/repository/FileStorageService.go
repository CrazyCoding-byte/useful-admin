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
	"github.com/bsm/redislock"
	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/encrypt"
	"github.com/redis/go-redis/v9"
	"gorm.io/gorm"
	"io"
	"local/im/src/config"
	"local/im/src/model"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"
)

/**
你的本地分片上传流程是：
前端直接传第一个分片 → 你创建分片目录 → 保存这个分片的元数据（JSON 文件）；
后续分片基于这个目录继续上传，元数据随分片逐步生成；
核心特点：没有 “提前创建上传任务” 的步骤，有分片数据才有元数据。
这是因为本地存储是 “文件驱动”—— 只有收到分片数据，才会在磁盘创建文件 / 目录，元数据依附于分片文件存在。


二、MinIO 分片上传的核心规则（必须先创建任务，再传分片）
MinIO/S3 标准的分片上传流程和本地完全不同，是 “任务驱动”：
plaintext
第一步：调用 NewMultipartUpload → MinIO 生成唯一的 UploadID（上传任务ID），表示“这个文件的分片上传任务已创建”；
第二步：基于 UploadID 上传所有分片（PutObjectPart）→ 每个分片必须关联 UploadID，否则MinIO不知道归属于哪个任务；
第三步：基于 UploadID 合并分片（CompleteMultipartUpload）→ 只有关联同一个 UploadID 的分片才能合并。
*/
// -------------------------- 1. 严格匹配源码的 Core 客户端初始化 --------------------------
func NewMinioCore(endpoint string, opts *minio.Options) (*minio.Core, error) {
	return minio.NewCore(endpoint, opts)
}

// -------------------------- 2. 独立的 MinIO Core 分片上传器（无本地耦合） --------------------------
type MinioCoreChunkUploader struct {
	Core        *minio.Core        // 你提供的 Core 客户端
	RedisClient *redis.Client      // Redis（进度/断点）
	Db          *gorm.DB           // 数据库（秒传元数据）
	Cfg         config.MinIOConfig // 分片配置
	Locker      *redislock.Client  // 分布式锁
	Client      *minio.Client
	Retry       config.Retry
}

// -------------------------- 4. 初始化 Core 分片上传器（修复回滚nil风险） --------------------------
func NewMinioCoreChunkUploader(
	Core *minio.Core,
	Client *minio.Client, // 显式传入顶层Client，避免nil
	RedisClient *redis.Client,
	Db *gorm.DB,
	Cfg config.MinIOConfig,
	retry config.Retry,
) *MinioCoreChunkUploader {
	return &MinioCoreChunkUploader{
		Core:        Core,
		Client:      Client, // 必传，避免回滚时nil panic
		RedisClient: RedisClient,
		Db:          Db,
		Cfg:         Cfg,
		Locker:      redislock.New(RedisClient),
		Retry:       retry,
	}
}

// -------------------------- 5. 修复：哈希函数名实一致（MD5/SHA256可选） --------------------------
// calculateChunkMD5Base64 真正计算MD5（Base64编码，MinIO标准）
func calculateChunkMD5Base64(data []byte) string {
	h := md5.New()
	h.Write(data)
	return base64.StdEncoding.EncodeToString(h.Sum(nil))
}

// calculateChunkSHA256Hex 计算SHA256（Hex编码，对齐本地实现）
func calculateChunkSHA256Hex(data []byte) string {
	h := sha256.New()
	h.Write(data)
	return hex.EncodeToString(h.Sum(nil))
}

// retry通用重试函数
func retry(maxRetries int, delay time.Duration, fn func() error) error {
	var err error
	for i := 0; i < maxRetries; i++ {
		if err = fn(); err == nil {
			return nil
		}
		slog.Warn("操作失败，重试中", "retry_times", i+1, "err", err)
		time.Sleep(delay * time.Duration(i+1)) // 指数退避
	}
	return fmt.Errorf("重试%d次后仍失败: %w", maxRetries, err)
}

// -------------------------- 5. 秒传校验（仅 Core 逻辑） --------------------------
func (u *MinioCoreChunkUploader) CheckMinioFileExists(ctx context.Context, fileHash string) (*model.FileStorage, bool) {
	var file model.FileStorage
	result := u.Db.Where("file_hash = ? AND file_system_type = ?", fileHash, "minio_core").First(&file)
	if result.Error != nil {
		return nil, false
	}

	// 核心修复：关闭GetObject返回的io.ReadCloser，避免资源泄漏
	rc, _, _, err := u.Core.GetObject(ctx, u.Cfg.BucketName, file.FilePath, minio.GetObjectOptions{})
	if rc != nil {
		defer func() {
			if closeErr := rc.Close(); closeErr != nil {
				slog.Warn("关闭GetObject ReadCloser失败", "err", closeErr)
			}
		}()
	}

	// 精准判断文件是否存在（仅NoSuchKey视为不存在）
	if err != nil {
		errResp := minio.ToErrorResponse(err)
		if errResp.Code == "NoSuchKey" || errResp.Code == "NotFound" {
			return nil, false
		}
		slog.Warn("MinIO GetObject校验失败", "err", err, "objectKey", file.FilePath)
		return nil, false
	}

	return &file, true
}
func (u *MinioCoreChunkUploader) saveUploadMeta(ctx context.Context, meta *model.MinioUploadMeta) error {
	// 1. 保存到Redis（断点续传快速读取）
	metaJSON, err := json.Marshal(meta)
	if err != nil {
		return fmt.Errorf("序列化元数据失败: %w", err)
	}
	redisKey := u.Cfg.RedisPrefix + meta.UploadID
	if err := u.RedisClient.Set(ctx, redisKey, metaJSON, u.Cfg.ExpireTime).Err(); err != nil {
		return fmt.Errorf("保存Redis元数据失败: %w", err)
	}

	// 2. 保存到DB（持久化，Redis丢失后可恢复）
	if err := u.Db.WithContext(ctx).Save(meta).Error; err != nil {
		slog.Warn("保存DB元数据失败（Redis已保存，不影响核心流程）", "err", err)
		// 仅日志，不返回错误（Redis可用即可继续上传）
	}

	return nil
}
func (u *MinioCoreChunkUploader) GetUploadMeta(ctx context.Context, uploadID string) (*model.MinioUploadMeta, error) {
	// 1. 优先读Redis
	redisKey := u.Cfg.RedisPrefix + uploadID
	metaJSON, err := u.RedisClient.Get(ctx, redisKey).Bytes()
	if err == nil {
		var meta model.MinioUploadMeta
		if err := json.Unmarshal(metaJSON, &meta); err != nil {
			return nil, fmt.Errorf("解析Redis元数据失败: %w", err)
		}
		return &meta, nil
	}

	// 2. Redis失败，从DB兜底
	var meta model.MinioUploadMeta
	result := u.Db.WithContext(ctx).Where("upload_id = ?", uploadID).First(&meta)
	if result.Error != nil {
		return nil, fmt.Errorf("Redis+DB均未找到元数据: %w", result.Error)
	}

	// 3. DB读到后同步回Redis
	_ = u.saveUploadMeta(ctx, &meta)
	return &meta, nil
}

// -------------------------- 8. 初始化分片上传（匹配源码+元数据持久化） --------------------------
func (u *MinioCoreChunkUploader) InitUpload(
	ctx context.Context,
	fileName string,
	mimeType string,
	fileSize int64,
	fileHash string,
) (string, error) {
	// 1. 秒传校验 判断文件整体hash
	if existingFile, exists := u.CheckMinioFileExists(ctx, fileHash); exists {
		return "", fmt.Errorf("file_exists|%s", existingFile.FilePath)
	}
	fmt.Println("当前文件大小", formatFileSize(fileSize))
	fmt.Println("当前文件名", fileName)
	fmt.Println("文件hash", fileHash)
	fmt.Println("maxFileSize", u.Cfg.MaxFileSize)
	// 2. 大小校验
	if formatData(fileSize) > u.Cfg.MaxFileSize {
		return "", fmt.Errorf("文件大小超出限制（最大%.2fMB）", float64(u.Cfg.MaxFileSize)/1024/1024)
	}

	// 3. 生成唯一ObjectKey（对齐本地实现的目录结构）
	mType := GetMinmeType(mimeType) // 复用本地实现的MIME分类
	objectKey := fmt.Sprintf("minio_core_files/%s/%s/%s%s",
		mType,
		time.Now().Format("2006-01-02"),
		fileHash,
		filepath.Ext(fileName),
	)

	// 4. 调用Core NewMultipartUpload（增加重试）
	var uploadID string
	err := retry(u.Retry.MaxRetries, u.Retry.RetryDelay, func() error {
		var innerErr error
		uploadID, innerErr = u.Core.NewMultipartUpload(ctx, u.Cfg.BucketName, objectKey, minio.PutObjectOptions{
			ContentType: mimeType,
		})
		return innerErr
	})
	if err != nil {
		return "", fmt.Errorf("Core初始化分片上传失败: %w", err)
	}

	// 5. 计算总分片数
	totalChunks := int((fileSize + u.Cfg.ChunkSize - 1) / u.Cfg.ChunkSize)

	// 6. 初始化元数据（增加分片哈希映射）
	meta := model.MinioUploadMeta{
		UploadID:       uploadID,
		FileHash:       fileHash,
		FileName:       fileName,
		MimeType:       mimeType,
		TotalChunks:    totalChunks,
		ChunkSize:      u.Cfg.ChunkSize,
		FileSize:       fileSize,
		UploadedChunks: []int{},
		ObjectKey:      objectKey,
		CreateTime:     time.Now().Unix(),
		ChunkHashes:    make(map[int]string), // 分片索引→SHA256哈希
		ChunkMD5s:      make(map[int]string),
	}

	// 7. 保存元数据（Redis+DB双存储）
	if err := u.saveUploadMeta(ctx, &meta); err != nil {
		// 回滚：取消分片上传（增加重试）
		_ = retry(u.Retry.MaxRetries, u.Retry.RetryDelay, func() error {
			return u.Core.AbortMultipartUpload(ctx, u.Cfg.BucketName, objectKey, uploadID)
		})
		return "", fmt.Errorf("保存元数据失败: %w", err)
	}

	return uploadID, nil
}

// 将字节转为mb
func formatData(size int64) int64 {
	return int64(float64(size) / 1024 / 1024)
}

// -------------------------- 9. 上传单个分片（修复：哈希校验+反向验证+重试） --------------------------
func (u *MinioCoreChunkUploader) UploadChunk(
	ctx context.Context,
	uploadID string,
	chunkIndex int,
	chunkData []byte,
) error {
	// 1. 获取元数据（Redis+DB兜底）
	meta, err := u.GetUploadMeta(ctx, uploadID)
	if err != nil {
		return fmt.Errorf("获取元数据失败: %w", err)
	}

	// 2. 断点续传：已上传且哈希一致则跳过
	if chunkHash, exists := meta.ChunkHashes[chunkIndex]; exists {
		localHash := calculateChunkSHA256Hex(chunkData)
		if chunkHash == localHash {
			slog.Info("分片已上传且哈希一致，跳过", "uploadID", uploadID, "chunkIndex", chunkIndex)
			return nil
		}
		slog.Warn("分片哈希不一致，重新上传", "uploadID", uploadID, "chunkIndex", chunkIndex)
	}

	// 3. 校验分片
	if chunkIndex >= meta.TotalChunks {
		return fmt.Errorf("分片索引超出范围（总分片数：%d）", meta.TotalChunks)
	}
	if int64(len(chunkData)) > u.Cfg.ChunkSize && chunkIndex != meta.TotalChunks-1 {
		return fmt.Errorf("分片大小超出限制（最大%.2fMB）", float64(u.Cfg.ChunkSize)/1024/1024)
	}

	// 4. 分布式锁（防并发上传同分片）
	lockKey := fmt.Sprintf("%schunk_lock:%s:%d", u.Cfg.RedisPrefix, uploadID, chunkIndex)
	lock, err := u.Locker.Obtain(ctx, lockKey, 10*time.Second, &redislock.Options{
		RetryStrategy: redislock.LinearBackoff(100 * time.Millisecond),
	})
	if err != nil {
		return fmt.Errorf("获取锁失败: %w", err)
	}
	defer lock.Release(ctx)

	// 5. 计算分片哈希（对齐本地实现的SHA256）
	partID := chunkIndex + 1
	chunkSize := int64(len(chunkData))
	md5Base64 := calculateChunkMD5Base64(chunkData) // MinIO需要的MD5（Base64）
	sha256Hex := calculateChunkSHA256Hex(chunkData) // 本地校验用的SHA256（Hex）

	// 6. 调用Core PutObjectPart（增加重试）
	var part minio.ObjectPart
	err = retry(u.Retry.MaxRetries, u.Retry.RetryDelay, func() error {
		var innerErr error
		part, innerErr = u.Core.PutObjectPart(ctx,
			u.Cfg.BucketName,           // bucket
			meta.ObjectKey,             // object
			uploadID,                   // uploadID
			partID,                     // partID
			bytes.NewReader(chunkData), // data
			chunkSize,                  // size
			minio.PutObjectPartOptions{
				Md5Base64:            md5Base64,
				Sha256Hex:            sha256Hex,
				SSE:                  encrypt.NewSSE(),
				CustomHeader:         http.Header{},
				Trailer:              http.Header{},
				DisableContentSha256: false,
			},
		)
		return innerErr
	})
	if err != nil {
		return fmt.Errorf("Core上传分片%d失败: %w", chunkIndex, err)
	}

	// 7. 反向校验：MinIO返回的ETag和本地MD5一致（核心，对齐本地实现的哈希对比）
	localMD5Hex := hex.EncodeToString(md5.New().Sum(chunkData))
	remoteETag := strings.Trim(part.ETag, "\"") // MinIO返回的ETag带引号，需去除
	if localMD5Hex != remoteETag {
		return fmt.Errorf("分片%d哈希校验失败（本地MD5：%s，MinIO ETag：%s）", chunkIndex, localMD5Hex, remoteETag)
	}

	// 8. 更新元数据
	meta.UploadedChunks = append(meta.UploadedChunks, chunkIndex)
	// 去重+排序
	uniqueChunks := make(map[int]struct{})
	for _, idx := range meta.UploadedChunks {
		uniqueChunks[idx] = struct{}{}
	}
	meta.UploadedChunks = []int{}
	for idx := range uniqueChunks {
		meta.UploadedChunks = append(meta.UploadedChunks, idx)
	}
	sort.Ints(meta.UploadedChunks)
	// 记录分片哈希
	meta.ChunkHashes[chunkIndex] = sha256Hex
	meta.ChunkMD5s[chunkIndex] = md5Base64
	// 9. 保存更新后的元数据（Redis+DB）
	if err := u.saveUploadMeta(ctx, meta); err != nil {
		return fmt.Errorf("更新元数据失败: %w", err)
	}

	return nil
}

// -------------------------- 10. 查询上传进度（断点续传必备） --------------------------
func (u *MinioCoreChunkUploader) QueryProgress(ctx context.Context, uploadID string) (float64, []int, int, error) {
	meta, err := u.GetUploadMeta(ctx, uploadID)
	if err != nil {
		return 0, nil, 0, fmt.Errorf("获取进度失败: %w", err)
	}

	uploadedCount := len(meta.UploadedChunks)
	progress := float64(uploadedCount) / float64(meta.TotalChunks) * 100

	return progress, meta.UploadedChunks, meta.TotalChunks, nil
}

// -------------------------- 11. 获取已上传分片（断点续传） --------------------------
func (u *MinioCoreChunkUploader) GetUploadedChunks(ctx context.Context, uploadID string) ([]int, int, error) {
	progress, uploadedChunks, totalChunks, err := u.QueryProgress(ctx, uploadID)
	if err != nil {
		return nil, 0, err
	}
	_ = progress
	return uploadedChunks, totalChunks, nil
}
func (u *MinioCoreChunkUploader) isChunkComplete(ctx context.Context, uploadID string) (bool, error) {
	meta, err := u.GetUploadMeta(ctx, uploadID)
	if err != nil {
		return false, fmt.Errorf("获取元数据失败: %w", err)
	}
	//1.校验分片数量
	if len(meta.UploadedChunks) != meta.TotalChunks {
		return false, fmt.Errorf("分片数量不匹配（已上传%d/%d）", len(meta.UploadedChunks), meta.TotalChunks)
	}
	//2.校验每个分片的哈希
	var allParts []minio.ObjectPart
	partNumberMarker := 0
	maxParts := 1000
	for {
		partsResult, err := u.Core.ListObjectParts(ctx,
			u.Cfg.BucketName,
			meta.ObjectKey,
			uploadID,
			partNumberMarker,
			maxParts,
		)
		if err != nil {
			return false, fmt.Errorf("列出分片失败: %w", err)
		}
		allParts = append(allParts, partsResult.ObjectParts...)
		if !partsResult.IsTruncated {
			break
		}
		partNumberMarker = partsResult.NextPartNumberMarker
	}
	//3.对比每个分片的Etag和本地记录的hash
	for _, part := range allParts {
		chunkIndex := part.PartNumber - 1 //partNumber //从1开始 索引从0开始
		localMD5Base64, exists := meta.ChunkMD5s[chunkIndex]
		if !exists {
			return false, fmt.Errorf("分片%d哈希未记录", chunkIndex)
		}
		remoteEtg := strings.Trim(part.ETag, "\"") //去除ETag的双引号,得到MD5 Hex
		//吧本地MD5(base64)转HEX,和Etag对比(minio Etag是MD5 Hex)
		localMD5Bytes, err := base64.StdEncoding.DecodeString(localMD5Base64)
		if err != nil {
			slog.Error("base64解码失败", "chunkIndex", chunkIndex, "err", err)
			return false, fmt.Errorf("分片%d哈希校验失败（本地MD5：%s，MinIO ETag：%s）", chunkIndex, localMD5Bytes, remoteEtg)
		}
		localMD5Hex := hex.EncodeToString(localMD5Bytes)
		if localMD5Hex != remoteEtg { //哈希校验失败
			return false, fmt.Errorf("分片%d哈希校验失败（本地MD5：%s，MinIO ETag：%s）", chunkIndex, localMD5Hex, remoteEtg)
		}
	}
	return true, nil
}

// -------------------------- 10. 完成分片合并（匹配源码 CompleteMultipartUpload） --------------------------
func (u *MinioCoreChunkUploader) CompleteUpload(ctx context.Context, uploadID string) (string, error) {
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

	// 3. 分页读取所有分片
	var allParts []minio.ObjectPart
	partNumberMarker := 0
	maxParts := 1000
	for {
		partsResult, err := u.Core.ListObjectParts(ctx,
			u.Cfg.BucketName,
			meta.ObjectKey,
			uploadID,
			partNumberMarker,
			maxParts,
		)
		if err != nil {
			return "", fmt.Errorf("Core列出分片失败: %w", err)
		}
		allParts = append(allParts, partsResult.ObjectParts...)
		if !partsResult.IsTruncated {
			break
		}
		partNumberMarker = partsResult.NextPartNumberMarker
	}

	// 4. 二次校验分片数量
	if len(allParts) != meta.TotalChunks {
		return "", fmt.Errorf("MinIO实际分片数(%d)与总分片数(%d)不匹配", len(allParts), meta.TotalChunks)
	}

	// 5. 整理分片信息
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
	// 6. 合并分片（增加重试）
	var uploadInfo minio.UploadInfo
	err = retry(u.Retry.MaxRetries, u.Retry.RetryDelay, func() error {
		var innerErr error
		uploadInfo, innerErr = u.Core.CompleteMultipartUpload(ctx,
			u.Cfg.BucketName,
			meta.ObjectKey,
			uploadID,
			completeParts,
			minio.PutObjectOptions{
				ContentType: meta.MimeType,
			},
		)
		return innerErr
	})
	if err != nil {
		return "", fmt.Errorf("Core合并分片失败: %w", err)
	}
	slog.Info("MinIO分片合并成功", "uploadID", uploadID, "objectKey", meta.ObjectKey, "etag", uploadInfo.ETag)

	// 7. 保存元数据到数据库（对齐本地实现）
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
		// 核心修复：回滚删除MinIO文件（确保client非nil）
		slog.Error("保存数据库元数据失败，执行回滚", "err", err, "objectKey", meta.ObjectKey)
		if u.Client != nil {
			_ = retry(u.Retry.MaxRetries, u.Retry.RetryDelay, func() error {
				return u.Client.RemoveObject(ctx, u.Cfg.BucketName, meta.ObjectKey, minio.RemoveObjectOptions{})
			})
		}
		return "", fmt.Errorf("保存元数据失败: %w", err)
	}

	// 8. 清理元数据（Redis+DB）
	redisKey := u.Cfg.RedisPrefix + uploadID
	if err := u.RedisClient.Del(ctx, redisKey).Err(); err != nil {
		slog.Warn("清理Redis元数据失败", "err", err, "uploadID", uploadID)
	}
	if err := u.Db.WithContext(ctx).Delete(&model.MinioUploadMeta{}, "upload_id = ?", uploadID).Error; err != nil {
		slog.Warn("清理DB元数据失败", "err", err, "uploadID", uploadID)
	}

	return meta.ObjectKey, nil
}

// -------------------------- 11. 取消上传（匹配源码 AbortMultipartUpload） --------------------------
func (u *MinioCoreChunkUploader) AbortUpload(ctx context.Context, uploadID string) error {
	meta, err := u.GetUploadMeta(ctx, uploadID)
	if err != nil {
		return fmt.Errorf("获取取消上传元数据失败: %w", err)
	}

	// 取消分片上传（增加重试）
	err = retry(u.Retry.MaxRetries, u.Retry.RetryDelay, func() error {
		return u.Core.AbortMultipartUpload(ctx, u.Cfg.BucketName, meta.ObjectKey, uploadID)
	})
	if err != nil {
		slog.Error("Core取消分片上传失败", "err", err, "uploadID", uploadID)
	}

	// 清理元数据（Redis+DB）
	redisKey := u.Cfg.RedisPrefix + uploadID
	if err := u.RedisClient.Del(ctx, redisKey).Err(); err != nil {
		slog.Error("删除Redis元数据失败", "err", err, "uploadID", uploadID)
	}
	if err := u.Db.WithContext(ctx).Delete(&model.MinioUploadMeta{}, "upload_id = ?", uploadID).Error; err != nil {
		slog.Error("删除DB元数据失败", "err", err, "uploadID", uploadID)
	}

	return nil
}

// -------------------------------本地实现---------------------------
// FileStorageService 整合MinIO的文件存储服务
type FileStorageService struct {
	minioClient  *minio.Client
	bucketName   string
	db           *gorm.DB
	baseUrl      string
	maxFileSize  int
	maxChunkSize int
}

// NewFileStorageService 创建文件存储服务实例
func NewFileStorageService(cfg *minio.Client, db *gorm.DB, bucketName string, baseUrl string, maxFileSize int, maxChunkSize int) (*FileStorageService, error) {
	return &FileStorageService{
		minioClient:  cfg,
		bucketName:   bucketName,
		db:           db,
		baseUrl:      baseUrl,
		maxFileSize:  maxFileSize,
		maxChunkSize: maxChunkSize,
	}, nil
}

// 保留你原来的调用方式，无感知替换，解决大文件内存问题
func CalculateFileHashByPath(filePath string) (string, error) {
	// 打开文件（只读模式，避免锁文件）
	file, err := os.Open(filePath)
	if err != nil {
		return "", fmt.Errorf("打开本地文件失败: %w", err)
	}
	defer file.Close()

	// 调用流式计算逻辑，不加载整个文件到内存
	return CalculateFileHash(file)
}

// CalculateFileHash 【核心流式逻辑】计算io.Reader的哈希（支持本地文件/用户上传流）
// IM场景扩展：用户上传文件时，可直接传HTTP请求的file流（不用先存本地）
func CalculateFileHash(file io.Reader) (string, error) {
	hash := sha256.New()
	// 流式拷贝：每次读一小块数据到哈希器，不占满内存
	if _, err := io.Copy(hash, file); err != nil {
		return "", fmt.Errorf("流式计算哈希失败: %w", err)
	}
	return hex.EncodeToString(hash.Sum(nil)), nil
}

// 检查文件是否已存在（秒传核心逻辑）
func (s *FileStorageService) CheckFileExists(fileHash string, fileType string) (*model.FileStorage, bool) {
	var file model.FileStorage
	result := s.db.Where("file_hash = ? and file_system_type=?", fileHash, fileType).First(&file)
	if result.Error != nil {
		return nil, false
	}
	return &file, true
}

// 普通文件上传
func (s *FileStorageService) UploadFile(ctx context.Context, redisClient *redis.Client, fileSystemType string, file io.Reader, fileName, mimeType, fileHash string, fileSize int64) (*model.FileStorage, error) {
	if fileSize > int64(s.maxFileSize) {
		return nil, fmt.Errorf("文件大小超出限制")
	}
	// 加锁：key = "file_lock:" + fileHash
	locker := redislock.New(redisClient)
	lock, err := locker.Obtain(ctx, "file_lock:"+fileHash, 30*time.Second, nil)
	if err == redislock.ErrNotObtained {
		// 细化错误：明确是“锁被占用”，而非泛化的“获取失败”
		return nil, fmt.Errorf("文件上传冲突（锁被占用）: %w", err)
	} else if err != nil {
		return nil, fmt.Errorf("获取锁失败: %w", err)
	}
	defer func() {
		// 释放锁时捕获错误（可选，生产环境建议记录日志）
		if releaseErr := lock.Release(ctx); releaseErr != nil {
			// 这里可以用日志库记录，比如 log.Printf("释放锁失败: %v", releaseErr)
			slog.Error("释放锁失败: %v", releaseErr)
		}
	}()
	// 检查是否可以秒传
	if existingFile, exists := s.CheckFileExists(fileHash, fileSystemType); exists {
		return existingFile, nil
	}

	// 生成存储路径
	objectName := fmt.Sprintf("files/%s/%s", time.Now().Format("2006-01-02"), fileHash+filepath.Ext(fileName))

	// 上传到MinIO
	_, err = s.minioClient.PutObject(context.Background(), s.bucketName, objectName, file, fileSize, minio.PutObjectOptions{
		ContentType: mimeType,
	})
	if err != nil {
		return nil, fmt.Errorf("文件上传失败: %w", err)
	}

	// 保存文件元数据到数据库
	fileStorage := &model.FileStorage{
		FileName:       fileName,
		FileHash:       fileHash,
		FilePath:       objectName,
		FileSize:       formatFileSize(fileSize),
		FileType:       mimeType,
		FileSystemType: fileSystemType, // 文件系统类型
	}

	if err := s.db.Create(fileStorage).Error; err != nil {
		// 数据库保存失败，删除MinIO中的文件
		_ = s.minioClient.RemoveObject(ctx, s.bucketName, objectName, minio.RemoveObjectOptions{})
		return nil, fmt.Errorf("保存文件元数据失败: %w", err)
	}

	return fileStorage, nil
}

func (s *FileStorageService) storeFileChunk(uploadId string, chunkData []byte, fileName string, chunkIndex int, totalChunks int) error {
	if len(chunkData) > s.maxChunkSize {
		return fmt.Errorf("分片索引超出范围")
	}
	chunkDir := filepath.Join(s.baseUrl, "chunks", uploadId)
	chunkFile := filepath.Join(chunkDir, fmt.Sprintf("chunk_%05d", chunkIndex))
	//先创建分片目录（MkdirAll已做存在判断，重复调用无副作用，直接调用即可）
	if err := os.MkdirAll(chunkDir, 0755); err != nil { // 优化：设置合理权限
		slog.Error("创建分片目录失败", slog.Any("err", err), slog.String("dir", chunkDir))
		return fmt.Errorf("创建分片目录失败: %w", err)
	}
	// 再判断分片文件是否存在
	if checkFileExists(chunkFile) {
		slog.Info("分片文件已存在，跳过存储", slog.String("chunkFile", chunkFile))
		return nil
	}
	if err := s.saveChunkFile(chunkData, chunkFile, chunkIndex); err != nil { // 接收saveChunkFile的错误
		return fmt.Errorf("保存分片文件失败: %w", err)
	}
	hash := sha256.New()
	hash.Write(chunkData)
	currentHash := hex.EncodeToString(hash.Sum(nil))

	fileHash, err := CalculateFileHashByPath(chunkFile)
	if err != nil {
		slog.Error("计算文件哈希失败", slog.Any("err", err), slog.String("file", chunkFile))
		return fmt.Errorf("计算文件哈希失败: %w", err)
	}
	verified := strings.EqualFold(currentHash, fileHash)
	if !verified {
		if checkFileExists(chunkFile) {
			if err := os.Remove(chunkFile); err != nil {
				slog.Error("删除分片文件失败", slog.Any("err", err), slog.String("file", chunkFile))
				return fmt.Errorf("删除分片文件失败: %w", err)
			}
		}
		slog.Error("删除分片文件{}", chunkFile)
		return fmt.Errorf("分片文件哈希校验失败: %s", chunkFile)
	}
	// 修复：接收SaveChunkMetadata的错误
	if err := SaveChunkMetadata(chunkData, chunkDir, fileName, uploadId, chunkIndex, currentHash, verified, totalChunks); err != nil {
		return fmt.Errorf("保存分片元数据失败: %w", err)
	}
	return nil
}

func (file *FileStorageService) saveChunkFile(chunkData []byte, chunkPath string, chunkIndex int) error {
	//使用临时文件确保原子性写入
	temp := chunkPath + ".tmp"
	defer func() {
		if err := recover(); err != nil {
			_ = os.Remove(temp)
			panic(err)
		}
	}()
	// 修复：处理文件写入错误，返回给上层
	if err := os.WriteFile(temp, chunkData, 0644); err != nil {
		slog.Error("写入临时分片文件失败", slog.Any("err", err), slog.String("temp", temp))
		return fmt.Errorf("写入临时分片文件失败: %w", err)
	}
	err := os.Rename(temp, chunkPath)
	if err == nil {
		return nil
	}
	// 原子移动失败，尝试普通替换
	if err := os.Remove(chunkPath); err != nil && !os.IsNotExist(err) {
		slog.Error("删除分片文件失败", slog.Any("err", err), slog.String("file", chunkPath))
		return fmt.Errorf("删除分片文件失败: %w", err) // 返回错误而非panic
	}
	slog.Info("原子移动失败，降级为普通替换", slog.Any("err", err))
	if err := os.Rename(temp, chunkPath); err != nil {
		_ = os.Remove(temp)
		slog.Error("普通替换分片文件失败", slog.Any("err", err), slog.String("temp", temp))
		return fmt.Errorf("普通替换分片文件失败: %w", err)
	}
	return nil
}

/**
 * 保存分片元数据到JSON文件
 */
func SaveChunkMetadata(chunkData []byte, chunkPath string, fileName string, uploadId string, chunkIndex int, chunkHash string, verified bool, totalChunks int) error {
	//保存分片元数据
	meta := model.ChunkMeta{
		FileName:    fileName,
		UploadId:    uploadId,
		ChunkIndex:  chunkIndex,
		ChunkHash:   chunkHash,
		TotalChunks: totalChunks,
	}
	if chunkData == nil || len(chunkData) == 0 {
		return fmt.Errorf("分片数据为空")
	}
	byteLength := len(chunkData)
	chunkSize := formatFileSize(int64(byteLength))
	meta.ChunkSize = chunkSize
	meta.Verified = verified
	meta.FilePath = fmt.Sprintf("chunk_%05d", chunkIndex)
	jsonData, err := json.Marshal(meta)
	if err != nil {
		return fmt.Errorf("序列化分片元数据失败: %w", err)
	}
	chunkJson := filepath.Join(chunkPath, fmt.Sprintf("chunk_%05d.json", chunkIndex))
	err = os.WriteFile(chunkJson, jsonData, 0644)
	if err != nil {
		return fmt.Errorf("保存分片元数据失败: %w", err)
	}
	return nil
}
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

// SaveMetaData 保存文件元数据
func (s *FileStorageService) SaveMetaData(fileSystemType, fileName, fileType, fileHash, normalizedPath string, fileSize int64) error {
	metadata := model.FileStorage{
		FileName:       fileName,
		FileType:       fileType,
		FileHash:       fileHash,
		FilePath:       normalizedPath,
		CreatedAt:      time.Now(),
		UpdatedAt:      time.Now(),
		FileSystemType: fileSystemType,
		FileSize:       formatFileSize(fileSize),
	}

	// 修复：正确获取Gorm错误
	if err := s.db.Save(metadata).Error; err != nil {
		slog.Error("保存文件元数据失败", slog.Any("err", err), slog.Any("metadata", metadata))
		return fmt.Errorf("保存文件元数据失败: %w", err) // 仅错误时返回
	}
	return nil // 成功时返回nil
}
func (s *FileStorageService) CompleteMergeChunks(fileSystemType string, uploadId string, fileName string, minmeType string) (downloadURL string, err error) {
	//首先判断文件夹存不存在
	path := filepath.Join(s.baseUrl, "chunks", uploadId)
	if !checkFileExists(path) {
		slog.Error("分片文件夹不存在", slog.String("path", path))
		return "", fmt.Errorf("分片文件夹不存在: %s", path) // 修复：失败时返回错误，而非nil
	}
	filePath, totalSize, err := s.MergeChunks(path, fileName)
	if err != nil {
		slog.Error("合并分片失败: %w", slog.Any("err", err))
		return "", fmt.Errorf("合并分片失败: %w", err)
	}
	hash, err2 := CalculateFileHashByPath(filePath)
	if err2 != nil {
		slog.Error("计算文件哈希失败: %w", err2)
		return "", fmt.Errorf("计算文件哈希失败: %w", err2)
	}
	if existingFile, ok := s.CheckFileExists(hash, fileSystemType); ok {
		// 秒传：删除临时文件，返回已存在的文件路径
		_ = os.Remove(filePath)
		return existingFile.FilePath, nil
	}
	//获取文件类型
	mType := GetMinmeType(minmeType)
	splitName := strings.Split(fileName, ".")
	var extType string = "." + splitName[len(splitName)-1]
	hashedFileName := hash + extType
	targetPath := filepath.Join(s.baseUrl, fileSystemType, mType, hashedFileName)

	if err := copyFile(filePath, targetPath); err != nil {
		slog.Error("复制合并后的文件失败", "err", err, "src", filePath, "dst", targetPath)
		return "", fmt.Errorf("复制文件失败: %w", err)
	}
	downloadDir := filepath.Join(fileSystemType, minmeType, hashedFileName)
	s.SaveMetaData(fileSystemType, fileName, mType, hash, downloadDir, totalSize)
	return downloadDir, nil
}
func copyFile(src, dst string) error {
	srcFile, err := os.Open(src)
	if err != nil {
		return fmt.Errorf("打开源文件失败: %w", err)
	}
	defer srcFile.Close()
	dstFile, err := os.Create(dst)
	if err != nil {
		return fmt.Errorf("创建目标文件失败: %w", err)
	}
	defer dstFile.Close()
	_, err = io.Copy(dstFile, srcFile)
	if err != nil {
		return fmt.Errorf("复制文件内容失败: %w", err)
	}
	return nil
}
func GetMinmeType(contentType string) string {
	if contentType == "" {
		return "others"
	} else if strings.HasPrefix(contentType, "image/") {
		return "images"
	} else if strings.HasPrefix(contentType, "video/") {
		return "videos"
	} else if strings.HasPrefix(contentType, "application/pdf") || strings.HasPrefix(contentType, "text/") {
		return "documents"
	} else {
		return "others"
	}
}

func (s *FileStorageService) MergeChunks(path string, fileName string) (tempFilePath string, totalSize int64, err error) {
	//第一个参数""：使用系统默认临时目录；pattern：merge_*+原文件名后缀，保证唯一性
	pattern := "merge_*" + filepath.Ext(fileName)
	tempDir := filepath.Join(s.baseUrl, "temp")
	if err := os.MkdirAll(tempDir, 0755); err != nil {
		return "", 0, fmt.Errorf("创建临时目录失败: %w", err)
	}
	tempFile, err := os.CreateTemp(tempDir, pattern)
	if err != nil {
		slog.Error("创建临时文件失败: %w", err)
		return "", 0, fmt.Errorf("创建临时文件失败: %w", err)
	}
	//defer tempFile.Close()  //如果直接这样关闭会丢失错误
	defer func() {
		if err := tempFile.Close(); err != nil {
			slog.Error("关闭临时文件失败", "err", err, "path", tempFile.Name())
		}
	}()
	// 1. 遍历分片目录，收集所有chunk_*分片文件，并累加分片大小
	var chunkFiles []string
	filepath.Walk(path, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			slog.Error("遍历分片目录失败: %w", err)
			return fmt.Errorf("遍历分片目录失败: %w", err)
		}
		if !info.IsDir() {
			fileName := filepath.Base(path)            // 获取文件名
			if strings.HasPrefix(fileName, "chunk_") { // 判断文件名是否以"chunk_"开头
				chunkFiles = append(chunkFiles, path)
				totalSize += info.Size()
			}
		}
		return nil
	})
	if len(chunkFiles) == 0 {
		slog.Error("没有分片文件")
		return "", 0, fmt.Errorf("没有分片文件")
	}
	//2.排序分片
	sort.Slice(chunkFiles, func(i, j int) bool {
		return extractChunkIndex(chunkFiles[i]) < extractChunkIndex(chunkFiles[j])
	})
	//3.遍历所有的分片 合并到临时文件
	for _, chunkFile := range chunkFiles {
		slog.Info("合并分片文件: %s", chunkFile)
		//打开分片文件
		chunkFile, err := os.Open(chunkFile)
		if err != nil {
			slog.Error("打开分片文件失败: %w", err)
			return "", 0, fmt.Errorf("打开分片文件失败: %w", err)
		}
		defer chunkFile.Close()
		_, err = io.Copy(tempFile, chunkFile)
		if err != nil {
			slog.Error("复制分片文件内容失败: %w", err)
			return "", 0, fmt.Errorf("复制分片文件内容失败: %w", err)
		}
		slog.Info("合并分片文件内容成功: %s", chunkFile)
	}
	//4.获取临时文件大小是否和总大小一致
	if err := tempFile.Sync(); err != nil {
		slog.Error("临时文件同步失败: %w", err)
		return "", 0, fmt.Errorf("临时文件同步失败: %w", err)
	}
	tempFileInfo, err := os.Stat(tempFile.Name())
	if err != nil {
		slog.Error("获取临时文件信息失败: %w", err)
		return "", 0, fmt.Errorf("获取临时文件信息失败: %w", err)
	}
	actualSize := tempFileInfo.Size()
	if actualSize != totalSize {
		slog.Error("临时文件大小不一致")
		return "", 0, fmt.Errorf("临时文件大小不一致")
	}
	tempFilePath = tempFile.Name()
	return tempFilePath, totalSize, nil
}
func extractChunkIndex(filePath string) int {
	filename := filepath.Base(filePath)
	// Remove "chunk_" prefix
	indexStr := strings.TrimPrefix(filename, "chunk_")
	// Remove any extension if present
	if dotIndex := strings.Index(indexStr, "."); dotIndex != -1 {
		indexStr = indexStr[:dotIndex]
	}
	index, err := strconv.Atoi(indexStr)
	if err != nil {
		return 0
	}
	return index
}
func (s *FileStorageService) isChunkComplete(uploadId string) bool {
	chunks := filepath.Join(s.baseUrl, "chunks", uploadId)
	if !checkFileExists(chunks) {
		return false
	}
	chunkMeta := readChunkMeta(chunks, 0)
	if chunkMeta == nil {
		return false
	}
	for chunkIndex := 0; chunkIndex < chunkMeta.TotalChunks; chunkIndex++ {
		meta := readChunkMeta(chunks, chunkIndex)
		if meta == nil || !meta.Verified {
			slog.Error("分片文件损坏")
			return false
		}
		if !checkFileExists(filepath.Join(chunks, fmt.Sprintf("chunk_%05d", chunkIndex))) {
			return false
		}
	}
	return true
}
func checkFileExists(filename string) bool {
	_, err := os.Stat(filename)
	if err == nil {
		return true // 文件存在
	}
	if os.IsNotExist(err) {
		return false // 文件不存在
	}
	return false // 其他错误（如权限不足）
}
func readChunkMeta(path string, chunkIndex int) *model.ChunkMeta {
	chunkFilePath := filepath.Join(path, fmt.Sprintf("chunk_%05d.json", chunkIndex))
	if !checkFileExists(chunkFilePath) {
		return nil
	}
	file, err := os.Open(chunkFilePath)
	if err != nil {
		slog.Error("打开文件失败: %w", err)
		return nil
	}
	defer file.Close()

	bytes, err := io.ReadAll(file)
	if err != nil {
		slog.Error("读取分片元数据失败", "err", err)
		return nil
	}
	var meta model.ChunkMeta
	if err := json.Unmarshal(bytes, &meta); err != nil {
		slog.Error("解析分片元数据失败", "err", err)
		return nil
	}
	return &meta
}
