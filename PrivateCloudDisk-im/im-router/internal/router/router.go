// Package router 实现 IM Router 的核心路由逻辑。
//
// router.go：
//   - 查询 Redis 获取接收方当前连接的 IM Server 节点
//   - 通过 gRPC 将消息转发至目标 IM Server
//   - 用户离线或推送失败时，写入离线消息队列
//
// push_handler.go：
//   - 实现 mq.Handler，处理 PushMessageCommand
package router

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"

	"privateclouddisk/im-router/internal/config"
	"privateclouddisk/im-router/internal/grpc"
	"privateclouddisk/im-router/internal/model"
	rediscli "privateclouddisk/im-router/internal/redis"
	pb "privateclouddisk/im-router/pkg/proto"
)

// 监控指标
var (
	routeLookupTotal = promauto.NewCounterVec(prometheus.CounterOpts{
		Namespace: "im_router",
		Subsystem: "route",
		Name:      "lookup_total",
		Help:      "路由查询次数（按结果分类）",
	}, []string{"result"})

	pushTotal = promauto.NewCounterVec(prometheus.CounterOpts{
		Namespace: "im_router",
		Subsystem: "route",
		Name:      "push_total",
		Help:      "消息推送次数（按结果分类）",
	}, []string{"result"})

	pushDuration = promauto.NewHistogramVec(prometheus.HistogramOpts{
		Namespace: "im_router",
		Subsystem: "route",
		Name:      "push_duration_seconds",
		Help:      "消息推送耗时（含 gRPC 调用）",
		Buckets:   prometheus.DefBuckets,
	}, []string{"result"})

	offlineStoredTotal = promauto.NewCounterVec(prometheus.CounterOpts{
		Namespace: "im_router",
		Subsystem: "offline",
		Name:      "stored_total",
		Help:      "离线消息存储次数（按原因分类）",
	}, []string{"reason"})
)

// Router 核心路由器。
type Router struct {
	nodeID     string
	redis      *rediscli.Client
	grpc       *grpc.ClientPool
	offlineCfg config.OfflineConfig
	logger     *slog.Logger
	// offlineStore 通过 SetOfflineStore 注入，避免循环构造
	offlineStore OfflineStorer
}

// OfflineStorer 抽象离线存储能力，便于解耦与测试。
type OfflineStorer interface {
	Store(ctx context.Context, cmd *pb.PushMessageCommand) error
}

// New 创建 Router（不含 offlineStore，需后续注入）。
func New(nodeID string, rdb *rediscli.Client, pool *grpc.ClientPool, offCfg config.OfflineConfig) *Router {
	return &Router{
		nodeID:     nodeID,
		redis:      rdb,
		grpc:       pool,
		offlineCfg: offCfg,
	}
}

// SetLogger 注入日志器。
func (r *Router) SetLogger(l *slog.Logger) {
	r.logger = l
}

// SetOfflineStore 注入离线存储实现。
func (r *Router) SetOfflineStore(s OfflineStorer) {
	r.offlineStore = s
}

