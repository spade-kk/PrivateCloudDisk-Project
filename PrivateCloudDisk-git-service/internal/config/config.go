package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

// Config 集中声明 Git Service 的协议、存储、事件与安全边界。
// [REQ-GIT-SERVICE-3.1/15.5] 所有敏感值仅从部署环境注入，不提供生产默认口令。
type Config struct {
	HTTPAddr                 string
	SSHAddr                  string
	SSHHostKeyPath           string
	RepoRoot                 string
	DatabaseDSN              string
	MigrationPath            string
	AutoMigrate              bool
	PlatformURL              string
	StorageURL               string
	InternalServiceToken     string
	HTTPCloneBaseURL         string
	SSHCloneHost             string
	SSHClonePort             int
	GitBinary                string
	MaxProtocolConcurrent    int
	MaxProtocolRequestBytes  int64
	MaxProtocolResponseBytes int64
	GitCommandTimeout        time.Duration
	SSHHandshakeTimeout      time.Duration
	AuthFailureLimit         int
	AuthFailureWindow        time.Duration
	AuthFailureCooldown      time.Duration
	MaxAPIOutputBytes        int64
	MaxRawFileBytes          int64
	MaxObjectBytes           int64
	ObjectSyncConcurrency    int
	RabbitURL                string
	EventExchange            string
	WebhookTimeout           time.Duration
	WebhookAllowHTTP         bool
}

