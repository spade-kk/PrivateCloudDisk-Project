// Package redis 封装 IM Router 所需的 Redis 操作。
//
// 主要职责：
//   - 维护带连接池的 Redis 客户端
//   - 查询用户当前连接的 IM Server 节点（含节点存活校验）
//   - 查询 IM Server 节点注册信息
//   - 节点活跃性检测（心跳超时判断）
//   - 过期节点与用户映射清理
//   - 维护离线消息队列（List 结构）
//
// Redis Key 规范（与 im-common ImConstants 保持一致）：
//
//	im:user:{userId}     → {nodeId}                  用户连接节点映射（TTL=90s）
//	im:server:{nodeId}   → JSON(ServerNode)           IM Server 节点信息（含 lastHeartbeat）
//	im:servers           → Set<nodeId>                所有活跃 IM Server 节点列表
//	im:offline:{userId}  → List<Protobuf bytes>       离线消息队列
package redis

import (
	"context"
	"encoding/json"
	"fmt"
	"math"
	"time"

	"github.com/redis/go-redis/v9"

	"privateclouddisk/im-router/internal/model"
)

// Key 前缀与公共常量（与 Java 侧 ImConstants 对齐）。
const (
	keyUserServer  = "im:user:%s"    // 用户 → 节点映射
	keyServerNode  = "im:server:%s"  // 节点信息（JSON，含 lastHeartbeat）
	keyServerSet   = "im:servers"    // 活跃节点集合（Set）
	keyOfflineList = "im:offline:%s" // 离线消息队列

	// 心跳超时阈值（秒）：IM Server 每 30s 心跳一次，90s 无心跳视为离线
	heartbeatTimeoutSeconds = 90

	// 过期节点清理扫描批次大小
	cleanupScanBatchSize = 100
)

// Client 封装 Redis 客户端与 Router 所需的业务操作。
type Client struct {
	cli *redis.Client
}

// New 根据配置创建 Redis 客户端（带连接池）。
func New(host string, port, db, poolSize int, password string) (*Client, error) {
	cli := redis.NewClient(&redis.Options{
		Addr:         fmt.Sprintf("%s:%d", host, port),
		Password:     password,
		DB:           db,
		PoolSize:     poolSize,
		MinIdleConns: poolSize / 5,
		DialTimeout:  5 * time.Second,
		ReadTimeout:  3 * time.Second,
		WriteTimeout: 3 * time.Second,
		PoolTimeout:  4 * time.Second,
	})

	// 连接性探测
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	if err := cli.Ping(ctx).Err(); err != nil {
		_ = cli.Close()
		return nil, fmt.Errorf("redis ping 失败: %w", err)
	}
	return &Client{cli: cli}, nil
}

// Raw 返回底层 *redis.Client，便于需要原生命令的场景使用。
func (c *Client) Raw() *redis.Client { return c.cli }

// Close 关闭 Redis 连接。
func (c *Client) Close() error { return c.cli.Close() }

// Ping 探测 Redis 连通性。
func (c *Client) Ping(ctx context.Context) error {
	return c.cli.Ping(ctx).Err()
}

// ----------------------------------------------------------------
// 用户路由查询（增强版：含节点存活校验）
// ----------------------------------------------------------------

// GetUserServerNode 查询用户当前连接的 IM Server 节点。
//
// 增强校验（v3.0）：
//  1. GET im:user:{userId} → 获取 nodeId
//  2. SISMEMBER im:servers {nodeId} → 验证节点仍在活跃集合中
//  3. GET im:server:{nodeId} → 获取节点详细信息
//  4. 检查 lastHeartbeat 是否超时（>90s 视为离线）
//  5. 若节点不在活跃集合或心跳超时 → 视为离线，异步清理过期映射
//
// 返回值：
//   - online: 用户是否在线（映射存在 AND 节点活跃 AND 心跳未超时）
//   - node: 在线时返回节点信息，离线时为 nil
//   - err: Redis 异常
func (c *Client) GetUserServerNode(ctx context.Context, userID string) (online bool, node *model.ServerNode, err error) {
	if userID == "" {
		return false, nil, fmt.Errorf("userID 为空")
	}

	// 1. 查询用户连接的节点 ID
	userKey := fmt.Sprintf(keyUserServer, userID)
	val, err := c.cli.Get(ctx, userKey).Result()
	if err == redis.Nil {
		return false, nil, nil
	}
	if err != nil {
		return false, nil, fmt.Errorf("查询用户节点映射失败: %w", err)
	}

	// 值格式: 纯 nodeId（v3.0 不再使用 "server:" 前缀）
	nodeID := parseServerValue(val)
	if nodeID == "" {
		return false, nil, nil
	}

	// 2. 兜底校验：节点是否仍在活跃集合中
	active, err := c.cli.SIsMember(ctx, keyServerSet, nodeID).Result()
	if err != nil {
		return false, nil, fmt.Errorf("查询节点活跃状态失败: %w", err)
	}
	if !active {
		// 节点已从活跃集合中移除（可能已下线），视为离线
		// 异步清理过期的用户映射
		go c.cleanupStaleUserMapping(userKey)
		return false, nil, nil
	}

	// 3. 查询节点详细信息
	node, err = c.GetServerNode(ctx, nodeID)
	if err != nil {
		return false, nil, err
	}
	if node == nil {
		// 节点映射存在但节点信息已失效，视为离线
		return false, nil, nil
	}

	// 4. 检查心跳是否超时
	if c.isHeartbeatStale(node.LastHeartbeatAt) {
		// 心跳超时，节点可能已宕机，视为离线
		go c.cleanupStaleUserMapping(userKey)
		return false, nil, nil
	}

	return true, node, nil
}

