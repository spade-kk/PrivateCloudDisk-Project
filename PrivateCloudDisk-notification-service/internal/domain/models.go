// Package domain 定义通知服务的核心领域模型。
package domain

import (
	"encoding/json"
	"time"
)

// =============================================================================
// 渠道类型
// =============================================================================
type Channel string

const (
	ChannelEmail    Channel = "email"
	ChannelSMS      Channel = "sms"
	ChannelPush     Channel = "push" // 通用推送（自动选择 APNs/FCM/WebPush）
	ChannelAPNs     Channel = "apns"
	ChannelFCM      Channel = "fcm"
	ChannelWeChatMP Channel = "wechat_mp"
	ChannelAlipayMP Channel = "alipay_mp"
	ChannelWebPush  Channel = "webpush"
	ChannelInApp    Channel = "in_app"
	ChannelWS       Channel = "ws" // 系统 WebSocket 推送
)

func (c Channel) String() string { return string(c) }

// =============================================================================
// 模板源类型
// =============================================================================
type TemplateSource string

const (
	TemplateSourceDatabase TemplateSource = "database" // 从数据库模板表加载
	TemplateSourceFile     TemplateSource = "file"     // 从嵌入模板文件加载
	TemplateSourceRaw      TemplateSource = "raw"      // 直接传入原始模板字符串
)

// =============================================================================
// 系统推送消息缓存策略
// =============================================================================
type WSCacheStrategy string

const (
	WSCacheNone    WSCacheStrategy = "none"    // 一次性：离线丢弃
	WSCachePersist WSCacheStrategy = "persist" // 缓存：离线存入 Redis，上线后推送
)

// =============================================================================
// 参数校验正则
// =============================================================================
const (
	EmailPattern = `^[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}$`
	PhonePattern = `^\+?[1-9]\d{1,14}$` // E.164 格式: +86xxxxxxxxxxx
)

// =============================================================================
// 通知状态
// =============================================================================
type DeliveryStatus string

const (
	StatusPending    DeliveryStatus = "pending"
	StatusProcessing DeliveryStatus = "processing"
	StatusSent       DeliveryStatus = "sent"
	StatusDelivered  DeliveryStatus = "delivered"
	StatusFailed     DeliveryStatus = "failed"
	StatusCancelled  DeliveryStatus = "cancelled"
	StatusAggregated DeliveryStatus = "aggregated" // 已聚合，等待批量发送
)

// =============================================================================
// 通知类型
// =============================================================================
type NotificationType string

const (
	TypeVerification NotificationType = "verification"   // 验证码
	TypeWelcome      NotificationType = "welcome"         // 欢迎消息
	TypeShare        NotificationType = "share"           // 分享通知
	TypeSystem       NotificationType = "system"           // 系统通知
	TypeSecurity     NotificationType = "security"         // 安全通知
	TypeMarketing    NotificationType = "marketing"        // 营销通知
	TypeReminder     NotificationType = "reminder"         // 提醒
	TypeCustom       NotificationType = "custom"           // 自定义
)

// =============================================================================
// 通知事件（RabbitMQ 消息体）
// =============================================================================
type NotificationEvent struct {
	// 事件元数据
	EventID   string `json:"event_id"`
	EventType string `json:"event_type"` // user_registered, email_verification, share_notify, etc.

	// 接收者
	UserID string `json:"user_id"`
	Email  string `json:"email,omitempty"`
	Phone  string `json:"phone,omitempty"`

	// 渠道选择（为空则根据用户偏好自动选择）
	Channels []string `json:"channels,omitempty"` // email, sms, push, wechat_mp, etc.

	// 模板变量
	TemplateCode   string                 `json:"template_code"`
	TemplateLang   string                 `json:"template_lang,omitempty"`   // zh-CN, en-US, etc.
	TemplateSource string                 `json:"template_source,omitempty"` // database / file / raw（默认 database）
	RawTemplate    *RawTemplate           `json:"raw_template,omitempty"`    // 仅 template_source=raw 时使用
	Variables      map[string]interface{} `json:"variables,omitempty"`

	// 设备推送参数
	DeviceTokens []string `json:"device_tokens,omitempty"` // APNs/FCM device tokens
	PushTitle    string   `json:"push_title,omitempty"`
	PushBody     string   `json:"push_body,omitempty"`
	PushData     map[string]interface{} `json:"push_data,omitempty"` // 自定义数据

	// 优先级
	Priority int `json:"priority"` // 0=低, 5=正常, 10=高

	// WebSocket 系统推送
	WSCacheStrategy string `json:"ws_cache_strategy,omitempty"` // none / persist（默认 persist）

	// 幂等 & 重试
	RetryCount int       `json:"retry_count"`
	CreatedAt  time.Time `json:"created_at"`
}

// ToJSON 序列化为 JSON 字节
func (e *NotificationEvent) ToJSON() ([]byte, error) {
	return json.Marshal(e)
}

// FromJSON 从 JSON 字节反序列化
func FromJSON(data []byte) (*NotificationEvent, error) {
	var event NotificationEvent
	if err := json.Unmarshal(data, &event); err != nil {
		return nil, err
	}
	return &event, nil
}

// RawTemplate 原始模板（当 template_source=raw 时使用）
type RawTemplate struct {
	Title    string `json:"title"`
	Body     string `json:"body"`
	HTMLBody string `json:"html_body,omitempty"`
}

// =============================================================================
// 系统 WebSocket 推送消息
// =============================================================================
type WSSystemMessage struct {
	ID        string                 `json:"id"`
	Type      string                 `json:"type"` // system, security, notification
	Title     string                 `json:"title"`
	Body      string                 `json:"body"`
	Priority  int                    `json:"priority"`
	Data      map[string]interface{} `json:"data,omitempty"`
	Timestamp int64                  `json:"timestamp"`
}