func Load() (Config, error) {
	cfg := Config{
		// 原默认监听所有网卡，宿主机直启时管理 API
		// 会信任 Gateway 注入的 X-User-Id，可能被同网段请求直接伪造。默认仅监听回环地址；
		// Docker Compose 显式覆盖为容器内全监听，外部入口仍由 Gateway/SSH 端口控制。
		HTTPAddr: env("GIT_HTTP_ADDR", "127.0.0.1:8091"),
		SSHAddr:  env("GIT_SSH_ADDR", "127.0.0.1:2222"),
		// 原默认值仅适用于容器挂载的 /data/git，
		// 宿主机直接 go run 会在 SSH 初始化阶段因无权创建 /data 而失败。
		// 新行为使用项目相对目录；Compose 仍显式注入 /data/git 下的生产路径，
		// 因而不改变容器部署的存储位置和权限边界。
		SSHHostKeyPath:       env("GIT_SSH_HOST_KEY_PATH", "./data/git/ssh/host_ed25519"),
		RepoRoot:             env("GIT_REPO_ROOT", "./data/git/repos"),
		DatabaseDSN:          os.Getenv("GIT_DATABASE_DSN"),
		MigrationPath:        env("GIT_MIGRATION_PATH", "db/migration/V1__git_core.sql"),
		AutoMigrate:          envBool("GIT_AUTO_MIGRATE", false),
		PlatformURL:          strings.TrimRight(env("PLATFORM_SERVICE_URL", "http://127.0.0.1:8081"), "/"),
		StorageURL:           strings.TrimRight(env("STORAGE_SERVICE_URL", "http://127.0.0.1:8000"), "/"),
		InternalServiceToken: os.Getenv("PCD_INTERNAL_SERVICE_TOKEN"),
		// 标准公开克隆地址使用 /git；/api/v1/git 仍由 Gateway 兼容路由支持，部署环境可显式覆盖。
		HTTPCloneBaseURL:      strings.TrimRight(env("GIT_HTTP_CLONE_BASE_URL", "http://localhost:8080/git"), "/"),
		SSHCloneHost:          env("GIT_SSH_CLONE_HOST", "localhost"),
		SSHClonePort:          envInt("GIT_SSH_CLONE_PORT", 2222),
		GitBinary:             env("GIT_BINARY", "git"),
		MaxProtocolConcurrent: envInt("GIT_MAX_PROTOCOL_CONCURRENT", 64),
		// [REQ-GIT-AUDIT-2.12/6.12~6.15] 原协议入口只限制并发，恶意客户端可使用
		// 无限请求体、慢速 SSH 握手或持续失败认证占满进程；新配置分别限制 pack 请求、
		// receive-pack 回包缓冲、SSH 握手以及认证失败冷却。默认 2 GiB 与原单 Object
		// 上限一致，部署可按容量调低，不改变合法 Git CLI 的流式协议语义。
		MaxProtocolRequestBytes:  envInt64("GIT_MAX_PROTOCOL_REQUEST_BYTES", 2*1024*1024*1024),
		MaxProtocolResponseBytes: envInt64("GIT_MAX_PROTOCOL_RESPONSE_BYTES", 32*1024*1024),
		GitCommandTimeout:        envDuration("GIT_COMMAND_TIMEOUT", 10*time.Minute),
		SSHHandshakeTimeout:      envDuration("GIT_SSH_HANDSHAKE_TIMEOUT", 15*time.Second),
		AuthFailureLimit:         envInt("GIT_AUTH_FAILURE_LIMIT", 10),
		AuthFailureWindow:        envDuration("GIT_AUTH_FAILURE_WINDOW", 5*time.Minute),
		AuthFailureCooldown:      envDuration("GIT_AUTH_FAILURE_COOLDOWN", 15*time.Minute),
		MaxAPIOutputBytes:        envInt64("GIT_MAX_API_OUTPUT_BYTES", 4*1024*1024),
		// [REQ-GIT-UIUX-20260816] JSON Blob 预览仍严格使用 MaxAPIOutputBytes，
		// 原始文件预览/下载则使用独立上限，避免图片、PDF 和 ZIP 下载被 4 MiB 管理 API 限制截断。
		// 该上限只适用于单文件读取；对象写入仍受 MaxObjectBytes 约束。
		MaxRawFileBytes:       envInt64("GIT_MAX_RAW_FILE_BYTES", 128*1024*1024),
		MaxObjectBytes:        envInt64("GIT_MAX_OBJECT_BYTES", 2*1024*1024*1024),
		ObjectSyncConcurrency: envInt("GIT_OBJECT_SYNC_CONCURRENCY", 8),
		RabbitURL:             os.Getenv("GIT_RABBITMQ_URL"),
		EventExchange:         env("GIT_EVENT_EXCHANGE", "pcd.git.event.exchange"),
		WebhookTimeout:        envDuration("GIT_WEBHOOK_TIMEOUT", 5*time.Second),
		WebhookAllowHTTP:      envBool("GIT_WEBHOOK_ALLOW_HTTP", false),
	}
	if cfg.DatabaseDSN == "" {
		return Config{}, fmt.Errorf("GIT_DATABASE_DSN is required")
	}
	if cfg.InternalServiceToken == "" {
		return Config{}, fmt.Errorf("PCD_INTERNAL_SERVICE_TOKEN is required")
	}
	if cfg.MaxProtocolConcurrent < 1 || cfg.ObjectSyncConcurrency < 1 || cfg.MaxRawFileBytes < 1 ||
		cfg.MaxProtocolRequestBytes < 1 || cfg.MaxProtocolResponseBytes < 1 ||
		cfg.SSHHandshakeTimeout < time.Second || cfg.AuthFailureLimit < 1 ||
		cfg.AuthFailureWindow < time.Second || cfg.AuthFailureCooldown < time.Second {
		return Config{}, fmt.Errorf("Git concurrency must be positive")
	}
	return cfg, nil
}

func env(name, fallback string) string {
	if value := strings.TrimSpace(os.Getenv(name)); value != "" {
		return value
	}
	return fallback
}

func envBool(name string, fallback bool) bool {
	value := strings.TrimSpace(os.Getenv(name))
	if value == "" {
		return fallback
	}
	parsed, err := strconv.ParseBool(value)
	if err != nil {
		return fallback
	}
	return parsed
}

func envInt(name string, fallback int) int {
	value, err := strconv.Atoi(strings.TrimSpace(os.Getenv(name)))
	if err != nil {
		return fallback
	}
	return value
}

func envInt64(name string, fallback int64) int64 {
	value, err := strconv.ParseInt(strings.TrimSpace(os.Getenv(name)), 10, 64)
	if err != nil {
		return fallback
	}
	return value
}

func envDuration(name string, fallback time.Duration) time.Duration {
	value := strings.TrimSpace(os.Getenv(name))
	if value == "" {
		return fallback
	}
	parsed, err := time.ParseDuration(value)
	if err != nil {
		return fallback
	}
	return parsed
}
