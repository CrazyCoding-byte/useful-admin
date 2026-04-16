package main

import (
	"fmt"
	"github.com/gin-gonic/gin"
	"github.com/minio/minio-go/v7/pkg/credentials"
	"local/im/src/config"
	"local/im/src/repository"
	"log"

	"github.com/minio/minio-go/v7"
	_ "github.com/minio/minio-go/v7/pkg/credentials" // 凭证包
)

type User struct {
	ID      uint
	Name    string
	Profile Profile   `gorm:"foreignKey:UserID"`
	Article []Article `gorm:"foreignKey:UserID"`
}

func (User) TableName() string {
	return "users"
}

type Profile struct {
	ID     uint   `gorm:"column:id;primaryKey;autoIncrement"` // 显式声明自增主键
	UserID uint   `gorm:"column:user_id"`
	Bio    string `gorm:"column:bio"`
}

func (Profile) TableName() string {
	return "profiles"
}

type Article struct {
	ID     uint   `gorm:"column:id;primaryKey;autoIncrement"`
	Title  string `gorm:"column:title"`
	UserID uint   `gorm:"column:user_id"`
	Status uint   `gorm:"column:status"`
}

func (Article) TableName() string {
	return "articles"
}

type Tag struct {
	ID   uint
	Name string
}

func main() {
	//1.加载配置
	cfg, err := config.LoadConfig[config.Config]("src/application.yml")
	if err != nil {
		log.Fatal("加载配置失败:%v", err)
	}
	//2.初始化日志
	if err := cfg.Log.InitLogger(); err != nil {
		log.Fatal("初始化日志失败:%v", err)
	}
	//3.初始化数据库
	db, err := cfg.Database.InitDatabase()
	if err != nil {
		log.Fatal("初始化数据库失败:%v", err)
	}
	// 4. 初始化 Redis
	redisClient, err := cfg.Redis.InitRedisClient()
	if err != nil {
		log.Fatalf("初始化Redis失败: %v", err)
	}
	// 5. 初始化 MinIO 客户端
	minioClient, err := cfg.MinIO.InitMinIO()
	if err != nil {
		log.Fatalf("初始化MinIO失败: %v", err)
	}
	minioCore, err := repository.NewMinioCore(cfg.MinIO.Endpoint, &minio.Options{
		Creds:  credentials.NewStaticV4(cfg.MinIO.AccessKeyID, cfg.MinIO.SecretAccessKey, ""),
		Secure: false, //关闭https
	})
	if err != nil {
		log.Fatal("初始化MinioCore失败:%v", err)
	}
	//7.初始化分片上传器
	chunkUploader := repository.NewMinioCoreChunkUploader(
		minioCore,
		minioClient,
		redisClient,
		db,
		cfg.MinIO,
		cfg.Retry,
	)

	//8.初始化普通文件存储服务
	fileService, err := repository.NewFileStorageService(
		minioClient,
		db,
		cfg.MinIO.BucketName,
		cfg.FileConfig.BaseUrl,
		int(cfg.FileConfig.MaxFileSize),
		int(cfg.FileConfig.MaxChunkSize),
	)
	if err != nil {
		log.Fatal("初始化文件存储服务失败:%v", err)
	}

	//9.创建Gin路由
	r := gin.Default()

	//11启动服务
	fmt.Println("服务器启动在: 8080")
	r.Run(":8080")
}
