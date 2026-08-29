// router/notify.go：回执通知实现。
//
// 消费 IM Server / IM Business 发布的送达（delivered）/ 推送失败（failed）/
// 发送失败（send.failed）事件，向消息原发送方所在 im-server 节点推送一条
// 回执通知（MessageType_RECEIPT）。回执内容即事件本身（envelope_bytes）。
//
// 防闭环：送达/推送失败事件仅在消息类型为普通聊天消息时才生成回执；
// 通知类消息（系统通知、错误消息、回执自身等）只记录日志，不再触发新回执。
// 发送失败事件（业务层）始终回执发送方。
//
// 若发送方不在线或推送回执本身失败，仅记录日志并跳过（不做离线存储、不重试）。
package router

import (
	"context"
	"errors"
	"log/slog"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	"google.golang.org/protobuf/proto"

	"privateclouddisk/im-router/internal/model"
	pb "privateclouddisk/im-router/pkg/proto"
)

// notifyTotal 回执推送次数（按事件类型 kind / 结果 result 分类）。
var notifyTotal = promauto.NewCounterVec(prometheus.CounterOpts{
	Namespace: "im_router",
	Subsystem: "route",
	Name:      "notify_total",
	Help:      "回执通知推送次数（按事件类型与结果分类）",
}, []string{"kind", "result"})

// NotifyDelivered 处理消息送达事件，向发送方推送"已送达"回执。
func (r *Router) NotifyDelivered(ctx context.Context, evt *pb.MessageDeliveredEvent) error {
	if evt == nil {
		return errors.New("MessageDeliveredEvent 为空")
	}
	// 防闭环：仅普通聊天消息需要送达回执
	if !shouldNotifyMessageType(evt.MessageType) {
		r.notifyLog("送达事件：非聊天消息，跳过回执",
			"kind", "delivered",
			"message_id", evt.MessageId,
			"message_type", evt.MessageType.String(),
			"sender_id", evt.SenderId,
			"receiver_id", evt.ReceiverId,
		)
		notifyTotal.WithLabelValues("delivered", "skipped_non_chat").Inc()
		return nil
	}
	return r.pushReceipt(ctx, "delivered", evt.SenderId, evt.ReceiverId,
		evt.MessageId, evt.ConversationId, evt)
}

// NotifyFailed 处理消息推送失败事件，向发送方推送"推送失败"回执。
func (r *Router) NotifyFailed(ctx context.Context, evt *pb.MessageFailedEvent) error {
	if evt == nil {
		return errors.New("MessageFailedEvent 为空")
	}
	// 防闭环：仅普通聊天消息需要推送失败回执
	if !shouldNotifyMessageType(evt.MessageType) {
		r.notifyLog("推送失败事件：非聊天消息，仅记录日志",
			"kind", "failed",
			"message_id", evt.MessageId,
			"message_type", evt.MessageType.String(),
			"sender_id", evt.SenderId,
			"receiver_id", evt.ReceiverId,
			"fail_reason", evt.FailReason,
		)
		notifyTotal.WithLabelValues("failed", "skipped_non_chat").Inc()
		return nil
	}
	return r.pushReceipt(ctx, "failed", evt.SenderId, evt.ReceiverId,
		evt.MessageId, evt.ConversationId, evt)
}

// NotifySendFailed 处理消息发送失败事件，始终向发送方推送"发送失败"回执。
func (r *Router) NotifySendFailed(ctx context.Context, evt *pb.MessageSendFailedEvent) error {
	if evt == nil {
		return errors.New("MessageSendFailedEvent 为空")
	}
	return r.pushReceipt(ctx, "send_failed", evt.SenderId, evt.ReceiverId,
		evt.MessageId, evt.ConversationId, evt)
}

// pushReceipt 将事件作为回执推送到发送方所在 im-server 节点。
// receiverID 为原消息发送方（回执的接收方）；senderID 为原接收方（回执的发送方）。
func (r *Router) pushReceipt(ctx context.Context, kind, receiverID, senderID, messageID, conversationID string, evt proto.Message) error {
	if receiverID == "" {
		r.notifyLog("回执跳过：发送方为空", "kind", kind, "message_id", messageID)
		notifyTotal.WithLabelValues(kind, "empty_sender").Inc()
		return nil
	}

	// 查询发送方当前连接的 im-server 节点
	online, node, err := r.redis.GetUserServerNode(ctx, receiverID)
	if err != nil {
		r.notifyLog("回执跳过：查询发送方路由失败", "kind", kind,
			"receiver_id", receiverID, "error", err.Error())
		notifyTotal.WithLabelValues(kind, "lookup_error").Inc()
		return nil
	}
	if !online || node == nil {
		r.notifyLog("回执跳过：发送方不在线", "kind", kind,
			"receiver_id", receiverID, "message_id", messageID)
		notifyTotal.WithLabelValues(kind, "sender_offline").Inc()
		return nil
	}

	// 序列化事件作为回执内容
	envBytes, err := proto.Marshal(evt)
	if err != nil {
		r.notifyLog("回执失败：事件序列化错误", "kind", kind,
			"receiver_id", receiverID, "error", err.Error())
		notifyTotal.WithLabelValues(kind, "marshal_error").Inc()
		return err
	}

	req := &pb.PushMessageRequest{
		MessageId:      messageID,
		ReceiverId:     receiverID,
		SenderId:       senderID,
		ConversationId: conversationID,
		MessageType:    pb.MessageType_RECEIPT,
		EnvelopeBytes:  envBytes,
		Timestamp:      uint64(time.Now().UnixMilli()),
	}

	resp, err := r.grpc.PushMessage(ctx, node.NodeID, node.GrpcAddress, req)
	if err != nil {
		// 回执推送本身失败：记录日志，不重试、不再触发新回执
		r.notifyLog("回执推送失败（gRPC）", "kind", kind,
			"receiver_id", receiverID, "node", node.NodeID, "error", err.Error())
		notifyTotal.WithLabelValues(kind, "push_error").Inc()
		return nil
	}

	if resp == nil || resp.Code != model.DeliverySuccess {
		r.notifyLog("回执推送失败（服务端未确认送达）", "kind", kind,
			"receiver_id", receiverID, "node", node.NodeID)
		notifyTotal.WithLabelValues(kind, "push_failed").Inc()
		return nil
	}

	r.notifyLog("回执推送成功", "kind", kind,
		"receiver_id", receiverID, "node", node.NodeID, "message_id", messageID)
	notifyTotal.WithLabelValues(kind, "success").Inc()
	return nil
}

// notifyLog 带默认日志器输出的回执日志（Router 未注入 logger 时回退到 slog.Default）。
func (r *Router) notifyLog(msg string, kv ...any) {
	l := r.logger
	if l == nil {
		l = slog.Default()
	}
	l.Info(msg, kv...)
}