// isHeartbeatStale 判断心跳时间戳是否已超时。
// lastHeartbeat 为 Unix 毫秒时间戳，0 表示无心跳数据。
func (c *Client) isHeartbeatStale(lastHeartbeatMs int64) bool {
	if lastHeartbeatMs <= 0 {
		return true // 无心跳数据，视为过期
	}
	elapsed := time.Since(time.UnixMilli(lastHeartbeatMs))
	return elapsed.Seconds() > heartbeatTimeoutSeconds
}

// cleanupStaleUserMapping 异步清理过期的用户映射（后台，不阻塞路由查询）。
func (c *Client) cleanupStaleUserMapping(userKey string) {
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	_ = c.cli.Del(ctx, userKey).Err()
}

// parseServerValue 解析 im:user:{userId} 的值。
// v3.0 格式：纯 nodeId（如 "im-server-1"）
// 兼容旧格式："server:{nodeId}"
func parseServerValue(val string) string {
	const prefix = "server:"
	if len(val) > len(prefix) && val[:len(prefix)] == prefix {
		return val[len(prefix):]
	}
	return val
}

// ----------------------------------------------------------------
// 节点信息查询
// ----------------------------------------------------------------

// GetServerNode 查询 IM Server 节点注册信息。
func (c *Client) GetServerNode(ctx context.Context, nodeID string) (*model.ServerNode, error) {
	if nodeID == "" {
		return nil, fmt.Errorf("nodeID 为空")
	}
	key := fmt.Sprintf(keyServerNode, nodeID)
	raw, err := c.cli.Get(ctx, key).Bytes()
	if err == redis.Nil {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("查询节点信息失败: %w", err)
	}
	var node model.ServerNode
	if err := json.Unmarshal(raw, &node); err != nil {
		return nil, fmt.Errorf("解析节点信息 JSON 失败: %w", err)
	}
	// 兼容新老两种节点信息字段（grpcAddress/lastHeartbeatAt 与 grpcHost/grpcPort/lastHeartbeat）
	node.Normalize()
	return &node, nil
}

// ListServerNodes 返回所有注册的 IM Server 节点。
func (c *Client) ListServerNodes(ctx context.Context) ([]*model.ServerNode, error) {
	nodeIDs, err := c.cli.SMembers(ctx, keyServerSet).Result()
	if err != nil {
		return nil, fmt.Errorf("查询节点集合失败: %w", err)
	}
	nodes := make([]*model.ServerNode, 0, len(nodeIDs))
	for _, id := range nodeIDs {
		n, err := c.GetServerNode(ctx, id)
		if err != nil {
			// 单个节点查询失败不影响整体
			continue
		}
		if n != nil {
			nodes = append(nodes, n)
		}
	}
	return nodes, nil
}

// GetActiveNodeIDs 返回所有活跃节点 ID 列表（不查询详细信息，性能更高）。
// 用于 gRPC 连接池维护和健康检查。
func (c *Client) GetActiveNodeIDs(ctx context.Context) ([]string, error) {
	return c.cli.SMembers(ctx, keyServerSet).Result()
}

// IsNodeActive 判断指定节点是否在活跃集合中且心跳未超时。
func (c *Client) IsNodeActive(ctx context.Context, nodeID string) bool {
	// 1. 检查是否在活跃集合中
	active, err := c.cli.SIsMember(ctx, keyServerSet, nodeID).Result()
	if err != nil || !active {
		return false
	}

	// 2. 检查心跳是否超时
	node, err := c.GetServerNode(ctx, nodeID)
	if err != nil || node == nil {
		return false
	}
	return !c.isHeartbeatStale(node.LastHeartbeatAt)
}

// ----------------------------------------------------------------
// 过期节点清理
// ----------------------------------------------------------------

