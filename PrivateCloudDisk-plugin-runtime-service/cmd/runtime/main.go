package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/exec"
	"os/signal"
	"path/filepath"
	"strings"
	"syscall"
	"time"

	"privateclouddisk/plugin-runtime-service/internal/api"
	"privateclouddisk/plugin-runtime-service/internal/audit"
	"privateclouddisk/plugin-runtime-service/internal/broker"
	"privateclouddisk/plugin-runtime-service/internal/capability"
	"privateclouddisk/plugin-runtime-service/internal/config"
	"privateclouddisk/plugin-runtime-service/internal/pkgclient"
	"privateclouddisk/plugin-runtime-service/internal/sandbox"
	"privateclouddisk/plugin-runtime-service/internal/sanitize"
	"privateclouddisk/plugin-runtime-service/internal/uds"
	"privateclouddisk/plugin-runtime-service/internal/validation"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		slog.Error("Runtime 配置校验失败", "error", err)
		os.Exit(1)
	}
	// 输出脱敏（6.2/6.10）：生产强制关闭 debug；sanitize 全局开关在启动时固定。
	sanitize.SetDebug(cfg.DebugMode)
	if len(cfg.SanitizeRules) > 0 {
		sanitize.Extend(cfg.SanitizeRules)
	}
	if err := os.MkdirAll(cfg.WorkRoot, 0o700); err != nil {
		slog.Error("Runtime 工作目录不可用", "error", err)
		os.Exit(1)
	}
	auditSink, err := audit.New(cfg.AuditLogPath)
	if err != nil {
		slog.Error("审计日志不可用", "error", sanitize.Error(err, 500))
		os.Exit(1)
	}
	defer auditSink.Close()
	if cfg.Environment == "production" {
		if err := verifyRunsc(cfg); err != nil {
			slog.Error("生产沙箱隔离门禁未通过", "error", err)
			os.Exit(1)
		}
	}
	capabilityClient := capability.New(cfg.CapabilityHubURL, cfg.InternalServiceToken, cfg.SocketRequestTimeout)
	sessionManager, err := uds.NewManager(uds.Config{
		RootDir: cfg.SocketRoot, GroupID: cfg.SocketGroupID, MaxFrameBytes: cfg.SocketMaxFrameBytes,
		MaxConnectionsPerPeer: cfg.SocketMaxConnections, RequestsPerSecond: cfg.SocketRequestsPerSec,
		RequestBurst: cfg.SocketRequestBurst, RequestTimeout: cfg.SocketRequestTimeout,
	}, capabilityClient)
	if err != nil {
		slog.Error("Runtime Unix Socket 管理器不可用", "error", sanitize.Error(err, 500))
		os.Exit(1)
	}
	defer sessionManager.Close()

	validatorScript := os.Getenv("PYTHON_VALIDATOR_SCRIPT")
	if validatorScript == "" {
		validatorScript = filepath.Join("validator", "validate_python.py")
	}
	jsValidatorScript := os.Getenv("JS_VALIDATOR_SCRIPT")
	if jsValidatorScript == "" {
		jsValidatorScript = filepath.Join("validator", "validate_js.mjs")
	}
	server := &api.Server{
		Config: cfg,
		Validator: validation.Validator{
			PythonScript: validatorScript,
			JSScript:     jsValidatorScript,
			Timeout:      cfg.ValidationTimeout,
		},
		Slots: make(chan struct{}, cfg.Concurrency),
	}
	server.Runner = &sandbox.Runner{
		Config: cfg,
		Packages: pkgclient.New(
			cfg.PluginServiceURL, cfg.InternalServiceToken, cfg.PackageMaxBytes,
		),
		Broker: broker.New(
			cfg.StorageBrokerURL, cfg.InternalServiceToken, cfg.CandidateMaxBytes,
		),
		Audit:    auditSink,
		Sessions: sessionManager,
	}

	httpServer := &http.Server{
		Addr:              cfg.ListenAddress,
		Handler:           server.Handler(),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       15 * time.Second,
		WriteTimeout:      cfg.ExecutionTimeout + 15*time.Second,
		IdleTimeout:       30 * time.Second,
		MaxHeaderBytes:    32 * 1024,
	}
	if cfg.RequireSandboxDigest {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		if err := server.Runner.VerifyImageDigest(ctx); err != nil {
			cancel()
			slog.Error("生产镜像摘要门禁未通过", "error", sanitize.Error(err, 500))
			os.Exit(1)
		}
		cancel()
	}
	go func() {
		slog.Info("Plugin Runtime 已启动",
			"address", cfg.ListenAddress, "version", cfg.Version,
			"environment", cfg.Environment, "sandbox", cfg.SandboxRuntime,
			"network", cfg.SandboxNetwork, "require_digest", cfg.RequireSandboxDigest,
			"audit_log", cfg.AuditLogPath, "socket_root", cfg.SocketRoot,
			"capability_hub_configured", cfg.CapabilityHubURL != "")
		if err := httpServer.ListenAndServe(); !errors.Is(err, http.ErrServerClosed) {
			slog.Error("Runtime HTTP 服务异常退出", "error", err)
			os.Exit(1)
		}
	}()

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, syscall.SIGINT, syscall.SIGTERM)
	<-stop
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	_ = httpServer.Shutdown(ctx)
	_ = sessionManager.Close()
}

func verifyRunsc(cfg config.Config) error {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	output, err := exec.CommandContext(
		ctx,
		cfg.DockerBinary,
		"info",
		"--format",
		"{{json .Runtimes}}",
	).Output()
	if err != nil {
		return errors.New("无法连接专用 rootless Docker API 代理")
	}
	if !strings.Contains(string(output), `"runsc"`) {
		return errors.New("Docker 节点未注册 runsc Runtime")
	}
	return nil
}
