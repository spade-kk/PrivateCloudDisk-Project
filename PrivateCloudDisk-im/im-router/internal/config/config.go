// Package config 负责 IM Router 的配置加载与解析。
//
// 加载策略（优先级从高到低）：
//  1. 环境变量（前缀 IM_ROUTER_，下划线分隔，如 IM_ROUTER_SERVER_NODE_ID）
//  2. 命令行 -config 指定的配置文件
//  3. 默认 ./config.yaml
//
// 支持的字段类型：string / int / uint / bool / time.Duration。
package config

import (
	"fmt"
	"os"
	"path/filepath"
	"reflect"
	"strconv"
	"strings"
	"time"

	"gopkg.in/yaml.v3"
)

// Config 是 IM Router 的顶层配置根。
type Config struct {
	Server   ServerConfig   `yaml:"server"`
	Redis    RedisConfig    `yaml:"redis"`
	RabbitMQ RabbitMQConfig `yaml:"rabbitmq"`
	GRPC     GRPCConfig     `yaml:"grpc"`
	Offline  OfflineConfig  `yaml:"offline"`
	Metrics  MetricsConfig  `yaml:"metrics"`
	Log      LogConfig      `yaml:"log"`
}

// ServerConfig 服务节点配置。
type ServerConfig struct {
	NodeID   string `yaml:"node_id"`
	GRPCPort int    `yaml:"grpc_port"`
}

// RedisConfig Redis 连接配置。
type RedisConfig struct {
	Host     string `yaml:"host"`
	Port     int    `yaml:"port"`
	Password string `yaml:"password"`
	DB       int    `yaml:"db"`
	PoolSize int    `yaml:"pool_size"`
}

// Addr 返回 Redis 地址字符串（host:port）。
func (r RedisConfig) Addr() string { return fmt.Sprintf("%s:%d", r.Host, r.Port) }

// RabbitMQConfig RabbitMQ 连接与队列配置。
type RabbitMQConfig struct {
	Host      string          `yaml:"host"`
	Port      int             `yaml:"port"`
	Username  string          `yaml:"username"`
	Password  string          `yaml:"password"`
	VHost     string          `yaml:"vhost"`
	Consumer  ConsumerConfig  `yaml:"consumer"`
	Publisher PublisherConfig `yaml:"publisher"`
}

// URI 返回 amqp:// 连接字符串。
func (r RabbitMQConfig) URI() string {
	return fmt.Sprintf("amqp://%s:%s@%s:%d%s", r.Username, r.Password, r.Host, r.Port, r.VHost)
}

// ConsumerConfig MQ 消费者配置。
type ConsumerConfig struct {
	PushCommandQueue     string        `yaml:"push_command_queue"`
	SendCommandQueue     string        `yaml:"send_command_queue"`
	DeliveredEventQueue  string        `yaml:"delivered_event_queue"`
	FailedEventQueue     string        `yaml:"failed_event_queue"`
	SendFailedEventQueue string        `yaml:"send_failed_event_queue"`
	PrefetchCount        int           `yaml:"prefetch_count"`
	WorkerCount          int           `yaml:"worker_count"`
	HandleTimeout        time.Duration `yaml:"handle_timeout"`
}

// PublisherConfig MQ 生产者配置。
type PublisherConfig struct {
	Exchange       string        `yaml:"exchange"`
	RetryCount     int           `yaml:"retry_count"`
	ConfirmTimeout time.Duration `yaml:"confirm_timeout"`
}

// GRPCConfig gRPC 客户端配置。
type GRPCConfig struct {
	DialTimeout      time.Duration `yaml:"dial_timeout"`
	KeepaliveTime    time.Duration `yaml:"keepalive_time"`
	KeepaliveTimeout time.Duration `yaml:"keepalive_timeout"`
	MaxRecvMsgSize   int           `yaml:"max_recv_msg_size"`
	PoolSizePerNode  int           `yaml:"pool_size_per_node"`
}

// OfflineConfig 离线消息配置。
type OfflineConfig struct {
	MaxMessagesPerUser int `yaml:"max_messages_per_user"`
	MessageTTL         int `yaml:"message_ttl"` // 秒
}

// MetricsConfig Prometheus 监控配置。
type MetricsConfig struct {
	Enabled bool   `yaml:"enabled"`
	Port    int    `yaml:"port"`
	Path    string `yaml:"path"`
}

// LogConfig 日志配置。
type LogConfig struct {
	Level  string `yaml:"level"`
	Format string `yaml:"format"`
}

// Load 从指定路径加载配置文件，并应用环境变量覆盖。
// 若 path 为空，则依次尝试 ./config.yaml。
func Load(path string) (*Config, error) {
	if path == "" {
		// 默认查找路径：当前目录
		path = "config.yaml"
	}
	abs, _ := filepath.Abs(path)

	cfg := &Config{}
	data, err := os.ReadFile(abs)
	if err != nil {
		return nil, fmt.Errorf("读取配置文件失败 [%s]: %w", abs, err)
	}
	if err := yaml.Unmarshal(data, cfg); err != nil {
		return nil, fmt.Errorf("解析配置文件失败: %w", err)
	}

	// 应用默认值
	cfg.applyDefaults()

	// 应用环境变量覆盖
	if err := applyEnvOverrides(cfg); err != nil {
		return nil, fmt.Errorf("应用环境变量覆盖失败: %w", err)
	}

	return cfg, nil
}

