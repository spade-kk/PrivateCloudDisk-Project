package redisutil

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/privateclouddisk/client-registration-service/internal/domain"
	"github.com/redis/go-redis/v9"
)

// ClientRedisRepo 客户端公钥 Redis 缓存仓库
//
// 实现缓存优先（Cache-Aside）模式：
//  1. 查询时先查 Redis
//  2. Redis 未命中则查数据库
//  3. 数据库查询结果回写 Redis
//  4. 注册时同时写入 DB 和 Redis
type ClientRedisRepo struct {
	client *redis.Client
	ttl    time.Duration
}

// NewClientRedisRepo 创建新的 Redis 缓存仓库
func NewClientRedisRepo(client *redis.Client, ttl time.Duration) *ClientRedisRepo {
	return &ClientRedisRepo{
		client: client,
		ttl:    ttl,
	}
}

// ─── 公钥缓存 ──────────────────────────────────────────────────────────────────

// CachePublicKey 将客户端公钥信息缓存到 Redis
//
// Key 格式: pcd:client:pubkey:{client_id}
// Value: JSON 序列化的 PublicKeyResponse
func (r *ClientRedisRepo) CachePublicKey(ctx context.Context, resp *domain.PublicKeyResponse) error {
	key := pubkeyKey(resp.ClientID)
	data, err := json.Marshal(resp)
	if err != nil {
		return fmt.Errorf("序列化公钥信息失败: %w", err)
	}

	return r.client.Set(ctx, key, data, r.ttl).Err()
}

// GetPublicKey 从 Redis 获取客户端公钥缓存
//
// 返回 nil 表示缓存未命中。
func (r *ClientRedisRepo) GetPublicKey(ctx context.Context, clientID string) (*domain.PublicKeyResponse, error) {
	key := pubkeyKey(clientID)
	data, err := r.client.Get(ctx, key).Bytes()
	if err != nil {
		if err == redis.Nil {
			return nil, nil // 缓存未命中
		}
		return nil, fmt.Errorf("Redis 查询公钥失败: %w", err)
	}

	var resp domain.PublicKeyResponse
	if err := json.Unmarshal(data, &resp); err != nil {
		return nil, fmt.Errorf("反序列化公钥信息失败: %w", err)
	}

	return &resp, nil
}

// InvalidatePublicKey 使客户端公钥缓存失效
func (r *ClientRedisRepo) InvalidatePublicKey(ctx context.Context, clientID string) error {
	return r.client.Del(ctx, pubkeyKey(clientID)).Err()
}

// ─── 挑战值缓存 ────────────────────────────────────────────────────────────────

// CacheChallenge 缓存挑战值
//
// Key 格式: pcd:client:challenge:{challenge}
// Value: 客户端公钥
func (r *ClientRedisRepo) CacheChallenge(ctx context.Context, challenge, publicKey string, ttl time.Duration) error {
	key := challengeKey(challenge)
	return r.client.Set(ctx, key, publicKey, ttl).Err()
}

// GetChallenge 获取挑战值关联的公钥
func (r *ClientRedisRepo) GetChallenge(ctx context.Context, challenge string) (string, error) {
	key := challengeKey(challenge)
	publicKey, err := r.client.Get(ctx, key).Result()
	if err != nil {
		if err == redis.Nil {
			return "", nil
		}
		return "", fmt.Errorf("Redis 查询挑战值失败: %w", err)
	}
	return publicKey, nil
}

// MarkChallengeUsed 标记挑战值已使用（删除）
func (r *ClientRedisRepo) MarkChallengeUsed(ctx context.Context, challenge string) error {
	return r.client.Del(ctx, challengeKey(challenge)).Err()
}

// ─── 设备注册状态 ──────────────────────────────────────────────────────────────

// CheckDeviceRegistered 检查设备是否已注册
//
// Key 格式: pcd:client:device:{device_id}
func (r *ClientRedisRepo) CheckDeviceRegistered(ctx context.Context, deviceID string) (bool, error) {
	key := deviceKey(deviceID)
	exists, err := r.client.Exists(ctx, key).Result()
	if err != nil {
		return false, fmt.Errorf("Redis 检查设备注册状态失败: %w", err)
	}
	return exists > 0, nil
}

// MarkDeviceRegistered 标记设备已注册
func (r *ClientRedisRepo) MarkDeviceRegistered(ctx context.Context, deviceID, clientID string, ttl time.Duration) error {
	key := deviceKey(deviceID)
	return r.client.Set(ctx, key, clientID, ttl).Err()
}

// ─── Key 生成 ──────────────────────────────────────────────────────────────────

func pubkeyKey(clientID string) string {
	return fmt.Sprintf("pcd:client:pubkey:%s", clientID)
}

func challengeKey(challenge string) string {
	return fmt.Sprintf("pcd:client:challenge:%s", challenge)
}

func deviceKey(deviceID string) string {
	return fmt.Sprintf("pcd:client:device:%s", deviceID)
}