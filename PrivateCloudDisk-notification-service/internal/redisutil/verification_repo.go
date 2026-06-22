// Package redisutil 提供验证码服务的 Redis 存储层。
// 封装所有 Redis 操作，包括验证码存储、频率控制、Token 管理、防爆破计数。
package redisutil

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"time"

	"github.com/redis/go-redis/v9"

	"github.com/privateclouddisk/notification-service/internal/domain"
)

// VerificationRepo 验证码 Redis 存储层
type VerificationRepo struct {
	client *redis.Client
}

// NewVerificationRepo 创建验证码存储层
func NewVerificationRepo(client *redis.Client) *VerificationRepo {
	return &VerificationRepo{client: client}
}

// =============================================================================
// 验证码存储
// =============================================================================

// StoreCode 存储验证码到 Redis
// key 格式：verif:code:{targetType}:{targetHash}:{purpose}:{ipHash}
// TTL: 5 分钟
func (r *VerificationRepo) StoreCode(ctx context.Context, targetType, targetHash, purpose, ipHash, code string) error {
	key := buildCodeKey(targetType, targetHash, purpose, ipHash)
	return r.client.Set(ctx, key, code, domain.CodeExpireSeconds*time.Second).Err()
}

// GetCode 从 Redis 获取验证码
func (r *VerificationRepo) GetCode(ctx context.Context, targetType, targetHash, purpose, ipHash string) (string, error) {
	key := buildCodeKey(targetType, targetHash, purpose, ipHash)
	return r.client.Get(ctx, key).Result()
}

// DeleteCode 删除验证码（一次性使用，校验后删除）
func (r *VerificationRepo) DeleteCode(ctx context.Context, targetType, targetHash, purpose, ipHash string) error {
	key := buildCodeKey(targetType, targetHash, purpose, ipHash)
	return r.client.Del(ctx, key).Err()
}

// =============================================================================
// 频率控制
// =============================================================================

// IncrRateLimit 递增发送频率计数器
// key 格式：verif:rate:{targetType}:{targetHash}:{purpose}:{ipHash}
// TTL: 1 小时，记录同一 IP 对同一目标同一用途的发送次数
func (r *VerificationRepo) IncrRateLimit(ctx context.Context, targetType, targetHash, purpose, ipHash string) (int64, error) {
	key := buildRateKey(targetType, targetHash, purpose, ipHash)
	count, err := r.client.Incr(ctx, key).Result()
	if err != nil {
		return 0, err
	}
	// 首次设置 TTL
	if count == 1 {
		r.client.Expire(ctx, key, 1*time.Hour)
	}
	return count, nil
}

// =============================================================================
// 重发间隔
// =============================================================================

// SetLastSendTime 记录上次发送时间戳
// key 格式：verif:last:{targetType}:{targetHash}:{purpose}:{ipHash}
// TTL: 60 秒
func (r *VerificationRepo) SetLastSendTime(ctx context.Context, targetType, targetHash, purpose, ipHash string) error {
	key := buildLastKey(targetType, targetHash, purpose, ipHash)
	return r.client.Set(ctx, key, time.Now().UnixMilli(), domain.ResendIntervalSeconds*time.Second).Err()
}

// GetLastSendTime 获取上次发送时间戳
func (r *VerificationRepo) GetLastSendTime(ctx context.Context, targetType, targetHash, purpose, ipHash string) (int64, error) {
	key := buildLastKey(targetType, targetHash, purpose, ipHash)
	val, err := r.client.Get(ctx, key).Int64()
	if err == redis.Nil {
		return 0, nil
	}
	return val, err
}

// =============================================================================
// Resend Token 管理
// =============================================================================

// StoreToken 存储不透明 resend token
// key 格式：verif:token:{tokenUUID}
// TTL: 10 分钟
func (r *VerificationRepo) StoreToken(ctx context.Context, token string, data *domain.ResendTokenData, ttl time.Duration) error {
	key := buildTokenKey(token)
	jsonStr, err := data.ToJSON()
	if err != nil {
		return fmt.Errorf("token 序列化失败: %w", err)
	}
	return r.client.Set(ctx, key, jsonStr, ttl).Err()
}

// GetToken 获取 resend token 数据
func (r *VerificationRepo) GetToken(ctx context.Context, token string) (*domain.ResendTokenData, error) {
	key := buildTokenKey(token)
	jsonStr, err := r.client.Get(ctx, key).Result()
	if err == redis.Nil {
		return nil, nil // token 不存在或已过期
	}
	if err != nil {
		return nil, err
	}
	return domain.ResendTokenDataFromJSON(jsonStr)
}

// UpdateTokenRemaining 更新 token 的剩余次数（不重新颁发 token）
func (r *VerificationRepo) UpdateTokenRemaining(ctx context.Context, token string, data *domain.ResendTokenData) error {
	key := buildTokenKey(token)
	ttl, err := r.client.TTL(ctx, key).Result()
	if err != nil || ttl <= 0 {
		ttl = domain.ResendTokenTTLSeconds * time.Second
	}
	jsonStr, err := data.ToJSON()
	if err != nil {
		return fmt.Errorf("token 序列化失败: %w", err)
	}
	return r.client.Set(ctx, key, jsonStr, ttl).Err()
}

