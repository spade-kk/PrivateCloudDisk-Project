// mq/consumer.go：RabbitMQ 消费者管理（Worker Pool 模式）。
//
// 核心能力：
//   - 维护到 RabbitMQ 的长连接，断线自动重连（指数退避）
//   - 声明交换机 / 队列 / 绑定（幂等，与 im-common ImConstants 对齐）
//   - 设置 QoS（prefetch_count）控制单实例未确认消息数
//   - 命令队列并发消费，统一投递到任务通道
//   - Worker Pool（worker_count 个协程）从任务通道拉取处理
//   - 专用 ACK 协程（生命周期级）串行化 amqp.Channel 操作（Channel 非并发安全）
//   - 优雅停止：取消消费 → 排空任务 → 处理完在途 → 关闭连接
//
// 重连设计：
//   - taskCh / acker / workers / ack 循环均为"生命周期级"，跨重连复用
//   - consume 通道（c.consumeCh）为"连接级"，通过 mutex 在重连时切换
//   - 连接断开时，在途消息因未 ACK 由 RabbitMQ 自动重投（at-least-once）
//
// 优雅停止顺序（保证在途消息被 ACK）：
//
//	取消消费 → 等待 delivery 协程 → 关闭 taskCh → workers 排空 →
//	关闭 acker → ack 循环处理剩余 ACK → 关闭 consume 通道与连接
//
// 队列 → TaskKind 映射：
//
//	push_command_queue   → TaskKindPushCommand
//	delivered_event_queue → TaskKindDeliveredEvent
//	failed_event_queue   → TaskKindFailedEvent
//	send_failed_event_queue → TaskKindSendFailedEvent
package mq

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"sync"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	amqp "github.com/rabbitmq/amqp091-go"

	"privateclouddisk/im-router/internal/config"
	"privateclouddisk/im-router/internal/model"
)

// 交换机常量（与 im-common ImConstants 对齐）
const (
	exchangeCommand = "im.command.exchange" // direct，命令交换机
	exchangeEvent   = "im.event.exchange"   // topic，事件交换机
	dlxEvent        = "im.dlx.event"        // direct，事件死信交换机
)

// 重连退避参数
const (
	reconnectInitialBackoff = 1 * time.Second
	reconnectMaxBackoff     = 30 * time.Second
)

// 监控指标
var (
	// 消费者连接状态：1=已连接, 0=断开
	consumerConnected = promauto.NewGauge(prometheus.GaugeOpts{
		Namespace: "im_router",
		Subsystem: "mq",
		Name:      "consumer_connected",
		Help:      "MQ 消费者连接状态 (1=已连接 0=断开)",
	})
	// 每个队列的消费速率
	messagesReceived = promauto.NewCounterVec(prometheus.CounterOpts{
		Namespace: "im_router",
		Subsystem: "mq",
		Name:      "messages_received_total",
		Help:      "从 RabbitMQ 接收的消息总量",
	}, []string{"queue"})
	// 任务通道当前排队数
	taskChannelDepth = promauto.NewGauge(prometheus.GaugeOpts{
		Namespace: "im_router",
		Subsystem: "mq",
		Name:      "task_channel_depth",
		Help:      "任务通道当前排队数",
	})
)

// ackReq 表示一个 ACK/NACK 请求，由专用协程串行执行。
type ackReq struct {
	tag     uint64
	ack     bool
	requeue bool
}

// channelAcker 实现 Acker 接口，将 ACK/NACK 请求转发到串行化通道。
// 通道为生命周期级，跨重连复用；ack 循环从其中读取并调用当前 consume 通道。
type channelAcker struct {
	ch     chan ackReq
	closed bool
	mu     sync.Mutex
}

func newChannelAcker(buf int) *channelAcker {
	return &channelAcker{ch: make(chan ackReq, buf)}
}

func (a *channelAcker) Ack(tag uint64) error {
	a.mu.Lock()
	if a.closed {
		a.mu.Unlock()
		return errors.New("acker 已关闭")
	}
	a.mu.Unlock()
	select {
	case a.ch <- ackReq{tag: tag, ack: true}:
		return nil
	default:
		// ack 通道满，说明消费速度跟不上，丢弃此 ACK（消息将由 RabbitMQ 超时重投）
		return errors.New("ack 通道已满")
	}
}

