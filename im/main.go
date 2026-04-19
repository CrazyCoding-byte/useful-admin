package main

import (
	"fmt"
	"github.com/gin-gonic/gin"
	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
	"local/im/src/config"
	"local/im/src/handler"
	"local/im/src/repository"
	"log"
)

func main() {
	fmt.Println("=== 开始启动程序 ===")

	// 1. 加载配置
	fmt.Println("1. 正在加载配置...")
	cfg, err := config.LoadConfig[config.Config]("src/application.yml")
	if err != nil {
		log.Fatalf("加载配置失败: %v", err)
	}
	fmt.Println("✓ 配置加载成功")

	// 2. 初始化日志
	fmt.Println("2. 正在初始化日志...")
	if err := cfg.Log.InitLogger(); err != nil {
		log.Fatalf("初始化日志失败: %v", err)
	}
	fmt.Println("✓ 日志初始化成功")

	// 3. 初始化数据库
	fmt.Println("3. 正在连接数据库...")
	db, err := cfg.Database.InitDatabase()
	if err != nil {
		log.Fatalf("初始化数据库失败: %v", err)
	}
	fmt.Println("✓ 数据库连接成功")

	// 4. 初始化 Redis
	fmt.Println("4. 正在连接 Redis...")
	redisClient, err := cfg.Redis.InitRedisClient()
	if err != nil {
		log.Fatalf("初始化Redis失败: %v", err)
	}
	fmt.Println("✓ Redis 连接成功")

	// 5. 初始化 MinIO 客户端
	fmt.Println("5.// 5. 正在连接 MinIO...")
	fmt.Printf("   MinIO 配置: endpoint=%s, bucket=%s, useSSL=%v\n", cfg.MinIO.Endpoint, cfg.MinIO.BucketName, cfg.MinIO.UseSSL)
	minioClient, err := cfg.MinIO.InitMinIO()
	if err != nil {
		log.Fatalf("初始化MinIO失败: %v", err)
	}
	fmt.Println("✓ MinIO 连接成功")

	// 6. 创建 MinIO Core
	fmt.Println("6. 正在创建 MinIO Core...")
	minioCore, err := repository.NewMinioCore(cfg.MinIO.Endpoint, &minio.Options{
		Creds:  credentials.NewStaticV4(cfg.MinIO.AccessKeyID, cfg.MinIO.SecretAccessKey, ""),
		Secure: cfg.MinIO.UseSSL,
	})
	if err != nil {
		log.Fatalf("初始化MinioCore失败: %v", err)
	}
	fmt.Println("✓ MinIO Core 创建成功")

	// 7. 初始化分片上传器
	fmt.Println("7. 正在初始化分片上传器...")
	chunkUploader := repository.NewMinioCoreChunkUploader(
		minioCore,
		minioClient,
		redisClient,
		db,
		cfg.MinIO,
		cfg.Retry,
	)
	fmt.Println("✓ 分片上传器初始化成功")

	//8.初始化普通文件存储服务
	fmt.Println("8. 正在初始化文件存储服务...")
	// 将 MB 转换为字节
	maxFileSizeBytes := int(cfg.FileConfig.MaxFileSize * 1024 * 1024)   // MB -> Bytes
	maxChunkSizeBytes := int(cfg.FileConfig.MaxChunkSize * 1024 * 1024) // MB -> Bytes
	fileService, err := repository.NewFileStorageService(
		minioClient,
		db,
		cfg.MinIO.BucketName,
		cfg.FileConfig.BaseUrl,
		maxFileSizeBytes,
		maxChunkSizeBytes,
	)
	if err != nil {
		log.Fatalf("初始化文件存储服务失败: %v", err)
	}
	fmt.Println("✓ 文件存储服务初始化成功")

	// 9. 创建 Gin 路由
	fmt.Println("9. 正在创建路由...")
	r := gin.Default()

	// 10. 创建文件处理器
	fileHandler := handler.NewFileHandler(fileService, chunkUploader, redisClient)
	fileGroup := r.Group("/api/file")
	{
		//单文件上传接口
		fileGroup.POST("/upload", fileHandler.UploadFile)
		//分片初始化接口
		fileGroup.POST("/chunk/init", fileHandler.InitChunkUpload)
		fileGroup.POST("/chunk/upload", fileHandler.UploadChunk)
		//查询分片上传进度
		fileGroup.GET("/chunk/progress", fileHandler.QueryProgress)
		//完成分片上传
		fileGroup.POST("/chunk/complete", fileHandler.CompleteChunkUpload)
		//取消分片上传
		fileGroup.POST("/chunk/abort", fileHandler.AbortChunkUpload)

		//下载文件接口
		fileGroup.GET("/download", fileHandler.DownloadFile)

		//预览文件接口
		fileGroup.GET("/preview", fileHandler.PreviewFile)
	}
	fmt.Println("✓ 路由创建成功")

	// 11. 启动服务
	fmt.Println("10. 服务器启动在: 8080")
	fmt.Println("=== 启动完成，等待请求 ===")
	r.Run(":8080")

}
