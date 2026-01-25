package config

import (
	"fmt"
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
