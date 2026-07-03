// Package main 通知服务入口。
// 职责：
//   - 多渠道推送：APNs (iOS)、FCM (Android)、微信/支付宝小程序、Web Push、邮件、短信
//   - 消息模板管理（支持变量替换、i18n）
//   - 用户通知偏好设置（免打扰时段、渠道偏好）
//   - 通知送达追踪和失败重试
//   - 消息聚合（避免短时间内大量推送骚扰用户）
package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/redis/go-redis/v9"

	"github.com/privateclouddisk/notification-service/internal/channel"
	"github.com/privateclouddisk/notification-service/internal/config"
	"github.com/privateclouddisk/notification-service/internal/database"
	httpHandler "github.com/privateclouddisk/notification-service/internal/handler/http"
	"github.com/privateclouddisk/notification-service/internal/rabbitmq"
	"github.com/privateclouddisk/notification-service/internal/redisutil"
	"github.com/privateclouddisk/notification-service/internal/repository"
	"github.com/privateclouddisk/notification-service/internal/service"
	"github.com/privateclouddisk/notification-service/internal/ws"
)

func main() {
	log.Println("[Notification] 启动通知服务...")

	// 1. 加载配置
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("[Notification] 配置加载失败: %v", err)
	}

	// 2. 连接数据库
	if err := database.Connect(cfg.MySQL); err != nil {
		log.Fatalf("[Notification] 数据库连接失败: %v", err)
	}
	defer database.Close()

	// 3. 确保表结构存在
	if err := database.RunMigrations(); err != nil {
		log.Fatalf("[Notification] 数据库迁移失败: %v", err)
	}

	// 4. 连接 RabbitMQ
	rmqConn, err := rabbitmq.Connect(cfg.RabbitMQ)
	if err != nil {
		log.Fatalf("[Notification] RabbitMQ 连接失败: %v", err)
	}
	defer rmqConn.Close()

	// 4.5. 连接 Redis
	redisClient := redis.NewClient(&redis.Options{
		Addr:     cfg.Redis.Addr(),
		Password: cfg.Redis.Password,
		DB:       cfg.Redis.DB,
	})
	if err := redisClient.Ping(context.Background()).Err(); err != nil {
		log.Fatalf("[Notification] Redis 连接失败: %v", err)
	}
	defer redisClient.Close()
	log.Printf("[Notification] Redis 连接成功: %s", cfg.Redis.Addr())

	// 5. 初始化 Repository
	db := database.DB
	templateRepo := repository.NewTemplateRepo(db)
	notificationRepo := repository.NewNotificationRepo(db)
	deliveryLogRepo := repository.NewDeliveryLogRepo(db)
	preferenceRepo := repository.NewPreferenceRepo(db)
	deviceRepo := repository.NewDeviceRepo(db)
	aggregationRepo := repository.NewAggregationRepo(db)

	// 6. 初始化 Service
	templateService := service.NewTemplateService(templateRepo)
	preferenceService := service.NewPreferenceService(preferenceRepo)
	aggregationService := service.NewAggregationService(cfg, aggregationRepo, notificationRepo)

	// 7. 初始化 Channel
	channelManager := channel.NewChannelManager()

	// APNs
	apnsCh, err := channel.NewAPNsChannel(cfg.APNs)
	if err != nil {
		log.Printf("[Notification] APNs 初始化失败（非致命）: %v", err)
	} else {
		channelManager.Register(apnsCh)
	}

	// FCM
	fcmCh, err := channel.NewFCMChannel(cfg.FCM)
	if err != nil {
		log.Printf("[Notification] FCM 初始化失败（非致命）: %v", err)
	} else {
		channelManager.Register(fcmCh)
	}

	// 邮件
	emailCh := channel.NewEmailChannel(cfg.Email)
	channelManager.Register(emailCh)

	// 短信
	smsCh := channel.NewSMSChannel(cfg.SMS)
	channelManager.Register(smsCh)

	// 微信小程序
	wechatMPCh := channel.NewWeChatMPChannel(cfg.WeChatMP)
	channelManager.Register(wechatMPCh)

	// 支付宝小程序
	alipayMPCh := channel.NewAlipayMPChannel(cfg.AlipayMP)
	channelManager.Register(alipayMPCh)

	// Web Push
	webPushCh := channel.NewWebPushChannel(cfg.WebPush)
	channelManager.Register(webPushCh)

	log.Printf("[Notification] 已注册渠道: %v", channelManager.GetEnabledChannels())

	// 7.5. 初始化 WebSocket Hub（系统推送）
	var wsHub *ws.Hub
	if cfg.WS.Enabled {
		wsHub = ws.NewHub(&ws.HubConfig{
			RedisClient: redisClient,
			RedisPrefix: cfg.WS.RedisOfflinePrefix,
		})
		go wsHub.Run(context.Background())
		log.Printf("[WS] WebSocket Hub 已启动")
	}

	notifService := service.NewNotificationService(
		cfg,
		templateRepo,
		notificationRepo,
		deliveryLogRepo,
		preferenceRepo,
		deviceRepo,
		aggregationRepo,
		channelManager, // 注入 ChannelManager 作为 ChannelSender
		wsHub,          // 注入 WS Hub 作为 WSHubPublisher
	)

	// 8. 初始化嵌入邮件模板（从 Spring Boot 迁移的完整 HTML 模板）
	templateInitializer := service.NewTemplateInitializer(templateRepo)
	if err := templateInitializer.InitializeIfEmpty(); err != nil {
		log.Printf("[Notification] 模板初始化失败（非致命）: %v", err)
	} else {
		log.Println("[Notification] 嵌入邮件模板初始化完成")
	}

	// 8.5. 初始化验证码服务（从 Spring Boot 平台服务完整迁移，支持模板系统）
	verificationRepo := redisutil.NewVerificationRepo(redisClient)
	verificationService := service.NewVerificationCodeService(cfg, verificationRepo, templateRepo, channelManager)
	verificationHandler := httpHandler.NewVerificationHandler(verificationService)
	log.Println("[Verification] 验证码服务初始化完成")

	// 9. 初始化消费者
	consumer := rabbitmq.NewConsumer(rmqConn, cfg, notifService)
	// 设置验证码消费者处理器
	consumer.SetVerificationHandler(verificationService)
	if err := consumer.Start(); err != nil {
		log.Fatalf("[Notification] 消费者启动失败: %v", err)
	}
	defer consumer.Shutdown()

	// 设置 MQ 发布器到验证码服务（异步发送验证码）
	verificationService.SetMQPublisher(consumer)

	// 10. 启动 HTTP 服务
	handler := httpHandler.NewHandler(
		cfg, db, consumer,
		notifService, templateService, preferenceService, aggregationService,
		templateRepo,
	)

	gin.SetMode(cfg.Server.Mode)
	router := gin.Default()
	handler.RegisterRoutes(router)
	verificationHandler.RegisterRoutes(router) // 注册验证码路由

	// 注册 WebSocket 路由
	if wsHub != nil {
		router.GET(cfg.WS.Path, func(c *gin.Context) {
			ws.ServeWs(wsHub, c.Writer, c.Request)
		})
		log.Printf("[WS] WebSocket 路由注册: %s", cfg.WS.Path)
	}

	// 启动聚合窗口定时检查
	go startAggregationWorker(context.Background(), notifService, cfg)

	// 启动日志清理定时任务
	go startLogCleanupWorker(context.Background(), deliveryLogRepo, cfg)

	server := &http.Server{
		Addr:         ":" + cfg.Server.Port,
		Handler:      router,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	// 优雅关闭
	go func() {
		log.Printf("[Notification] HTTP 服务启动: port=%s", cfg.Server.Port)
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("[Notification] HTTP 服务错误: %v", err)
		}
	}()

	// 等待关闭信号
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Println("[Notification] 收到关闭信号，开始优雅关闭...")

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	if err := server.Shutdown(ctx); err != nil {
		log.Printf("[Notification] HTTP 服务关闭错误: %v", err)
	}

	log.Println("[Notification] 通知服务已关闭")
}

// startAggregationWorker 启动聚合窗口定时检查任务
func startAggregationWorker(ctx context.Context, svc *service.NotificationService, cfg *config.Config) {
	ticker := time.NewTicker(time.Duration(cfg.Worker.AggregationCheckIntervalSec) * time.Second)
	defer ticker.Stop()

	log.Printf("[Worker] 聚合检查任务启动: interval=%ds", cfg.Worker.AggregationCheckIntervalSec)

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			if err := svc.ProcessAggregation(ctx); err != nil {
				log.Printf("[Worker] 聚合处理失败: %v", err)
			}
		}
	}
}

// startLogCleanupWorker 启动日志清理定时任务
func startLogCleanupWorker(ctx context.Context, repo *repository.DeliveryLogRepo, cfg *config.Config) {
	ticker := time.NewTicker(24 * time.Hour) // 每天执行一次
	defer ticker.Stop()

	log.Println("[Worker] 日志清理任务启动")

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			if _, err := repo.CleanupOldLogs(cfg.Worker.LogRetentionDays); err != nil {
				log.Printf("[Worker] 日志清理失败: %v", err)
			}
		}
	}
}