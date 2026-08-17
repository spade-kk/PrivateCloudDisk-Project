package domain

import "time"

// =============================================================================
// 客户端注册微服务 — 领域模型
// =============================================================================
// 对应 macOS 客户端中的 DeviceAttestationService.AttestationObject
// 和 DeviceIdentityManager.ClientIdentity 数据结构。
// =============================================================================

// ─── 客户端身份 ────────────────────────────────────────────────────────────────

// ClientIdentity 已注册的客户端身份信息。
//
// 对应数据库表 client_identities 和 Redis 缓存 key pcd:client:pubkey:{client_id}。
type ClientIdentity struct {
	// ClientID 服务端分配的唯一客户端标识符（UUID v4）
	ClientID string `json:"client_id" db:"client_id"`

	// DeviceID 设备硬件指纹（SHA-256 哈希）
	DeviceID string `json:"device_id" db:"device_id"`

	// Platform 平台标识（macOS 原生客户端或受限 Web 本地插件运行时）
	Platform string `json:"platform" db:"platform"`

	// AppID 应用标识（Bundle ID）
	AppID string `json:"app_id" db:"app_id"`

	// PublicKey 客户端公钥（Base64 编码的 DER 格式，ECDSA P-256）
	PublicKey string `json:"public_key" db:"public_key"`

	// KeyAlgorithm 密钥算法（固定 "ECDSA-P256"）
	KeyAlgorithm string `json:"key_algorithm" db:"key_algorithm"`

	// TokenID 密钥存储位置（SecureEnclave / Keychain）
	TokenID string `json:"token_id" db:"token_id"`

	// IntegrityLevel 设备完整性等级（high / medium / low）
	IntegrityLevel string `json:"integrity_level" db:"integrity_level"`

	// OSVersion 操作系统版本
	OSVersion string `json:"os_version" db:"os_version"`

	// Hostname 设备主机名
	Hostname string `json:"hostname" db:"hostname"`

	// Status 身份状态（active / revoked / pending）
	Status string `json:"status" db:"status"`

	// RegisteredAt 注册时间
	RegisteredAt time.Time `json:"registered_at" db:"registered_at"`

	// LastVerifiedAt 最后验证时间
	LastVerifiedAt time.Time `json:"last_verified_at" db:"last_verified_at"`

	// CreatedAt 数据库创建时间
	CreatedAt time.Time `json:"created_at" db:"created_at"`
}

// ─── 设备信任证明 ──────────────────────────────────────────────────────────────

// AttestationObject 设备信任证明对象。
//
// 客户端提交的完整证明数据结构，包含三层证明：
//  1. 硬件证明（Secure Enclave 密钥生成）
//  2. APP 证明（Apple App Attestation）
//  3. 业务实例签名（ECDSA 签名）
//
// 对应 macOS 客户端 DeviceAttestationService.AttestationObject；
// Web 客户端复用业务签名字段，但不声明 Apple 证明，完整性等级固定为 low。
type AttestationObject struct {
	// Version 证明版本号
	Version string `json:"version"`

	// AppID 应用标识
	AppID string `json:"app_id"`

	// Platform 平台标识
	Platform string `json:"platform"`

	// DeviceID 设备硬件指纹
	DeviceID string `json:"device_id"`

	// PublicKey 业务公钥（DER SubjectPublicKeyInfo 格式 Base64）
	// 客户端通过 SecureEnclaveManager.ecPublicKeyToDER() 封装裸坐标为 DER SPKI 格式
	// 服务端使用 x509.ParsePKIXPublicKey() 解析
	PublicKey string `json:"public_key"`

	// KeyAlgorithm 密钥算法
	KeyAlgorithm string `json:"key_algorithm"`

	// TokenID 密钥存储位置
	TokenID string `json:"token_id"`

	// IntegrityLevel 客户端自评的完整性等级
	IntegrityLevel string `json:"integrity_level"`

	// OSVersion 操作系统版本
	OSVersion string `json:"os_version"`

	// Hostname 设备主机名
	Hostname string `json:"hostname"`

	// Timestamp 证明生成时间戳（Unix 秒）
	Timestamp int64 `json:"timestamp"`

	// Challenge 服务器挑战值
	Challenge string `json:"challenge"`

	// Signature 业务密钥对证明数据的 ECDSA 签名（Base64）
	// 签名负载: challenge + "\n" + app_id + "\n" + device_id + "\n" + public_key + "\n" + timestamp
	Signature string `json:"signature"`

	// SigningPayload 签名负载（用于服务端验证）
	SigningPayload string `json:"signing_payload"`

	// AppleAttestation Apple App Attestation 证明语句（CBOR 格式，Base64 编码）
	// 由 DCAppAttestService.attestKey() 返回，经 Apple 服务器签名
	// 包含：
	//   - 证明密钥在真实 Secure Enclave 中生成（硬件证明）
	//   - 证明密钥由特定 Bundle ID 的 APP 调用生成（APP 证明）
	//   - clientDataHash = SHA256(PublicKey) 绑定到证明中
	// 空字符串表示 App Attest 不可用（降级到 medium）
	AppleAttestation string `json:"apple_attestation"`

	// AppleAttestKeyId App Attest 密钥标识（Base64 编码）
	// 由 DCAppAttestService.generateKey() 返回
	AppleAttestKeyId string `json:"apple_attest_key_id"`
}

