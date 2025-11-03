package main

import (
	"fmt"
	"local/im/src/config"
	"log/slog"
	"time"
)

func main() {
	dateStr := time.Now().Format("20060102")
	fmt.Println(dateStr)
	// 1. 加载总配置   [是返回值类型]
	cfg, err := config.LoadConfig[config.Config]("E:\\studyoauth2\\springcloud-oauth2\\im\\src\\application.yml")
	if err != nil {
		panic(fmt.Sprintf("加载配置失败：%v", err))
	}

	// 2. 初始化日志
	if err := cfg.Log.InitLogger(); err != nil {
		panic(fmt.Sprintf("日志初始化失败：%v", err))
	}

	// 3. 初始化数据库（获取 *sql.DB 连接）
	db, err := cfg.Database.InitDatabase()
	if err != nil {
		slog.Error("数据库初始化失败", "error", err)
		panic(err)
	}
	db.Close()
	// 后续业务逻辑...
	slog.Info("应用启动成功")
	fmt.Println("配置加载成功，程序启动中...")
}
