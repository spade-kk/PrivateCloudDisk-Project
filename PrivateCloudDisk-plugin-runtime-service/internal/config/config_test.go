package config

import (
	"strings"
	"testing"
	"time"
)

// baseEnv 是每个用例重置的基线（development 安全值），随后用例覆盖个别键。
var baseEnv = map[string]string{
	"RUNTIME_ENV":                            "development",
	"RUNTIME_LISTEN_ADDRESS":                 ":8090",
	"PCD_INTERNAL_SERVICE_TOKEN":             "test-token",
	"PLUGIN_SERVICE_URL":                     "http://plugin:8085",
	"STORAGE_BROKER_URL":                     "http://broker:8000",
	"CAPABILITY_HUB_URL":                     "http://workflow:8087",
	"RUNTIME_WORK_ROOT":                      "/tmp/pcd-runtime/work",
	"RUNTIME_SOCKET_ROOT":                    "/tmp/pcd-runtime/sockets",
	"RUNTIME_SOCKET_GROUP_ID":                "65532",
	"RUNTIME_SOCKET_MAX_FRAME_BYTES":         "1048576",
	"RUNTIME_SOCKET_MAX_CONNECTIONS":         "16",
	"RUNTIME_SOCKET_REQUESTS_PER_SECOND":     "100",
	"RUNTIME_SOCKET_REQUEST_BURST":           "200",
	"RUNTIME_SOCKET_REQUEST_TIMEOUT_SECONDS": "20",
	"RUNTIME_DOCKER_BINARY":                  "docker",
	"PLUGIN_SANDBOX_IMAGE":                   "pcd/plugin-sandbox-python:0.1.0",
	"PLUGIN_SANDBOX_IMAGE_DIGEST":            "",
	"PLUGIN_SANDBOX_REQUIRE_DIGEST":          "false",
	"PLUGIN_SANDBOX_RUNTIME":                 "runc",
	"PLUGIN_SANDBOX_SECCOMP_PROFILE":         "",
	"PLUGIN_SANDBOX_APPARMOR_PROFILE":        "",
	"PLUGIN_SANDBOX_NETWORK":                 "none",
	"PLUGIN_SANDBOX_USER":                    "65532:65532",
	"PLUGIN_SANDBOX_USERNS_REMAP":            "false",
	"PLUGIN_SANDBOX_MAX_RETRIES":             "0",
	"PLUGIN_RUNTIME_AUDIT_LOG":               "",
	"PLUGIN_SANDBOX_SANITIZE_RULES":          "",
	"RUNTIME_DEBUG":                          "false",
	"PLUGIN_RUNTIME_VERSION":                 "0.2.0",
	"PLUGIN_RUNTIME_TIMEOUT_SECONDS":         "120",
	"PLUGIN_VALIDATION_TIMEOUT_SECONDS":      "5",
	"PLUGIN_SANDBOX_MEMORY_BYTES":            "536870912",
	"PLUGIN_SANDBOX_CPUS":                    "1.0",
	"PLUGIN_SANDBOX_PIDS_LIMIT":              "64",
	"PLUGIN_LOG_LIMIT_BYTES":                 "102400",
	"PLUGIN_PACKAGE_MAX_BYTES":               "10485760",
	"PLUGIN_CANDIDATE_MAX_BYTES":             "10737418240",
	"PLUGIN_RUNTIME_CONCURRENCY":             "16",
}

func loadWith(t *testing.T, overrides map[string]string) (Config, error) {
	t.Helper()
	for key, value := range baseEnv {
		t.Setenv(key, value)
	}
	for key, value := range overrides {
		t.Setenv(key, value)
	}
	return Load()
}

func TestLoadDefaults(t *testing.T) {
	cfg, err := loadWith(t, nil)
	if err != nil {
		t.Fatalf("development 基线应加载成功：%v", err)
	}
	if cfg.Environment != "development" {
		t.Fatalf("默认环境应为 development：%q", cfg.Environment)
	}
	if cfg.SandboxRuntime != "runc" {
		t.Fatalf("开发默认应使用 runc：%q", cfg.SandboxRuntime)
	}
	if cfg.SandboxNetwork != "none" {
		t.Fatalf("沙箱默认应无网络：%q", cfg.SandboxNetwork)
	}
	if cfg.MemoryBytes != 512*1024*1024 || cfg.PidsLimit != 64 || cfg.CPUs != "1.0" {
		t.Fatalf("默认资源边界错误：%+v", cfg)
	}
	if cfg.ExecutionTimeout != 120*time.Second {
		t.Fatalf("默认执行超时应为 120s：%v", cfg.ExecutionTimeout)
	}
	if cfg.Concurrency != 16 {
		t.Fatalf("默认并发度应为 16：%d", cfg.Concurrency)
	}
	if cfg.CapabilityHubURL != "http://workflow:8087" || cfg.SocketRoot != "/tmp/pcd-runtime/sockets" ||
		cfg.SocketMaxFrameBytes != 1024*1024 || cfg.SocketRequestTimeout != 20*time.Second {
		t.Fatalf("UDS/Capability Hub 默认配置错误：%+v", cfg)
	}
}

