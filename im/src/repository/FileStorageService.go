package repository

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"local/im/src/config"
	"local/im/src/model"
	"log/slog"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
	"gorm.io/gorm"
)

var baseUrl = "file-storage"

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
func (s *FileStorageService) CheckFileExists(fileHash string, fileType string) (*model.FileStorage, bool) {
	var file model.FileStorage
	result := s.db.Where("file_hash = ? and file_system_type=?", fileHash, fileType).First(&file)
	if result.Error != nil {
		return nil, false
	}
	return &file, true
}

// 普通文件上传
func (s *FileStorageService) UploadFile(ctx context.Context, fileSystemType string, file io.Reader, fileName, mimeType, fileHash string, fileSize int64) (*model.FileStorage, error) {
	// 检查是否可以秒传
	if existingFile, exists := s.CheckFileExists(fileHash, fileSystemType); exists {
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
	chunkDir := filepath.Join(baseUrl, "chunks", uploadId)
	chunkFile := filepath.Join(chunkDir, fmt.Sprintf("chunk_%05d", chunkIndex))
	//先创建分片目录（MkdirAll已做存在判断，重复调用无副作用，直接调用即可）
	if err := os.MkdirAll(chunkDir, 0755); err != nil { // 优化：设置合理权限
		s.logger.Error("创建分片目录失败", slog.Any("err", err), slog.String("dir", chunkDir))
		return fmt.Errorf("创建分片目录失败: %w", err)
	}
	// 再判断分片文件是否存在
	if checkFileExists(chunkFile) {
		s.logger.Info("分片文件已存在，跳过存储", slog.String("chunkFile", chunkFile))
		return nil
	}
	if err := s.saveChunkFile(chunkData, chunkFile, chunkIndex); err != nil { // 接收saveChunkFile的错误
		return fmt.Errorf("保存分片文件失败: %w", err)
	}
	hash := sha256.New()
	hash.Write(chunkData)
	currentHash := hex.EncodeToString(hash.Sum(nil))

	fileHash, err := CalculateFileHash(chunkFile)
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
		file.logger.Error("写入临时分片文件失败", slog.Any("err", err), slog.String("temp", temp))
		return fmt.Errorf("写入临时分片文件失败: %w", err)
	}
	err := os.Rename(temp, chunkPath)
	if err == nil {
		return nil
	}
	// 原子移动失败，尝试普通替换
	if err := os.Remove(chunkPath); err != nil && !os.IsNotExist(err) {
		file.logger.Error("删除分片文件失败", slog.Any("err", err), slog.String("file", chunkPath))
		panic(err)
	}
	file.logger.Info("原子移动失败，降级为普通替换", slog.Any("err", err))
	if err := os.Rename(temp, chunkPath); err != nil {
		_ = os.Remove(temp)
		file.logger.Error("普通替换分片文件失败", slog.Any("err", err), slog.String("temp", temp))
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
	if chunkData == nil && len(chunkData) == 0 {
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
		s.logger.Error("保存文件元数据失败", slog.Any("err", err), slog.Any("metadata", metadata))
		return fmt.Errorf("保存文件元数据失败: %w", err) // 仅错误时返回
	}
	return nil // 成功时返回nil
}
func (s *FileStorageService) CompleteMergeChunks(fileSystemType string, uploadId string, fileName string, minmeType string) (downloadURL string, err error) {
	//首先判断文件夹存不存在
	path := filepath.Join(baseUrl, "chunks", uploadId)
	if !checkFileExists(path) {
		slog.Error("分片文件夹不存在", slog.String("path", path))
		return "", fmt.Errorf("分片文件夹不存在: %s", path) // 修复：失败时返回错误，而非nil
	}
	filePath, totalSize, err := s.MergeChunks(path, fileName)
	if err != nil {
		slog.Error("合并分片失败: %w", slog.Any("err", err))
		return "", fmt.Errorf("合并分片失败: %w", err)
	}
	hash, err2 := CalculateFileHash(filePath)
	if err2 != nil {
		slog.Error("计算文件哈希失败: %w", err2)
		return "", fmt.Errorf("计算文件哈希失败: %w", err)
	}
	if existingFile, ok := s.CheckFileExists(hash, fileSystemType); ok {
		// 秒传：删除临时文件，返回已存在的文件路径
		_ = os.Remove(filePath)
		return existingFile.FilePath, nil
	}
	//获取文件类型
	mType := getMinmeType(minmeType)
	splitName := strings.Split(fileName, ".")
	var extType string = "." + splitName[len(splitName)-1]
	hashedFileName := hash + extType
	targetPath := filepath.Join(baseUrl, fileSystemType, mType, hashedFileName)

	copyFile(filePath, targetPath)
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
func getMinmeType(contentType string) string {
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
	tempFile, err := os.CreateTemp("", pattern)
	if err != nil {
		slog.Error("创建临时文件失败: %w", err)
		return "", 0, fmt.Errorf("创建临时文件失败: %w", err)
	}
	//defer tempFile.Close()  //如果直接这样关闭会丢失错误
	defer func() {
		if err := tempFile.Close(); err != nil && err == nil {
			slog.Error("关闭临时文件失败: %w", err)
		}
	}()
	// 1. 遍历分片目录，收集所有chunk_*分片文件，并累加分片大小
	var chunkFiles []string
	filepath.Walk(path, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			slog.Error("遍历分片目录失败: %w", err)
			return fmt.Errorf("遍历分片目录失败: %w", err)
		}
		if !info.IsDir() && strings.HasPrefix(path, "chunk_") {
			chunkFiles = append(chunkFiles, path)
			//go的返回值可以直接在方法里面用
			totalSize += info.Size()
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
