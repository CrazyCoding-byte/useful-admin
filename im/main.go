package main

import (
	"local/im/src/config"
	"local/im/src/repository"
	"log"

	"github.com/minio/minio-go/v7"
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
		Creds: ,
		Secure: false,
	})
	if err != nil {
		log.Fatal("初始化MinioCore失败:%v", err)
	}
	//7.初始化分片上传器
	repository.NewMinioCoreChunkUploader(
		minioCore,
		minioClient,
		redisClient,
		db,
		cfg.MinIO,
		cfg.Retry,
	)
}
