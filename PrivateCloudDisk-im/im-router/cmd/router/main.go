// Package main 是 IM Router 服务入口。
//
// 职责：
//   - 加载配置（config.yaml + 环境变量覆盖）
//   - 装配各组件：Redis / gRPC 客户端池 / 离线存储 / 路由器 / MQ 消费者 / gRPC 服务端
//   - 启动 Prometheus 监控指标暴露
//   - 监听系统信号，优雅停止（处理完在途消息后退出）
//
// 启动顺序：Redis → gRPC ClientPool → Offline → Router → Handlers → gRPC Server → Metrics → MQ Consumer
// 停止顺序：MQ Consumer（排空在途）→ gRPC Server（GracefulStop）→ ClientPool → Redis → Metrics
package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/prometheus/client_golang/prometheus/promhttp"
	amqp "github.com/rabbitmq/amqp091-go"
	"log/slog"

	"privateclouddisk/im-router/internal/config"
	grpcsrv "privateclouddisk/im-router/internal/grpc"
	"privateclouddisk/im-router/internal/mq"
	"privateclouddisk/im-router/internal/offline"
	rediscli "privateclouddisk/im-router/internal/redis"
	"privateclouddisk/im-router/internal/router"
)

// 版本信息（编译时通过 -ldflags 注入）
var (
	version   = "dev"
	buildTime = "unknown"
	commit    = "none"
)

// 启动超时：组件初始化最长等待时间
const startupTimeout = 30 * time.Second

