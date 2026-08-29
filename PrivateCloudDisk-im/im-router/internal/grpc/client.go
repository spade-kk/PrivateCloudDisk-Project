// Package grpc 封装 IM Router 的 gRPC 客户端与服务端。
//
// client.go：gRPC 客户端连接池（Router → IM Server）
//   - 维护到每个 IM Server 节点的连接池（复用 HTTP/2 长连接）
//   - 提供 PushMessage / BatchPushMessages / CheckUserOnline / KickUser 接口
//   - 支持运行时动态发现新节点
package grpc

import (
	"context"
	"errors"
	"fmt"
	"sync"
	"sync/atomic"
	"time"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/keepalive"
	"google.golang.org/grpc/status"

	"privateclouddisk/im-router/internal/config"
	pb "privateclouddisk/im-router/pkg/proto"
)

// ErrNodeNotConnected 表示目标节点尚未建立连接（节点未注册或地址不可达）。
var ErrNodeNotConnected = errors.New("im server 节点未建立连接")

// ClientPool 维护到所有 IM Server 节点的 gRPC 连接池。
//
// 设计要点：
//   - 按 nodeID 维护独立子池，每个子池持有 pool_size_per_node 条长连接
//   - 取连接时使用原子计数器做 round-robin 负载均衡
//   - 连接惰性建立：首次访问某节点时才拨号，避免无用连接
//   - gRPC HTTP/2 多路复用使单连接即可支撑高并发，池化用于容错与吞吐提升
type ClientPool struct {
	cfg config.GRPCConfig

	mu    sync.RWMutex
	pools map[string]*nodePool // nodeID → 子池
}

// nodePool 单个 IM Server 节点的连接子池。
type nodePool struct {
	nodeID  string
	target  string
	conns   []*grpc.ClientConn
	counter uint64 // round-robin 计数器
}

// NewClientPool 创建空的 gRPC 客户端连接池。
func NewClientPool(cfg config.GRPCConfig) *ClientPool {
	return &ClientPool{
		cfg:   cfg,
		pools: make(map[string]*nodePool),
	}
}

// dial 拨号到目标地址，应用 keepalive 与消息大小限制。
func (p *ClientPool) dial(target string) (*grpc.ClientConn, error) {
	ctx, cancel := context.WithTimeout(context.Background(), p.cfg.DialTimeout)
	defer cancel()
	return grpc.DialContext(ctx, target,
		// 内部服务间通信，使用 insecure；生产环境应替换为 mTLS 凭证
		grpc.WithTransportCredentials(insecure.NewCredentials()),
		grpc.WithKeepaliveParams(keepalive.ClientParameters{
			Time:                p.cfg.KeepaliveTime,
			Timeout:             p.cfg.KeepaliveTimeout,
			PermitWithoutStream: true,
		}),
		grpc.WithDefaultCallOptions(grpc.MaxCallRecvMsgSize(p.cfg.MaxRecvMsgSize)),
		// 连接失败自动重试（gRPC 内置重试需 interceptors，此处依赖底层重连）
	)
}

// clientFor 返回目标节点的 gRPC 客户端，按需建立连接池。
func (p *ClientPool) clientFor(nodeID, addr string) (pb.IMServerServiceClient, error) {
	if nodeID == "" || addr == "" {
		return nil, fmt.Errorf("nodeID 或 addr 为空")
	}

	// 快路径：读锁获取已有子池
	p.mu.RLock()
	np, ok := p.pools[nodeID]
	p.mu.RUnlock()
	if ok {
		return np.pick()
	}

	// 慢路径：加写锁创建子池
	p.mu.Lock()
	defer p.mu.Unlock()
	// double-check
	if np, ok = p.pools[nodeID]; ok {
		return np.pick()
	}

	np = &nodePool{nodeID: nodeID, target: addr}
	poolSize := p.cfg.PoolSizePerNode
	if poolSize < 1 {
		poolSize = 1
	}
	for i := 0; i < poolSize; i++ {
		cc, err := p.dial(addr)
		if err != nil {
			// 部分连接失败不影响整体，只要有 1 条可用即可
			// 全部失败则清理并返回错误
			if len(np.conns) == 0 {
				return nil, fmt.Errorf("拨号 %s 失败: %w", addr, err)
			}
			continue
		}
		np.conns = append(np.conns, cc)
	}
	p.pools[nodeID] = np
	return np.pick()
}

