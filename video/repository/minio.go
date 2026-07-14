// Package repository 中的 MinIO 对象存储操作层。
//
// 职责：
//
//	封装视频上传、下载、预签名 URL、删除等操作。MinIO 客户端由公共模块 pkg 初始化并传入，
//	这里只负责基于 client 做业务相关的对象操作。
package repository

import (
	"context"
	"fmt"
	"io"
	"net/url"
	"time"

	"github.com/minio/minio-go/v7"
	// 复用公共模块的 MinIO 配置结构，避免在 video 中重复定义
	pkgconfig "local/pkg/config"
)

// MinioRepository 封装 MinIO 操作，所有方法都接受 context.Context 以便支持超时和取消。
type MinioRepository struct {
	client *minio.Client // MinIO 客户端，由 pkg 的 InitMinIO() 创建
	bucket string        // 默认操作的桶名
}

// NewMinioRepository 创建 MinIO 操作仓库。
// 注意：client 由公共模块的 InitMinIO() 初始化并传入，video 不再自己写一遍 MinIO 连接代码。
func NewMinioRepository(client *minio.Client, cfg *pkgconfig.MinIOConfig) *MinioRepository {
	return &MinioRepository{client: client, bucket: cfg.BucketName}
}

// Upload 上传一个 Reader 中的对象到 MinIO。
// objectName 是对象在桶中的路径，contentType 建议设置为 video/mp4、application/vnd.apple.mpegurl 等。
func (r *MinioRepository) Upload(ctx context.Context, objectName string, reader io.Reader, size int64, contentType string) error {
	_, err := r.client.PutObject(ctx, r.bucket, objectName, reader, size, minio.PutObjectOptions{
		ContentType: contentType,
	})
	return err
}

// UploadFile 上传本地文件到 MinIO。
func (r *MinioRepository) UploadFile(ctx context.Context, objectName, filePath, contentType string) error {
	_, err := r.client.FPutObject(ctx, r.bucket, objectName, filePath, minio.PutObjectOptions{
		ContentType: contentType,
	})
	return err
}

// Download 从 MinIO 下载对象，返回可读对象流，调用方需要负责关闭。
func (r *MinioRepository) Download(ctx context.Context, objectName string) (*minio.Object, error) {
	return r.client.GetObject(ctx, r.bucket, objectName, minio.GetObjectOptions{})
}

// PresignedGetURL 生成一个带签名的临时下载 URL，有效期由 expiry 参数指定。
// 前端/小程序拿到该 URL 后可直接访问 MinIO 下载视频或 HLS 切片，无需再次经过 video 服务。
func (r *MinioRepository) PresignedGetURL(ctx context.Context, objectName string, expiry time.Duration) (string, error) {
	reqParams := make(url.Values)
	reqParams.Set("response-content-type", "application/octet-stream")
	u, err := r.client.PresignedGetObject(ctx, r.bucket, objectName, expiry, reqParams)
	if err != nil {
		return "", err
	}
	return u.String(), nil
}

// ObjectExists 判断对象是否存在于 MinIO。
func (r *MinioRepository) ObjectExists(ctx context.Context, objectName string) (bool, error) {
	_, err := r.client.StatObject(ctx, r.bucket, objectName, minio.StatObjectOptions{})
	if err != nil {
		errResponse, ok := err.(minio.ErrorResponse)
		if ok && errResponse.Code == "NoSuchKey" {
			return false, nil
		}
		return false, err
	}
	return true, nil
}

// RemoveObject 删除 MinIO 上的单个对象。
func (r *MinioRepository) RemoveObject(ctx context.Context, objectName string) error {
	return r.client.RemoveObject(ctx, r.bucket, objectName, minio.RemoveObjectOptions{})
}

// RemovePrefix 删除 MinIO 上指定前缀下的所有对象（递归删除）。
// 删除视频时会用此方法清理该视频下的所有 HLS 切片和 m3u8 索引。
func (r *MinioRepository) RemovePrefix(ctx context.Context, prefix string) error {
	objects := r.client.ListObjects(ctx, r.bucket, minio.ListObjectsOptions{
		Prefix:    prefix,
		Recursive: true,
	})
	for obj := range objects {
		if obj.Err != nil {
			return obj.Err
		}
		if err := r.client.RemoveObject(ctx, r.bucket, obj.Key, minio.RemoveObjectOptions{}); err != nil {
			return err
		}
	}
	return nil
}

// BuildVideoPrefix 构造课程视频在 MinIO 上的公共前缀。
// 例如 course/123/
func BuildVideoPrefix(courseID uint64) string {
	return fmt.Sprintf("course/%d/", courseID)
}

// BuildHlsPrefix 构造某个具体视频的 HLS 文件公共前缀。
// 例如 course/123/video/456/
func BuildHlsPrefix(courseID, videoID uint64) string {
	return fmt.Sprintf("course/%d/video/%d/", courseID, videoID)
}
