// mq/worker.go：Worker Pool 的工作协程实现。
//
// 每个 Worker 从共享的任务通道 (taskCh) 拉取 Task，交由对应 Handler 处理，
// 处理完成后通过 Acker 接口执行 ACK / NACK。
//
// 设计要点：
//   - 任务通道由 Consumer 投递，实现 Consumer 与 Worker 解耦
//   - 处理超时由 context 控制，防止单条消息阻塞 Worker
//   - 失败任务按重试次数决定是否 requeue（避免毒丸消息无限重投）
//   - ACK 串行化由 Consumer 的专用 ack goroutine 保证（amqp Channel 非并发安全）
package mq

import (
	"context"
	"errors"
	"log/slog"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"

	"privateclouddisk/im-router/internal/model"
)

// 最大重试次数，超过后 NACK(requeue=false) 进入死信队列（DLX 配置下）
const maxRetryCount = 3

// 监控指标（promauto 自动注册到 DefaultRegisterer）
var (
	// 消息处理总量，按类型与结果分维
	messagesProcessed = promauto.NewCounterVec(prometheus.CounterOpts{
		Namespace: "im_router",
		Subsystem: "mq",
		Name:      "messages_processed_total",
		Help:      "MQ 消息处理总量",
	}, []string{"kind", "result"})

	// 消息处理耗时直方图
	messageHandleDuration = promauto.NewHistogramVec(prometheus.HistogramOpts{
		Namespace: "im_router",
		Subsystem: "mq",
		Name:      "handle_duration_seconds",
		Help:      "MQ 消息处理耗时",
		Buckets:   prometheus.DefBuckets,
	}, []string{"kind"})

	// Worker 正在处理的任务数（gauge）
	workerInflight = promauto.NewGauge(prometheus.GaugeOpts{
		Namespace: "im_router",
		Subsystem: "mq",
		Name:      "worker_inflight",
		Help:      "当前正在处理消息的 Worker 数",
	})
)

// Acker 抽象 ACK/NACK 操作，由 Consumer 实现（内部转发到串行化的 ack 通道）。
type Acker interface {
	// Ack 确认消息（multiple=false，逐条确认）。
	Ack(deliveryTag uint64) error
	// Nack 否定确认，requeue 决定是否重入队列。
	Nack(deliveryTag uint64, requeue bool) error
}

// noopAcker 用于测试或关闭后阶段，丢弃 ACK/NACK。
type noopAcker struct{}

func (noopAcker) Ack(uint64) error        { return nil }
func (noopAcker) Nack(uint64, bool) error { return nil }

// Worker 工作协程。
type Worker struct {
	id            int
	taskCh        <-chan *model.Task
	registry      *HandlerRegistry
	acker         Acker
	handleTimeout time.Duration
	logger        *slog.Logger
}

// NewWorker 创建一个 Worker。
func NewWorker(id int, taskCh <-chan *model.Task, registry *HandlerRegistry, acker Acker, handleTimeout time.Duration, logger *slog.Logger) *Worker {
	return &Worker{
		id:            id,
		taskCh:        taskCh,
		registry:      registry,
		acker:         acker,
		handleTimeout: handleTimeout,
		logger:        logger.With(slog.Int("worker_id", id)),
	}
}

// Run 启动 Worker 循环，直到 ctx 取消或任务通道关闭。
func (w *Worker) Run(ctx context.Context) {
	for {
		select {
		case <-ctx.Done():
			return
		case task, ok := <-w.taskCh:
			if !ok {
				// 通道关闭，退出
				return
			}
			w.process(ctx, task)
		}
	}
}