// pick 从子池中 round-robin 选择一个连接返回客户端。
func (np *nodePool) pick() (pb.IMServerServiceClient, error) {
	n := uint64(len(np.conns))
	if n == 0 {
		return nil, ErrNodeNotConnected
	}
	idx := atomic.AddUint64(&np.counter, 1) % n
	return pb.NewIMServerServiceClient(np.conns[idx]), nil
}

// PushMessage 向目标 IM Server 节点推送单条消息。
func (p *ClientPool) PushMessage(ctx context.Context, nodeID, addr string, req *pb.PushMessageRequest) (*pb.PushMessageResponse, error) {
	cli, err := p.clientFor(nodeID, addr)
	if err != nil {
		return nil, err
	}
	resp, err := cli.PushMessage(ctx, req)
	if err != nil {
		return nil, fmt.Errorf("gRPC PushMessage 调用失败 (node=%s): %w", nodeID, err)
	}
	return resp, nil
}

// BatchPushMessages 向目标 IM Server 节点批量推送消息（群聊场景）。
func (p *ClientPool) BatchPushMessages(ctx context.Context, nodeID, addr string, req *pb.BatchPushMessageRequest) (*pb.BatchPushMessageResponse, error) {
	cli, err := p.clientFor(nodeID, addr)
	if err != nil {
		return nil, err
	}
	resp, err := cli.BatchPushMessages(ctx, req)
	if err != nil {
		return nil, fmt.Errorf("gRPC BatchPushMessages 调用失败 (node=%s): %w", nodeID, err)
	}
	return resp, nil
}

// CheckUserOnline 查询用户在目标节点是否在线。
func (p *ClientPool) CheckUserOnline(ctx context.Context, nodeID, addr string, userID string) (*pb.CheckUserOnlineResponse, error) {
	cli, err := p.clientFor(nodeID, addr)
	if err != nil {
		return nil, err
	}
	resp, err := cli.CheckUserOnline(ctx, &pb.CheckUserOnlineRequest{UserId: userID})
	if err != nil {
		return nil, fmt.Errorf("gRPC CheckUserOnline 调用失败 (node=%s): %w", nodeID, err)
	}
	return resp, nil
}

// KickUser 在目标节点上踢出指定用户。
func (p *ClientPool) KickUser(ctx context.Context, nodeID, addr, userID, reason string) (*pb.KickUserResponse, error) {
	cli, err := p.clientFor(nodeID, addr)
	if err != nil {
		return nil, err
	}
	resp, err := cli.KickUser(ctx, &pb.KickUserRequest{UserId: userID, Reason: reason})
	if err != nil {
		return nil, fmt.Errorf("gRPC KickUser 调用失败 (node=%s): %w", nodeID, err)
	}
	return resp, nil
}

// RemoveNode 移除指定节点的连接池（节点下线时调用）。
func (p *ClientPool) RemoveNode(nodeID string) {
	p.mu.Lock()
	defer p.mu.Unlock()
	np, ok := p.pools[nodeID]
	if !ok {
		return
	}
	delete(p.pools, nodeID)
	for _, cc := range np.conns {
		_ = cc.Close()
	}
}

// Close 关闭所有连接池。
func (p *ClientPool) Close() error {
	p.mu.Lock()
	defer p.mu.Unlock()
	var errs []error
	for id, np := range p.pools {
		for _, cc := range np.conns {
			if err := cc.Close(); err != nil {
				errs = append(errs, fmt.Errorf("关闭节点 %s 连接失败: %w", id, err))
			}
		}
	}
	p.pools = make(map[string]*nodePool)
	if len(errs) > 0 {
		return fmt.Errorf("关闭连接池出现错误: %v", errs)
	}
	return nil
}

// Healthy 检查连接池是否就绪（至少有一个节点连接）。
func (p *ClientPool) Healthy() bool {
	p.mu.RLock()
	defer p.mu.RUnlock()
	for _, np := range p.pools {
		if len(np.conns) > 0 {
			return true
		}
	}
	return false
}

// NodeCount 返回当前已建立连接的节点数。
func (p *ClientPool) NodeCount() int {
	p.mu.RLock()
	defer p.mu.RUnlock()
	return len(p.pools)
}

// GRPCStatus 提取 gRPC 错误的状态码字符串，便于日志与监控。
func GRPCStatus(err error) string {
	if err == nil {
		return "ok"
	}
	if st, ok := status.FromError(err); ok {
		return st.Code().String()
	}
	return "unknown"
}

// 防止 time 包未使用（保留以便后续扩展超时指标）。
var _ = time.Second
