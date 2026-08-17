package config

import (
	"fmt"
	"os"
	"time"

	"gopkg.in/yaml.v3"
)

// Config 应用配置
type Config struct {
	Server   ServerConfig   `yaml:"server"`
	Database DatabaseConfig `yaml:"database"`
	Redis    RedisConfig    `yaml:"redis"`
	Security SecurityConfig `yaml:"security"`
	Apple    AppleConfig    `yaml:"apple"`
}

// ServerConfig HTTP 服务器配置
type ServerConfig struct {
	Port         int           `yaml:"port"`
	ReadTimeout  time.Duration `yaml:"read_timeout"`
	WriteTimeout time.Duration `yaml:"write_timeout"`
	Mode         string        `yaml:"mode"` // debug / release / test
}

// DatabaseConfig MySQL 数据库配置
type DatabaseConfig struct {
	Host            string        `yaml:"host"`
	Port            int           `yaml:"port"`
	User            string        `yaml:"user"`
	Password        string        `yaml:"password"`
	Database        string        `yaml:"database"`
	MaxOpenConns    int           `yaml:"max_open_conns"`
	MaxIdleConns    int           `yaml:"max_idle_conns"`
	ConnMaxLifetime time.Duration `yaml:"conn_max_lifetime"`
}

// RedisConfig Redis 配置
type RedisConfig struct {
	Addr     string `yaml:"addr"`
	Password string `yaml:"password"`
	DB       int    `yaml:"db"`
	PoolSize int    `yaml:"pool_size"`
}

// SecurityConfig 安全配置
type SecurityConfig struct {
	// ChallengeTTL 挑战值有效期
	ChallengeTTL time.Duration `yaml:"challenge_ttl"`

	// AttestationTimestampWindow 证明时间戳有效窗口
	AttestationTimestampWindow time.Duration `yaml:"attestation_timestamp_window"`

	// PublicKeyCacheTTL 公钥 Redis 缓存有效期
	PublicKeyCacheTTL time.Duration `yaml:"public_key_cache_ttl"`

	// AllowedAppIDs 允许的应用 Bundle ID 列表
	AllowedAppIDs []string `yaml:"allowed_app_ids"`

	// InternalServiceToken 仅用于服务私网查询客户端绑定，不允许由公网客户端提交。
	InternalServiceToken string `yaml:"internal_service_token"`
}

// AppleConfig 苹果根证书配置
type AppleConfig struct {
	// RootCAPath 苹果根证书文件路径
	RootCAPath string `yaml:"root_ca_path"`

	// EnableAttestationVerification 是否启用证明在线验证
	EnableAttestationVerification bool `yaml:"enable_attestation_verification"`
}

// Load 从文件加载配置，并应用环境变量覆盖
func Load(path string) (*Config, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("读取配置文件失败: %w", err)
	}

	cfg := &Config{
		Server: ServerConfig{
			Port:         8089,
			ReadTimeout:  30 * time.Second,
			WriteTimeout: 30 * time.Second,
			Mode:         "release",
		},
		Database: DatabaseConfig{
			MaxOpenConns:    20,
			MaxIdleConns:    10,
			ConnMaxLifetime: 5 * time.Minute,
		},
		Redis: RedisConfig{
			PoolSize: 20,
		},
		Security: SecurityConfig{
			ChallengeTTL:               5 * time.Minute,
			AttestationTimestampWindow: 5 * time.Minute,
			PublicKeyCacheTTL:          24 * time.Hour,
			AllowedAppIDs: []string{
				"com.privateclouddisk.app",
			},
		},
		Apple: AppleConfig{
			RootCAPath:                    "config/apple_root_ca.pem",
			EnableAttestationVerification: true,
		},
	}

	if err := yaml.Unmarshal(data, cfg); err != nil {
		return nil, fmt.Errorf("解析配置文件失败: %w", err)
	}

	// 环境变量覆盖
	cfg.applyEnvOverrides()

	return cfg, nil
}

// DSN 返回 MySQL 数据源名称
func (d *DatabaseConfig) DSN() string {
	return fmt.Sprintf("%s:%s@tcp(%s:%d)/%s?charset=utf8mb4&parseTime=True&loc=Local",
		d.User, d.Password, d.Host, d.Port, d.Database)
}

func (c *Config) applyEnvOverrides() {
	if v := os.Getenv("SERVER_PORT"); v != "" {
		fmt.Sscanf(v, "%d", &c.Server.Port)
	}
	if v := os.Getenv("DB_HOST"); v != "" {
		c.Database.Host = v
	}
	if v := os.Getenv("DB_PORT"); v != "" {
		fmt.Sscanf(v, "%d", &c.Database.Port)
	}
	if v := os.Getenv("DB_USER"); v != "" {
		c.Database.User = v
	}
	if v := os.Getenv("DB_PASSWORD"); v != "" {
		c.Database.Password = v
	}
	if v := os.Getenv("DB_NAME"); v != "" {
		c.Database.Database = v
	}
	if v := os.Getenv("REDIS_ADDR"); v != "" {
		c.Redis.Addr = v
	}
	if v := os.Getenv("REDIS_PASSWORD"); v != "" {
		c.Redis.Password = v
	}
	if v := os.Getenv("GIN_MODE"); v != "" {
		c.Server.Mode = v
	}
	if v := os.Getenv("PCD_INTERNAL_SERVICE_TOKEN"); v != "" {
		c.Security.InternalServiceToken = v
	}
}
