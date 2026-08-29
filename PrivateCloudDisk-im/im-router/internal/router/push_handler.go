// router/push_handler.go：PushMessageCommand 处理器。
//
// 实现 mq.Handler 接口，负责将 MQ 任务体反序列化为 PushMessageCommand，
// 交由 Router 执行路由推送。
package router

import (
	"context"
	"fmt"
	"log/slog"

	"google.golang.org/protobuf/proto"

	"privateclouddisk/im-router/internal/model"
	pb "privateclouddisk/im-router/pkg/proto"
)

// PushCommandHandler 处理 im.message.push.command 队列消息。
type PushCommandHandler struct {
	router *Router
	logger *slog.Logger
}

// NewPushCommandHandler 创建处理器。
func NewPushCommandHandler(r *Router, logger *slog.Logger) *PushCommandHandler {
	return &PushCommandHandler{
		router: r,
		logger: logger.With(slog.String("handler", "push_command")),
	}
}

// Handle 反序列化 PushMessageCommand 并执行路由推送。
func (h *PushCommandHandler) Handle(ctx context.Context, task *model.Task) error {
	if task == nil || len(task.Body) == 0 {
		return fmt.Errorf("task 为空或无消息体")
	}

	cmd := &pb.PushMessageCommand{}
	if err := proto.Unmarshal(task.Body, cmd); err != nil {
		// 坏消息，无法通过重试修复
		h.logger.Error("PushMessageCommand 反序列化失败",
			slog.Int("body_len", len(task.Body)),
			slog.String("error", err.Error()),
			slog.String("hint", "请检查业务服务与 IM Router 的 Protobuf 定义是否一致"),
		)
		return fmt.Errorf("反序列化 PushMessageCommand 失败: %w", err)
	}

	// 提取链路追踪 ID，便于日志关联
	if cmd.Header != nil {
		task.TraceID = cmd.Header.TraceId
	}

	// 校验必要字段
	if cmd.MessageId == "" || cmd.ReceiverId == "" {
		h.logger.Error("PushMessageCommand 缺少必要字段",
			slog.String("message_id", cmd.MessageId),
			slog.String("receiver_id", cmd.ReceiverId),
			slog.String("trace_id", task.TraceID),
		)
		return fmt.Errorf("PushMessageCommand 缺少必要字段: messageId=%q receiverId=%q",
			cmd.MessageId, cmd.ReceiverId)
	}

	h.logger.Info("收到推送命令",
		slog.String("message_id", cmd.MessageId),
		slog.String("receiver_id", cmd.ReceiverId),
		slog.String("sender_id", cmd.SenderId),
		slog.String("conversation_id", cmd.ConversationId),
		slog.Uint64("conversation_type", uint64(cmd.ConversationType)),
		slog.Uint64("message_type", uint64(cmd.MessageType)),
		slog.Bool("is_offline_compensation", cmd.IsOfflineCompensation),
		slog.String("trace_id", task.TraceID),
	)

	return h.router.PushToUser(ctx, cmd)
}

// 编译期保证接口实现
var _ interface {
	Handle(context.Context, *model.Task) error
} = (*PushCommandHandler)(nil)
