// Package mq 实现 IM Router 的 RabbitMQ 消费层。
//
// 核心组件：
//   - handler.go：定义 Handler 接口与 HandlerRegistry，
//     以及消息推送命令的处理器注册
//   - worker.go  ：Worker Pool 的工作协程，从任务通道拉取任务并执行
//   - consumer.go：管理 AMQP 连接、QoS、消费分发与 ACK 串行化
//
// 消费模型：Competing Consumers
//
//	多个 IM Router 实例消费同一队列，RabbitMQ 将每条消息仅投递给一个消费者，
//	天然实现水平扩展。配合 Worker Pool 实现单实例内的高并发处理。
package mq

import (
	"context"
	"fmt"
	"log/slog"

	"google.golang.org/protobuf/proto"

	"privateclouddisk/im-router/internal/model"
	pb "privateclouddisk/im-router/pkg/proto"
)

// Handler 定义 MQ 消息处理接口。
// 每种 TaskKind 对应一个 Handler 实现。
type Handler interface {
	// Handle 处理一条 MQ 任务。
	// 返回 error 时，Consumer 将根据重试次数决定 NACK(requeue) 或 ACK(丢弃)。
	Handle(ctx context.Context, task *model.Task) error
}

// HandlerRegistry 按 TaskKind 路由到对应 Handler。
type HandlerRegistry struct {
	handlers map[model.TaskKind]Handler
}

// NewHandlerRegistry 创建注册表并注册各处理器。
// pushHandler 处理消息推送命令；notifier 处理送达/失败事件转回执；
// logger 注入各事件处理器，保证日志可观测（logger 为空时使用 slog.Default()）。
func NewHandlerRegistry(pushHandler Handler, notifier Notifier, logger *slog.Logger) *HandlerRegistry {
	if logger == nil {
		logger = slog.Default()
	}
	r := &HandlerRegistry{
		handlers: make(map[model.TaskKind]Handler),
	}
	r.Register(model.TaskKindPushCommand, pushHandler)
	r.Register(model.TaskKindDeliveredEvent, NewDeliveredHandler(notifier, logger))
	r.Register(model.TaskKindFailedEvent, NewFailedHandler(notifier, logger))
	r.Register(model.TaskKindSendFailedEvent, NewSendFailedHandler(notifier, logger))
	return r
}

// Register 注册一个 Handler。
func (r *HandlerRegistry) Register(kind model.TaskKind, h Handler) {
	r.handlers[kind] = h
}

// Get 取出对应 Handler，不存在返回 nil。
func (r *HandlerRegistry) Get(kind model.TaskKind) Handler {
	return r.handlers[kind]
}

// Notifier 抽象回执通知能力，由 router 包实现。
// 负责将送达/失败事件转换为回执推送回消息发送方。
type Notifier interface {
	// NotifyDelivered 处理消息送达事件（推送"已送达"回执）。
	NotifyDelivered(ctx context.Context, evt *pb.MessageDeliveredEvent) error
	// NotifyFailed 处理消息推送失败事件（推送"推送失败"回执）。
	NotifyFailed(ctx context.Context, evt *pb.MessageFailedEvent) error
	// NotifySendFailed 处理消息发送失败事件（推送"发送失败"回执）。
	NotifySendFailed(ctx context.Context, evt *pb.MessageSendFailedEvent) error
}

// DeliveredHandler 处理 im.message.delivered.event 队列消息。
type DeliveredHandler struct {
	notifier Notifier
	logger   *slog.Logger
}

// NewDeliveredHandler 创建送达事件处理器。
func NewDeliveredHandler(n Notifier, logger *slog.Logger) *DeliveredHandler {
	return &DeliveredHandler{
		notifier: n,
		logger:   logger.With(slog.String("handler", "delivered_event")),
	}
}