// applyDefaults 为关键字段补充默认值，避免零值导致运行异常。
func (c *Config) applyDefaults() {
	if c.Server.NodeID == "" {
		c.Server.NodeID = "router-1"
	}
	if c.Server.GRPCPort == 0 {
		c.Server.GRPCPort = 9092
	}
	if c.Redis.PoolSize == 0 {
		c.Redis.PoolSize = 50
	}
	if c.RabbitMQ.Consumer.PrefetchCount == 0 {
		c.RabbitMQ.Consumer.PrefetchCount = 50
	}
	if c.RabbitMQ.Consumer.WorkerCount == 0 {
		c.RabbitMQ.Consumer.WorkerCount = 200
	}
	if c.RabbitMQ.Consumer.HandleTimeout == 0 {
		c.RabbitMQ.Consumer.HandleTimeout = 10 * time.Second
	}
	if c.RabbitMQ.Publisher.RetryCount == 0 {
		c.RabbitMQ.Publisher.RetryCount = 3
	}
	if c.RabbitMQ.Publisher.ConfirmTimeout == 0 {
		c.RabbitMQ.Publisher.ConfirmTimeout = 5 * time.Second
	}
	if c.GRPC.DialTimeout == 0 {
		c.GRPC.DialTimeout = 5 * time.Second
	}
	if c.GRPC.KeepaliveTime == 0 {
		c.GRPC.KeepaliveTime = 30 * time.Second
	}
	if c.GRPC.KeepaliveTimeout == 0 {
		c.GRPC.KeepaliveTimeout = 10 * time.Second
	}
	if c.GRPC.MaxRecvMsgSize == 0 {
		c.GRPC.MaxRecvMsgSize = 4 * 1024 * 1024
	}
	if c.GRPC.PoolSizePerNode == 0 {
		c.GRPC.PoolSizePerNode = 5
	}
	if c.Offline.MaxMessagesPerUser == 0 {
		c.Offline.MaxMessagesPerUser = 1000
	}
	if c.Offline.MessageTTL == 0 {
		c.Offline.MessageTTL = 7 * 24 * 3600
	}
	if c.Metrics.Path == "" {
		c.Metrics.Path = "/metrics"
	}
	if c.Metrics.Port == 0 {
		c.Metrics.Port = 9093
	}
	if c.Log.Level == "" {
		c.Log.Level = "info"
	}
	if c.Log.Format == "" {
		c.Log.Format = "json"
	}
}

// envPrefix 环境变量前缀。
const envPrefix = "IM_ROUTER_"

// applyEnvOverrides 通过反射遍历配置结构，将 IM_ROUTER_ 前缀的环境变量
// 映射到对应字段（yaml tag 中的下划线名 → 环境变量大写下划线名）。
func applyEnvOverrides(cfg *Config) error {
	return walkStruct(reflect.ValueOf(cfg).Elem(), envPrefix)
}

// walkStruct 递归遍历结构体字段，处理 yaml tag 与环境变量映射。
func walkStruct(v reflect.Value, prefix string) error {
	t := v.Type()
	for i := 0; i < t.NumField(); i++ {
		field := t.Field(i)
		// 跳过非导出字段
		if !field.IsExported() {
			continue
		}
		tag := field.Tag.Get("yaml")
		name := strings.Split(tag, ",")[0]
		if name == "" {
			name = strings.ToLower(field.Name)
		}
		envName := prefix + strings.ToUpper(strings.ReplaceAll(name, "-", "_"))
		fv := v.Field(i)

		// 嵌套结构体递归处理
		if fv.Kind() == reflect.Struct && fv.Type().Name() != "Duration" {
			if err := walkStruct(fv, envName+"_"); err != nil {
				return err
			}
			continue
		}

		// 从环境变量读取
		raw, ok := os.LookupEnv(envName)
		if !ok {
			continue
		}
		if err := setField(fv, raw); err != nil {
			return fmt.Errorf("环境变量 %s=%q 解析失败: %w", envName, raw, err)
		}
	}
	return nil
}

// setField 根据字段类型将字符串值写入。
func setField(fv reflect.Value, raw string) error {
	// 特殊处理 time.Duration（底层为 int64）
	if fv.Type() == reflect.TypeOf(time.Duration(0)) {
		d, err := time.ParseDuration(raw)
		if err != nil {
			// 支持纯数字（秒）
			if sec, e := strconv.ParseInt(raw, 10, 64); e == nil {
				fv.SetInt(sec * int64(time.Second))
				return nil
			}
			return err
		}
		fv.SetInt(int64(d))
		return nil
	}

	switch fv.Kind() {
	case reflect.String:
		fv.SetString(raw)
	case reflect.Int, reflect.Int64:
		n, err := strconv.ParseInt(raw, 10, 64)
		if err != nil {
			return err
		}
		fv.SetInt(n)
	case reflect.Uint, reflect.Uint64:
		n, err := strconv.ParseUint(raw, 10, 64)
		if err != nil {
			return err
		}
		fv.SetUint(n)
	case reflect.Bool:
		b, err := strconv.ParseBool(raw)
		if err != nil {
			return err
		}
		fv.SetBool(b)
	default:
		// 其他类型暂不支持环境变量覆盖
	}
	return nil
}
