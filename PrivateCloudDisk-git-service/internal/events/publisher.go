package events

import (
	"bytes"
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"net/url"
	"time"

	amqp "github.com/rabbitmq/amqp091-go"

	"privateclouddisk/git-service/internal/config"
	"privateclouddisk/git-service/internal/secretbox"
	"privateclouddisk/git-service/internal/store"
)

type Publisher struct {
	cfg       config.Config
	store     *store.Store
	secretBox *secretbox.Box
	http      *http.Client
}

func NewPublisher(cfg config.Config, dataStore *store.Store) (*Publisher, error) {
	box, err := secretbox.New(cfg.InternalServiceToken)
	if err != nil {
		return nil, err
	}
	transport := &http.Transport{DialContext: (&net.Dialer{Timeout: 2 * time.Second}).DialContext, TLSHandshakeTimeout: 3 * time.Second}
	return &Publisher{cfg: cfg, store: dataStore, secretBox: box, http: &http.Client{
		Transport: transport,
		Timeout:   cfg.WebhookTimeout,
		// [FIX-GIT-WEBHOOK-20260816] 原行为跟随 3xx 自动重定向，导致创建时校验的
		// 地址与实际投递地址不一致，并可能绕过内网地址校验；重定向交由 Outbox 重试并记录失败。
		CheckRedirect: func(_ *http.Request, _ []*http.Request) error { return http.ErrUseLastResponse },
	}}, nil
}

// Run 使用数据库 Outbox 实现 at-least-once 事件发布。
// [REQ-GIT-HOOK-10.1~10.4] 生产者只发布 git.push.completed 事实，不感知订阅者数量。
func (p *Publisher) Run(ctx context.Context) {
	if p.cfg.RabbitURL == "" {
		log.Printf("GIT_RABBITMQ_URL is empty; Git outbox publisher is disabled")
		return
	}
	for ctx.Err() == nil {
		if err := p.runConnection(ctx); err != nil && ctx.Err() == nil {
			log.Printf("Git outbox publisher reconnecting after error: %v", err)
			select {
			case <-ctx.Done():
				return
			case <-time.After(3 * time.Second):
			}
		}
	}
}

func (p *Publisher) runConnection(ctx context.Context) error {
	connection, err := amqp.Dial(p.cfg.RabbitURL)
	if err != nil {
		return err
	}
	defer connection.Close()
	channel, err := connection.Channel()
	if err != nil {
		return err
	}
	defer channel.Close()
	if err := channel.ExchangeDeclare(p.cfg.EventExchange, "topic", true, false, false, false, nil); err != nil {
		return err
	}
	// [FIX-GIT-OUTBOX-20260816] 原行为未开启 publisher confirm，PublishWithContext
	// 返回 nil 只代表客户端已将消息写入连接，不能证明 RabbitMQ 已接收持久化。
	// 启用 Broker confirm 后，只有收到 ACK 才能将 Outbox 标记为 SENT。
	if err := channel.Confirm(false); err != nil {
		return fmt.Errorf("enable RabbitMQ publisher confirms: %w", err)
	}
	confirmations := channel.NotifyPublish(make(chan amqp.Confirmation, 1))
	ticker := time.NewTicker(250 * time.Millisecond)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return nil
		case <-ticker.C:
			events, err := p.store.ClaimOutbox(ctx, 50)
			if err != nil {
				return err
			}
			for _, event := range events {
				err := channel.PublishWithContext(ctx, event.Exchange, event.RoutingKey, false, false, amqp.Publishing{
					ContentType: "application/cloudevents+json", DeliveryMode: amqp.Persistent,
					MessageId: event.ID, Timestamp: time.Now().UTC(), Body: event.Payload,
				})
				if err == nil {
					select {
					case confirmation, open := <-confirmations:
						if !open || !confirmation.Ack {
							err = fmt.Errorf("RabbitMQ publisher NACK for event %s", event.ID)
						}
					case <-ctx.Done():
						err = ctx.Err()
					}
				}
				if err == nil {
					err = p.deliverWebhooks(ctx, event)
				}
				if err != nil {
					_ = p.store.MarkOutboxFailed(ctx, event.ID, err.Error(), event.Attempts)
					continue
				}
				_ = p.store.MarkOutboxSent(ctx, event.ID)
			}
		}
	}
}

func (p *Publisher) deliverWebhooks(ctx context.Context, event struct {
	ID, Aggregate, EventType, Exchange, RoutingKey string
	Payload                                        []byte
	Attempts                                       int
}) error {
	hooks, err := p.store.ListWebhooks(ctx, event.Aggregate, true)
	if err != nil {
		return err
	}
	for _, hook := range hooks {
		if !hook.Active || !contains(hook.Events, event.EventType) {
			continue
		}
		if err := validateDestination(hook.URL, p.cfg.WebhookAllowHTTP); err != nil {
			return err
		}
		secret, err := p.secretBox.Open(hook.Secret)
		if err != nil {
			return err
		}
		digest := hmac.New(sha256.New, secret)
		_, _ = digest.Write(event.Payload)
		req, err := http.NewRequestWithContext(ctx, http.MethodPost, hook.URL, bytes.NewReader(event.Payload))
		if err != nil {
			return err
		}
		req.Header.Set("Content-Type", "application/cloudevents+json")
		req.Header.Set("X-PCD-Git-Event", event.EventType)
		req.Header.Set("X-PCD-Git-Delivery", event.ID)
		req.Header.Set("X-PCD-Git-Signature-256", "sha256="+hex.EncodeToString(digest.Sum(nil)))
		response, err := p.http.Do(req)
		if err != nil {
			return err
		}
		_, _ = io.Copy(io.Discard, io.LimitReader(response.Body, 4096))
		response.Body.Close()
		if response.StatusCode < 200 || response.StatusCode >= 300 {
			return fmt.Errorf("webhook returned %d", response.StatusCode)
		}
	}
	return nil
}

func contains(values []string, target string) bool {
	for _, value := range values {
		if value == target {
			return true
		}
	}
	return false
}

func validateDestination(raw string, allowHTTP bool) error {
	parsed, err := url.Parse(raw)
	if err != nil || parsed.Hostname() == "" || parsed.User != nil {
		return fmt.Errorf("invalid webhook URL")
	}
	if parsed.Scheme != "https" && !(allowHTTP && parsed.Scheme == "http") {
		return fmt.Errorf("webhook requires HTTPS")
	}
	addresses, err := net.LookupIP(parsed.Hostname())
	if err != nil {
		return err
	}
	for _, address := range addresses {
		if address.IsPrivate() || address.IsLoopback() || address.IsLinkLocalUnicast() || address.IsUnspecified() {
			return fmt.Errorf("webhook destination is private")
		}
	}
	return nil
}
