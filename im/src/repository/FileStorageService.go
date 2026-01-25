package repository

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
	"gorm.io/gorm"
	"io"
	"io/ioutil"
	"local/im/src/config"
	"local/im/src/model"
	"log/slog"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"
)

var baseUrl = "/file-storage"

// FileStorageService 整合MinIO的文件存储服务
type FileStorageService struct {
	minioClient *minio.Client
	bucketName  string
	db          *gorm.DB
	logger      *slog.Logger
}

// NewFileStorageService 创建文件存储服务实例
func NewFileStorageService(cfg *config.MinIOConfig, db *gorm.DB, logger *slog.Logger) (*FileStorageService, error) {
	// 初始化MinIO客户端
	client, err := minio.New(cfg.Endpoint, &minio.Options{
		Creds:  credentials.NewStaticV4(cfg.AccessKeyID, cfg.SecretAccessKey, ""),
		Secure: cfg.UseSSL,
	})
	if err != nil {
		return nil, fmt.Errorf("初始化MinIO客户端失败: %w", err)
	}

	// 检查并创建存储桶
	ctx := context.Background()
	exists, err := client.BucketExists(ctx, cfg.BucketName)
	if err != nil {
		return nil, fmt.Errorf("检查存储桶失败: %w", err)
	}
	if !exists {
		if err := client.MakeBucket(ctx, cfg.BucketName, minio.MakeBucketOptions{}); err != nil {
			return nil, fmt.Errorf("创建存储桶失败: %w", err)
		}
	}

	return &FileStorageService{
		minioClient: client,
		bucketName:  cfg.BucketName,
		db:          db,
		logger:      logger,
	}, nil
}

// 计算文件哈希（用于秒传判断）
func CalculateFileHash(filePath string) (string, error) {
	file, err := os.Open(filePath)
	if err != nil {
		return "", err
	}
	defer file.Close()

	hash := sha256.New()
	if _, err := io.Copy(hash, file); err != nil {
		return "", err
	}

	return hex.EncodeToString(hash.Sum(nil)), nil
}

// 检查文件是否已存在（秒传核心逻辑）
func (s *FileStorageService) CheckFileExists(fileHash string) (*model.FileStorage, bool) {
	var file model.FileStorage
	result := s.db.Where("file_hash = ?", fileHash).First(&file)
	return &file, result.Error == nil
}

