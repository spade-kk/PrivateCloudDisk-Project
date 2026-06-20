// Package config 提供通知服务的统一配置管理。
// 支持从环境变量、配置文件加载配置，遵循 12-Factor App 原则。
package config

import (
	"fmt"
	"os"
	"strconv"
	"time"

	"gopkg.in/yaml.v3"
)

// Config 通知服务全局配置
type Config struct {
	Server   ServerConfig   `yaml:"server"`
	MySQL    MySQLConfig    `yaml:"mysql"`
	Redis    RedisConfig    `yaml:"redis"`
	RabbitMQ RabbitMQConfig `yaml:"rabbitmq"`
	APNs     APNsConfig     `yaml:"apns"`
	FCM      FCMConfig      `yaml:"fcm"`
	Email    EmailConfig    `yaml:"email"`
	SMS      SMSConfig      `yaml:"sms"`
	WeChatMP WeChatMPConfig `yaml:"wechat_mp"`
	AlipayMP AlipayMPConfig `yaml:"alipay_mp"`
	WebPush  WebPushConfig  `yaml:"webpush"`
	Worker   WorkerConfig   `yaml:"worker"`
}

// ServerConfig HTTP 服务配置
type ServerConfig struct {
	Host string `yaml:"host"`
	Port string `yaml:"port"`
	Mode string `yaml:"mode"`
	Name string `yaml:"name"`
}

// MySQLConfig MySQL 数据库配置
type MySQLConfig struct {
	Host            string        `yaml:"host"`
	Port            int           `yaml:"port"`
	User            string        `yaml:"user"`
	Password        string        `yaml:"password"`
	Database        string        `yaml:"database"`
	MaxOpenConns    int           `yaml:"max_open_conns"`
	MaxIdleConns    int           `yaml:"max_idle_conns"`
	ConnMaxLifetime time.Duration `yaml:"conn_max_lifetime"`
}

// DSN 返回 MySQL 连接字符串
func (c MySQLConfig) DSN() string {
	return fmt.Sprintf("%s:%s@tcp(%s:%d)/%s?charset=utf8mb4&parseTime=true&loc=Local",
		c.User, c.Password, c.Host, c.Port, c.Database)
}

// RedisConfig Redis 配置
type RedisConfig struct {
	Host     string `yaml:"host"`
	Port     int    `yaml:"port"`
	Password string `yaml:"password"`
	DB       int    `yaml:"db"`
}

// Addr 返回 Redis 地址
func (c RedisConfig) Addr() string {
	return fmt.Sprintf("%s:%d", c.Host, c.Port)
}

// RabbitMQConfig RabbitMQ 配置
type RabbitMQConfig struct {
	Host     string `yaml:"host"`
	Port     int    `yaml:"port"`
	User     string `yaml:"user"`
	Password string `yaml:"password"`
	VHost    string `yaml:"vhost"`
}

// URL 返回 RabbitMQ 连接 URL
func (c RabbitMQConfig) URL() string {
	return fmt.Sprintf("amqp://%s:%s@%s:%d/%s",
		c.User, c.Password, c.Host, c.Port, c.VHost)
}

// APNsConfig Apple Push Notification service 配置
type APNsConfig struct {
	Enabled    bool   `yaml:"enabled"`
	KeyPath    string `yaml:"key_path"`    // .p8 密钥文件路径
	KeyID      string `yaml:"key_id"`       // APNs Key ID
	TeamID     string `yaml:"team_id"`       // Apple Developer Team ID
	Topic      string `yaml:"topic"`         // Bundle ID
	Production bool   `yaml:"production"`    // true=生产环境, false=沙箱
}

// FCMConfig Firebase Cloud Messaging 配置
type FCMConfig struct {
	Enabled            bool   `yaml:"enabled"`
	CredentialsPath    string `yaml:"credentials_path"` // 服务账号 JSON 路径
}

// EmailConfig 邮件配置
type EmailConfig struct {
	Enabled     bool   `yaml:"enabled"`
	SMTPHost    string `yaml:"smtp_host"`
	SMTPPort    int    `yaml:"smtp_port"`
	Username    string `yaml:"username"`
	Password    string `yaml:"password"`
	FromName    string `yaml:"from_name"`
	FromAddr    string `yaml:"from_addr"`
	UseTLS      bool   `yaml:"use_tls"`
	FrontendURL string `yaml:"frontend_url"` // 前端地址，用于邮件中登录链接等
}