func (a *channelAcker) Nack(tag uint64, requeue bool) error {
	a.mu.Lock()
	if a.closed {
		a.mu.Unlock()
		return errors.New("acker 已关闭")
	}
	a.mu.Unlock()
	select {
	case a.ch <- ackReq{tag: tag, ack: false, requeue: requeue}:
		return nil
	default:
		return errors.New("ack 通道已满")
	}
}

func (a *channelAcker) close() {
	a.mu.Lock()
	defer a.mu.Unlock()
	a.closed = true
	close(a.ch)
}

// Consumer 管理 RabbitMQ 消费与 Worker Pool。
type Consumer struct {
	cfg      config.RabbitMQConfig
	registry *HandlerRegistry
	logger   *slog.Logger

	// 生命周期级资源（跨重连复用）
	taskCh chan *model.Task
	acker  *channelAcker

	// 连接级资源（重连时切换）
	mu        sync.Mutex
	consumeCh *amqp.Channel // 当前消费通道（含 QoS），ack 循环通过 currentCh 访问
	conn      *amqp.Connection

	// 队列 → TaskKind 映射
	queues map[string]model.TaskKind

	ctx    context.Context
	cancel context.CancelFunc

	workerWg sync.WaitGroup // workers + monitor
	ackWg    sync.WaitGroup // ack 循环

	startOnce sync.Once
	stopOnce  sync.Once
	stopped   chan struct{}
}

// NewConsumer 创建消费者实例（未连接）。
func NewConsumer(cfg config.RabbitMQConfig, registry *HandlerRegistry, logger *slog.Logger) *Consumer {
	c := &Consumer{
		cfg:      cfg,
		registry: registry,
		logger:   logger.With(slog.String("component", "mq-consumer")),
		stopped:  make(chan struct{}),
	}
	c.queues = map[string]model.TaskKind{
		cfg.Consumer.PushCommandQueue:     model.TaskKindPushCommand,
		cfg.Consumer.DeliveredEventQueue:  model.TaskKindDeliveredEvent,
		cfg.Consumer.FailedEventQueue:     model.TaskKindFailedEvent,
		cfg.Consumer.SendFailedEventQueue: model.TaskKindSendFailedEvent,
	}
	return c
}

// currentCh 返回当前 consume 通道（线程安全）。
func (c *Consumer) currentCh() *amqp.Channel {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.consumeCh
}

// setConsumeCh 设置当前 consume 通道。
func (c *Consumer) setConsumeCh(ch *amqp.Channel) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.consumeCh = ch
}

// Start 启动消费者（阻塞运行 supervisor，直到 ctx 取消）。
// 应在独立 goroutine 中调用；通过 Stop 触发优雅停止。
func (c *Consumer) Start(ctx context.Context) error {
	c.ctx, c.cancel = context.WithCancel(ctx)

	// 初始化任务通道与 Worker Pool
	workerCount := c.cfg.Consumer.WorkerCount
	if workerCount < 1 {
		workerCount = 1
	}
	c.taskCh = make(chan *model.Task, workerCount*2)
	c.acker = newChannelAcker(workerCount * 4)

	c.logger.Info("MQ 消费者初始化",
		slog.Int("workers", workerCount),
		slog.Int("prefetch", c.cfg.Consumer.PrefetchCount),
		slog.String("push_command_queue", c.cfg.Consumer.PushCommandQueue),
		slog.Duration("handle_timeout", c.cfg.Consumer.HandleTimeout),
	)

	// 启动 Worker Pool（生命周期级）
	for i := 0; i < workerCount; i++ {
		w := NewWorker(i, c.taskCh, c.registry, c.acker, c.cfg.Consumer.HandleTimeout, c.logger)
		c.workerWg.Add(1)
		go func() {
			defer c.workerWg.Done()
			w.Run(c.ctx)
		}()
	}
	c.logger.Info("Worker Pool 已启动", slog.Int("count", workerCount))

	// 启动 ack 循环（生命周期级）
	c.ackWg.Add(1)
	go func() {
		defer c.ackWg.Done()
		c.runAckLoop()
	}()

	// 启动任务通道深度监控（生命周期级）
	c.workerWg.Add(1)
	go func() {
		defer c.workerWg.Done()
		c.monitorDepth()
	}()

	// supervisor：运行消费循环，断线重连
	c.runSupervisor()
	return nil
}