// Handle 反序列化 MessageDeliveredEvent 并转回执通知。
func (h *DeliveredHandler) Handle(ctx context.Context, task *model.Task) error {
	if task == nil || len(task.Body) == 0 {
		return fmt.Errorf("task 为空或无消息体")
	}
	evt := &pb.MessageDeliveredEvent{}
	if err := proto.Unmarshal(task.Body, evt); err != nil {
		h.logger.Error("MessageDeliveredEvent 反序列化失败",
			slog.Int("body_len", len(task.Body)),
			slog.String("error", err.Error()),
		)
		return fmt.Errorf("反序列化 MessageDeliveredEvent 失败: %w", err)
	}
	if evt.Header != nil {
		task.TraceID = evt.Header.TraceId
	}
	h.logger.Info("收到送达事件",
		slog.String("message_id", evt.MessageId),
		slog.String("receiver_id", evt.ReceiverId),
		slog.String("sender_id", evt.SenderId),
		slog.Uint64("message_type", uint64(evt.MessageType)),
		slog.String("trace_id", task.TraceID),
	)
	return h.notifier.NotifyDelivered(ctx, evt)
}

// FailedHandler 处理 im.message.failed.event 队列消息。
type FailedHandler struct {
	notifier Notifier
	logger   *slog.Logger
}

// NewFailedHandler 创建推送失败事件处理器。
func NewFailedHandler(n Notifier, logger *slog.Logger) *FailedHandler {
	return &FailedHandler{
		notifier: n,
		logger:   logger.With(slog.String("handler", "failed_event")),
	}
}

// Handle 反序列化 MessageFailedEvent 并转回执通知。
func (h *FailedHandler) Handle(ctx context.Context, task *model.Task) error {
	if task == nil || len(task.Body) == 0 {
		return fmt.Errorf("task 为空或无消息体")
	}
	evt := &pb.MessageFailedEvent{}
	if err := proto.Unmarshal(task.Body, evt); err != nil {
		h.logger.Error("MessageFailedEvent 反序列化失败",
			slog.Int("body_len", len(task.Body)),
			slog.String("error", err.Error()),
		)
		return fmt.Errorf("反序列化 MessageFailedEvent 失败: %w", err)
	}
	if evt.Header != nil {
		task.TraceID = evt.Header.TraceId
	}
	h.logger.Info("收到推送失败事件",
		slog.String("message_id", evt.MessageId),
		slog.String("receiver_id", evt.ReceiverId),
		slog.String("sender_id", evt.SenderId),
		slog.Uint64("fail_code", uint64(evt.FailCode)),
		slog.String("fail_reason", evt.FailReason),
		slog.Uint64("message_type", uint64(evt.MessageType)),
		slog.String("trace_id", task.TraceID),
	)
	return h.notifier.NotifyFailed(ctx, evt)
}

// SendFailedHandler 处理 im.message.send.failed.event 队列消息。
type SendFailedHandler struct {
	notifier Notifier
	logger   *slog.Logger
}

// NewSendFailedHandler 创建发送失败事件处理器。
func NewSendFailedHandler(n Notifier, logger *slog.Logger) *SendFailedHandler {
	return &SendFailedHandler{
		notifier: n,
		logger:   logger.With(slog.String("handler", "send_failed_event")),
	}
}

// Handle 反序列化 MessageSendFailedEvent 并转回执通知。
func (h *SendFailedHandler) Handle(ctx context.Context, task *model.Task) error {
	if task == nil || len(task.Body) == 0 {
		return fmt.Errorf("task 为空或无消息体")
	}
	evt := &pb.MessageSendFailedEvent{}
	if err := proto.Unmarshal(task.Body, evt); err != nil {
		h.logger.Error("MessageSendFailedEvent 反序列化失败",
			slog.Int("body_len", len(task.Body)),
			slog.String("error", err.Error()),
		)
		return fmt.Errorf("反序列化 MessageSendFailedEvent 失败: %w", err)
	}
	if evt.Header != nil {
		task.TraceID = evt.Header.TraceId
	}
	h.logger.Info("收到发送失败事件",
		slog.String("message_id", evt.MessageId),
		slog.String("sender_id", evt.SenderId),
		slog.String("receiver_id", evt.ReceiverId),
		slog.Uint64("error_code", uint64(evt.ErrorCode)),
		slog.String("error_message", evt.ErrorMessage),
		slog.String("trace_id", task.TraceID),
	)
	return h.notifier.NotifySendFailed(ctx, evt)
}
