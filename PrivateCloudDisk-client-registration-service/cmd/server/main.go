package main

import (
	"context"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/redis/go-redis/v9"

	"github.com/privateclouddisk/client-registration-service/internal/config"
	"github.com/privateclouddisk/client-registration-service/internal/database"
	httphandler "github.com/privateclouddisk/client-registration-service/internal/handler/http"
	"github.com/privateclouddisk/client-registration-service/internal/redisutil"
	"github.com/privateclouddisk/client-registration-service/internal/repository"
	"github.com/privateclouddisk/client-registration-service/internal/security"
	"github.com/privateclouddisk/client-registration-service/internal/service"
)

func main() {
	log.SetFlags(log.LstdFlags | log.Lshortfile)
	log.Println("=== PrivateCloudDisk 客户端注册微服务 v1.0.0 ===")

	// ─── 1. 加载配置 ────────────────────────────────────────────────────────────
	configPath := "../../config/config.yaml"
	if envPath := os.Getenv("CONFIG_PATH"); envPath != "" {
		configPath = envPath
	}

	cfg, err := config.Load(configPath)
	if err != nil {
		log.Fatalf("加载配置失败: %v", err)
	}
	log.Printf("配置加载成功: server.port=%d", cfg.Server.Port)

	// ─── 2. 初始化数据库 ────────────────────────────────────────────────────────
	db, err := database.NewMySQL(&cfg.Database)
	if err != nil {
		log.Fatalf("连接 MySQL 失败: %v", err)
	}
	defer db.Close()
	log.Println("MySQL 连接成功")

	// 执行数据库迁移
	if err := database.RunMigrations(db); err != nil {
		log.Fatalf("数据库迁移失败: %v", err)
	}
	log.Println("数据库迁移完成")

	// ─── 3. 初始化 Redis ────────────────────────────────────────────────────────
	redisClient := redis.NewClient(&redis.Options{
		Addr:     cfg.Redis.Addr,
		Password: cfg.Redis.Password,
		DB:       cfg.Redis.DB,
		PoolSize: cfg.Redis.PoolSize,
	})

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := redisClient.Ping(ctx).Err(); err != nil {
		log.Fatalf("连接 Redis 失败: %v", err)
	}
	log.Println("Redis 连接成功")

	// ─── 4. 初始化安全组件 ──────────────────────────────────────────────────────
	appleRootCA, err := security.NewAppleRootCA(
		cfg.Apple.RootCAPath,
		cfg.Apple.EnableAttestationVerification,
	)
	if err != nil {
		log.Printf("警告: 苹果根证书加载失败（将使用内置证书）: %v", err)
		// 降级使用内置证书
		appleRootCA, _ = security.NewAppleRootCA("", false)
	}

	// ─── 5. 初始化仓储层 ────────────────────────────────────────────────────────
	clientRepo := repository.NewClientRepository(db)
	clientRedisRepo := redisutil.NewClientRedisRepo(redisClient, cfg.Security.PublicKeyCacheTTL)

	// ─── 6. 初始化服务层 ────────────────────────────────────────────────────────
	attestationService := service.NewAttestationService(
		appleRootCA,
		cfg.Security.AllowedAppIDs,
		cfg.Security.AttestationTimestampWindow,
	)

	registrationService := service.NewRegistrationService(
		clientRepo,
		clientRedisRepo,
		attestationService,
		cfg.Security.ChallengeTTL,
		cfg.Security.PublicKeyCacheTTL,
	)

	// ─── 7. 初始化 HTTP 路由 ────────────────────────────────────────────────────
	gin.SetMode(cfg.Server.Mode)
	router := gin.Default()

	// 添加请求日志中间件
	router.Use(gin.LoggerWithFormatter(func(param gin.LogFormatterParams) string {
		return fmt.Sprintf("[GIN] %s | %3d | %13v | %-7s %s\n",
			param.TimeStamp.Format("2006-01-02 15:04:05"),
			param.StatusCode,
			param.Latency,
			param.Method,
			param.Path,
		)
	}))

	handler := httphandler.NewHandler(registrationService)
	handler.RegisterRoutes(router)

	// ─── 8. 启动 HTTP 服务器 ────────────────────────────────────────────────────
	srv := &http.Server{
		Addr:         fmt.Sprintf(":%d", cfg.Server.Port),
		Handler:      router,
		ReadTimeout:  cfg.Server.ReadTimeout,
		WriteTimeout: cfg.Server.WriteTimeout,
	}

	// 优雅关闭
	go func() {
		log.Printf("HTTP 服务器启动: http://localhost:%d", cfg.Server.Port)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("HTTP 服务器启动失败: %v", err)
		}
	}()

	// ─── 9. 等待退出信号 ───────────────────────────────────────────────────────
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Println("正在优雅关闭服务...")

	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer shutdownCancel()

	if err := srv.Shutdown(shutdownCtx); err != nil {
		log.Fatalf("服务器关闭失败: %v", err)
	}

	log.Println("服务已关闭")
}