// 普通文件上传
func (s *FileStorageService) UploadFile(ctx context.Context, file io.Reader, fileName, mimeType, fileHash string, fileSize int64) (*model.FileStorage, error) {
	// 检查是否可以秒传
	if existingFile, exists := s.CheckFileExists(fileHash); exists {
		return existingFile, nil
	}

	// 生成存储路径
	objectName := fmt.Sprintf("files/%s/%s", time.Now().Format("2006-01-02"), fileHash+filepath.Ext(fileName))

	// 上传到MinIO
	_, err := s.minioClient.PutObject(ctx, s.bucketName, objectName, file, fileSize, minio.PutObjectOptions{
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
		FileSize:       fileSize,
		FileType:       mimeType,
		FileSystemType: mimeType, // 文件系统类型
	}

	if err := s.db.Create(fileStorage).Error; err != nil {
		// 数据库保存失败，删除MinIO中的文件
		_ = s.minioClient.RemoveObject(ctx, s.bucketName, objectName, minio.RemoveObjectOptions{})
		return nil, fmt.Errorf("保存文件元数据失败: %w", err)
	}

	return fileStorage, nil
}

func (s *FileStorageService) storeFileChunk(uploadId string, chunkData []byte, fileName string, chunkIndex int, totalChunks int) {
	chunkDir := filepath.Join(baseUrl, "chunks", uploadId)
	chunkFile := filepath.Join(chunkDir, fmt.Sprintf("chunk_%05d", chunkIndex))
	originHash, err := CalculateFileHash(chunkFile)
	if err != nil {
		slog.Error("计算文件哈希失败", "error", err)
	}
	//1.首先应该判断文件的hash存不存在 如果存在则直接返回
	if checkFileExists(chunkFile) {
		return
	}
	//2.如果不存在则创建文件
	result := checkFileExists(chunkDir)
	if !result {
		os.MkdirAll(chunkDir, os.ModePerm)
	}
	saveChunkFile(chunkData, chunkFile, chunkIndex)
	currentHash := hex.EncodeToString(chunkData)
	verified := strings.EqualFold(currentHash, originHash)
	SaveChunkMetadata(chunkData, chunkDir, fileName, uploadId, chunkIndex, currentHash, verified, totalChunks)
	//验证失败就删除损坏的文件
	if !verified {
		if checkFileExists(chunkFile) {
			os.Remove(chunkFile)
		}
		slog.Error("删除分片文件{}", chunkFile)
	}
}
func saveChunkFile(chunkData []byte, chunkPath string, chunkIndex int) {
	//使用临时文件确保原子性写入
	temp := filepath.Join(chunkPath, ".tmp")
	defer func() {
		if err := recover(); err != nil {
			_ = os.Remove(temp)
			panic(err)
		}
	}()
	if err := os.WriteFile(temp, chunkData, 0644); err != nil {
		return
	}
	err := os.Rename(temp, chunkPath)
	if err == nil { //原子移动成功就直接返回
		return
	}
	if err := os.Remove(chunkPath); err != nil && !os.IsNotExist(err) {
		panic(err)
	}
	slog.Info("原子移动失败，降级为普通替换：%v", err)
}
func SaveChunkMetadata(chunkData []byte, chunkPath string, fileName string, uploadId string, chunkIndex int, chunkHash string, verified bool, totalChunks int) error {
	//保存分片元数据
	meta := model.ChunkMeta{
		FileName:    fileName,
		UploadId:    uploadId,
		ChunkIndex:  chunkIndex,
		ChunkHash:   chunkHash,
		TotalChunks: totalChunks,
	}
	if chunkData == nil && len(chunkData) == 0 {
		return fmt.Errorf("分片数据为空")
	}
	byteLength := len(chunkData)
	chunkSize := formatFileSzie(byteLength)
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
func formatFileSzie(bytes int) string {
	if bytes >= 1024*1024 {
		mbSize := float64(bytes) / 1024 * 1024
		return fmt.Sprintf("%.2f MB", mbSize)
	} else if bytes >= 1024 {
		kbSize := float64(bytes) / 1024
		return fmt.Sprintf("%.2f KB", kbSize)
	} else {
		return fmt.Sprintf("%d B", bytes)
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
		FileSize:       fileSize,
	}

	err := s.db.Save(metadata)
	if err != nil {
		s.logger.Error("保存文件元数据失败: %w", err)
	}
	return fmt.Errorf("保存文件元数据失败: %w", err)
}
func (s *FileStorageService) CompleteMergeChunks(uploadId string, fileType string, fileName string, minmeType string) {
	chunks := filepath.Join(baseUrl, "chunk", uploadId)
	os.Stat(chunks)
	file, err := os.Open("baseUrl")
	if err != nil {
		slog.Error("打开文件失败: %w", err)
	}
	if !isChunkComplete(uploadId) {
		slog.Error("打开文件失败: %w", err)
		return
	}
	tempFile := s.MergeChunks(chunks, fileName)
	fileHash, err := CalculateFileHash(tempFile)
	if err != nil {
		slog.Error("计算文件哈希失败: %w", err)
		return
	}

}
func getMinmeType(contentType string) string {
	if contentType == "" {
		return "others"
	} else if strings.HasPrefix(contentType, "image/") {
		return "images"
	} else if strings.HasPrefix(contentType, "video/") {
		return "videos"
	} else if strings.HasPrefix(contentType, "application/pdf") || strings.HasPrefix("contentType", "text/") {
		return "documents"
	} else {
		return "others"
	}
}
func (s *FileStorageService) MergeChunks(path string, fileName string) string {
	tempFile, err := os.CreateTemp("merge_", fileName)
	if err != nil {
		slog.Error("创建临时文件失败: %w", err)
	}
	defer tempFile.Close()
	var chunkFiles []string
	filepath.Walk(path, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}
		if !info.IsDir() && strings.HasPrefix(info.Name(), "chunk_") {
			chunkFiles = append(chunkFiles, path)
		}
		return nil
	})
	sort.Slice(chunkFiles, func(i, j int) bool {
		return extractChunkIndex(chunkFiles[i]) < extractChunkIndex(chunkFiles[j])
	})
	for _, chunkFile := range chunkFiles {
		sourceFile, err := os.Open(chunkFile)
		if err != nil {
			slog.Error("打开文件失败: %w", err)
		}
		defer sourceFile.Close()
		io.Copy(sourceFile, tempFile)
	}
	return tempFile.Name()
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
func isChunkComplete(uploadId string) bool {
	chunks := filepath.Join(baseUrl, "chunk", uploadId)
	if !checkFileExists(chunks) {
		return false
	}
	chunkMeta := readChunkMeta(chunks, 0)
	if chunkMeta == nil {
		return false
	}
	for chunkIndex := 0; chunkIndex < chunkMeta.TotalChunks; chunkIndex++ {
		meta := readChunkMeta(chunks, chunkIndex)
		if meta == nil && !meta.Verified {
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
	file, err := os.Open(chunkFilePath)
	if err != nil {
		slog.Error("打开文件失败: %w", err)
	}
	if !checkFileExists(chunkFilePath) {
		return nil
	}
	defer file.Close()
	var meta model.ChunkMeta
	var bytes = make([]byte, 1024)
	file.Read(bytes)
	if err := json.Unmarshal(bytes, &meta); err != nil {
		slog.Error("解析文件元数据失败: %w", err)
		return nil
	}
	return &meta
}

// 完成分片上传
func (s *FileStorageService) CompleteMultipartUpload(ctx context.Context, uploadID string, objectName string, parts []minio.ObjectPart) (*minio.CompleteMultipartUploadResult, error) {
	return s.minioClient.CompleteMultipartUpload(ctx, s.bucketName, objectName, uploadID, parts, minio.CompleteMultipartUploadOptions{})
}

// 断点续传 - 获取已上传分片
func (s *FileStorageService) ListUploadedParts(ctx context.Context, uploadID string, objectName string) ([]minio.ObjectPart, error) {
	parts := make([]minio.ObjectPart, 0)
	for partNumber := 1; ; partNumber++ {
		result, err := s.minioClient.ListParts(ctx, s.bucketName, objectName, uploadID, partNumber, 1000)
		if err != nil {
			if err == io.EOF {
				break
			}
			return nil, err
		}
		parts = append(parts, result.Parts...)
		if result.IsTruncated {
			continue
		}
		break
	}
	return parts, nil
}
