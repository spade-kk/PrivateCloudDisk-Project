// Package domain 定义验证码服务的核心领域模型。
package domain

import (
	"encoding/json"
	"time"
)

// =============================================================================
// 验证码用途类型
// =============================================================================
type VerificationPurpose string

const (
	PurposeRegister VerificationPurpose = "REGISTER" // 注册
	PurposeBind     VerificationPurpose = "BIND"     // 绑定
	PurposeReset    VerificationPurpose = "RESET"    // 重置密码
)

// Valid 校验用途是否合法
func (p VerificationPurpose) Valid() bool {
	switch p {
	case PurposeRegister, PurposeBind, PurposeReset:
		return true
	}
	return false
}

// =============================================================================
// 目标类型
// =============================================================================
type TargetType string

const (
	TargetEmail TargetType = "email"
	TargetPhone TargetType = "phone"
)

// =============================================================================
// 验证码发送请求（HTTP 请求体）
// =============================================================================
type VerificationSendRequest struct {
	Email         string `json:"email,omitempty"`
	Phone         string `json:"phone,omitempty"`
	Purpose       string `json:"purpose" binding:"required"`
	CaptchaToken  string `json:"captcha_token,omitempty"`  // Turnstile 人机验证 token
	CaptchaAction string `json:"captcha_action,omitempty"` // Turnstile action
}

// ValidTarget 校验目标和用途
func (r *VerificationSendRequest) ValidTarget() (TargetType, string, VerificationPurpose, error) {
	purpose := VerificationPurpose(r.Purpose)
	if !purpose.Valid() {
		return "", "", "", ErrInvalidPurpose
	}

	if r.Email != "" {
		return TargetEmail, r.Email, purpose, nil
	}
	if r.Phone != "" {
		return TargetPhone, r.Phone, purpose, nil
	}
	return "", "", "", ErrNoTarget
}

// =============================================================================
// 验证码发送响应（VO）
// =============================================================================
type VerificationSendVO struct {
	ResendToken      string `json:"resend_token"`
	ExpiresIn        int64  `json:"expires_in"`
	RemainingResends int    `json:"remaining_resends"`
}

// =============================================================================
// 验证码校验请求
// =============================================================================
type VerificationVerifyRequest struct {
	TargetType string `json:"target_type" binding:"required"` // "email" / "phone"
	Target     string `json:"target" binding:"required"`      // 邮箱或手机号
	Purpose    string `json:"purpose" binding:"required"`     // 用途
	Code       string `json:"code" binding:"required"`        // 6位验证码
}

// =============================================================================
// Resend Token 数据（存储在 Redis 中的 JSON）
// =============================================================================
type ResendTokenData struct {
	TargetType       string `json:"targetType"`
	TargetHash       string `json:"targetHash"`
	Purpose          string `json:"purpose"`
	IPHash           string `json:"ipHash"`
	RemainingResends int    `json:"remainingResends"`
	CreatedAt        int64  `json:"createdAt"`
}

// ToJSON 序列化
func (t *ResendTokenData) ToJSON() (string, error) {
	b, err := json.Marshal(t)
	if err != nil {
		return "", err
	}
	return string(b), nil
}

// ResendTokenDataFromJSON 反序列化
func ResendTokenDataFromJSON(data string) (*ResendTokenData, error) {
	var t ResendTokenData
	if err := json.Unmarshal([]byte(data), &t); err != nil {
		return nil, err
	}
	return &t, nil
}

// =============================================================================
// 验证码消息事件（RabbitMQ 消息体）
// =============================================================================
type VerificationMessageEvent struct {
	EventID    string `json:"event_id"`
	TargetType string `json:"target_type"` // "email" / "phone"
	Target     string `json:"target"`      // 邮箱或手机号
	Code       string `json:"code"`        // 验证码
	Purpose    string `json:"purpose"`     // 用途
	ExpireSec  int    `json:"expire_sec"`  // 有效期（秒）
	CreatedAt  int64  `json:"created_at"`
}

// ToJSON 序列化
func (e *VerificationMessageEvent) ToJSON() ([]byte, error) {
	return json.Marshal(e)
}

// VerificationMessageEventFromJSON 反序列化
func VerificationMessageEventFromJSON(data []byte) (*VerificationMessageEvent, error) {
	var e VerificationMessageEvent
	if err := json.Unmarshal(data, &e); err != nil {
		return nil, err
	}
	return &e, nil
}

// =============================================================================
// 配置常量
// =============================================================================

