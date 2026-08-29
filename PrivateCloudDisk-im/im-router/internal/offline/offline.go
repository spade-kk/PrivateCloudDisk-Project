// Package offline 实现离线消息的存储与清理。
//
// 当 Router 发现接收方离线（或 gRPC 推送失败）时，将消息存入 Redis List
// im:offline:{userId}，等待接收方上线后由业务层重新下发。
//
// 存储格式：Protobuf 序列化后的 PushMessageCommand 二进制，便于直接重投。
// TTL 策略：每次写入刷新 key 过期时间（默认 7 天），长期不上线用户自动清理。
// 容量策略：超过 max_messages_per_user 时淘汰最旧消息（LTRIM 保留右侧最新）。
//
// 注意：Router 本身不消费用户上线事件，离线消息的重新下发由 IM Business
// 在用户上线后重新发起 PushMessageCommand（is_offline_compensation=true）触发。
package offline

import (
	"context"
	"fmt"

	"google.golang.org/protobuf/proto"

	rediscli "privateclouddisk/im-router/internal/redis"
	pb "privateclouddisk/im-router/pkg/proto"
)

// Store 离线消息存储。
type Store struct {
	redis      *rediscli.Client
	maxPerUser int
	ttlSeconds int
}

// New 创建离线消息存储实例。
func New(rdb *rediscli.Client, maxPerUser, ttlSeconds int) *Store {
	if maxPerUser < 1 {
		maxPerUser = 1000
	}
	if ttlSeconds < 1 {
		ttlSeconds = 7 * 24 * 3600
	}
	return &Store{
		redis:      rdb,
		maxPerUser: maxPerUser,
		ttlSeconds: ttlSeconds,
	}
}

// Store 将一条推送命令存入接收方的离线队列。
// 步骤：
//  1. 序列化 PushMessageCommand 为 Protobuf 二进制
//  2. RPush 入队 + 刷新 TTL
//  3. 超过容量上限时 LTRIM 淘汰最旧消息
func (s *Store) Store(ctx context.Context, cmd *pb.PushMessageCommand) error {
	if cmd == nil || cmd.ReceiverId == "" {
		return fmt.Errorf("PushMessageCommand 或 ReceiverId 为空")
	}
	payload, err := proto.Marshal(cmd)
	if err != nil {
		return fmt.Errorf("序列化 PushMessageCommand 失败: %w", err)
	}
	if err := s.redis.PushOfflineMessage(ctx, cmd.ReceiverId, payload, s.ttlSeconds); err != nil {
		return err
	}
	// 容量控制：超出上限时淘汰最旧消息
	if err := s.redis.TrimOfflineMessages(ctx, cmd.ReceiverId, s.maxPerUser); err != nil {
		// 淘汰失败不影响主流程，仅记录
		return fmt.Errorf("截断离线队列失败: %w", err)
	}
	return nil
}

// Drain 弹出指定用户的所有离线消息（按入队顺序）。
// 返回每条消息的 Protobuf 二进制（可直接反序列化为 PushMessageCommand）。
func (s *Store) Drain(ctx context.Context, userID string) ([][]byte, error) {
	if userID == "" {
		return nil, fmt.Errorf("userID 为空")
	}
	return s.redis.PopOfflineMessages(ctx, userID, s.maxPerUser)
}

// Count 查询用户当前离线消息数。
func (s *Store) Count(ctx context.Context, userID string) (int64, error) {
	return s.redis.OfflineMessageCount(ctx, userID)
}