// DeleteToken 删除 token
func (r *VerificationRepo) DeleteToken(ctx context.Context, token string) error {
	key := buildTokenKey(token)
	return r.client.Del(ctx, key).Err()
}

// GetTokenTTL 获取 token 剩余 TTL
func (r *VerificationRepo) GetTokenTTL(ctx context.Context, token string) (time.Duration, error) {
	key := buildTokenKey(token)
	return r.client.TTL(ctx, key).Result()
}

// =============================================================================
// 防爆破：验证码校验失败次数
// =============================================================================

// IncrCodeAttempts 递增验证码校验失败次数
// key 格式：verif:attempts:{targetType}:{targetHash}:{purpose}:{ipHash}
// TTL: 15 分钟
func (r *VerificationRepo) IncrCodeAttempts(ctx context.Context, targetType, targetHash, purpose, ipHash string) (int64, error) {
	key := buildAttemptsKey(targetType, targetHash, purpose, ipHash)
	count, err := r.client.Incr(ctx, key).Result()
	if err != nil {
		return 0, err
	}
	if count == 1 {
		r.client.Expire(ctx, key, domain.CodeFailureWindowSeconds*time.Second)
	}
	return count, nil
}

// GetCodeAttempts 获取当前失败次数
func (r *VerificationRepo) GetCodeAttempts(ctx context.Context, targetType, targetHash, purpose, ipHash string) (int64, error) {
	key := buildAttemptsKey(targetType, targetHash, purpose, ipHash)
	val, err := r.client.Get(ctx, key).Int64()
	if err == redis.Nil {
		return 0, nil
	}
	return val, err
}

// ClearCodeAttempts 清除验证码校验失败计数
func (r *VerificationRepo) ClearCodeAttempts(ctx context.Context, targetType, targetHash, purpose, ipHash string) error {
	key := buildAttemptsKey(targetType, targetHash, purpose, ipHash)
	return r.client.Del(ctx, key).Err()
}

// =============================================================================
// 向后兼容：旧版注册接口防爆破
// =============================================================================

// IncrRegisterAttempts 递增旧版注册接口验证码失败次数（仅 IP 维度）
func (r *VerificationRepo) IncrRegisterAttempts(ctx context.Context, ipHash string) (int64, error) {
	key := "verif:register:attempts:" + ipHash
	count, err := r.client.Incr(ctx, key).Result()
	if err != nil {
		return 0, err
	}
	if count == 1 {
		r.client.Expire(ctx, key, domain.CodeFailureWindowSeconds*time.Second)
	}
	return count, nil
}

// ClearRegisterAttempts 清除旧版注册接口验证码失败计数
func (r *VerificationRepo) ClearRegisterAttempts(ctx context.Context, ipHash string) error {
	key := "verif:register:attempts:" + ipHash
	return r.client.Del(ctx, key).Err()
}

// =============================================================================
// 向后兼容：旧版验证码存储（供事件消费者使用）
// =============================================================================

// StoreLegacyEmailCode 存储旧版邮箱验证码
func (r *VerificationRepo) StoreLegacyEmailCode(ctx context.Context, email, code string, expireSec int) error {
	key := domain.PrefixEmailLegacy + email
	return r.client.Set(ctx, key, code, time.Duration(expireSec)*time.Second).Err()
}

// StoreLegacyPhoneCode 存储旧版手机验证码
func (r *VerificationRepo) StoreLegacyPhoneCode(ctx context.Context, phone, code string, expireSec int) error {
	key := domain.PrefixPhoneLegacy + phone
	return r.client.Set(ctx, key, code, time.Duration(expireSec)*time.Second).Err()
}

// CheckLegacyRateLimit 检查旧版频率限制
func (r *VerificationRepo) CheckLegacyRateLimit(ctx context.Context, verifyKey string) (bool, error) {
	rateKey := verifyKey + domain.SuffixRateLegacy
	count, err := r.client.Incr(ctx, rateKey).Result()
	if err != nil {
		return false, err
	}
	if count == 1 {
		r.client.Expire(ctx, rateKey, 1*time.Hour)
	}
	return count <= domain.MaxSendsPerHour, nil
}

// =============================================================================
// Redis Key 构建（私有）
// =============================================================================

func buildCodeKey(targetType, targetHash, purpose, ipHash string) string {
	return domain.PrefixCode + targetType + ":" + targetHash + ":" + purpose + ":" + ipHash
}

func buildRateKey(targetType, targetHash, purpose, ipHash string) string {
	return domain.PrefixRate + targetType + ":" + targetHash + ":" + purpose + ":" + ipHash
}

func buildLastKey(targetType, targetHash, purpose, ipHash string) string {
	return domain.PrefixLast + targetType + ":" + targetHash + ":" + purpose + ":" + ipHash
}

func buildTokenKey(token string) string {
	return domain.PrefixToken + token
}

func buildAttemptsKey(targetType, targetHash, purpose, ipHash string) string {
	return domain.PrefixAttempts + targetType + ":" + targetHash + ":" + purpose + ":" + ipHash
}

// =============================================================================
// SHA-256 哈希工具
// =============================================================================

// SHA256 计算 SHA-256 哈希，用于保护 Redis key 中的敏感信息
func SHA256(input string) string {
	hash := sha256.Sum256([]byte(input))
	return hex.EncodeToString(hash[:])
}