func main() {
	// 命令行参数
	configPath := flag.String("config", "config.yaml", "配置文件路径")
	showVersion := flag.Bool("version", false, "显示版本信息")
	flag.Parse()

	if *showVersion {
		fmt.Printf("im-router %s (commit=%s build=%s)\n", version, commit, buildTime)
		return
	}

	// 1. 加载配置
	cfg, err := config.Load(*configPath)
	if err != nil {
		fmt.Fprintf(os.Stderr, "[FATAL] 加载配置失败: %v\n", err)
		os.Exit(1)
	}

	// 2. 初始化日志（slog 结构化日志）
	logger := initLogger(cfg)
	logger.Info("IM Router 启动中",
		slog.String("version", version),
		slog.String("node_id", cfg.Server.NodeID),
		slog.String("config", *configPath),
	)

	// 3. 根上下文：监听 SIGINT / SIGTERM
	ctx, stop := signal.NotifyContext(context.Background(),
		syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	// 4. 装配组件
	comp, err := assemble(ctx, cfg, logger)
	if err != nil {
		logger.Error("组件装配失败", slog.Any("err", err))
		os.Exit(1)
	}

	// 打印启动汇总
	logger.Info("========== 组件装配完成 ==========")
	logger.Info("Redis",
		slog.String("addr", cfg.Redis.Addr()),
		slog.Int("db", cfg.Redis.DB),
		slog.String("status", "已连接"),
	)
	logger.Info("RabbitMQ",
		slog.String("broker", cfg.RabbitMQ.URI()),
		slog.String("status", "连通性验证通过"),
	)
	logger.Info("gRPC 客户端池",
		slog.Int("pool_per_node", cfg.GRPC.PoolSizePerNode),
		slog.String("status", "已就绪"),
	)
	logger.Info("MQ 消费者",
		slog.Int("workers", cfg.RabbitMQ.Consumer.WorkerCount),
		slog.Int("prefetch", cfg.RabbitMQ.Consumer.PrefetchCount),
		slog.String("push_command_queue", cfg.RabbitMQ.Consumer.PushCommandQueue),
	)
	logger.Info("离线消息",
		slog.Int("max_per_user", cfg.Offline.MaxMessagesPerUser),
		slog.Int("ttl_seconds", cfg.Offline.MessageTTL),
	)
	logger.Info("==================================")

	// 5. 启动 Prometheus 监控
	metricsSrv := startMetrics(cfg, logger)

	// 6. 启动 MQ 消费者
	go func() {
		if err := comp.consumer.Start(ctx); err != nil {
			logger.Error("MQ 消费者异常退出", slog.Any("err", err))
		}
	}()

	// 7. 启动过期节点清理定时任务（每 60s 扫描一次）
	//go runStaleNodeCleanup(ctx, comp.redis, logger)

	logger.Info("IM Router 启动完成，所有服务就绪",
		slog.String("node_id", cfg.Server.NodeID),
		slog.Int("grpc_port", cfg.Server.GRPCPort),
		slog.Int("metrics_port", cfg.Metrics.Port),
	)

	// 8. 等待停止信号
	<-ctx.Done()
	logger.Info("收到停止信号，开始优雅停止...", slog.String("signal", "shutdown"))

	// 9. 优雅停止（按依赖逆序）
	shutdown(comp, metricsSrv, logger, 30*time.Second)
	logger.Info("IM Router 已停止")
}

// components 装配后的组件集合。
type components struct {
	redis    *rediscli.Client
	grpcPool *grpcsrv.ClientPool
	offline  *offline.Store
	router   *router.Router
	consumer *mq.Consumer
}

// assemble 装配所有组件（不含网络监听启动）。
func assemble(ctx context.Context, cfg *config.Config, logger *slog.Logger) (*components, error) {
	// 用启动超时保护装配
	ictx, cancel := context.WithTimeout(ctx, startupTimeout)
	defer cancel()

	// ================================================================
	// 1. Redis
	// ================================================================
	logger.Info("正在连接 Redis...",
		slog.String("addr", cfg.Redis.Addr()),
		slog.Int("db", cfg.Redis.DB),
		slog.Int("pool_size", cfg.Redis.PoolSize),
	)
	rdb, err := rediscli.New(cfg.Redis.Host, cfg.Redis.Port, cfg.Redis.DB, cfg.Redis.PoolSize, cfg.Redis.Password)
	if err != nil {
		return nil, fmt.Errorf("初始化 Redis 失败: %w", err)
	}
	if err := rdb.Ping(ictx); err != nil {
		_ = rdb.Close()
		return nil, fmt.Errorf("Redis 连通性检查失败 [%s]: %w", cfg.Redis.Addr(), err)
	}
	logger.Info("Redis 已连接",
		slog.String("addr", cfg.Redis.Addr()),
		slog.Int("db", cfg.Redis.DB),
		slog.Int("pool", cfg.Redis.PoolSize),
	)

	// ================================================================
	// 2. RabbitMQ 连通性验证
	// ================================================================
	logger.Info("正在验证 RabbitMQ 连通性...",
		slog.String("broker", cfg.RabbitMQ.URI()),
		slog.String("vhost", cfg.RabbitMQ.VHost),
	)
	if err := verifyMQ(ictx, cfg.RabbitMQ); err != nil {
		return nil, fmt.Errorf("RabbitMQ 连通性验证失败 [%s]: %w", cfg.RabbitMQ.URI(), err)
	}
	logger.Info("RabbitMQ 连通性验证通过",
		slog.String("broker", cfg.RabbitMQ.URI()),
	)

	// ================================================================
	// 3. gRPC 客户端连接池
	// ================================================================
	grpcPool := grpcsrv.NewClientPool(cfg.GRPC)
	logger.Info("gRPC 客户端池已创建", slog.Int("pool_per_node", cfg.GRPC.PoolSizePerNode))

	// 离线存储
	offlineStore := offline.New(rdb, cfg.Offline.MaxMessagesPerUser, cfg.Offline.MessageTTL)

	// 路由器
	rt := router.New(cfg.Server.NodeID, rdb, grpcPool, cfg.Offline)
	rt.SetOfflineStore(offlineStore)
	rt.SetLogger(logger)

	// 处理器注册表（注入 PushCommandHandler）
	pushHandler := router.NewPushCommandHandler(rt, logger)
	// Router 实现 Notifier，负责将送达/失败事件转为回执推送
	registry := mq.NewHandlerRegistry(pushHandler, rt, logger)

	// MQ 消费者（注入 logger，确保连接/消费状态可观测）
	consumer := mq.NewConsumer(cfg.RabbitMQ, registry, logger)

	return &components{
		redis:    rdb,
		grpcPool: grpcPool,
		offline:  offlineStore,
		router:   rt,
		consumer: consumer,
	}, nil
}

// startMetrics 启动 Prometheus 指标暴露 HTTP 服务。
func startMetrics(cfg *config.Config, logger *slog.Logger) *http.Server {
	mux := http.NewServeMux()
	mux.Handle(cfg.Metrics.Path, promhttp.Handler())
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("ok"))
	})
	srv := &http.Server{
		Addr:    fmt.Sprintf(":%d", cfg.Metrics.Port),
		Handler: mux,
	}
	go func() {
		if !cfg.Metrics.Enabled {
			logger.Info("Prometheus 监控已禁用")
			return
		}
		logger.Info("Prometheus 监控服务启动",
			slog.String("addr", srv.Addr),
			slog.String("path", cfg.Metrics.Path),
		)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			logger.Error("监控服务异常", slog.Any("err", err))
		}
	}()
	return srv
}