// runSupervisor 反复运行 runOnce，断线时按指数退避重连。
// 最终停止（ctx 取消）的逻辑由 runOnce 内部完成。
func (c *Consumer) runSupervisor() {
	defer close(c.stopped)
	backoff := reconnectInitialBackoff
	retryCount := 0
	for {
		c.runOnce()
		if c.ctx.Err() != nil {
			// 主动停止，资源已在 runOnce 内清理
			c.logger.Info("MQ 消费者已停止（主动关闭）")
			return
		}
		consumerConnected.Set(0)
		retryCount++
		c.logger.Warn("MQ 连接断开，准备重连",
			slog.Int("retry_count", retryCount),
			slog.Duration("backoff", backoff),
			slog.String("broker", c.cfg.URI()),
		)
		backoff *= 2
		if backoff > reconnectMaxBackoff {
			backoff = reconnectMaxBackoff
		}
		select {
		case <-c.ctx.Done():
			return
		case <-time.After(backoff):
		}
	}
}

// runOnce 执行一次完整的连接生命周期。
// - 连接异常：返回后由 runSupervisor 重连
// - ctx 取消（最终停止）：在内部完成 workers 排空 + ACK + 关闭连接
func (c *Consumer) runOnce() {
	c.logger.Info("正在连接 RabbitMQ...",
		slog.String("broker", c.cfg.URI()),
		slog.String("vhost", c.cfg.VHost),
	)

	conn, err := amqp.Dial(c.cfg.URI())
	if err != nil {
		// 拨号失败，由 runSupervisor 重连
		c.logger.Error("RabbitMQ 连接失败",
			slog.String("broker", c.cfg.URI()),
			slog.String("error", err.Error()),
		)
		return
	}
	c.conn = conn

	c.logger.Info("RabbitMQ TCP 连接已建立，开始声明拓扑...")

	// 监听连接关闭
	closeErr := make(chan *amqp.Error, 1)
	conn.NotifyClose(closeErr)

	ch, err := c.setupTopology(conn)
	if err != nil {
		c.logger.Error("RabbitMQ 拓扑声明失败",
			slog.String("error", err.Error()),
		)
		_ = conn.Close()
		return
	}
	c.setConsumeCh(ch)
	consumerConnected.Set(1)

	c.logger.Info("RabbitMQ 拓扑声明完成，开始消费消息",
		slog.String("exchange_command", exchangeCommand),
		slog.Int("prefetch", c.cfg.Consumer.PrefetchCount),
	)

	// 启动每个队列的 delivery 协程（连接级）
	deliveryWg := sync.WaitGroup{}
	for queue, kind := range c.queues {
		deliveries, err := ch.Consume(
			queue,
			"",    // consumer tag 自动生成
			false, // autoAck=false，手动确认
			false, // exclusive
			false, // noLocal
			false, // noWait
			nil,
		)
		if err != nil {
			c.logger.Error("队列消费注册失败",
				slog.String("queue", queue),
				slog.String("error", err.Error()),
			)
			_ = ch.Close()
			_ = conn.Close()
			c.setConsumeCh(nil)
			return
		}
		c.logger.Info("队列消费已注册",
			slog.String("queue", queue),
			slog.String("kind", kind.String()),
		)
		deliveryWg.Add(1)
		go func(q string, k model.TaskKind, dlv <-chan amqp.Delivery) {
			defer deliveryWg.Done()
			c.dispatchLoop(q, k, dlv)
		}(queue, kind, deliveries)
	}

	// 等待连接关闭或主动停止
	finalShutdown := false
	select {
	case <-c.ctx.Done():
		finalShutdown = true
		c.logger.Info("收到停止信号，准备关闭 MQ 连接")
	case err := <-closeErr:
		// 连接异常关闭，需重连
		finalShutdown = false
		c.logger.Warn("RabbitMQ 连接异常关闭",
			slog.Int("code", err.Code),
			slog.String("reason", err.Reason),
			slog.Bool("recoverable", err.Recover),
		)
	}

	// 取消消费（停止新 delivery）
	_ = ch.Cancel("", false)
	// 等待所有 delivery 协程退出（conn 关闭后 deliveries channel 关闭）
	deliveryWg.Wait()

	if finalShutdown {
		// 最终停止：先让 workers 排空 taskCh 中的剩余任务并 ACK
		// 此时 consume 通道仍打开，ack 循环可正常处理 ACK
		close(c.taskCh)   // workers 排空后退出
		c.workerWg.Wait() // 等待 workers + monitor 退出
		c.acker.close()   // 关闭 ack 通道，ack 循环处理剩余 ACK 后退出
		c.ackWg.Wait()    // 等待 ack 循环退出
	}

	// 清理连接级资源
	c.setConsumeCh(nil)
	consumerConnected.Set(0)
	_ = ch.Close()
	_ = conn.Close()
}

