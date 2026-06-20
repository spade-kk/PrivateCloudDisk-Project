// Package rabbitmq 提供 RabbitMQ 消息消费者。
package rabbitmq

import (
	"encoding/json"
	"fmt"
	"log"
	"time"

	amqp "github.com/rabbitmq/amqp091-go"

	"github.com/privateclouddisk/notification-service/internal/config"
	"github.com/privateclouddisk/notification-service/internal/domain"
	"github.com/privateclouddisk/notification-service/internal/service"
)

// Consumer 消息消费者
type Consumer struct {
	conn            *Connection
	cfg             *config.Config
	notifService    *service.NotificationService
	shutdownCh      chan struct{}
}

// NewConsumer 创建消费者
func NewConsumer(conn *Connection, cfg *config.Config, notifService *service.NotificationService) *Consumer {
	return &Consumer{
		conn:         conn,
		cfg:          cfg,
		notifService: notifService,
		shutdownCh:   make(chan struct{}),
	}
}

// Start 启动所有消费者
func (c *Consumer) Start() error {
	log.Println("[Consumer] 启动消费者...")

	// 启动各队列消费者
	queues := map[string]string{
		QueueEmail: "email-consumer",
		QueueSMS:   "sms-consumer",
		QueuePush:  "push-consumer",
		QueueBatch: "batch-consumer",
	}

	for queue, tag := range queues {
		go c.consumeQueue(queue, tag)
	}

	log.Println("[Consumer] 所有消费者已启动")
	return nil
}

// consumeQueue 消费指定队列
func (c *Consumer) consumeQueue(queueName, consumerTag string) {
	log.Printf("[Consumer] 开始消费队列: %s (tag=%s)", queueName, consumerTag)

	for {
		select {
		case <-c.shutdownCh:
			log.Printf("[Consumer] 消费者 %s 收到关闭信号", consumerTag)
			return
		default:
		}

		deliveries, err := c.conn.Consume(queueName, consumerTag, false)
		if err != nil {
			log.Printf("[Consumer] 消费队列 %s 失败: %v，5秒后重试...", queueName, err)
			time.Sleep(5 * time.Second)
			continue
		}

		c.processDeliveries(deliveries, consumerTag)

		// 如果连接断开，等待后重连
		time.Sleep(5 * time.Second)
	}
}

// processDeliveries 处理消息投递
func (c *Consumer) processDeliveries(deliveries <-chan amqp.Delivery, consumerTag string) {
	for delivery := range deliveries {
		go c.handleDelivery(delivery)
	}
}

// handleDelivery 处理单条消息
func (c *Consumer) handleDelivery(delivery amqp.Delivery) {
	eventID := "unknown"
	defer func() {
		if r := recover(); r != nil {
			log.Printf("[Consumer] 消息处理 panic: eventID=%s, panic=%v", eventID, r)
			delivery.Nack(false, false) // 不重试，进入 DLQ
		}
	}()

	// 1. 解析消息
	event, err := domain.FromJSON(delivery.Body)
	if err != nil {
		log.Printf("[Consumer] 消息解析失败: %v, body=%s", err, string(delivery.Body))
		delivery.Nack(false, false) // 格式错误，不重试
		return
	}
	eventID = event.EventID

	log.Printf("[Consumer] 收到消息: eventID=%s, type=%s, userID=%s, channels=%v",
		eventID, event.EventType, event.UserID, event.Channels)

	// 2. 重试次数检查
	if event.RetryCount >= c.cfg.Worker.RetryMaxAttempts {
		log.Printf("[Consumer] 已达最大重试次数: eventID=%s, retryCount=%d",
			eventID, event.RetryCount)
		delivery.Nack(false, false) // 进入 DLQ
		return
	}

	// 3. 处理事件
	err = c.notifService.ProcessEvent(event)
	if err != nil {
		log.Printf("[Consumer] 处理失败: eventID=%s, error=%v", eventID, err)

		// 重试逻辑：递增重试次数，Nack 并重新入队
		event.RetryCount++
		retryBody, _ := event.ToJSON()
		backoff := time.Duration(c.cfg.Worker.RetryBackoffMs) * time.Millisecond * time.Duration(event.RetryCount)

		// 重新发布到原队列（带延迟）
		time.Sleep(backoff)
		_ = c.conn.Publish(delivery.RoutingKey, retryBody)

		delivery.Nack(false, false) // 不重新入队（已手动重新发布）
		return
	}

	// 4. 成功确认
	delivery.Ack(false)
	log.Printf("[Consumer] 消息处理成功: eventID=%s", eventID)
}

// Shutdown 关闭消费者
func (c *Consumer) Shutdown() {
	close(c.shutdownCh)
	log.Println("[Consumer] 消费者已关闭")
}

// PublishToDLQ 发布消息到死信队列
func (c *Consumer) PublishToDLQ(event *domain.NotificationEvent, reason string) error {
	event.RetryCount = c.cfg.Worker.RetryMaxAttempts + 1 // 标记为 DLQ
	body, err := event.ToJSON()
	if err != nil {
		return fmt.Errorf("序列化 DLQ 消息失败: %w", err)
	}

	log.Printf("[Consumer] 消息进入 DLQ: eventID=%s, reason=%s", event.EventID, reason)
	return c.conn.Publish(RoutingDLQ, body)
}

// PublishEvent 发布通知事件到交换机（供 HTTP API 使用）
func (c *Consumer) PublishEvent(event *domain.NotificationEvent) error {
	body, err := event.ToJSON()
	if err != nil {
		return fmt.Errorf("序列化事件失败: %w", err)
	}

	// 根据事件类型选择路由键
	routingKey := RoutingPush
	switch event.EventType {
	case "email_verification", "user_registered":
		if event.Email != "" {
			routingKey = RoutingEmail
		}
	case "phone_verification":
		routingKey = RoutingSMS
	}

	log.Printf("[Consumer] 发布事件: eventID=%s, routing=%s", event.EventID, routingKey)
	return c.conn.Publish(routingKey, body)
}

// =============================================================================
// 重连监控
// =============================================================================

// MonitorConnection 监控连接状态，断线自动重连
func (c *Consumer) MonitorConnection() {
	ticker := time.NewTicker(10 * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-c.shutdownCh:
			return
		case <-ticker.C:
			if !c.conn.IsHealthy() {
				log.Println("[Consumer] RabbitMQ 连接断开，尝试重连...")
				// 重连逻辑由外部处理
			}
		}
	}
}

// =============================================================================
// 辅助函数
// =============================================================================

// parseEventType 解析事件类型到路由键
func parseEventType(eventType string) string {
	switch eventType {
	case "user_registered":
		return RoutingEmail
	case "email_verification":
		return RoutingEmail
	case "phone_verification":
		return RoutingSMS
	case "share_notify", "system_notify":
		return RoutingPush
	default:
		return RoutingPush
	}
}

// JSON helpers
func toJSON(v interface{}) []byte {
	b, _ := json.Marshal(v)
	return b
}