package model

import (
	"time"
)

type ChunkMeta struct {
	UploadId    string `json:"uploadId"`
	FileName    string `json:"fileName"`
	ChunkSize   string `json:"chunkSize""`
	ChunkHash   string `json:"chunkHash"`
	ChunkIndex  int    `json:"chunkIndex"`
	FilePath    string `json:"filePath"`
	Verified    bool   `json:"verified"`
	TotalChunks int    `json:"TotalChunks"`
}

// MinioUploadMeta Redis存储的上传元数据（断点续传核心）
type MinioUploadMeta struct {
	UploadID       string         `json:"upload_id" gorm:"column:upload_id;primaryKey"`
	FileHash       string         `json:"file_hash" gorm:"column:file_hash"`
	FileName       string         `json:"file_name" gorm:"column:file_name"`
	MimeType       string         `json:"mime_type" gorm:"column:mime_type"`
	TotalChunks    int            `json:"total_chunks" gorm:"column:total_chunks"`
	ChunkSize      int64          `json:"chunk_size" gorm:"column:chunk_size"`
	FileSize       int64          `json:"file_size" gorm:"column:file_size"`
	UploadedChunks []int          `json:"uploaded_chunks" gorm:"column:uploaded_chunks;serializer:json"`
	ObjectKey      string         `json:"object_key" gorm:"column:object_key"`
	CreateTime     int64          `json:"create_time" gorm:"column:create_time"`
	ChunkHashes    map[int]string `json:"chunk_hashes" gorm:"column:chunk_hashes;serializer:json"`
	ChunkMD5s      map[int]string `json:"chunk_md5s" gorm:"column:chunk_md5s;serializer:json"`
}

func (MinioUploadMeta) TableName() string {
	return "minio_upload_meta"
}

// FileStorage 文件存储表
type FileStorage struct {
	ID             int64     `json:"id" gorm:"column:id;primaryKey;autoIncrement"`
	FileName       string    `json:"fileName" gorm:"column:file_name"`
	FileType       string    `json:"fileType" gorm:"column:file_type"`
	FileHash       string    `json:"fileHash" gorm:"column:file_hash"`
	FilePath       string    `json:"filePath" gorm:"column:file_path"`
	Uploader       *int64    `json:"uploader,omitempty" gorm:"column:uploader"` // 可选, 关联用户表
	FileSize       string    `json:"fileSize" gorm:"column:file_size"`          // 文件大小(字节)
	FileSystemType string    `json:"fileSystemType" gorm:"column:file_system_type"`
	CreatedAt      time.Time `json:"createdAt" gorm:"column:created_at;autoCreateTime"`
	UpdatedAt      time.Time `json:"updatedAt" gorm:"column:updated_at;autoUpdateTime"`
	ETag           string    `json:"eTag" gorm:"column:e_tag"`
}

// TableName 指定数据库表名
func (FileStorage) TableName() string {
	return "file_storage"
}
