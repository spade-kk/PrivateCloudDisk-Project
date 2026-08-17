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
	"privateclouddisk/plugin-runtime-service/internal/broker"
	"privateclouddisk/plugin-runtime-service/internal/config"
	"privateclouddisk/plugin-runtime-service/internal/pkgclient"
	"privateclouddisk/plugin-runtime-service/internal/sandbox"
	"privateclouddisk/plugin-runtime-service/internal/validation"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		slog.Error("Runtime 配置校验失败", "error", err)
		os.Exit(1)
	}
	if err := os.MkdirAll(cfg.WorkRoot, 0o700); err != nil {
		slog.Error("Runtime 工作目录不可用", "error", err)
		os.Exit(1)
	}
	if cfg.Environment == "production" {
		if err := verifyRunsc(cfg); err != nil {
			slog.Error("生产沙箱隔离门禁未通过", "error", err)
			os.Exit(1)
		}
	}

	validatorScript := os.Getenv("PYTHON_VALIDATOR_SCRIPT")
	if validatorScript == "" {
		validatorScript = filepath.Join("validator", "validate_python.py")
	}
	server := &api.Server{
		Config: cfg,
		Validator: validation.Validator{
			PythonScript: validatorScript,
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
	go func() {
		slog.Info("Plugin Runtime 已启动", "address", cfg.ListenAddress)
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
