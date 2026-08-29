// mq/worker_test.go：Worker 重试逻辑与死信路由单元测试
//
// 测试场景：
//  1. 处理成功 → ACK
//  2. 处理失败，retryCount < 3 → NACK(requeue=false) → DLX 重试
//  3. 处理失败，retryCount >= 3 → NACK(requeue=false) → DLX DLQ
//  4. 无对应 Handler → ACK 丢弃
//  5. 处理超时 → NACK(requeue=false)
package mq

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"sync"
	"testing"
	"time"

	"privateclouddisk/im-router/internal/model"
)

// spyAcker 记录 ACK/NACK 调用，用于测试断言。
type spyAcker struct {
	mu       sync.Mutex
	acks     []uint64
	nacks    []uint64
	requeues []bool
}

func (s *spyAcker) Ack(tag uint64) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.acks = append(s.acks, tag)
	return nil
}

func (s *spyAcker) Nack(tag uint64, requeue bool) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.nacks = append(s.nacks, tag)
	s.requeues = append(s.requeues, requeue)
	return nil
}

func (s *spyAcker) ackCount() int {
	s.mu.Lock()
	defer s.mu.Unlock()
	return len(s.acks)
}

func (s *spyAcker) nackCount() int {
	s.mu.Lock()
	defer s.mu.Unlock()
	return len(s.nacks)
}

func (s *spyAcker) lastNackRequeue() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	if len(s.requeues) == 0 {
		return false
	}
	return s.requeues[len(s.requeues)-1]
}

// successHandler 总是返回 nil（处理成功）。
type successHandler struct{}

func (h *successHandler) Handle(ctx context.Context, task *model.Task) error {
	return nil
}

// failHandler 总是返回错误。
type failHandler struct {
	err error
}

func (h *failHandler) Handle(ctx context.Context, task *model.Task) error {
	if h.err != nil {
		return h.err
	}
	return errors.New("处理失败")
}

// timeoutHandler 模拟超时。
type timeoutHandler struct {
	delay time.Duration
}

func (h *timeoutHandler) Handle(ctx context.Context, task *model.Task) error {
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-time.After(h.delay):
	}
	return nil
}

func TestWorkerProcessSuccess(t *testing.T) {
	taskCh := make(chan *model.Task, 1)
	acker := &spyAcker{}

	registry := &HandlerRegistry{
		handlers: map[model.TaskKind]Handler{
			model.TaskKindPushCommand: &successHandler{},
		},
	}

	w := NewWorker(0, taskCh, registry, acker, 10*time.Second, slog.Default())

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	task := &model.Task{
		Kind:        model.TaskKindPushCommand,
		Body:        []byte("test"),
		DeliveryTag: 200,
		RetryCount:  0, // < maxRetryCount(3)
	}
	taskCh <- task
	close(taskCh)

	w.Run(ctx)

	time.Sleep(100 * time.Millisecond)

	// 处理成功 → 1 次 ACK，不产生 NACK
	if acker.ackCount() != 1 {
		t.Errorf("期望 1 次 ACK，实际 %d 次", acker.ackCount())
	}
	if acker.nackCount() != 0 {
		t.Errorf("期望 0 次 NACK，实际 %d 次", acker.nackCount())
	}
}

func TestWorkerProcessFailureMaxRetry(t *testing.T) {
	taskCh := make(chan *model.Task, 1)
	acker := &spyAcker{}

	registry := &HandlerRegistry{
		handlers: map[model.TaskKind]Handler{
			model.TaskKindPushCommand: &failHandler{},
		},
	}

	w := NewWorker(0, taskCh, registry, acker, 10*time.Second, slog.Default())

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	task := &model.Task{
		Kind:        model.TaskKindPushCommand,
		Body:        []byte("test"),
		DeliveryTag: 300,
		RetryCount:  3, // >= maxRetryCount(3)
	}
	taskCh <- task
	close(taskCh)

	w.Run(ctx)

	time.Sleep(100 * time.Millisecond)

	// 达到最大重试次数，仍 NACK(requeue=false) 进入 DLQ
	if acker.nackCount() != 1 {
		t.Errorf("期望 1 次 NACK，实际 %d 次", acker.nackCount())
	}
	if acker.lastNackRequeue() != false {
		t.Errorf("期望 NACK(requeue=false)，实际 requeue=%v", acker.lastNackRequeue())
	}
}

