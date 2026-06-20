package config

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"time"

	"github.com/spf13/viper"
)

// Config 全局配置
type Config struct {
	Endpoint string `json:"endpoint" mapstructure:"endpoint"`
	Token    string `json:"token" mapstructure:"token"`
	UserID   string `json:"user_id" mapstructure:"user_id"`
	Account  string `json:"account" mapstructure:"account"`

	// 上传配置
	ChunkSizeMB     int `json:"chunk_size_mb" mapstructure:"chunk_size_mb"`
	MaxConcurrency  int `json:"max_concurrency" mapstructure:"max_concurrency"`
	MaxRetries      int `json:"max_retries" mapstructure:"max_retries"`
	RetryBackoffMs  int `json:"retry_backoff_ms" mapstructure:"retry_backoff_ms"`

	// 下载配置
	DownloadWorkers int `json:"download_workers" mapstructure:"download_workers"`

	// 同步配置
	SyncIntervalS int  `json:"sync_interval_s" mapstructure:"sync_interval_s"`
	DryRun        bool `json:"dry_run" mapstructure:"dry_run"`
}

// DefaultConfig 默认配置
func DefaultConfig() *Config {
	return &Config{
		Endpoint:        "http://localhost:8080",
		ChunkSizeMB:     10,
		MaxConcurrency:  4,
		MaxRetries:      3,
		RetryBackoffMs:  1000,
		DownloadWorkers: 4,
		SyncIntervalS:   60,
		DryRun:          false,
	}
}

// ConfigDir 配置文件目录
func ConfigDir() string {
	home, _ := os.UserHomeDir()
	dir := filepath.Join(home, ".cloud-cli")
	os.MkdirAll(dir, 0700)
	return dir
}

// ConfigFile 配置文件路径
func ConfigFile() string {
	return filepath.Join(ConfigDir(), "config.json")
}

// TokenFile Token 存储路径
func TokenFile() string {
	return filepath.Join(ConfigDir(), "auth.json")
}

// TaskDBFile 任务数据库路径
func TaskDBFile() string {
	return filepath.Join(ConfigDir(), "tasks.db")
}

// LogDir 日志目录
func LogDir() string {
	dir := filepath.Join(ConfigDir(), "logs")
	os.MkdirAll(dir, 0700)
	return dir
}

// LoadConfig 加载配置
func LoadConfig() (*Config, error) {
	cfg := DefaultConfig()

	configFile := ConfigFile()
	if _, err := os.Stat(configFile); err == nil {
		data, err := os.ReadFile(configFile)
		if err != nil {
			return nil, fmt.Errorf("读取配置文件失败: %w", err)
		}
		if err := json.Unmarshal(data, cfg); err != nil {
			return nil, fmt.Errorf("解析配置文件失败: %w", err)
		}
	}

	// Viper 覆盖环境变量
	viper.SetEnvPrefix("PCD")
	viper.AutomaticEnv()

	if v := viper.GetString("ENDPOINT"); v != "" {
		cfg.Endpoint = v
	}
	if v := viper.GetInt("CHUNK_SIZE_MB"); v > 0 {
		cfg.ChunkSizeMB = v
	}

	return cfg, nil
}

// SaveConfig 保存配置
func SaveConfig(cfg *Config) error {
	data, err := json.MarshalIndent(cfg, "", "  ")
	if err != nil {
		return fmt.Errorf("序列化配置失败: %w", err)
	}
	if err := os.WriteFile(ConfigFile(), data, 0600); err != nil {
		return fmt.Errorf("写入配置文件失败: %w", err)
	}
	return nil
}

// AuthData 认证数据
type AuthData struct {
	Token     string    `json:"token"`
	UserID    string    `json:"user_id"`
	Account   string    `json:"account"`
	LoginAt   time.Time `json:"login_at"`
	ExpiresAt time.Time `json:"expires_at"`
}

// SaveAuth 保存认证信息
func SaveAuth(auth *AuthData) error {
	data, err := json.MarshalIndent(auth, "", "  ")
	if err != nil {
		return fmt.Errorf("序列化认证数据失败: %w", err)
	}
	if err := os.WriteFile(TokenFile(), data, 0600); err != nil {
		return fmt.Errorf("写入认证文件失败: %w", err)
	}
	return nil
}

// LoadAuth 加载认证信息
func LoadAuth() (*AuthData, error) {
	file := TokenFile()
	if _, err := os.Stat(file); os.IsNotExist(err) {
		return nil, fmt.Errorf("未登录，请先执行 login 命令")
	}
	data, err := os.ReadFile(file)
	if err != nil {
		return nil, fmt.Errorf("读取认证文件失败: %w", err)
	}
	var auth AuthData
	if err := json.Unmarshal(data, &auth); err != nil {
		return nil, fmt.Errorf("解析认证数据失败: %w", err)
	}
	if auth.Token == "" {
		return nil, fmt.Errorf("未登录，请先执行 login 命令")
	}
	return &auth, nil
}

// ClearAuth 清除认证信息
func ClearAuth() error {
	os.Remove(TokenFile())
	return nil
}

// EnsureLoggedIn 确保已登录，返回认证信息
func EnsureLoggedIn() (*AuthData, error) {
	auth, err := LoadAuth()
	if err != nil {
		return nil, err
	}
	// 检查是否过期
	if time.Now().After(auth.ExpiresAt) {
		return nil, fmt.Errorf("登录已过期，请重新登录")
	}
	return auth, nil
}

// GoVersion 返回 Go 版本
func GoVersion() string {
	return runtime.Version()
}

// OSArch 返回操作系统和架构
func OSArch() string {
	return fmt.Sprintf("%s/%s", runtime.GOOS, runtime.GOARCH)
}