// PushToUser 将一条推送命令路由到接收方的 IM Server。
// 处理流程：
//  1. 查询 Redis 获取接收方连接的 IM Server 节点
//  2. 在线 → gRPC 调用 IM Server.PushMessage
//  3. 离线 / gRPC 失败 / IM Server 返回用户不在线 → 写入离线队列
func (r *Router) PushToUser(ctx context.Context, cmd *pb.PushMessageCommand) error {
	if cmd == nil {
		return errors.New("PushMessageCommand 为空")
	}
	start := time.Now()

	// 1. 路由查询
	online, node, err := r.redis.GetUserServerNode(ctx, cmd.ReceiverId)
	if err != nil {
		routeLookupTotal.WithLabelValues("error").Inc()
		pushTotal.WithLabelValues("lookup_error").Inc()
		pushDuration.WithLabelValues("lookup_error").Observe(time.Since(start).Seconds())
		return fmt.Errorf("查询用户 %s 路由失败: %w", cmd.ReceiverId, err)
	}
	if !online || node == nil {
		routeLookupTotal.WithLabelValues("offline").Inc()
		if !shouldStoreOfflineMessageType(cmd.MessageType) {
			pushTotal.WithLabelValues("ephemeral_offline_drop").Inc()
			pushDuration.WithLabelValues("ephemeral_offline_drop").Observe(time.Since(start).Seconds())
			return nil
		}
		return r.storeOffline(ctx, cmd, "user_offline")
	}
	routeLookupTotal.WithLabelValues("online").Inc()

	// 2. 构造 gRPC 推送请求
	req := buildPushRequest(cmd, node.NodeID)

	// 3. gRPC 调用
	resp, err := r.grpc.PushMessage(ctx, node.NodeID, node.GrpcAddress, req)
	if err != nil {
		// gRPC 调用失败：写入离线队列等待补偿
		pushTotal.WithLabelValues("grpc_error").Inc()
		pushDuration.WithLabelValues("grpc_error").Observe(time.Since(start).Seconds())
		if shouldStoreOfflineMessageType(cmd.MessageType) {
			_ = r.storeOffline(ctx, cmd, "grpc_error")
		}
		return fmt.Errorf("gRPC 推送失败 (user=%s node=%s): %w", cmd.ReceiverId, node.NodeID, err)
	}

	// 4. 处理 IM Server 响应
	switch resp.Code {
	case model.DeliverySuccess:
		pushTotal.WithLabelValues("success").Inc()
		pushDuration.WithLabelValues("success").Observe(time.Since(start).Seconds())
		return nil
	case model.DeliveryUserOffline:
		// 节点记录在线但实际不在线（连接刚断开），转离线存储
		pushTotal.WithLabelValues("user_offline").Inc()
		pushDuration.WithLabelValues("user_offline").Observe(time.Since(start).Seconds())
		if !shouldStoreOfflineMessageType(cmd.MessageType) {
			return nil
		}
		return r.storeOffline(ctx, cmd, "user_offline")
	default:
		// 推送失败（DeliveryFailed 或未知码）：记录但不写离线（避免毒丸）
		pushTotal.WithLabelValues("failed").Inc()
		pushDuration.WithLabelValues("failed").Observe(time.Since(start).Seconds())
		return fmt.Errorf("IM Server 推送失败 (user=%s): %s", cmd.ReceiverId, resp.Message)
	}
}

// buildPushRequest 由 PushMessageCommand 构造 gRPC PushMessageRequest。
func buildPushRequest(cmd *pb.PushMessageCommand, nodeID string) *pb.PushMessageRequest {
	traceID := ""
	if cmd.Header != nil {
		traceID = cmd.Header.TraceId
	}
	return &pb.PushMessageRequest{
		MessageId:             cmd.MessageId,
		ReceiverId:            cmd.ReceiverId,
		SenderId:              cmd.SenderId,
		ConversationId:        cmd.ConversationId,
		ConversationType:      cmd.ConversationType,
		MessageType:           cmd.MessageType,
		EnvelopeBytes:         cmd.EnvelopeBytes,
		ServerSeq:             cmd.ServerSeq,
		Timestamp:             cmd.MessageTimestamp,
		IsOfflineCompensation: cmd.IsOfflineCompensation,
		TraceId:               traceID,
	}
}

// storeOffline 写入离线队列并更新监控。
func (r *Router) storeOffline(ctx context.Context, cmd *pb.PushMessageCommand, reason string) error {
	if r.offlineStore == nil {
		return errors.New("offlineStore 未注入")
	}
	if err := r.offlineStore.Store(ctx, cmd); err != nil {
		offlineStoredTotal.WithLabelValues(reason + "_error").Inc()
		return fmt.Errorf("写入离线队列失败: %w", err)
	}
	offlineStoredTotal.WithLabelValues(reason).Inc()
	return nil
}