// ToJSON 序列化
func (m *WSSystemMessage) ToJSON() ([]byte, error) {
	return json.Marshal(m)
}

// =============================================================================
// 消息模板
// =============================================================================
type Template struct {
	ID          int64                  `json:"id" db:"id"`
	Code        string                 `json:"code" db:"code"`          // 模板唯一标识
	Name        string                 `json:"name" db:"name"`          // 模板名称
	Channel     string                 `json:"channel" db:"channel"`    // 渠道
	Lang        string                 `json:"lang" db:"lang"`          // 语言 zh-CN/en-US
	Title       string                 `json:"title" db:"title"`        // 标题模板（支持 {{.var}} 变量）
	Body        string                 `json:"body" db:"body"`          // 正文模板
	HTMLBody    string                 `json:"html_body" db:"html_body"` // HTML 模板（邮件专用）
	Variables   map[string]interface{} `json:"variables" db:"-"`        // 变量定义
	VariablesJSON string               `json:"-" db:"variables_json"`
	IsActive    bool                   `json:"is_active" db:"is_active"`
	CreatedAt   time.Time              `json:"created_at" db:"created_at"`
	UpdatedAt   time.Time              `json:"updated_at" db:"updated_at"`
}

// =============================================================================
// 通知记录
// =============================================================================
type NotificationRecord struct {
	ID           int64          `json:"id" db:"id"`
	EventID      string         `json:"event_id" db:"event_id"`
	UserID       string         `json:"user_id" db:"user_id"`
	Channel      string         `json:"channel" db:"channel"`
	Type         string         `json:"type" db:"type"`
	Title        string         `json:"title" db:"title"`
	Body         string         `json:"body" db:"body"`
	Recipient    string         `json:"recipient" db:"recipient"`       // 邮箱/手机号/deviceToken
	TemplateCode string         `json:"template_code" db:"template_code"`
	Status       DeliveryStatus `json:"status" db:"status"`
	Priority     int            `json:"priority" db:"priority"`
	RetryCount   int            `json:"retry_count" db:"retry_count"`
	MaxRetries   int            `json:"max_retries" db:"max_retries"`
	ErrorMsg     string         `json:"error_msg" db:"error_msg"`
	AggregationID string        `json:"aggregation_id" db:"aggregation_id"` // 聚合批次 ID
	CreatedAt    time.Time      `json:"created_at" db:"created_at"`
	UpdatedAt    time.Time      `json:"updated_at" db:"updated_at"`
}

// =============================================================================
// 送达日志
// =============================================================================
type DeliveryLog struct {
	ID               int64          `json:"id" db:"id"`
	NotificationID   int64          `json:"notification_id" db:"notification_id"`
	EventID          string         `json:"event_id" db:"event_id"`
	Channel          string         `json:"channel" db:"channel"`
	Status           DeliveryStatus `json:"status" db:"status"`
	ProviderResponse string         `json:"provider_response" db:"provider_response"` // 第三方响应
	ErrorMsg         string         `json:"error_msg" db:"error_msg"`
	DurationMs       int64          `json:"duration_ms" db:"duration_ms"` // 发送耗时
	CreatedAt        time.Time      `json:"created_at" db:"created_at"`
}

// =============================================================================
// 用户通知偏好
// =============================================================================
type NotificationPreference struct {
	ID        int64              `json:"id" db:"id"`
	UserID    string             `json:"user_id" db:"user_id"`
	Channel   string             `json:"channel" db:"channel"`
	Enabled   bool               `json:"enabled" db:"enabled"`
	DNDStart  string             `json:"dnd_start" db:"dnd_start"` // 免打扰开始 HH:MM
	DNDEnd    string             `json:"dnd_end" db:"dnd_end"`     // 免打扰结束 HH:MM
	DNDEnabled bool              `json:"dnd_enabled" db:"dnd_enabled"`
	MaxPerDay int                `json:"max_per_day" db:"max_per_day"` // 每日最大推送数
	QuietHours []string           `json:"quiet_hours" db:"-"`          // 静音时段
	QuietHoursJSON string        `json:"-" db:"quiet_hours_json"`
	CreatedAt time.Time          `json:"created_at" db:"created_at"`
	UpdatedAt time.Time          `json:"updated_at" db:"updated_at"`
}

// =============================================================================
// 设备订阅
// =============================================================================
type DeviceSubscription struct {
	ID          int64     `json:"id" db:"id"`
	UserID      string    `json:"user_id" db:"user_id"`
	DeviceToken string    `json:"device_token" db:"device_token"`
	Platform    string    `json:"platform" db:"platform"` // ios, android, web
	AppVersion  string    `json:"app_version" db:"app_version"`
	IsActive    bool      `json:"is_active" db:"is_active"`
	CreatedAt   time.Time `json:"created_at" db:"created_at"`
	UpdatedAt   time.Time `json:"updated_at" db:"updated_at"`
}

// =============================================================================
// 聚合窗口
// =============================================================================
type AggregationWindow struct {
	ID          string    `json:"id" db:"id"`
	UserID      string    `json:"user_id" db:"user_id"`
	Channel     string    `json:"channel" db:"channel"`
	Type        string    `json:"type" db:"type"`
	RecordIDs   []int64   `json:"record_ids"` // 聚合的通知记录 ID
	Count       int       `json:"count" db:"count"`
	Status      string    `json:"status" db:"status"` // open, closed, sent
	WindowStart time.Time `json:"window_start" db:"window_start"`
	WindowEnd   time.Time `json:"window_end" db:"window_end"`
	SentAt      *time.Time `json:"sent_at" db:"sent_at"`
	CreatedAt   time.Time  `json:"created_at" db:"created_at"`
}