// SMSConfig 短信配置
type SMSConfig struct {
	Enabled     bool   `yaml:"enabled"`
	Provider    string `yaml:"provider"` // aliyun / tencent / generic
	BaseURL     string `yaml:"base_url"`
	APIKey      string `yaml:"api_key"`
	SignName    string `yaml:"sign_name"`
	TemplateID  string `yaml:"template_id"`
}

// WeChatMPConfig 微信小程序配置
type WeChatMPConfig struct {
	Enabled  bool   `yaml:"enabled"`
	AppID    string `yaml:"app_id"`
	AppSecret string `yaml:"app_secret"`
}

// AlipayMPConfig 支付宝小程序配置
type AlipayMPConfig struct {
	Enabled            bool   `yaml:"enabled"`
	AppID              string `yaml:"app_id"`
	PrivateKeyPath     string `yaml:"private_key_path"`
	AlipayPublicKeyPath string `yaml:"alipay_public_key_path"`
}

// WebPushConfig Web Push 配置
type WebPushConfig struct {
	Enabled      bool   `yaml:"enabled"`
	VAPIDPublic  string `yaml:"vapid_public_key"`
	VAPIDPrivate string `yaml:"vapid_private_key"`
	Subject      string `yaml:"subject"`
}

// WorkerConfig Worker 进程配置
type WorkerConfig struct {
	Concurrency               int `yaml:"concurrency"`
	PrefetchCount             int `yaml:"prefetch_count"`
	RetryMaxAttempts          int `yaml:"retry_max_attempts"`
	RetryBackoffMs            int `yaml:"retry_backoff_ms"`
	AggregationWindowSec      int `yaml:"aggregation_window_sec"`       // 消息聚合窗口（秒）
	MaxAggregationSize        int `yaml:"max_aggregation_size"`         // 单次聚合最大消息数
	AggregationCheckIntervalSec int `yaml:"aggregation_check_interval_sec"` // 聚合检查间隔（秒）
	LogRetentionDays          int `yaml:"log_retention_days"`           // 日志保留天数
}