func TestLoadEnvOverrides(t *testing.T) {
	cfg, err := loadWith(t, map[string]string{
		"RUNTIME_DEBUG":                            "true",
		"PLUGIN_SANDBOX_USERNS_REMAP":              "true",
		"PLUGIN_SANDBOX_MAX_RETRIES":               "2",
		"PLUGIN_SANDBOX_REQUIRE_DIGEST":            "true",
		"PLUGIN_SANDBOX_IMAGE_DIGEST":              "sha256:abc123",
		"PLUGIN_SANDBOX_RUNTIME":                   "runsc",
		"PLUGIN_SANDBOX_SECCOMP_PROFILE":           "/etc/seccomp.json",
		"PLUGIN_SANDBOX_APPARMOR_PROFILE":          "pcd-guest",
		"PLUGIN_SANDBOX_USER":                      "1001:1001",
		"PLUGIN_SANDBOX_SANITIZE_RULES":            "foo=bar;baz",
		"PLUGIN_SANDBOX_DISABLE_RESTRICTED_PYTHON": "true",
		"PLUGIN_RUNTIME_CONCURRENCY":               "8",
	})
	if err != nil {
		t.Fatalf("覆盖项应加载成功：%v", err)
	}
	if !cfg.DebugMode || !cfg.UserNamespaceRemap || cfg.MaxExecutionRetries != 2 {
		t.Fatalf("布尔/整数覆盖未生效：%+v", cfg)
	}
	if !cfg.RequireSandboxDigest || cfg.SandboxImageDigest != "sha256:abc123" {
		t.Fatalf("摘要门禁覆盖未生效：%+v", cfg)
	}
	if cfg.SandboxRuntime != "runsc" || cfg.SeccompProfile != "/etc/seccomp.json" ||
		cfg.AppArmorProfile != "pcd-guest" {
		t.Fatalf("加固 profile 覆盖未生效：%+v", cfg)
	}
	if len(cfg.SanitizeRules) != 2 || cfg.SanitizeRules[0] != "foo=bar" {
		t.Fatalf("脱敏规则切分错误：%v", cfg.SanitizeRules)
	}
	if cfg.Concurrency != 8 {
		t.Fatalf("并发度覆盖未生效：%d", cfg.Concurrency)
	}
	if cfg.SandboxUser != "1001:1001" {
		t.Fatalf("沙箱用户覆盖未生效：%q", cfg.SandboxUser)
	}
	if !cfg.DisableRestrictedPython {
		t.Fatalf("受限 Python 开关覆盖未生效：%+v", cfg)
	}
}

func TestLoadRestrictedPythonDefaultOn(t *testing.T) {
	cfg, err := loadWith(t, map[string]string{})
	if err != nil {
		t.Fatalf("默认加载应成功：%v", err)
	}
	if cfg.DisableRestrictedPython {
		t.Fatal("受限 Python 层默认必须开启")
	}
}

