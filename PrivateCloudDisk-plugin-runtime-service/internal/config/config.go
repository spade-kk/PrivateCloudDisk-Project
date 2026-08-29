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
	// CapabilityHubURL 是 Runtime Agent 到受信 Capability Hub 的唯一网络出口。
	// [CF-PLUGIN-UDS-001] 插件容器始终保持 network=none，不能直接使用该地址。
	CapabilityHubURL string
	WorkRoot         string
	// SocketRoot 保存每个插件实例独占的 UDS 文件；生产默认 /run/pcd/plugins。
	// SocketGroupID 必须与插件沙箱的 gid 一致，确保仅本实例容器能够连接其挂载 socket。
	SocketRoot           string
	SocketGroupID        int
	SocketMaxFrameBytes  int
	SocketMaxConnections int
	SocketRequestsPerSec int
	SocketRequestBurst   int
	SocketRequestTimeout time.Duration
	DockerBinary         string
	SandboxImage         string
	SandboxImageDigest   string
	RequireSandboxDigest bool
	SandboxRuntime       string
	SeccompProfile       string
	AppArmorProfile      string
	SandboxNetwork       string
	SandboxUser          string
	UserNamespaceRemap   bool
	// DisableRestrictedPython 为 true 时容器内关闭受限 Python 层（仅测试探针，
	// 生产模式禁止，见下方门禁）。
	DisableRestrictedPython bool
	MaxExecutionRetries     int
	AuditLogPath            string
	SanitizeRules           []string
	DebugMode               bool
	Version                 string
	ExecutionTimeout        time.Duration
	ValidationTimeout       time.Duration
	MemoryBytes             int64
	CPUs                    string
	PidsLimit               int
	LogLimitBytes           int64
	PackageMaxBytes         int64
	CandidateMaxBytes       int64
	Concurrency             int
}

