package main

import (
	"context"
	"errors"
	"log/slog"
	"net"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"privateclouddisk/cloudflow-mcp-server/internal/config"
	"privateclouddisk/cloudflow-mcp-server/internal/mcp"
	"privateclouddisk/cloudflow-mcp-server/internal/telemetry"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		slog.Error("CloudFlow MCP Server configuration is invalid", "error", err)
		os.Exit(1)
	}
	if len(os.Args) == 2 && os.Args[1] == "--healthcheck" {
		host, port, splitErr := net.SplitHostPort(cfg.ListenAddress)
		if splitErr != nil || port == "" {
			slog.Error("healthcheck listen address is invalid", "error", splitErr)
			os.Exit(1)
		}
		if host == "" || host == "0.0.0.0" || host == "::" {
			host = "127.0.0.1"
		}
		response, checkErr := (&http.Client{Timeout: 2 * time.Second}).Get("http://" + net.JoinHostPort(host, port) + "/health/ready")
		if checkErr != nil || response.StatusCode != http.StatusOK {
			if response != nil {
				response.Body.Close()
			}
			slog.Error("CloudFlow MCP Server is not ready", "error", checkErr)
			os.Exit(1)
		}
		response.Body.Close()
		return
	}
	logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo}))
	shutdownTelemetry, err := telemetry.Setup(context.Background(), cfg)
	if err != nil {
		logger.Error("CloudFlow MCP Server telemetry is invalid", "error", err)
		os.Exit(1)
	}
	defer func() {
		shutdownContext, cancelTelemetry := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancelTelemetry()
		if err := shutdownTelemetry(shutdownContext); err != nil {
			logger.Warn("CloudFlow MCP Server telemetry shutdown failed", "error", err)
		}
	}()
	server := &http.Server{
		Addr: cfg.ListenAddress, Handler: mcp.New(cfg, logger).Handler(),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       15 * time.Second,
		WriteTimeout:      cfg.RequestTimeout + 15*time.Second,
		IdleTimeout:       60 * time.Second,
		MaxHeaderBytes:    32 * 1024,
	}
	go func() {
		logger.Info("CloudFlow MCP Server started", "address", cfg.ListenAddress, "version", cfg.Version,
			"capability_hub_configured", cfg.CapabilityHubURL != "", "metrics_enabled", cfg.MetricsEnabled,
			"otlp_trace_export_enabled", cfg.OTLPEndpoint != "")
		if err := server.ListenAndServe(); !errors.Is(err, http.ErrServerClosed) {
			logger.Error("CloudFlow MCP Server stopped unexpectedly", "error", err)
			os.Exit(1)
		}
	}()
	stop := make(chan os.Signal, 1)
	signal.Notify(stop, syscall.SIGINT, syscall.SIGTERM)
	<-stop
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	if err := server.Shutdown(ctx); err != nil {
		logger.Error("CloudFlow MCP Server shutdown timed out", "error", err)
	}
}