// CleanupStaleNodes 扫描所有活跃节点，移除心跳超时的节点及其用户映射。
//
// 清理流程：
//  1. SMEMBERS im:servers → 获取所有节点 ID
//  2. 逐个检查 im:server:{nodeId} 中的 lastHeartbeat
//  3. 心跳超时（>90s）→ SREM im:servers + DEL im:server:{nodeId}
//  4. SCAN im:user:* 清理值等于该 nodeId 的用户映射
//
// 返回被清理的节点 ID 列表。
func (c *Client) CleanupStaleNodes(ctx context.Context) ([]string, error) {
	nodeIDs, err := c.cli.SMembers(ctx, keyServerSet).Result()
	if err != nil {
		return nil, fmt.Errorf("查询活跃节点集合失败: %w", err)
	}

	var cleaned []string
	for _, nodeID := range nodeIDs {
		node, err := c.GetServerNode(ctx, nodeID)
		if err != nil {
			continue
		}

		// 节点不存在或心跳超时 → 清理
		if node == nil || c.isHeartbeatStale(node.LastHeartbeatAt) {
			// 从活跃集合中移除
			_ = c.cli.SRem(ctx, keyServerSet, nodeID).Err()
			// 删除节点详细信息
			_ = c.cli.Del(ctx, fmt.Sprintf(keyServerNode, nodeID)).Err()
			// 清理关联的用户映射
			cleanedUsers := c.cleanupUserMappingsForNode(ctx, nodeID)
			cleaned = append(cleaned, nodeID)
			_ = cleanedUsers // 日志由调用方输出
		}
	}
	return cleaned, nil
}

// cleanupUserMappingsForNode 清理指定节点的所有用户映射。
// 使用 SCAN 遍历 im:user:* 键，删除值等于 nodeID 的键。
// 返回清理的映射数量。
func (c *Client) cleanupUserMappingsForNode(ctx context.Context, nodeID string) int {
	var cleaned int
	var cursor uint64
	for {
		keys, nextCursor, err := c.cli.Scan(ctx, cursor, "im:user:*", cleanupScanBatchSize).Result()
		if err != nil {
			return cleaned
		}
		for _, key := range keys {
			val, err := c.cli.Get(ctx, key).Result()
			if err != nil {
				continue
			}
			// 兼容新旧两种值格式
			if val == nodeID || parseServerValue(val) == nodeID {
				_ = c.cli.Del(ctx, key).Err()
				cleaned++
			}
		}
		cursor = nextCursor
		if cursor == 0 {
			break
		}
		// 控制扫描速率，避免阻塞 Redis
		if cleaned > 1000 {
			break
		}
	}
	return cleaned
}

// ----------------------------------------------------------------
// 离线消息队列操作
// ----------------------------------------------------------------

// PushOfflineMessage 将一条离线消息推入用户的离线队列尾部（右侧）。
// 同时为整个列表设置 TTL，避免无限增长。
func (c *Client) PushOfflineMessage(ctx context.Context, userID string, payload []byte, ttlSeconds int) error {
	key := fmt.Sprintf(keyOfflineList, userID)
	pipe := c.cli.Pipeline()
	pipe.RPush(ctx, key, payload)
	pipe.Expire(ctx, key, time.Duration(ttlSeconds)*time.Second)
	if _, err := pipe.Exec(ctx); err != nil {
		return fmt.Errorf("写入离线队列失败: %w", err)
	}
	return nil
}

// PopOfflineMessages 弹出用户所有离线消息（从左到右，按入队顺序）。
func (c *Client) PopOfflineMessages(ctx context.Context, userID string, max int) ([][]byte, error) {
	key := fmt.Sprintf(keyOfflineList, userID)
	res, err := c.cli.LPopCount(ctx, key, max).Result()
	if err != nil && err != redis.Nil {
		return nil, fmt.Errorf("弹出离线队列失败: %w", err)
	}
	_ = c.cli.Del(ctx, key).Err()
	out := make([][]byte, 0, len(res))
	for _, s := range res {
		out = append(out, []byte(s))
	}
	return out, nil
}

// OfflineMessageCount 查询用户当前离线消息数量。
func (c *Client) OfflineMessageCount(ctx context.Context, userID string) (int64, error) {
	key := fmt.Sprintf(keyOfflineList, userID)
	n, err := c.cli.LLen(ctx, key).Result()
	if err != nil {
		return 0, fmt.Errorf("查询离线队列长度失败: %w", err)
	}
	return n, nil
}

// TrimOfflineMessages 截断离线队列，保留最新的 max 条（左侧截断）。
func (c *Client) TrimOfflineMessages(ctx context.Context, userID string, max int) error {
	key := fmt.Sprintf(keyOfflineList, userID)
	n, err := c.cli.LLen(ctx, key).Result()
	if err != nil {
		return err
	}
	if n <= int64(max) {
		return nil
	}
	start := n - int64(max)
	if _, err := c.cli.LTrim(ctx, key, start, -1).Result(); err != nil {
		return fmt.Errorf("截断离线队列失败: %w", err)
	}
	return nil
}

// 确保 math 包被使用（用于未来可能的指数退避计算）
var _ = math.MaxInt32
