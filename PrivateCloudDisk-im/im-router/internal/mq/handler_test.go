// mq/handler_test.go：Handler 注册与分发单元测试
//
// 测试场景：
//  1. HandlerRegistry 注册与获取（PushCommand + 事件处理器）
//  2. 无对应 Handler → Get 返回 nil
package mq

import (
	"context"
	"log/slog"
	"testing"

	"google.golang.org/protobuf/proto"

	"privateclouddisk/im-router/internal/model"
	pb "privateclouddisk/im-router/pkg/proto"
)

// pbEncode 将 protobuf 消息编码为字节。
func pbEncode(m proto.Message) ([]byte, error) {
	return proto.Marshal(m)
}

// mockNotifier 记录回执通知调用，用于测试。
type mockNotifier struct {
	delivered     int
	failed        int
	sendFailed    int
	deliveredErr  error
	failedErr     error
	sendFailedErr error
}

func (m *mockNotifier) NotifyDelivered(_ context.Context, _ *pb.MessageDeliveredEvent) error {
	m.delivered++
	return m.deliveredErr
}

func (m *mockNotifier) NotifyFailed(_ context.Context, _ *pb.MessageFailedEvent) error {
	m.failed++
	return m.failedErr
}

func (m *mockNotifier) NotifySendFailed(_ context.Context, _ *pb.MessageSendFailedEvent) error {
	m.sendFailed++
	return m.sendFailedErr
}

func TestHandlerRegistry(t *testing.T) {
	pushHandler := &successHandler{}
	notifier := &mockNotifier{}

	registry := NewHandlerRegistry(pushHandler, notifier, slog.Default())

	// 验证 PushCommand Handler 已注册
	if h := registry.Get(model.TaskKindPushCommand); h == nil {
		t.Error("期望 PushCommand Handler 已注册")
	}
	// 验证事件处理器已注册
	if h := registry.Get(model.TaskKindDeliveredEvent); h == nil {
		t.Error("期望 DeliveredEvent Handler 已注册")
	}
	if h := registry.Get(model.TaskKindFailedEvent); h == nil {
		t.Error("期望 FailedEvent Handler 已注册")
	}
	if h := registry.Get(model.TaskKindSendFailedEvent); h == nil {
		t.Error("期望 SendFailedEvent Handler 已注册")
	}
	// 未知 TaskKind 应返回 nil
	if h := registry.Get(model.TaskKind(999)); h != nil {
		t.Error("未知 TaskKind 应返回 nil")
	}
}

func TestDeliveredHandler(t *testing.T) {
	notifier := &mockNotifier{}
	h := NewDeliveredHandler(notifier, slog.Default())
	evt := &pb.MessageDeliveredEvent{
		MessageId:   "m1",
		ReceiverId:  "r1",
		SenderId:    "s1",
		MessageType: pb.MessageType_CHAT_MESSAGE,
	}
	body, err := pbEncode(evt)
	if err != nil {
		t.Fatalf("编码失败: %v", err)
	}
	task := &model.Task{Kind: model.TaskKindDeliveredEvent, Body: body}
	if err := h.Handle(context.Background(), task); err != nil {
		t.Fatalf("Handle 失败: %v", err)
	}
	if notifier.delivered != 1 {
		t.Errorf("期望 NotifyDelivered 被调用 1 次，实际 %d", notifier.delivered)
	}
}

func TestFailedHandler(t *testing.T) {
	notifier := &mockNotifier{}
	h := NewFailedHandler(notifier, slog.Default())
	evt := &pb.MessageFailedEvent{
		MessageId:   "m1",
		ReceiverId:  "r1",
		SenderId:    "s1",
		MessageType: pb.MessageType_CHAT_MESSAGE,
	}
	body, err := pbEncode(evt)
	if err != nil {
		t.Fatalf("编码失败: %v", err)
	}
	task := &model.Task{Kind: model.TaskKindFailedEvent, Body: body}
	if err := h.Handle(context.Background(), task); err != nil {
		t.Fatalf("Handle 失败: %v", err)
	}
	if notifier.failed != 1 {
		t.Errorf("期望 NotifyFailed 被调用 1 次，实际 %d", notifier.failed)
	}
}

func TestSendFailedHandler(t *testing.T) {
	notifier := &mockNotifier{}
	h := NewSendFailedHandler(notifier, slog.Default())
	evt := &pb.MessageSendFailedEvent{MessageId: "m1", SenderId: "s1", ReceiverId: "r1"}
	body, err := pbEncode(evt)
	if err != nil {
		t.Fatalf("编码失败: %v", err)
	}
	task := &model.Task{Kind: model.TaskKindSendFailedEvent, Body: body}
	if err := h.Handle(context.Background(), task); err != nil {
		t.Fatalf("Handle 失败: %v", err)
	}
	if notifier.sendFailed != 1 {
		t.Errorf("期望 NotifySendFailed 被调用 1 次，实际 %d", notifier.sendFailed)
	}
}
