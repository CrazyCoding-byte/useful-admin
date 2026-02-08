package model

import "time"

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