func Load() (Config, error) {
	workRoot := env("RUNTIME_WORK_ROOT", "/Users/user/Desktop/pcd-runtime/work")
	environment := strings.ToLower(env("RUNTIME_ENV", "development"))
	socketRoot := os.Getenv("RUNTIME_SOCKET_ROOT")
	if socketRoot == "" {
		if environment == "production" {
			socketRoot = "/run/pcd/plugins"
		} else {
			socketRoot = filepath.Join(workRoot, "sockets")
		}
	}
	cfg := Config{
		ListenAddress: env("RUNTIME_LISTEN_ADDRESS", ":8090"),
		Environment:   environment,
		// 安全基线：不再把服务令牌写入代码默认值；开发/测试也必须显式注入。
		InternalServiceToken:    os.Getenv("PCD_INTERNAL_SERVICE_TOKEN"),
		PluginServiceURL:        strings.TrimRight(env("PLUGIN_SERVICE_URL", "http://localhost:8085"), "/"),
		StorageBrokerURL:        strings.TrimRight(env("STORAGE_BROKER_URL", "http://localhost:8000"), "/"),
		CapabilityHubURL:        strings.TrimRight(os.Getenv("CAPABILITY_HUB_URL"), "/"),
		WorkRoot:                workRoot,
		SocketRoot:              socketRoot,
		SocketGroupID:           integer("RUNTIME_SOCKET_GROUP_ID", 65532),
		SocketMaxFrameBytes:     integer("RUNTIME_SOCKET_MAX_FRAME_BYTES", 1024*1024),
		SocketMaxConnections:    integer("RUNTIME_SOCKET_MAX_CONNECTIONS", 16),
		SocketRequestsPerSec:    integer("RUNTIME_SOCKET_REQUESTS_PER_SECOND", 100),
		SocketRequestBurst:      integer("RUNTIME_SOCKET_REQUEST_BURST", 200),
		SocketRequestTimeout:    seconds("RUNTIME_SOCKET_REQUEST_TIMEOUT_SECONDS", 20),
		DockerBinary:            env("RUNTIME_DOCKER_BINARY", "docker"),
		SandboxImage:            env("PLUGIN_SANDBOX_IMAGE", "pcd/plugin-sandbox-python:0.1.2"),
		SandboxImageDigest:      os.Getenv("PLUGIN_SANDBOX_IMAGE_DIGEST"),
		RequireSandboxDigest:    env("PLUGIN_SANDBOX_REQUIRE_DIGEST", "false") == "true",
		SandboxRuntime:          env("PLUGIN_SANDBOX_RUNTIME", "runsc"),
		SeccompProfile:          os.Getenv("PLUGIN_SANDBOX_SECCOMP_PROFILE"),
		AppArmorProfile:         os.Getenv("PLUGIN_SANDBOX_APPARMOR_PROFILE"),
		SandboxNetwork:          env("PLUGIN_SANDBOX_NETWORK", "none"),
		SandboxUser:             env("PLUGIN_SANDBOX_USER", "65532:65532"),
		UserNamespaceRemap:      env("PLUGIN_SANDBOX_USERNS_REMAP", "false") == "true",
		DisableRestrictedPython: env("PLUGIN_SANDBOX_DISABLE_RESTRICTED_PYTHON", "false") == "true",
		MaxExecutionRetries:     integer("PLUGIN_SANDBOX_MAX_RETRIES", 0),
		AuditLogPath:            os.Getenv("PLUGIN_RUNTIME_AUDIT_LOG"),
		SanitizeRules:           splitList(os.Getenv("PLUGIN_SANDBOX_SANITIZE_RULES")),
		DebugMode:               env("RUNTIME_DEBUG", "false") == "true",
		Version:                 env("PLUGIN_RUNTIME_VERSION", "0.2.0"),
		ExecutionTimeout:        seconds("PLUGIN_RUNTIME_TIMEOUT_SECONDS", 120),
		ValidationTimeout:       seconds("PLUGIN_VALIDATION_TIMEOUT_SECONDS", 5),
		MemoryBytes:             integer64("PLUGIN_SANDBOX_MEMORY_BYTES", 512*1024*1024),
		CPUs:                    env("PLUGIN_SANDBOX_CPUS", "1.0"),
		PidsLimit:               integer("PLUGIN_SANDBOX_PIDS_LIMIT", 64),
		LogLimitBytes:           integer64("PLUGIN_LOG_LIMIT_BYTES", 100*1024),
		PackageMaxBytes:         integer64("PLUGIN_PACKAGE_MAX_BYTES", 10*1024*1024),
		CandidateMaxBytes:       integer64("PLUGIN_CANDIDATE_MAX_BYTES", 10*1024*1024*1024),
		Concurrency:             integer("PLUGIN_RUNTIME_CONCURRENCY", 16),
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
	if cfg.Environment == "production" && cfg.DebugMode {
		return Config{}, errors.New("生产环境禁止开启 RUNTIME_DEBUG")
	}
	if cfg.Environment == "production" && cfg.SandboxNetwork != "none" {
		return Config{}, errors.New("生产环境必须使用无网络沙箱（PLUGIN_SANDBOX_NETWORK=none）")
	}
	if cfg.Environment == "production" && cfg.DisableRestrictedPython {
		return Config{}, errors.New("生产环境禁止关闭受限 Python 层（PLUGIN_SANDBOX_DISABLE_RESTRICTED_PYTHON 必须为 false）")
	}
	if cfg.MaxExecutionRetries < 0 || cfg.MaxExecutionRetries > 3 {
		return Config{}, errors.New("插件沙箱重试次数必须在 0 到 3 之间")
	}
	if cfg.RequireSandboxDigest && !strings.HasPrefix(cfg.SandboxImageDigest, "sha256:") {
		return Config{}, errors.New("启用镜像摘要门禁时必须提供以 sha256: 开头的摘要")
	}
	if !filepath.IsAbs(cfg.WorkRoot) {
		return Config{}, errors.New("RUNTIME_WORK_ROOT 必须是绝对路径")
	}
	if !filepath.IsAbs(cfg.SocketRoot) {
		return Config{}, errors.New("RUNTIME_SOCKET_ROOT 必须是绝对路径")
	}
	if cfg.Environment == "production" && cfg.CapabilityHubURL == "" {
		return Config{}, errors.New("生产环境必须配置 CAPABILITY_HUB_URL，插件能力调用不允许回退到文件通道")
	}
	if cfg.SocketGroupID < 0 || cfg.SocketMaxFrameBytes < 1024 || cfg.SocketMaxFrameBytes > 4*1024*1024 ||
		cfg.SocketMaxConnections < 1 || cfg.SocketMaxConnections > 128 || cfg.SocketRequestsPerSec < 1 ||
		cfg.SocketRequestsPerSec > 100000 || cfg.SocketRequestBurst < 1 || cfg.SocketRequestBurst > 100000 ||
		cfg.SocketRequestTimeout <= 0 || cfg.SocketRequestTimeout > 2*time.Minute {
		return Config{}, errors.New("Runtime Unix Socket 配置越界")
	}
	if cfg.ExecutionTimeout <= 0 || cfg.ExecutionTimeout > 10*time.Minute {
		return Config{}, errors.New("插件执行超时配置越界")
	}
	if cfg.Concurrency < 1 || cfg.Concurrency > 256 {
		return Config{}, errors.New("插件 Runtime 并发度必须在 1 到 256 之间")
	}
	if strings.Count(cfg.SandboxUser, ":") != 1 {
		return Config{}, errors.New("PLUGIN_SANDBOX_USER 必须是 uid:gid 格式")
	}
	return cfg, nil
}

// splitList 以分号切分配置列表（如脱敏追加规则）。
func splitList(value string) []string {
	var out []string
	for _, part := range strings.Split(value, ";") {
		part = strings.TrimSpace(part)
		if part != "" {
			out = append(out, part)
		}
	}
	return out
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