// setupTopology 声明交换机 / 队列 / 绑定，并设置 QoS。
// 所有声明幂等，与 Java 侧 ImConstants 一致。
// 包含完整的 DLX/DLQ 死信机制配置。
func (c *Consumer) setupTopology(conn *amqp.Connection) (*amqp.Channel, error) {
	ch, err := conn.Channel()
	if err != nil {
		return nil, err
	}

	// 命令交换机（direct）
	if err := ch.ExchangeDeclare(exchangeCommand, "direct", true, false, false, false, nil); err != nil {
		return nil, fmt.Errorf("声明命令交换机失败: %w", err)
	}
	// 命令死信交换机（direct）
	if err := ch.ExchangeDeclare("im.dlx.command", "direct", true, false, false, false, nil); err != nil {
		return nil, fmt.Errorf("声明命令死信交换机失败: %w", err)
	}

	// 命令重试队列（TTL 5s，过期后通过 DLX 重投到原队列）
	// 幂等声明，与 Java 侧 RabbitMQConfig 对齐
	retrySendCmdArgs := amqp.Table{
		"x-dead-letter-exchange":    exchangeCommand,
		"x-dead-letter-routing-key": c.cfg.Consumer.SendCommandQueue,
		"x-message-ttl":             int32(5000),
	}
	if _, err := ch.QueueDeclare("im.retry.command.send", true, false, false, false, retrySendCmdArgs); err != nil {
		return nil, fmt.Errorf("声明重试队列 im.retry.command.send 失败: %w", err)
	}
	if err := ch.QueueBind("im.retry.command.send", "im.retry.command.send", "im.dlx.command", false, nil); err != nil {
		return nil, fmt.Errorf("绑定重试队列 im.retry.command.send 失败: %w", err)
	}

	retryPushCmdArgs := amqp.Table{
		"x-dead-letter-exchange":    exchangeCommand,
		"x-dead-letter-routing-key": c.cfg.Consumer.PushCommandQueue,
		"x-message-ttl":             int32(5000),
	}
	if _, err := ch.QueueDeclare("im.retry.command.push", true, false, false, false, retryPushCmdArgs); err != nil {
		return nil, fmt.Errorf("声明重试队列 im.retry.command.push 失败: %w", err)
	}
	if err := ch.QueueBind("im.retry.command.push", "im.retry.command.push", "im.dlx.command", false, nil); err != nil {
		return nil, fmt.Errorf("绑定重试队列 im.retry.command.push 失败: %w", err)
	}

	// 声明队列并绑定（含 DLX 配置）
	// 命令队列：DLX 指向 im.dlx.command，RK 指向重试队列
	queueDecls := []struct {
		name       string
		exchange   string
		routingKey string
		dlx        string
		dlxRK      string
	}{
		// 命令队列 — NACK(requeue=false) → im.dlx.command → retry queue → 原队列
		{c.cfg.Consumer.PushCommandQueue, exchangeCommand, c.cfg.Consumer.PushCommandQueue,
			"im.dlx.command", "im.retry.command.push"},
	}
	for _, q := range queueDecls {
		qd := amqp.Table{
			"x-dead-letter-exchange":    q.dlx,
			"x-dead-letter-routing-key": q.dlxRK,
		}
		if _, err := ch.QueueDeclare(q.name, true, false, false, false, qd); err != nil {
			return nil, fmt.Errorf("声明队列 %s 失败: %w", q.name, err)
		}
		if err := ch.QueueBind(q.name, q.routingKey, q.exchange, false, nil); err != nil {
			return nil, fmt.Errorf("绑定队列 %s 到交换机 %s 失败: %w", q.name, q.exchange, err)
		}
	}

	// 命令死信队列（重试耗尽后的最终归宿）
	cmdDlqDecls := []struct {
		name string
		rk   string
	}{
		{"im.dlq.command.send", "im.dlq.command.send"},
		{"im.dlq.command.push", "im.dlq.command.push"},
	}
	for _, dlq := range cmdDlqDecls {
		if _, err := ch.QueueDeclare(dlq.name, true, false, false, false, nil); err != nil {
			return nil, fmt.Errorf("声明命令死信队列 %s 失败: %w", dlq.name, err)
		}
		if err := ch.QueueBind(dlq.name, dlq.rk, "im.dlx.command", false, nil); err != nil {
			return nil, fmt.Errorf("绑定命令死信队列 %s 失败: %w", dlq.name, err)
		}
	}

	// ==================== 事件拓扑（送达 / 失败 / 发送失败回执） ====================
	// 事件交换机（topic，按路由键订阅，与 im-common ImConstants 对齐）
	if err := ch.ExchangeDeclare(exchangeEvent, "topic", true, false, false, false, nil); err != nil {
		return nil, fmt.Errorf("声明事件交换机失败: %w", err)
	}
	// 事件死信交换机（direct）
	if err := ch.ExchangeDeclare(dlxEvent, "direct", true, false, false, false, nil); err != nil {
		return nil, fmt.Errorf("声明事件死信交换机失败: %w", err)
	}

	// 事件消费队列 — 绑定到事件交换机，DLX 指向 im.dlx.event
	// 事件消息：NACK(requeue=false) → im.dlx.event → 各消费者独立 DLQ
	eventQueueDecls := []struct {
		name       string
		routingKey string
		dlx        string
		dlxRK      string
	}{
		{c.cfg.Consumer.DeliveredEventQueue, "im.message.delivered.event", dlxEvent, "im.dlq.event.delivered.router"},
		{c.cfg.Consumer.FailedEventQueue, "im.message.failed.event", dlxEvent, "im.dlq.event.failed.router"},
		{c.cfg.Consumer.SendFailedEventQueue, "im.message.send.failed.event", dlxEvent, "im.dlq.event.send.failed.router"},
	}
	for _, q := range eventQueueDecls {
		qd := amqp.Table{
			"x-dead-letter-exchange":    q.dlx,
			"x-dead-letter-routing-key": q.dlxRK,
		}
		if _, err := ch.QueueDeclare(q.name, true, false, false, false, qd); err != nil {
			return nil, fmt.Errorf("声明事件队列 %s 失败: %w", q.name, err)
		}
		if err := ch.QueueBind(q.name, q.routingKey, exchangeEvent, false, nil); err != nil {
			return nil, fmt.Errorf("绑定事件队列 %s 到交换机 %s 失败: %w", q.name, exchangeEvent, err)
		}
	}

	// 事件死信队列（重试耗尽后的最终归宿，各消费者独立）
	eventDlqDecls := []struct {
		name string
		rk   string
	}{
		{"im.dlq.event.delivered.router", "im.dlq.event.delivered.router"},
		{"im.dlq.event.failed.router", "im.dlq.event.failed.router"},
		{"im.dlq.event.send.failed.router", "im.dlq.event.send.failed.router"},
	}
	for _, dlq := range eventDlqDecls {
		if _, err := ch.QueueDeclare(dlq.name, true, false, false, false, nil); err != nil {
			return nil, fmt.Errorf("声明事件死信队列 %s 失败: %w", dlq.name, err)
		}
		if err := ch.QueueBind(dlq.name, dlq.rk, dlxEvent, false, nil); err != nil {
			return nil, fmt.Errorf("绑定事件死信队列 %s 失败: %w", dlq.name, err)
		}
	}

	// 设置 QoS（prefetch_count 控制每个消费者未确认消息数）
	prefetch := c.cfg.Consumer.PrefetchCount
	if prefetch < 1 {
		prefetch = 1
	}
	if err := ch.Qos(prefetch, 0, false); err != nil {
		return nil, fmt.Errorf("设置 QoS 失败: %w", err)
	}
	return ch, nil
}

