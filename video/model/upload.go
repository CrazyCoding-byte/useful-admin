// Package model 中与文件上传相关的实体。
//
// 说明：
//
//	分片上传（Multipart Upload）的元数据需要 Redis+DB 双份持久化，
//	以保证断点续传在服务重启、Redis 丢失时仍可恢复。这里定义的两张表
//	（minio_upload_meta / file_storage）与 IM 模块同名同构，便于未来统一治理。
package model

import "time"

// MinioUploadMeta 分片上传任务元数据（断点续传核心）。
// 主键是 MinIO 返回的 UploadID；ChunkHashes/ChunkMD5s 记录每个分片的哈希，
// 用于断点续传时跳过已上传分片、以及合并前的一致性校验。
//
// 注意：所有 string 字段必须带 size 标签，否则 GORM AutoMigrate 会推断为 TEXT/LONGTEXT，
// 而 TEXT/LONGTEXT 列在 MySQL 上无法作为普通索引（必须指定 key prefix length），
// 会触发 "BLOB/TEXT column used in key specification without a key length"。
type MinioUploadMeta struct {
	// UploadID 是 MinIO 返回的 multipart uploadID。MinIO 没有固定长度规范，
	// 新版可能返回 100+ 字符的 base64（时间戳+UUID 编码），所以这里给 255 兜底。
	UploadID       string         `json:"upload_id" gorm:"column:upload_id;primaryKey;size:255"`
	FileHash       string         `json:"file_hash" gorm:"column:file_hash;size:64"`
	FileName       string         `json:"file_name" gorm:"column:file_name;size:255"`
	MimeType       string         `json:"mime_type" gorm:"column:mime_type;size:100"`
	TotalChunks    int            `json:"total_chunks" gorm:"column:total_chunks"`
	ChunkSize      int64          `json:"chunk_size" gorm:"column:chunk_size"`
	FileSize       int64          `json:"file_size" gorm:"column:file_size"`
	UploadedChunks []int          `json:"uploaded_chunks" gorm:"column:uploaded_chunks;serializer:json"`
	ObjectKey      string         `json:"object_key" gorm:"column:object_key;size:500"`
	CreateTime     int64          `json:"create_time" gorm:"column:create_time"`
	ChunkHashes    map[int]string `json:"chunk_hashes" gorm:"column:chunk_hashes;serializer:json;type:text"`
	ChunkMD5s      map[int]string `json:"chunk_md5s" gorm:"column:chunk_md5s;serializer:json;type:text"`
}

// TableName 指定数据库表名（与 IM 模块保持一致）。
func (MinioUploadMeta) TableName() string {
	return "minio_upload_meta"
}

// FileStorage 已完成上传的文件记录（用于秒传判定）。
// 视频分片合并完成后写一条记录；后续再传相同 hash 的文件时直接复用，无需重新上传。
//
// 注意：所有 string 字段带 size 标签，避免 GORM 推断为 TEXT（MySQL 不允许
// TEXT 列作为普通索引键，详见 MinioUploadMeta 注释）。
type FileStorage struct {
	ID             int64     `json:"id" gorm:"column:id;primaryKey;autoIncrement"`
	FileName       string    `json:"fileName" gorm:"column:file_name;size:255"`
	FileType       string    `json:"fileType" gorm:"column:file_type;size:100"`
	FileHash       string    `json:"fileHash" gorm:"column:file_hash;size:64;index"`
	FilePath       string    `json:"filePath" gorm:"column:file_path;size:500"`
	Uploader       *int64    `json:"uploader,omitempty" gorm:"column:uploader"` // 可选, 关联用户表
	FileSize       string    `json:"fileSize" gorm:"column:file_size;size:50"`  // 文件大小(字节)
	FileSystemType string    `json:"fileSystemType" gorm:"column:file_system_type;size:50;index"`
	CreatedAt      time.Time `json:"createdAt" gorm:"column:created_at;autoCreateTime"`
	UpdatedAt      time.Time `json:"updatedAt" gorm:"column:updated_at;autoUpdateTime"`
	ETag           string    `json:"eTag" gorm:"column:e_tag;size:200"`
}

// TableName 指定数据库表名（与 IM 模块保持一致）。
func (FileStorage) TableName() string {
	return "file_storage"
}
