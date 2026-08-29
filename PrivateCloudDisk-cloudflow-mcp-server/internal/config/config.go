// Package config contains the deliberately small configuration surface of the
// server-only CloudFlow MCP gateway.  It never contains database, storage,
// plugin, or workflow-service connection settings: Capability Hub is the only
// allowed enterprise capability dependency.
package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

type Config struct {
	ListenAddress             string
	CapabilityHubURL          string
	InternalServiceToken      string
	IdentitySharedSecret      string
	IdentityMaxAge            time.Duration
	RequestTimeout            time.Duration
	AuditTimeout              time.Duration
	ToolListCacheTTL          time.Duration
	SessionTTL                time.Duration
	MaxBodyBytes              int64
	MaxConcurrentRequests     int
	RequestsPerMinutePerUser  int
	MetricsEnabled            bool
	OAuthAuthorizationServers []string
	OTLPEndpoint              string
	OTLPInsecure              bool
	Version                   string
}

func Load() (Config, error) {
	cfg := Config{
		ListenAddress:             env("MCP_LISTEN_ADDRESS", ":8093"),
		CapabilityHubURL:          strings.TrimRight(env("MCP_CAPABILITY_HUB_URL", ""), "/"),
		InternalServiceToken:      env("MCP_INTERNAL_SERVICE_TOKEN", ""),
		IdentitySharedSecret:      env("MCP_IDENTITY_SHARED_SECRET", ""),
		IdentityMaxAge:            seconds("MCP_IDENTITY_MAX_AGE_SECONDS", 120),
		RequestTimeout:            seconds("MCP_REQUEST_TIMEOUT_SECONDS", 30),
		AuditTimeout:              seconds("MCP_AUDIT_TIMEOUT_SECONDS", 3),
		ToolListCacheTTL:          seconds("MCP_TOOL_LIST_CACHE_TTL_SECONDS", 300),
		SessionTTL:                seconds("MCP_SESSION_TTL_SECONDS", 1800),
		MaxBodyBytes:              int64(integer("MCP_MAX_BODY_BYTES", 1_048_576)),
		MaxConcurrentRequests:     integer("MCP_MAX_CONCURRENT_REQUESTS", 128),
		RequestsPerMinutePerUser:  integer("MCP_REQUESTS_PER_MINUTE_PER_USER", 120),
		MetricsEnabled:            boolean("MCP_METRICS_ENABLED", true),
		OAuthAuthorizationServers: csv("MCP_OAUTH_AUTHORIZATION_SERVERS"),
		OTLPEndpoint:              env("MCP_OTEL_EXPORTER_OTLP_ENDPOINT", ""),
		OTLPInsecure:              boolean("MCP_OTEL_EXPORTER_OTLP_INSECURE", false),
		Version:                   env("MCP_SERVER_VERSION", "0.1.0"),
	}
	if cfg.CapabilityHubURL == "" {
		return Config{}, fmt.Errorf("MCP_CAPABILITY_HUB_URL is required")
	}
	if cfg.InternalServiceToken == "" {
		return Config{}, fmt.Errorf("MCP_INTERNAL_SERVICE_TOKEN is required")
	}
	if len(cfg.IdentitySharedSecret) < 32 {
		return Config{}, fmt.Errorf("MCP_IDENTITY_SHARED_SECRET must be at least 32 bytes")
	}
	if cfg.MaxBodyBytes < 1024 || cfg.MaxBodyBytes > 16*1024*1024 {
		return Config{}, fmt.Errorf("MCP_MAX_BODY_BYTES must be between 1024 and 16777216")
	}
	if cfg.MaxConcurrentRequests < 1 || cfg.MaxConcurrentRequests > 4096 {
		return Config{}, fmt.Errorf("MCP_MAX_CONCURRENT_REQUESTS must be between 1 and 4096")
	}
	if cfg.RequestsPerMinutePerUser < 1 {
		return Config{}, fmt.Errorf("MCP_REQUESTS_PER_MINUTE_PER_USER must be positive")
	}
	return cfg, nil
}

func env(key, fallback string) string {
	if value := strings.TrimSpace(os.Getenv(key)); value != "" {
		return value
	}
	return fallback
}

func integer(key string, fallback int) int {
	value := env(key, "")
	if value == "" {
		return fallback
	}
	parsed, err := strconv.Atoi(value)
	if err != nil {
		return fallback
	}
	return parsed
}

func seconds(key string, fallback int) time.Duration {
	return time.Duration(integer(key, fallback)) * time.Second
}

func boolean(key string, fallback bool) bool {
	value := env(key, "")
	if value == "" {
		return fallback
	}
	parsed, err := strconv.ParseBool(value)
	if err != nil {
		return fallback
	}
	return parsed
}

func csv(key string) []string {
	value := env(key, "")
	if value == "" {
		return nil
	}
	result := make([]string, 0)
	for _, item := range strings.Split(value, ",") {
		if item = strings.TrimSpace(item); item != "" {
			result = append(result, item)
		}
	}
	return result
}