// dispatchLoop 读取某队列的 delivery，转换为 Task 投递到任务通道。
// conn 关闭后 deliveries channel 关闭，循环退出。
func (c *Consumer) dispatchLoop(queue string, kind model.TaskKind, deliveries <-chan amqp.Delivery) {
	c.logger.Info("dispatchLoop 已启动，等待消息...",
		slog.String("queue", queue),
		slog.String("kind", kind.String()),
	)

	var count uint64
	lastLog := time.Now()
	for d := range deliveries {
		count++
		messagesReceived.WithLabelValues(queue).Inc()

		// 每 10 秒或每 100 条消息输出一次接收日志
		if count%100 == 1 || time.Since(lastLog) > 10*time.Second {
			c.logger.Info("收到 MQ 消息",
				slog.String("queue", queue),
				slog.String("kind", kind.String()),
				slog.Uint64("total_received", count),
				slog.Int("body_len", len(d.Body)),
				slog.Uint64("delivery_tag", d.DeliveryTag),
				slog.String("trace_id", parseTraceID(d.Headers)),
			)
			lastLog = time.Now()
		}

		task := &model.Task{
			Kind:        kind,
			Body:        d.Body,
			DeliveryTag: d.DeliveryTag,
			RetryCount:  parseRetryCount(d.Headers),
			TraceID:     parseTraceID(d.Headers),
		}
		select {
		case c.taskCh <- task:
		case <-c.ctx.Done():
			// 停止中，无法投递；conn 即将关闭，消息由 RabbitMQ 重投
			c.logger.Warn("dispatchLoop 停止中，消息将重投",
				slog.String("queue", queue),
				slog.Uint64("total_received", count),
			)
			return
		}
	}
	c.logger.Info("dispatchLoop 已退出",
		slog.String("queue", queue),
		slog.Uint64("total_received", count),
	)
}