// shutdown 按依赖逆序优雅停止。
func shutdown(comp *components, metricsSrv *http.Server, logger *slog.Logger, timeout time.Duration) {
	// 1. 停止 MQ 消费者（排空在途消息）
	logger.Info("停止 MQ 消费者...")
	stopWithTimeout(comp.consumer.Stop, "consumer", logger, timeout)

	// 2. 关闭 gRPC 客户端连接池
	logger.Info("关闭 gRPC 客户端池...")
	if err := comp.grpcPool.Close(); err != nil {
		logger.Warn("关闭 gRPC 客户端池出现错误", slog.Any("err", err))
	}

	// 3. 关闭 Redis
	logger.Info("关闭 Redis...")
	if err := comp.redis.Close(); err != nil {
		logger.Warn("关闭 Redis 出现错误", slog.Any("err", err))
	}

	// 4. 关闭监控 HTTP 服务
	logger.Info("关闭监控服务...")
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := metricsSrv.Shutdown(ctx); err != nil && !errors.Is(err, http.ErrServerClosed) {
		logger.Warn("关闭监控服务出现错误", slog.Any("err", err))
	}
}

// stopWithTimeout 为阻塞的停止操作增加超时保护。
func stopWithTimeout(stopFn func(), name string, logger *slog.Logger, timeout time.Duration) {
	done := make(chan struct{})
	go func() {
		stopFn()
		close(done)
	}()
	select {
	case <-done:
		logger.Info("停止完成", slog.String("component", name))
	case <-time.After(timeout):
		logger.Warn("停止超时，强制继续", slog.String("component", name), slog.Duration("timeout", timeout))
	}
}

// initLogger 初始化 slog 结构化日志。
func initLogger(cfg *config.Config) *slog.Logger {
	var level slog.Level
	switch cfg.Log.Level {
	case "debug":
		level = slog.LevelDebug
	case "warn":
		level = slog.LevelWarn
	case "error":
		level = slog.LevelError
	default:
		level = slog.LevelInfo
	}
	opts := &slog.HandlerOptions{Level: level}
	var handler slog.Handler
	if cfg.Log.Format == "console" {
		handler = slog.NewTextHandler(os.Stdout, opts)
	} else {
		handler = slog.NewJSONHandler(os.Stdout, opts)
	}
	return slog.New(handler).With(slog.String("service", "im-router"))
}

// verifyMQ 验证 RabbitMQ 连通性：拨号 → 打开 Channel → 关闭。
// 如果地址/密码/vhost 任一配置错误，此处会立即返回明确错误，避免启动后静默重试。
func verifyMQ(_ context.Context, cfg config.RabbitMQConfig) error {
	conn, err := amqp.Dial(cfg.URI())
	if err != nil {
		return fmt.Errorf("RabbitMQ 拨号失败 (请检查 host/port/username/password): %w", err)
	}
	defer conn.Close()

	ch, err := conn.Channel()
	if err != nil {
		return fmt.Errorf("RabbitMQ 打开 Channel 失败 (请检查 vhost 权限): %w", err)
	}
	_ = ch.Close()

	return nil
}

// runStaleNodeCleanup 定期扫描并清理心跳超时的 IM Server 节点。
//
// 每 60 秒执行一次 CleanupStaleNodes：
//   - 检查 im:servers 中所有节点的 lastHeartbeat
//   - 心跳超时（>90s）的节点从活跃集合中移除
//   - 清理关联的 im:user:* 映射
//
// 此机制处理 IM Server 异常宕机（kill -9）场景：
// 心跳停止 → 90s 后 Router 自动清理 → 用户映射依赖 TTL 过期
func runStaleNodeCleanup(ctx context.Context, rdb *rediscli.Client, logger *slog.Logger) {
	ticker := time.NewTicker(60 * time.Second)
	defer ticker.Stop()

	logger.Info("过期节点清理任务已启动", slog.Duration("interval", 60*time.Second))

	for {
		select {
		case <-ctx.Done():
			logger.Info("过期节点清理任务已停止")
			return
		case <-ticker.C:
			cleaned, err := rdb.CleanupStaleNodes(ctx)
			if err != nil {
				logger.Warn("过期节点清理扫描失败", slog.Any("err", err))
				continue
			}
			if len(cleaned) > 0 {
				logger.Warn("已清理过期节点",
					slog.Int("count", len(cleaned)),
					slog.Any("nodes", cleaned),
				)
			}
		}
	}
}
