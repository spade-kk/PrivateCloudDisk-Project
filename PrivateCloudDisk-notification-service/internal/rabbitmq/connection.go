// Package rabbitmq 提供 RabbitMQ 连接管理、拓扑声明和消息消费。
package rabbitmq

import (
	"context"
	"fmt"
	"log"
	"time"

	amqp "github.com/rabbitmq/amqp091-go"

	"github.com/privateclouddisk/notification-service/internal/config"
)

// =============================================================================
// 拓扑常量（与现有服务命名规范一致）
// =============================================================================
const (
	// 交换机
	ExchangeNotification    = "pcd.notification.exchange"
	ExchangeNotificationDLX = "pcd.notification.dlx"

	// 队列
	QueueEmail        = "pcd.notification.email.queue"
	QueueSMS          = "pcd.notification.sms.queue"
	QueuePush         = "pcd.notification.push.queue"
	QueueBatch        = "pcd.notification.batch.queue"
	QueueVerification = "pcd.notification.verification.queue" // 验证码队列
	QueueDLQ          = "pcd.notification.dlq"

	// 路由键
	RoutingEmail        = "notification.email"
	RoutingSMS          = "notification.sms"
	RoutingPush         = "notification.push"
	RoutingBatch        = "notification.batch"
	RoutingVerification = "notification.verification" // 验证码路由键
	RoutingDLQ          = "notification.dlq"

	// 消息 TTL
	MessageTTLVerification = 10 * 60 * 1000     // 验证码 10 分钟
	MessageTTLNormal       = 24 * 60 * 60 * 1000 // 普通消息 24 小时
)

// =============================================================================
// RabbitMQ 连接管理
// =============================================================================
type Connection struct {
	conn    *amqp.Connection
	channel *amqp.Channel
	cfg     config.RabbitMQConfig
}

// NewConnection 创建 RabbitMQ 连接
func NewConnection(cfg config.RabbitMQConfig) (*Connection, error) {
	conn, err := amqp.Dial(cfg.URL())
	if err != nil {
		return nil, fmt.Errorf("RabbitMQ 连接失败: %w", err)
	}

	ch, err := conn.Channel()
	if err != nil {
		conn.Close()
		return nil, fmt.Errorf("创建 Channel 失败: %w", err)
	}

	// 设置 QoS
	if err := ch.Qos(10, 0, false); err != nil {
		return nil, fmt.Errorf("设置 QoS 失败: %w", err)
	}

	log.Printf("[RabbitMQ] 连接成功: %s:%d%s", cfg.Host, cfg.Port, cfg.VHost)
	return &Connection{conn: conn, channel: ch, cfg: cfg}, nil
}

// Channel 返回 AMQP Channel
func (c *Connection) Channel() *amqp.Channel {
	return c.channel
}

// DeclareTopology 声明完整的消息拓扑
func (c *Connection) DeclareTopology() error {
	ch := c.channel

	// 1. 声明主交换机
	if err := ch.ExchangeDeclare(
		ExchangeNotification, "topic", true, false, false, false, nil,
	); err != nil {
		return fmt.Errorf("声明主交换机失败: %w", err)
	}

	// 2. 声明死信交换机
	if err := ch.ExchangeDeclare(
		ExchangeNotificationDLX, "topic", true, false, false, false, nil,
	); err != nil {
		return fmt.Errorf("声明死信交换机失败: %w", err)
	}

	// 3. 声明队列（带 DLX 绑定）
	queues := []struct {
		name    string
		routing string
		ttl     int
	}{
		{QueueEmail, RoutingEmail, MessageTTLNormal},
		{QueueSMS, RoutingSMS, MessageTTLNormal},
		{QueuePush, RoutingPush, MessageTTLNormal},
		{QueueBatch, RoutingBatch, MessageTTLNormal},
		{QueueVerification, RoutingVerification, MessageTTLVerification},
	}

	for _, q := range queues {
		args := amqp.Table{
			"x-dead-letter-exchange":    ExchangeNotificationDLX,
			"x-dead-letter-routing-key": RoutingDLQ,
			"x-message-ttl":             int32(q.ttl),
		}
		if _, err := ch.QueueDeclare(q.name, true, false, false, false, args); err != nil {
			return fmt.Errorf("声明队列 %s 失败: %w", q.name, err)
		}
		// 绑定路由键
		if err := ch.QueueBind(q.name, q.routing, ExchangeNotification, false, nil); err != nil {
			return fmt.Errorf("绑定队列 %s 失败: %w", q.name, err)
		}
		log.Printf("[RabbitMQ] 队列已声明: %s (routing: %s, ttl: %ds)", q.name, q.routing, q.ttl/1000)
	}

	// 4. 声明死信队列
	if _, err := ch.QueueDeclare(QueueDLQ, true, false, false, false, nil); err != nil {
		return fmt.Errorf("声明死信队列失败: %w", err)
	}
	if err := ch.QueueBind(QueueDLQ, RoutingDLQ, ExchangeNotificationDLX, false, nil); err != nil {
		return fmt.Errorf("绑定死信队列失败: %w", err)
	}
	log.Printf("[RabbitMQ] 死信队列已声明: %s", QueueDLQ)

	log.Println("[RabbitMQ] 拓扑声明完成")
	return nil
}

// Publish 发布消息到通知交换机
func (c *Connection) Publish(routingKey string, body []byte) error {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	return c.channel.PublishWithContext(ctx,
		ExchangeNotification,
		routingKey,
		false, // mandatory
		false, // immediate
		amqp.Publishing{
			ContentType:  "application/json",
			DeliveryMode: amqp.Persistent,
			Timestamp:    time.Now(),
			Body:         body,
		},
	)
}

// Connect 建立连接并声明拓扑
func Connect(cfg config.RabbitMQConfig) (*Connection, error) {
	conn, err := NewConnection(cfg)
	if err != nil {
		return nil, err
	}
	if err := conn.DeclareTopology(); err != nil {
		conn.Close()
		return nil, fmt.Errorf("声明拓扑失败: %w", err)
	}
	return conn, nil
}

// Consume 开始消费消息
func (c *Connection) Consume(queueName string, consumerTag string, autoAck bool) (<-chan amqp.Delivery, error) {
	return c.channel.Consume(
		queueName,
		consumerTag,
		autoAck,
		false, // exclusive
		false, // noLocal
		false, // noWait
		nil,
	)
}

// Close 关闭连接
func (c *Connection) Close() {
	if c.channel != nil {
		c.channel.Close()
	}
	if c.conn != nil {
		c.conn.Close()
	}
	log.Println("[RabbitMQ] 连接已关闭")
}

// IsHealthy 检查连接健康状态
func (c *Connection) IsHealthy() bool {
	if c.conn == nil || c.conn.IsClosed() {
		return false
	}
	return true
}