// runAckLoop 串行执行 ACK/NACK（保证 amqp.Channel 并发安全）。
// 生命周期级：跨重连运行，通过 currentCh() 访问当前 consume 通道。
// 通道断开时 Ack/Nack 返回错误（忽略），消息由 RabbitMQ 重投。
func (c *Consumer) runAckLoop() {
	for req := range c.acker.ch {
		ch := c.currentCh()
		if ch == nil {
			// 当前无连接，跳过（消息将由 RabbitMQ 重投）
			continue
		}
		if req.ack {
			_ = ch.Ack(req.tag, false)
		} else {
			_ = ch.Nack(req.tag, false, req.requeue)
		}
	}
}

// monitorDepth 周期性上报任务通道排队深度。
func (c *Consumer) monitorDepth() {
	ticker := time.NewTicker(2 * time.Second)
	defer ticker.Stop()
	for {
		select {
		case <-c.ctx.Done():
			return
		case <-ticker.C:
			taskChannelDepth.Set(float64(len(c.taskCh)))
		}
	}
}

// Stop 优雅停止：取消消费 → 排空任务 → 等待处理完成 → 关闭连接。
// 阻塞直到所有在途消息处理完毕。
func (c *Consumer) Stop() {
	c.stopOnce.Do(func() {
		if c.cancel != nil {
			c.cancel()
		}
		// 等待 supervisor 退出（含 workers / ack 循环 / monitor）
		<-c.stopped
	})
}

// parseRetryCount 从 AMQP 消息头解析重试次数。
func parseRetryCount(headers amqp.Table) uint32 {
	if headers == nil {
		return 0
	}
	if v, ok := headers["x-retry-count"]; ok {
		switch n := v.(type) {
		case int32:
			return uint32(n)
		case int64:
			return uint32(n)
		case int:
			return uint32(n)
		}
	}
	return 0
}

// parseTraceID 从 AMQP 消息头解析链路追踪 ID。
func parseTraceID(headers amqp.Table) string {
	if headers == nil {
		return ""
	}
	if v, ok := headers["x-trace-id"]; ok {
		if s, ok := v.(string); ok {
			return s
		}
	}
	return ""
}

// 编译期保证接口实现
var _ Acker = (*channelAcker)(nil)