func TestWorkerNoHandler(t *testing.T) {
	taskCh := make(chan *model.Task, 1)
	acker := &spyAcker{}

	// 空注册表，无对应 Handler
	registry := &HandlerRegistry{
		handlers: make(map[model.TaskKind]Handler),
	}

	w := NewWorker(0, taskCh, registry, acker, 10*time.Second, slog.Default())

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	task := &model.Task{
		Kind:        model.TaskKindPushCommand,
		Body:        []byte("test"),
		DeliveryTag: 400,
		RetryCount:  0,
	}
	taskCh <- task
	close(taskCh)

	w.Run(ctx)

	time.Sleep(100 * time.Millisecond)

	// 无 Handler，直接 ACK 丢弃
	if acker.ackCount() != 1 {
		t.Errorf("期望 1 次 ACK（丢弃），实际 %d 次", acker.ackCount())
	}
	if acker.nackCount() != 0 {
		t.Errorf("期望 0 次 NACK，实际 %d 次", acker.nackCount())
	}
}

func TestWorkerMultipleTasks(t *testing.T) {
	taskCh := make(chan *model.Task, 3)
	acker := &spyAcker{}

	registry := &HandlerRegistry{
		handlers: map[model.TaskKind]Handler{
			model.TaskKindPushCommand: &successHandler{},
		},
	}

	w := NewWorker(0, taskCh, registry, acker, 10*time.Second, slog.Default())

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// 任务 1: 成功
	taskCh <- &model.Task{Kind: model.TaskKindPushCommand, Body: []byte("t1"), DeliveryTag: 1, RetryCount: 0}
	// 任务 2: 成功
	taskCh <- &model.Task{Kind: model.TaskKindPushCommand, Body: []byte("t2"), DeliveryTag: 2, RetryCount: 0}
	// 任务 3: 成功
	taskCh <- &model.Task{Kind: model.TaskKindPushCommand, Body: []byte("t3"), DeliveryTag: 3, RetryCount: 0}
	close(taskCh)

	w.Run(ctx)

	time.Sleep(200 * time.Millisecond)

	if acker.ackCount() != 3 {
		t.Errorf("期望 3 次 ACK，实际 %d 次", acker.ackCount())
	}
	if acker.nackCount() != 0 {
		t.Errorf("期望 0 次 NACK，实际 %d 次", acker.nackCount())
	}
}

func TestWorkerTimeout(t *testing.T) {
	taskCh := make(chan *model.Task, 1)
	acker := &spyAcker{}

	registry := &HandlerRegistry{
		handlers: map[model.TaskKind]Handler{
			model.TaskKindPushCommand: &timeoutHandler{delay: 200 * time.Millisecond},
		},
	}

	// 设置 50ms 超时，handler 需要 200ms → 超时
	w := NewWorker(0, taskCh, registry, acker, 50*time.Millisecond, slog.Default())

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	task := &model.Task{
		Kind:        model.TaskKindPushCommand,
		Body:        []byte("timeout-test"),
		DeliveryTag: 500,
		RetryCount:  0,
	}
	taskCh <- task
	close(taskCh)

	w.Run(ctx)

	time.Sleep(300 * time.Millisecond)

	// 超时 → NACK(requeue=false)
	if acker.nackCount() != 1 {
		t.Errorf("期望 1 次 NACK（超时），实际 %d 次", acker.nackCount())
	}
	if acker.lastNackRequeue() != false {
		t.Errorf("期望 NACK(requeue=false)，实际 requeue=%v", acker.lastNackRequeue())
	}
}

// TestRetryBoundary 测试重试次数边界
func TestRetryBoundary(t *testing.T) {
	tests := []struct {
		name       string
		retryCount uint32
		expectAck  bool // 期望 ACK？(false = 期望 NACK)
	}{
		{"retryCount=0 (< 3)", 0, false},
		{"retryCount=1 (< 3)", 1, false},
		{"retryCount=2 (< 3)", 2, false},
		{"retryCount=3 (>=3)", 3, false}, // 仍 NACK 进入 DLQ
		{"retryCount=5 (>=3)", 5, false}, // 仍 NACK 进入 DLQ
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			taskCh := make(chan *model.Task, 1)
			acker := &spyAcker{}

			registry := &HandlerRegistry{
				handlers: map[model.TaskKind]Handler{
					model.TaskKindPushCommand: &failHandler{err: fmt.Errorf("test error")},
				},
			}

			w := NewWorker(0, taskCh, registry, acker, 10*time.Second, slog.Default())

			ctx, cancel := context.WithCancel(context.Background())
			defer cancel()

			task := &model.Task{
				Kind:        model.TaskKindPushCommand,
				Body:        []byte(tt.name),
				DeliveryTag: uint64(tt.retryCount + 100),
				RetryCount:  tt.retryCount,
			}
			taskCh <- task
			close(taskCh)

			w.Run(ctx)
			time.Sleep(100 * time.Millisecond)

			// 所有失败都 NACK(requeue=false)，通过 DLX 路由
			if acker.nackCount() != 1 {
				t.Errorf("期望 1 次 NACK，实际 %d 次", acker.nackCount())
			}
			if acker.lastNackRequeue() != false {
				t.Errorf("期望 NACK(requeue=false)，实际 requeue=%v", acker.lastNackRequeue())
			}
		})
	}
}
