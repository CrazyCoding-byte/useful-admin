package main

import (
	"fmt"
	"log"
	"video/config"
	"video/handler"
	"video/middleware"
	"video/repository"
	"video/service"

	"github.com/gin-gonic/gin"
	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
	// 复用公共模块的鉴权中间件，video 不再自己实现 Token 解析
	pkgmiddleware "local/pkg/middleware"
)

func main() {
	fmt.Println("=== 教学视频服务启动 ===")

	// 1. 加载配置。
	// Config 内嵌了公共模块的 Config，因此 database/minio/redis/log/aseKey 等节点会解析到公共配置里，
	// server/video/zlmediakit 节点由 video 自己解析。
	cfg, err := config.LoadConfig("application.yml")
	if err != nil {
		log.Fatalf("加载配置失败: %v", err)
	}
	fmt.Println("✓ 配置加载成功")

	// 2. 初始化日志（复用 IM 的 InitLogger）
	if err := cfg.Log.InitLogger(); err != nil {
		log.Fatalf("初始化日志失败: %v", err)
	}
	fmt.Println("✓ 日志初始化成功")

	// 3. 初始化数据库（复用 IM 的 InitDatabase）
	db, err := cfg.Database.InitDatabase()
	if err != nil {
		log.Fatalf("初始化数据库失败: %v", err)
	}
	fmt.Println("✓ 数据库连接成功")

	// 4. 初始化 Redis（复用 IM 的 InitRedisClient）
	redisClient, err := cfg.Redis.InitRedisClient()
	if err != nil {
		log.Fatalf("初始化 Redis 失败: %v", err)
	}
	fmt.Println("✓ Redis 连接成功")

	// 5. 初始化 MinIO（复用 IM 的 InitMinIO）
	// 注意：IM 模块已经实现了桶存在性检查和自动创建，video 直接拿 client 用即可。
	minioClient, err := cfg.MinIO.InitMinIO()
	if err != nil {
		log.Fatalf("初始化 MinIO 失败: %v", err)
	}
	fmt.Println("✓ MinIO 连接成功")

	// 5.5 创建 MinIO Core + 分片上传器（切片上传/断点续传，复用 IM 已验证的 Multipart 方案）
	minioCore, err := minio.NewCore(cfg.MinIO.Endpoint, &minio.Options{
		Creds:  credentials.NewStaticV4(cfg.MinIO.AccessKeyID, cfg.MinIO.SecretAccessKey, ""),
		Secure: cfg.MinIO.UseSSL,
	})
	if err != nil {
		log.Fatalf("初始化 MinIO Core 失败: %v", err)
	}
	chunkUploader := repository.NewChunkUploader(minioCore, minioClient, redisClient, db, cfg.MinIO, cfg.Retry)
	fmt.Println("✓ 分片上传器初始化成功")

	// 6. 初始化仓库
	minioRepo := repository.NewMinioRepository(minioClient, &cfg.MinIO)
	repos, err := repository.NewRepositories(db, minioRepo)
	if err != nil {
		log.Fatalf("初始化仓库失败: %v", err)
	}
	fmt.Println("✓ 仓库初始化成功")

	// 7. 初始化服务
	courseService := service.NewCourseService(repos)
	videoService := service.NewVideoService(repos, cfg, chunkUploader)
	danmakuHub := service.NewDanmakuHub(repos, redisClient, cfg)
	// 初始化直播服务：LiveHub 负责实时聊天，LiveService 负责直播间业务与 ZLMediaKit 信令
	liveHub := service.NewLiveHub(repos, redisClient, cfg)
	liveService := service.NewLiveService(repos, cfg, liveHub)
	fmt.Println("✓ 服务初始化成功")

	// 8. 初始化 Handler
	courseHandler := handler.NewCourseHandler(courseService)
	videoHandler := handler.NewVideoHandler(videoService)
	danmakuHandler := handler.NewDanmakuHandler(danmakuHub)
	liveHandler := handler.NewLiveHandler(liveService, liveHub)
	fmt.Println("✓ Handler 初始化成功")

	// 9. 创建路由
	// 鉴权中间件直接使用公共模块的 AuthMiddleware，解析 RSA+AES Token。
	// 构造函数需要传入公共 Config（即 video.Config 内嵌的 pkgconfig.Config）。
	authMiddleware := pkgmiddleware.NewAuthMiddleWare(&cfg.Config)
	r := gin.Default()
	r.Use(middleware.Cors())

	api := r.Group("/api/video")
	{
		// 课程管理（需要登录）
		course := api.Group("/course")
		course.Use(authMiddleware.HandlerFunc(""))
		{
			course.POST("", courseHandler.CreateCourse)
			course.PUT("", courseHandler.UpdateCourse)
			course.DELETE("/:id", courseHandler.DeleteCourse)
			course.GET("/:id", courseHandler.GetCourse)
			course.GET("/list", courseHandler.ListCourse)
			course.GET("/:id/detail", courseHandler.CourseDetail)
		}

		// 章节管理
		chapter := api.Group("/chapter")
		chapter.Use(authMiddleware.HandlerFunc(""))
		{
			chapter.POST("", courseHandler.CreateChapter)
			chapter.PUT("", courseHandler.UpdateChapter)
			chapter.DELETE("/:id", courseHandler.DeleteChapter)
			chapter.GET("/course/:courseId", courseHandler.ListChapter)
		}

		// 视频管理
		video := api.Group("/video")
		video.Use(authMiddleware.HandlerFunc(""))
		{
			video.POST("/upload", videoHandler.UploadVideo)
			video.PUT("", videoHandler.UpdateVideo)
			video.DELETE("/:id", videoHandler.DeleteVideo)
			video.GET("/:id", videoHandler.GetVideo)
			video.GET("/course/:courseId", videoHandler.ListByCourse)

			// 分片上传（切片上传 + 断点续传）
			video.POST("/chunk/init", videoHandler.InitChunkUpload)
			video.POST("/chunk/upload", videoHandler.UploadChunk)
			video.GET("/chunk/progress", videoHandler.ChunkProgress)
			video.POST("/chunk/complete", videoHandler.CompleteChunkUpload)
			video.POST("/chunk/abort", videoHandler.AbortChunkUpload)
		}

		// 播放接口（可选登录，未登录只能试看）
		api.GET("/play/:id", middleware.OptionalAuth(cfg.AES.Key), videoHandler.PlayInfo)

		// HLS AES-128 解密密钥下发。
		// 播放器拉取 m3u8 里的 EXT-X-KEY URI 时不带 Authorization header，
		// 因此放在鉴权组之外；安全依赖 keyId 随机不可枚举 + m3u8 预签名 URL。
		api.GET("/key/:keyId", videoHandler.GetPlayKey)

		// m3u8 代理：浏览器拉 m3u8 时走 video 服务，video 服务会把每个 .ts 替换成 presigned URL。
		// 不走 MinIO 原始 m3u8 URL（ts 相对路径没签名，浏览器会 AccessDenied）。
		api.GET("/m3u8/:id/:kind", videoHandler.ProxyM3U8)

		// 用户权限绑定（管理接口）
		api.POST("/permission/bind", authMiddleware.HandlerFunc(""), courseHandler.BindUserPermission)

		// 弹幕 HTTP 拉取
		api.GET("/danmaku/:videoId", danmakuHandler.ListDanmaku)

		// 弹幕 WebSocket
		api.GET("/danmaku/ws/:videoId", middleware.OptionalAuth(cfg.AES.Key), danmakuHandler.WebSocket)

		// ==================== 直播管理后台接口 ====================
		// 需要登录；后续可扩展按钮权限校验。
		liveAdmin := api.Group("/live/admin")
		liveAdmin.Use(authMiddleware.HandlerFunc(""))
		{
			// 直播间 CRUD
			liveAdmin.POST("/room", liveHandler.CreateRoom)
			liveAdmin.PUT("/room", liveHandler.UpdateRoom)
			liveAdmin.DELETE("/room/:id", liveHandler.DeleteRoom)
			liveAdmin.GET("/room/:id", liveHandler.GetRoom)
			liveAdmin.GET("/rooms", liveHandler.ListRooms)

			// 开播 / 结束 / 封禁
			liveAdmin.POST("/room/:id/start", liveHandler.StartLive)
			liveAdmin.POST("/room/:id/end", liveHandler.EndLive)
			liveAdmin.POST("/room/:id/ban", liveHandler.BanRoom)

			// 用户处罚：禁言 / 踢出 / 撤销 / 列表
			liveAdmin.POST("/ban", liveHandler.BanUser)
			liveAdmin.DELETE("/ban", liveHandler.UnbanUser)
			liveAdmin.GET("/bans/:roomId", liveHandler.ListBans)
		}

		// ==================== 直播观众端接口 ====================
		// 允许游客访问；WebSocket 支持可选鉴权。
		live := api.Group("/live")
		{
			live.GET("/room/:id/play", liveHandler.PlayInfo)
			live.GET("/room/:id/ws", middleware.OptionalAuth(cfg.AES.Key), liveHandler.WebSocket)
		}

		// ==================== ZLMediaKit Webhook 回调 ====================
		// 不经过 IM Token 鉴权，由 handler 内部校验 webhook-secret。
		liveWebhook := api.Group("/live/webhook")
		{
			liveWebhook.POST("/publish", liveHandler.WebhookPublish)
			liveWebhook.POST("/unpublish", liveHandler.WebhookUnpublish)
			liveWebhook.POST("/play", liveHandler.WebhookPlay)
		}
	}

	// 10. 启动服务
	addr := fmt.Sprintf(":%d", cfg.Server.Port)
	fmt.Printf("✓ 服务器启动在: %s\n", addr)
	fmt.Println("=== 启动完成，等待请求 ===")
	if err := r.Run(addr); err != nil {
		log.Fatalf("服务启动失败: %v", err)
	}
}