// ─── 注册请求/响应 ──────────────────────────────────────────────────────────────

// RegisterChallengeRequest 获取挑战值请求
type RegisterChallengeRequest struct {
	// Platform 平台标识
	Platform string `json:"platform"`

	// PublicKey 客户端公钥
	PublicKey string `json:"public_key"`

	// KeyAlgorithm 密钥算法
	KeyAlgorithm string `json:"key_algorithm"`
}

// RegisterChallengeResponse 挑战值响应
type RegisterChallengeResponse struct {
	// Challenge 服务器生成的挑战值（UUID v4 + 时间戳）
	Challenge string `json:"challenge"`

	// ExpiresAt 挑战值过期时间（Unix 秒）
	ExpiresAt int64 `json:"expires_at"`
}

// RegisterRequest 客户端注册请求
type RegisterRequest struct {
	// Attestation 设备信任证明
	Attestation AttestationObject `json:"attestation"`

	// Platform 平台标识
	Platform string `json:"platform"`

	// AppVersion 应用版本
	AppVersion string `json:"app_version"`
}

// RegisterResponse 注册响应
type RegisterResponse struct {
	// ClientID 服务端分配的唯一客户端标识符
	ClientID string `json:"client_id"`

	// IntegrityLevel 服务端验证后的完整性等级
	IntegrityLevel string `json:"integrity_level"`

	// RegisteredAt 注册时间戳（Unix 秒）
	RegisteredAt int64 `json:"registered_at"`
}

// BindUserRequest 由已完成设备签名校验且已登录的客户端提交。
type BindUserRequest struct {
	ClientType   string   `json:"client_type" binding:"required,oneof=web desktop mobile"`
	Platform     string   `json:"platform" binding:"required,oneof=web windows macos linux ios android"`
	AppVersion   string   `json:"app_version" binding:"required,max=32"`
	Capabilities []string `json:"capabilities" binding:"max=128"`
}

// ClientUserBinding 是 Plugin Service 进行本地插件分发前的可信客户端投影。
type ClientUserBinding struct {
	ClientID         string    `json:"client_id" db:"client_id"`
	UserID           string    `json:"user_id" db:"user_id"`
	ClientType       string    `json:"client_type" db:"client_type"`
	Platform         string    `json:"platform" db:"platform"`
	AppVersion       string    `json:"app_version" db:"app_version"`
	CapabilitiesJSON string    `json:"-" db:"capabilities_json"`
	Capabilities     []string  `json:"capabilities" db:"-"`
	Status           string    `json:"status" db:"status"`
	BoundAt          time.Time `json:"bound_at" db:"bound_at"`
}

// ─── 公钥查询 ──────────────────────────────────────────────────────────────────

// PublicKeyResponse 公钥查询响应（供网关内部调用）
type PublicKeyResponse struct {
	// ClientID 客户端标识
	ClientID string `json:"client_id"`

	// PublicKey 客户端公钥（Base64）
	PublicKey string `json:"public_key"`

	// KeyAlgorithm 密钥算法
	KeyAlgorithm string `json:"key_algorithm"`

	// IntegrityLevel 完整性等级
	IntegrityLevel string `json:"integrity_level"`

	// Status 客户端状态
	Status string `json:"status"`
}

// ─── 通用响应 ──────────────────────────────────────────────────────────────────

// APIResponse 统一 API 响应格式
type APIResponse struct {
	Code    int         `json:"code"`
	Message string      `json:"message"`
	Data    interface{} `json:"data,omitempty"`
}
