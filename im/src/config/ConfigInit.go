package config

import (
	"database/sql"
	"fmt"
	"log/slog"
	"os"
	"path/filepath"
	"strings"
	// 按需导入数据库驱动（以 mysql 为例）
	_ "github.com/go-sql-driver/mysql"
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

// InitDatabase 基于 DatabaseConfig 初始化数据库连接（返回 *sql.DB）
func (dc *DatabaseConfig) InitDatabase() (*sql.DB, error) {
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

	// 打开数据库连接
	db, err := sql.Open(dc.Driver, dsn)
	if err != nil {
		return nil, fmt.Errorf("创建数据库连接失败：%w", err)
	}

	// 验证连接有效性
	if err := db.Ping(); err != nil {
		return nil, fmt.Errorf("数据库连接验证失败：%w", err)
	}

	slog.Info("数据库初始化成功", "driver", dc.Driver, "host", dc.Host, "dbname", dc.Dbname)
	return db, nil
}
