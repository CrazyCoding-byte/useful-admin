package config

import (
	"context"
	"fmt"
	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
	"gorm.io/driver/mysql"
	"gorm.io/gorm"
	"log/slog"
	"os"
	"path/filepath"
	"strings"
)

// InitLogger 基于 LogConfig 初始化全局日志器
func (lc *LogConfig) InitLogger() error {
	// 1. 创建日志目录（确保路径存在）
	logDir := filepath.Dir(lc.Path)

	if err := os.MkdirAll(logDir, 0755); err != nil {
		return fmt.Errorf("创建日志目录 %s 失败：%w", logDir, err)
	}

	// 2. 打开日志文件（支持创建、追加、写入）
	file, err := os.OpenFile(lc.Path, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0644)
	if err != nil {
		return fmt.Errorf("打开日志文件 %s 失败：%w", lc.Path, err)
	}

	// 3. 解析日志级别（字符串转 slog.Level）
	var level slog.Level
	switch strings.ToLower(lc.Level) {
	case "debug":
		level = slog.LevelDebug
	case "warn":
		level = slog.LevelWarn
	case "error":
		level = slog.LevelError
	default: // 默认 info 级别
		level = slog.LevelInfo
	}

	// 4. 配置日志格式（text 或 json）
	var handler slog.Handler
	switch strings.ToLower(lc.Format) {
	case "json":
		handler = slog.NewJSONHandler(file, &slog.HandlerOptions{Level: level})
	default: // 默认 text 格式
		handler = slog.NewTextHandler(file, &slog.HandlerOptions{Level: level})
	}
	// 5. 设置全局日志器
	slog.SetDefault(slog.New(handler))
	slog.Info("日志初始化成功", "path", lc.Path, "level", level, "format", lc.Format)
	return nil
}

// 注意：移除参数，改为返回 *gorm.DB 和 error
func (dc *DatabaseConfig) InitDatabase() (*gorm.DB, error) {
	// 构建数据库连接字符串（以 mysql 为例）
	dsn := fmt.Sprintf(
		"%s:%s@tcp(%s:%d)/%s?charset=%s&parseTime=true",
		dc.Username,
		dc.Password,
		dc.Host,
		dc.Port,
		dc.Dbname,
		dc.Charset,
	)

	// 打开数据库连接（这里的 db 是局部变量，初始化后返回）
	db, err := gorm.Open(mysql.Open(dsn), &gorm.Config{})
	if err != nil {
		return nil, fmt.Errorf("创建数据库连接失败：%w", err)
	}

	// 验证连接有效性（获取底层 sql.DB 并检查）
	sqlDB, err := db.DB()
	if err != nil {
		return nil, fmt.Errorf("获取底层数据库连接失败：%w", err)
	}
	if err := sqlDB.Ping(); err != nil { // 显式 ping 验证连接
		return nil, fmt.Errorf("数据库连接验证失败：%w", err)
	}

	// 设置数据库连接池参数
	sqlDB.SetMaxOpenConns(100)
	sqlDB.SetMaxIdleConns(20)
	slog.Info("数据库初始化成功", "driver", dc.Driver, "host", dc.Host, "dbname", dc.Dbname)
	return db, nil // 返回初始化后的 db 实例
}

// 初始化minio配置
func (mc *MinIOConfig) InitMinIO() (*minio.Client, error) {
	// 第一步：校验必要配置项（不能为空）
	if err := mc.validate(); err != nil {
		return nil, fmt.Errorf("minio config validate failed: %w", err)
	}

	// 第二步：创建 MinIO 客户端实例
	// credentials.NewStaticV4：使用静态的 AccessKeyID/SecretAccessKey 创建凭证
	// 参数说明：endpoint(服务地址)、凭证、useSSL(是否加密连接)、区域(留空自动检测)
	minioClient, err := minio.New(mc.Endpoint, &minio.Options{
		Creds:  credentials.NewStaticV4(mc.AccessKeyID, mc.SecretAccessKey, ""),
		Secure: mc.UseSSL,
		// 可选：指定区域，如 "us-east-1"，不指定则自动检测
		// Region: "cn-north-1",
	})
	if err != nil {
		return nil, fmt.Errorf("create minio client failed: %w", err)
	}

	// 第三步（可选但实用）：检查配置的 Bucket 是否存在，不存在则自动创建
	// 注：如果不需要自动创建桶，可删除这部分逻辑
	ctx := context.Background()
	exists, err := minioClient.BucketExists(ctx, mc.BucketName)
	if err != nil {
		return nil, fmt.Errorf("check bucket [%s] exists failed: %w", mc.BucketName, err)
	}
	if !exists {
		// 创建桶（默认私有权限，如需公读可修改 ObjectLockingEnabled 或添加 Policy）
		err = minioClient.MakeBucket(ctx, mc.BucketName, minio.MakeBucketOptions{})
		if err != nil {
			return nil, fmt.Errorf("create bucket [%s] failed: %w", mc.BucketName, err)
		}
		fmt.Printf("minio bucket [%s] created successfully\n", mc.BucketName)
	}

	// 初始化成功，返回客户端实例
	return minioClient, nil
}

// validate 校验 MinIO 配置的必要字段
func (mc *MinIOConfig) validate() error {
	// 去除字段前后空格（避免配置中多打空格导致的错误）
	mc.Endpoint = strings.TrimSpace(mc.Endpoint)
	mc.AccessKeyID = strings.TrimSpace(mc.AccessKeyID)
	mc.SecretAccessKey = strings.TrimSpace(mc.SecretAccessKey)
	mc.BucketName = strings.TrimSpace(mc.BucketName)

	// 校验核心字段不能为空
	if mc.Endpoint == "" {
		return fmt.Errorf("endpoint is empty")
	}
	if mc.AccessKeyID == "" {
		return fmt.Errorf("accessKeyID is empty")
	}
	if mc.SecretAccessKey == "" {
		return fmt.Errorf("secretAccessKey is empty")
	}
	if mc.BucketName == "" {
		return fmt.Errorf("bucketName is empty")
	}

	return nil
}