// DefaultConfig 返回默认配置
func DefaultConfig() *Config {
	return &Config{
		Server: ServerConfig{
			Host: envStr("NOTIFICATION_SERVICE_HOST", "0.0.0.0"),
			Port: envStr("NOTIFICATION_SERVICE_PORT", "8002"),
			Mode: envStr("GIN_MODE", "release"),
			Name: "PrivateCloudDisk Notification Service",
		},
		MySQL: MySQLConfig{
			Host:            envStr("MYSQL_HOST", "localhost"),
			Port:            envInt("MYSQL_PORT", 3307),
			User:            envStr("MYSQL_USER", "root"),
			Password:        envStr("MYSQL_PASSWORD", "123456"),
			Database:        envStr("MYSQL_DATABASE", "private_cloud_disk"),
			MaxOpenConns:    envInt("MYSQL_MAX_OPEN_CONNS", 25),
			MaxIdleConns:    envInt("MYSQL_MAX_IDLE_CONNS", 10),
			ConnMaxLifetime: time.Duration(envInt("MYSQL_CONN_MAX_LIFETIME", 300)) * time.Second,
		},
		Redis: RedisConfig{
			Host:     envStr("REDIS_HOST", "localhost"),
			Port:     envInt("REDIS_PORT", 6389),
			Password: envStr("REDIS_PASSWORD", ""),
			DB:       envInt("REDIS_DB", 0),
		},
		RabbitMQ: RabbitMQConfig{
			Host:     envStr("RABBITMQ_HOST", "localhost"),
			Port:     envInt("RABBITMQ_PORT", 5673),
			User:     envStr("RABBITMQ_USER", "guest"),
			Password: envStr("RABBITMQ_PASSWORD", "guest"),
			VHost:    envStr("RABBITMQ_VHOST", "/"),
		},
		APNs: APNsConfig{
			Enabled:    envBool("APNS_ENABLED", false),
			KeyPath:    envStr("APNS_KEY_PATH", ""),
			KeyID:      envStr("APNS_KEY_ID", ""),
			TeamID:     envStr("APNS_TEAM_ID", ""),
			Topic:      envStr("APNS_TOPIC", ""),
			Production: envBool("APNS_PRODUCTION", false),
		},
		FCM: FCMConfig{
			Enabled:         envBool("FCM_ENABLED", false),
			CredentialsPath: envStr("FCM_CREDENTIALS_PATH", ""),
		},
		Email: EmailConfig{
			Enabled:     envBool("EMAIL_ENABLED", false),
			SMTPHost:    envStr("EMAIL_SMTP_HOST", ""),
			SMTPPort:    envInt("EMAIL_SMTP_PORT", 587),
			Username:    envStr("EMAIL_USERNAME", ""),
			Password:    envStr("EMAIL_PASSWORD", ""),
			FromName:    envStr("EMAIL_FROM_NAME", "私有云"),
			FromAddr:    envStr("EMAIL_FROM_ADDR", ""),
			UseTLS:      envBool("EMAIL_USE_TLS", true),
			FrontendURL: envStr("FRONTEND_URL", "https://privateclouddisk.com"),
		},
		SMS: SMSConfig{
			Enabled:    envBool("SMS_ENABLED", false),
			Provider:   envStr("SMS_PROVIDER", "generic"),
			BaseURL:    envStr("SMS_BASE_URL", ""),
			APIKey:     envStr("SMS_API_KEY", ""),
			SignName:   envStr("SMS_SIGN_NAME", ""),
			TemplateID: envStr("SMS_TEMPLATE_ID", ""),
		},
		WeChatMP: WeChatMPConfig{
			Enabled:   envBool("WECHAT_MP_ENABLED", false),
			AppID:     envStr("WECHAT_MP_APP_ID", ""),
			AppSecret: envStr("WECHAT_MP_APP_SECRET", ""),
		},
		AlipayMP: AlipayMPConfig{
			Enabled:            envBool("ALIPAY_MP_ENABLED", false),
			AppID:              envStr("ALIPAY_MP_APP_ID", ""),
			PrivateKeyPath:     envStr("ALIPAY_MP_PRIVATE_KEY_PATH", ""),
			AlipayPublicKeyPath: envStr("ALIPAY_MP_PUBLIC_KEY_PATH", ""),
		},
		WebPush: WebPushConfig{
			Enabled:      envBool("WEBPUSH_ENABLED", false),
			VAPIDPublic:  envStr("WEBPUSH_VAPID_PUBLIC", ""),
			VAPIDPrivate: envStr("WEBPUSH_VAPID_PRIVATE", ""),
			Subject:      envStr("WEBPUSH_SUBJECT", ""),
		},
		Worker: WorkerConfig{
			Concurrency:                envInt("WORKER_CONCURRENCY", 10),
			PrefetchCount:              envInt("WORKER_PREFETCH_COUNT", 5),
			RetryMaxAttempts:           envInt("WORKER_RETRY_MAX_ATTEMPTS", 3),
			RetryBackoffMs:             envInt("WORKER_RETRY_BACKOFF_MS", 5000),
			AggregationWindowSec:       envInt("AGGREGATION_WINDOW_SEC", 300),
			MaxAggregationSize:         envInt("MAX_AGGREGATION_SIZE", 10),
			AggregationCheckIntervalSec: envInt("AGGREGATION_CHECK_INTERVAL_SEC", 30),
			LogRetentionDays:           envInt("LOG_RETENTION_DAYS", 90),
		},
	}
}

// Load 从 YAML 文件加载配置（环境变量优先级更高）
func Load() (*Config, error) {
	return LoadConfig("config/config.yaml")
}

// LoadConfig 从指定 YAML 文件加载配置
func LoadConfig(path string) (*Config, error) {
	cfg := DefaultConfig()

	if path != "" {
		data, err := os.ReadFile(path)
		if err != nil {
			// 配置文件不存在时使用默认配置
			return cfg, nil
		}
		if err := yaml.Unmarshal(data, cfg); err != nil {
			return nil, fmt.Errorf("解析配置文件失败: %w", err)
		}
	}

	return cfg, nil
}

// =============================================================================
// 环境变量辅助函数
// =============================================================================
func envStr(key, defaultVal string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return defaultVal
}

func envInt(key string, defaultVal int) int {
	if v := os.Getenv(key); v != "" {
		if i, err := strconv.Atoi(v); err == nil {
			return i
		}
	}
	return defaultVal
}

func envBool(key string, defaultVal bool) bool {
	if v := os.Getenv(key); v != "" {
		if b, err := strconv.ParseBool(v); err == nil {
			return b
		}
	}
	return defaultVal
}