const (
	// 验证码长度
	CodeLength = 6

	// 验证码有效期（秒）
	CodeExpireSeconds = 300 // 5 分钟

	// 重新发送最小间隔（秒）
	ResendIntervalSeconds = 60

	// 同一 IP+目标每小时最大发送次数
	MaxSendsPerHour = 5

	// 重新发送最大次数
	MaxResends = 8

	// 重新发送 token 有效期（秒）
	ResendTokenTTLSeconds = 600 // 10 分钟

	// 验证码校验失败最大次数（同一 IP+目标）
	MaxCodeFailures = 5

	// 验证码校验失败窗口（秒）
	CodeFailureWindowSeconds = 900 // 15 分钟
)

// =============================================================================
// Redis Key 前缀
// =============================================================================

const (
	// 验证码存储 key
	// 格式：verif:code:{targetType}:{targetHash}:{purpose}:{ipHash}
	// TTL: 5 分钟
	PrefixCode = "verif:code:"

	// 发送频率计数器 key
	// 格式：verif:rate:{targetType}:{targetHash}:{purpose}:{ipHash}
	// TTL: 1 小时
	PrefixRate = "verif:rate:"

	// 上次发送时间戳 key
	// 格式：verif:last:{targetType}:{targetHash}:{purpose}:{ipHash}
	// TTL: 60 秒
	PrefixLast = "verif:last:"

	// 不透明 resend token key
	// 格式：verif:token:{tokenUUID}
	// TTL: 10 分钟
	PrefixToken = "verif:token:"

	// 验证码校验失败次数 key
	// 格式：verif:attempts:{targetType}:{targetHash}:{purpose}:{ipHash}
	// TTL: 15 分钟
	PrefixAttempts = "verif:attempts:"

	// 旧版兼容 key
	PrefixEmailLegacy = "email_verification_code:"
	PrefixPhoneLegacy = "phone_verification_code:"
	SuffixRateLegacy  = ":rate"
)

// =============================================================================
// 错误定义
// =============================================================================
type VerificationError struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
}

func (e *VerificationError) Error() string {
	return e.Message
}

var (
	ErrInvalidPurpose     = &VerificationError{Code: 400, Message: "验证码用途不合法，必须是 REGISTER、BIND 或 RESET"}
	ErrNoTarget           = &VerificationError{Code: 400, Message: "邮箱和手机号至少提供一个"}
	ErrCaptchaFailed      = &VerificationError{Code: 400, Message: "人机验证失败"}
	ErrSystemTarget       = &VerificationError{Code: 400, Message: "不能使用系统邮箱/手机号获取验证码"}
	ErrBlockedDomain      = &VerificationError{Code: 400, Message: "不允许使用该邮箱域名，请使用企业邮箱"}
	ErrRateLimitExceeded  = &VerificationError{Code: 429, Message: "发送频率超限，请稍后再试"}
	ErrResendInterval     = &VerificationError{Code: 429, Message: "发送间隔不足60秒，请稍后再试"}
	ErrResendTokenInvalid = &VerificationError{Code: 400, Message: "重新发送令牌无效或已过期"}
	ErrResendTokenExhausted = &VerificationError{Code: 400, Message: "重新发送次数已用完"}
	ErrCodeInvalid        = &VerificationError{Code: 400, Message: "验证码错误"}
	ErrCodeExpired        = &VerificationError{Code: 400, Message: "验证码已过期"}
	ErrCodeAttemptsExceeded = &VerificationError{Code: 429, Message: "验证码错误次数过多，请 15 分钟后重试"}
	ErrCodeIPMismatch     = &VerificationError{Code: 400, Message: "验证码与请求 IP 不匹配"}
)

// IsVerificationError 判断是否为验证码业务错误
func IsVerificationError(err error) (*VerificationError, bool) {
	if err == nil {
		return nil, false
	}
	ve, ok := err.(*VerificationError)
	return ve, ok
}

// =============================================================================
// 向后兼容：旧版验证码存储（供事件消费者使用）
// =============================================================================

// LegacyVerificationStoreRequest 旧版验证码存储请求
type LegacyVerificationStoreRequest struct {
	TargetType  string `json:"target_type"`  // "email" / "phone"
	Target      string `json:"target"`       // 邮箱或手机号
	Code        string `json:"code"`         // 验证码
	ExpireSec   int    `json:"expire_sec"`   // 有效期（秒）
	CreatedAt   time.Time `json:"created_at"` // 创建时间
}