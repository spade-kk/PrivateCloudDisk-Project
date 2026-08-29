// Package model 定义 IM Router 内部使用的领域模型。
//
// 这些模型用于在各内部模块（mq / router / grpc / redis / offline）之间
// 传递数据，与 protobuf 生成的 RPC/ MQ 消息体相互隔离，避免业务逻辑
// 直接依赖传输层结构。
package model

import (
	"fmt"
	"time"
)

// ServerNode 表示一个 IM Server 节点的注册信息。
// 存储于 Redis: im:server:{nodeId} → JSON(ServerNode)
type ServerNode struct {
	// 节点 ID（全局唯一，如 "im-server-1"）
	NodeID string `json:"nodeId"`
	// gRPC 监听地址（host:port），Router 通过此地址调用 IM Server
	GrpcAddress string `json:"grpcAddress"`
	// 节点对外暴露的 WebSocket 地址
	WsAddress string `json:"wsAddress,omitempty"`
	// 节点所在机房 / 可用区
	Region string `json:"region,omitempty"`
	// 节点当前在线连接数（由 IM Server 心跳上报）
	OnlineCount int64 `json:"onlineCount,omitempty"`
	// 节点权重（用于负载均衡选择，预留）
	Weight int `json:"weight,omitempty"`
	// 节点状态: 1=健康, 2=降级, 3=下线
	Status int `json:"status,omitempty"`
	// 最近一次心跳时间（Unix 毫秒）
	LastHeartbeatAt int64 `json:"lastHeartbeatAt,omitempty"`

	// 兼容旧格式字段（im-server 早期将 gRPC 拆为 grpcHost/grpcPort、心跳写为 lastHeartbeat）
	GrpcHost      string `json:"grpcHost,omitempty"`
	GrpcPort      int    `json:"grpcPort,omitempty"`
	LastHeartbeat int64  `json:"lastHeartbeat,omitempty"`
}

// Normalize 兼容新老两种节点信息 JSON 格式。
// 新格式（im-server v3.0 修复后）：grpcAddress + lastHeartbeatAt；
// 旧格式（历史 Redis 残留数据）：grpcHost/grpcPort + lastHeartbeat。
// 在解析后调用，统一字段值，避免节点被误判为心跳超时或 gRPC 地址为空。
func (n *ServerNode) Normalize() {
	if n == nil {
		return
	}
	// gRPC 地址：优先新格式；旧格式由 grpcHost:grpcPort 拼装
	if n.GrpcAddress == "" && n.GrpcHost != "" {
		if n.GrpcPort > 0 {
			n.GrpcAddress = fmt.Sprintf("%s:%d", n.GrpcHost, n.GrpcPort)
		} else {
			n.GrpcAddress = n.GrpcHost
		}
	}
	// 心跳时间戳：优先新格式 lastHeartbeatAt；旧格式为 lastHeartbeat
	if n.LastHeartbeatAt <= 0 && n.LastHeartbeat > 0 {
		n.LastHeartbeatAt = n.LastHeartbeat
	}
}

// RouteResult 表示一次路由查询的结果。
type RouteResult struct {
	// 用户是否在线
	Online bool
	// 用户当前连接的 IM Server 节点（在线时有效）
	Node *ServerNode
}

// OfflineMessage 表示一条离线消息的存储结构。
// 存储于 Redis List: im:offline:{userId}
type OfflineMessage struct {
	// 消息 ID（全局唯一）
	MessageID string `json:"messageId"`
	// 接收者用户 ID
	ReceiverID string `json:"receiverId"`
	// 发送者用户 ID
	SenderID string `json:"senderId"`
	// 会话 ID
	ConversationID string `json:"conversationId"`
	// 会话类型: 1=单聊, 2=群聊, 3=系统
	ConversationType uint32 `json:"conversationType"`
	// 消息类型
	MessageType uint32 `json:"messageType"`
	// 消息内容（Protobuf 序列化的 IMEnvelope 二进制帧）
	EnvelopeBytes []byte `json:"envelopeBytes"`
	// 服务端消息序列号
	ServerSeq uint64 `json:"serverSeq"`
	// 消息产生时间（Unix 毫秒）
	Timestamp int64 `json:"timestamp"`
	// 入队时间（Unix 毫秒，用于 TTL 过期判断）
	EnqueuedAt time.Time `json:"enqueuedAt"`
}

// DeliveryStatus 表示消息推送结果状态。
const (
	// DeliverySuccess 推送成功
	DeliverySuccess uint32 = 0
	// DeliveryUserOffline 用户不在线
	DeliveryUserOffline uint32 = 1
	// DeliveryFailed 推送失败（gRPC 调用失败或对端返回失败）
	DeliveryFailed uint32 = 2
)

// TaskKind 标识 MQ 任务类型，用于分发到不同的 Handler。
type TaskKind int

const (
	// TaskKindPushCommand 消息推送命令
	TaskKindPushCommand TaskKind = iota + 1
	// TaskKindDeliveredEvent 消息送达事件（IM Server → Router，转回执）
	TaskKindDeliveredEvent
	// TaskKindFailedEvent 消息推送失败事件（IM Server → Router，转回执）
	TaskKindFailedEvent
	// TaskKindSendFailedEvent 消息发送失败事件（IM Business → Router，转回执）
	TaskKindSendFailedEvent
)

// String 返回任务类型的可读名称，用于日志与监控。
func (k TaskKind) String() string {
	switch k {
	case TaskKindPushCommand:
		return "push_command"
	case TaskKindDeliveredEvent:
		return "delivered_event"
	case TaskKindFailedEvent:
		return "failed_event"
	case TaskKindSendFailedEvent:
		return "send_failed_event"
	default:
		return "unknown"
	}
}

// Task 表示一个待处理的 MQ 任务，由 Consumer 投递到 Worker Pool。
type Task struct {
	// 任务类型
	Kind TaskKind
	// 消息体（Protobuf 序列化后的二进制负载）
	Body []byte
	// 消息投递标识（amqp.Delivery.Tag），用于 ACK / NACK
	DeliveryTag uint64
	// 重试次数
	RetryCount uint32
	// 链路追踪 ID
	TraceID string
}
