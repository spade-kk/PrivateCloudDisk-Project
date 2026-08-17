package config

import (
	"errors"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

// Config 描述 Runtime 的安全边界；生产模式缺少关键隔离条件时必须失败关闭。
type Config struct {
	ListenAddress        string
	Environment          string
	InternalServiceToken string
	PluginServiceURL     string
	StorageBrokerURL     string
	WorkRoot             string
	DockerBinary         string
	SandboxImage         string
	SandboxRuntime       string
	SeccompProfile       string
	AppArmorProfile      string
	ExecutionTimeout     time.Duration
	ValidationTimeout    time.Duration
	MemoryBytes          int64
	CPUs                 string
	PidsLimit            int
	LogLimitBytes        int64
	PackageMaxBytes      int64
	CandidateMaxBytes    int64
	Concurrency          int
}

func Load() (Config, error) {
	cfg := Config{
		ListenAddress:        env("RUNTIME_LISTEN_ADDRESS", ":8090"),
		Environment:          strings.ToLower(env("RUNTIME_ENV", "development")),
		// 安全基线：不再把服务令牌写入代码默认值；开发/测试也必须显式注入。
		InternalServiceToken: os.Getenv("PCD_INTERNAL_SERVICE_TOKEN"),
		PluginServiceURL:     strings.TrimRight(env("PLUGIN_SERVICE_URL", "http://localhost:8085"), "/"),
		StorageBrokerURL:     strings.TrimRight(env("STORAGE_BROKER_URL", "http://localhost:8000"), "/"),
		WorkRoot:             env("RUNTIME_WORK_ROOT", "/Users/user/Desktop/pcd-runtime/work"),
		DockerBinary:         env("RUNTIME_DOCKER_BINARY", "docker"),
		SandboxImage:         env("PLUGIN_SANDBOX_IMAGE", "pcd/plugin-sandbox-python:0.1.0"),
		SandboxRuntime:       env("PLUGIN_SANDBOX_RUNTIME", "runsc"),
		SeccompProfile:       os.Getenv("PLUGIN_SANDBOX_SECCOMP_PROFILE"),
		AppArmorProfile:      os.Getenv("PLUGIN_SANDBOX_APPARMOR_PROFILE"),
		ExecutionTimeout:     seconds("PLUGIN_RUNTIME_TIMEOUT_SECONDS", 120),
		ValidationTimeout:    seconds("PLUGIN_VALIDATION_TIMEOUT_SECONDS", 5),
		MemoryBytes:          integer64("PLUGIN_SANDBOX_MEMORY_BYTES", 512*1024*1024),
		CPUs:                 env("PLUGIN_SANDBOX_CPUS", "1.0"),
		PidsLimit:            integer("PLUGIN_SANDBOX_PIDS_LIMIT", 64),
		LogLimitBytes:        integer64("PLUGIN_LOG_LIMIT_BYTES", 100*1024),
		PackageMaxBytes:      integer64("PLUGIN_PACKAGE_MAX_BYTES", 10*1024*1024),
		CandidateMaxBytes:    integer64("PLUGIN_CANDIDATE_MAX_BYTES", 10*1024*1024*1024),
		Concurrency:          integer("PLUGIN_RUNTIME_CONCURRENCY", 16),
	}
	if cfg.InternalServiceToken == "" {
		return Config{}, errors.New("必须配置 PCD_INTERNAL_SERVICE_TOKEN，禁止使用代码默认密钥")
	}
	if cfg.Environment == "production" && cfg.SandboxRuntime != "runsc" {
		return Config{}, errors.New("生产环境必须使用 gVisor runsc")
	}
	if cfg.Environment == "production" && cfg.SeccompProfile == "" {
		return Config{}, errors.New("生产环境必须配置插件沙箱 seccomp 策略")
	}
	if cfg.Environment == "production" && cfg.AppArmorProfile == "" {
		return Config{}, errors.New("生产环境必须配置插件沙箱 AppArmor 策略")
	}
	if !filepath.IsAbs(cfg.WorkRoot) {
		return Config{}, errors.New("RUNTIME_WORK_ROOT 必须是绝对路径")
	}
	if cfg.ExecutionTimeout <= 0 || cfg.ExecutionTimeout > 10*time.Minute {
		return Config{}, errors.New("插件执行超时配置越界")
	}
	if cfg.Concurrency < 1 || cfg.Concurrency > 256 {
		return Config{}, errors.New("插件 Runtime 并发度必须在 1 到 256 之间")
	}
	return cfg, nil
}

func env(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}

func seconds(key string, fallback int) time.Duration {
	return time.Duration(integer(key, fallback)) * time.Second
}

func integer(key string, fallback int) int {
	value, err := strconv.Atoi(env(key, strconv.Itoa(fallback)))
	if err != nil {
		return fallback
	}
	return value
}

func integer64(key string, fallback int64) int64 {
	value, err := strconv.ParseInt(env(key, strconv.FormatInt(fallback, 10)), 10, 64)
	if err != nil {
		return fallback
	}
	return value
}