func TestLoadSecurityGates(t *testing.T) {
	for _, td := range []struct {
		name      string
		overrides map[string]string
		contains  string
	}{
		{name: "缺服务令牌", overrides: map[string]string{"PCD_INTERNAL_SERVICE_TOKEN": ""}, contains: "PCD_INTERNAL_SERVICE_TOKEN"},
		{name: "生产必须 runsc", overrides: map[string]string{"RUNTIME_ENV": "production", "PLUGIN_SANDBOX_RUNTIME": "runc"}, contains: "runsc"},
		{name: "生产必须 seccomp", overrides: map[string]string{"RUNTIME_ENV": "production", "PLUGIN_SANDBOX_RUNTIME": "runsc", "PLUGIN_SANDBOX_SECCOMP_PROFILE": ""}, contains: "seccomp"},
		{name: "生产必须 AppArmor", overrides: map[string]string{"RUNTIME_ENV": "production", "PLUGIN_SANDBOX_RUNTIME": "runsc", "PLUGIN_SANDBOX_SECCOMP_PROFILE": "/etc/s.json", "PLUGIN_SANDBOX_APPARMOR_PROFILE": ""}, contains: "AppArmor"},
		{name: "生产禁 Debug", overrides: map[string]string{"RUNTIME_ENV": "production", "RUNTIME_DEBUG": "true", "PLUGIN_SANDBOX_RUNTIME": "runsc", "PLUGIN_SANDBOX_SECCOMP_PROFILE": "/etc/s.json", "PLUGIN_SANDBOX_APPARMOR_PROFILE": "p"}, contains: "RUNTIME_DEBUG"},
		{name: "生产必须无网络", overrides: map[string]string{"RUNTIME_ENV": "production", "RUNTIME_DEBUG": "false", "PLUGIN_SANDBOX_RUNTIME": "runsc", "PLUGIN_SANDBOX_SECCOMP_PROFILE": "/etc/s.json", "PLUGIN_SANDBOX_APPARMOR_PROFILE": "p", "PLUGIN_SANDBOX_NETWORK": "bridge"}, contains: "无网络"},
		{name: "生产禁止关闭受限 Python", overrides: map[string]string{"RUNTIME_ENV": "production", "PLUGIN_SANDBOX_RUNTIME": "runsc", "PLUGIN_SANDBOX_SECCOMP_PROFILE": "/etc/s.json", "PLUGIN_SANDBOX_APPARMOR_PROFILE": "p", "PLUGIN_SANDBOX_DISABLE_RESTRICTED_PYTHON": "true"}, contains: "受限 Python"},
		{name: "摘要门禁需 sha256 前缀", overrides: map[string]string{"PLUGIN_SANDBOX_REQUIRE_DIGEST": "true", "PLUGIN_SANDBOX_IMAGE_DIGEST": "abc"}, contains: "sha256:"},
		{name: "WorkRoot 必须绝对路径", overrides: map[string]string{"RUNTIME_WORK_ROOT": "relative"}, contains: "绝对路径"},
		{name: "SocketRoot 必须绝对路径", overrides: map[string]string{"RUNTIME_SOCKET_ROOT": "relative"}, contains: "绝对路径"},
		{name: "生产必须 Capability Hub", overrides: map[string]string{"RUNTIME_ENV": "production", "CAPABILITY_HUB_URL": "", "PLUGIN_SANDBOX_RUNTIME": "runsc", "PLUGIN_SANDBOX_SECCOMP_PROFILE": "/etc/s.json", "PLUGIN_SANDBOX_APPARMOR_PROFILE": "p"}, contains: "CAPABILITY_HUB_URL"},
		{name: "并发度越界", overrides: map[string]string{"PLUGIN_RUNTIME_CONCURRENCY": "99999"}, contains: "并发度"},
		{name: "沙箱用户格式非法", overrides: map[string]string{"PLUGIN_SANDBOX_USER": "1001"}, contains: "uid:gid"},
	} {
		t.Run(td.name, func(t *testing.T) {
			_, err := loadWith(t, td.overrides)
			if err == nil {
				t.Fatalf("%s 应被拒绝", td.name)
			}
			if !strings.Contains(err.Error(), td.contains) {
				t.Fatalf("错误应含 %q，实际 %q", td.contains, err.Error())
			}
		})
	}
}

func TestLoadRetryBoundary(t *testing.T) {
	for _, value := range []string{"-1", "4"} {
		if _, err := loadWith(t, map[string]string{"PLUGIN_SANDBOX_MAX_RETRIES": value}); err == nil {
			t.Fatalf("重试次数 %s 应越界被拒", value)
		}
	}
	if cfg, err := loadWith(t, map[string]string{"PLUGIN_SANDBOX_MAX_RETRIES": "3"}); err != nil || cfg.MaxExecutionRetries != 3 {
		t.Fatalf("重试次数 3 应合法：%+v err=%v", cfg, err)
	}
}

func TestLoadTimeoutBoundary(t *testing.T) {
	for _, value := range []string{"0", "601"} {
		if _, err := loadWith(t, map[string]string{"PLUGIN_RUNTIME_TIMEOUT_SECONDS": value}); err == nil {
			t.Fatalf("超时 %s 应越界被拒", value)
		}
	}
}

func TestSplitList(t *testing.T) {
	if got := splitList(" a ;b;; c "); len(got) != 3 || got[0] != "a" || got[2] != "c" {
		t.Fatalf("splitList 结果错误：%v", got)
	}
	if got := splitList(""); len(got) != 0 {
		t.Fatalf("空串应返回空列表：%v", got)
	}
}