// process 处理单条任务：查找 Handler → 执行（带超时）→ ACK/NACK。
func (w *Worker) process(ctx context.Context, task *model.Task) {
	start := time.Now()
	workerInflight.Inc()
	defer workerInflight.Dec()

	kindLabel := task.Kind.String()

	handler := w.registry.Get(task.Kind)
	if handler == nil {
		// 无对应 Handler，属于配置错误，丢弃避免阻塞
		w.logger.Error("无对应 Handler，消息将被丢弃",
			slog.String("kind", kindLabel),
			slog.Uint64("delivery_tag", task.DeliveryTag),
			slog.Int("body_len", len(task.Body)),
		)
		messagesProcessed.WithLabelValues(kindLabel, "no_handler").Inc()
		_ = w.acker.Ack(task.DeliveryTag)
		return
	}

	w.logger.Debug("开始处理消息",
		slog.String("kind", kindLabel),
		slog.Uint64("delivery_tag", task.DeliveryTag),
		slog.Int("body_len", len(task.Body)),
		slog.Uint64("retry_count", uint64(task.RetryCount)),
		slog.String("trace_id", task.TraceID),
	)

	// 设置处理超时上下文
	var cancel context.CancelFunc
	hctx := ctx
	if w.handleTimeout > 0 {
		hctx, cancel = context.WithTimeout(ctx, w.handleTimeout)
		defer cancel()
	} else {
		hctx, cancel = context.WithCancel(ctx)
		defer cancel()
	}

	err := handler.Handle(hctx, task)
	elapsed := time.Since(start)
	messageHandleDuration.WithLabelValues(kindLabel).Observe(elapsed.Seconds())

	if err == nil {
		// 处理成功，ACK
		if e := w.acker.Ack(task.DeliveryTag); e != nil {
			w.logger.Warn("ACK 发送失败（连接可能已断开）",
				slog.Uint64("delivery_tag", task.DeliveryTag),
				slog.String("error", e.Error()),
			)
			messagesProcessed.WithLabelValues(kindLabel, "ack_failed").Inc()
			return
		}
		w.logger.Debug("消息处理成功",
			slog.String("kind", kindLabel),
			slog.Uint64("delivery_tag", task.DeliveryTag),
			slog.Duration("elapsed", elapsed),
		)
		messagesProcessed.WithLabelValues(kindLabel, "success").Inc()
		return
	}

	// 处理失败：记录详细错误日志
	w.logger.Error("消息处理失败",
		slog.String("kind", kindLabel),
		slog.Uint64("delivery_tag", task.DeliveryTag),
		slog.Uint64("retry_count", uint64(task.RetryCount)),
		slog.Duration("elapsed", elapsed),
		slog.String("error", err.Error()),
		slog.Int("body_len", len(task.Body)),
	)

	messagesProcessed.WithLabelValues(kindLabel, "failed").Inc()
	if errors.Is(err, context.DeadlineExceeded) {
		messagesProcessed.WithLabelValues(kindLabel, "timeout").Inc()
		w.logger.Warn("消息处理超时",
			slog.String("kind", kindLabel),
			slog.Duration("timeout", w.handleTimeout),
		)
	}

	// 重试策略：NACK(requeue=false) → DLX → retry queue(TTL) → 原队列
	// 达到最大重试次数后，NACK(requeue=false) → DLX → DLQ
	requeue := task.RetryCount < maxRetryCount
	if requeue {
		w.logger.Warn("消息将进入重试队列（通过 DLX 延迟重试）",
			slog.Uint64("delivery_tag", task.DeliveryTag),
			slog.Uint64("retry_count", uint64(task.RetryCount)),
			slog.Int("max_retries", maxRetryCount),
		)
		// NACK(requeue=false) → DLX → retry queue(TTL 5s) → 原队列
		_ = w.acker.Nack(task.DeliveryTag, false)
	} else {
		w.logger.Error("消息已达最大重试次数，进入死信队列",
			slog.Uint64("delivery_tag", task.DeliveryTag),
			slog.Uint64("retry_count", uint64(task.RetryCount)),
			slog.Int("max_retries", maxRetryCount),
		)
		// NACK(requeue=false) → DLX → DLQ
		_ = w.acker.Nack(task.DeliveryTag, false)
	}
}
