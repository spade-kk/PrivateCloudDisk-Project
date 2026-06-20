// Package channel 提供 APNs 推送实现。
package channel

import (
	"context"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"time"

	"github.com/sideshow/apns2"
	"github.com/sideshow/apns2/token"

	"github.com/privateclouddisk/notification-service/internal/config"
)

// APNsChannel Apple Push Notification service 渠道
type APNsChannel struct {
	cfg    config.APNsConfig
	client *apns2.Client
}

// NewAPNsChannel 创建 APNs 渠道
func NewAPNsChannel(cfg config.APNsConfig) (*APNsChannel, error) {
	if !cfg.Enabled {
		return &APNsChannel{cfg: cfg}, nil
	}

	authKey, err := token.AuthKeyFromFile(cfg.KeyPath)
	if err != nil {
		return nil, fmt.Errorf("加载 APNs 密钥失败: %w", err)
	}

	token := &token.Token{
		AuthKey: authKey,
		KeyID:   cfg.KeyID,
		TeamID:  cfg.TeamID,
	}

	client := apns2.NewTokenClient(token)
	if cfg.Production {
		client = client.Production()
	} else {
		client = client.Development()
	}

	client.HTTPClient = &http.Client{
		Transport: &http.Transport{
			TLSClientConfig: &tls.Config{
				MinVersion: tls.VersionTLS12,
			},
			MaxIdleConns:       10,
			IdleConnTimeout:    90 * time.Second,
			DisableCompression: false,
		},
		Timeout: 30 * time.Second,
	}

	log.Printf("[APNs] 渠道初始化完成: topic=%s, production=%v", cfg.Topic, cfg.Production)
	return &APNsChannel{cfg: cfg, client: client}, nil
}

func (a *APNsChannel) Name() string    { return "apns" }
func (a *APNsChannel) IsEnabled() bool { return a.cfg.Enabled }

func (a *APNsChannel) Send(ctx context.Context, recipient string, msg *Message) (*SendResult, error) {
	if !a.cfg.Enabled {
		return &SendResult{Success: false, Error: fmt.Errorf("APNs 未启用")}, nil
	}

	notification := &apns2.Notification{
		DeviceToken: recipient,
		Topic:       a.cfg.Topic,
		Payload:     a.buildPayload(msg),
		Priority:    apns2.PriorityHigh,
	}

	if msg.Priority < 5 {
		notification.Priority = apns2.PriorityLow
	}

	resp, err := a.client.PushWithContext(ctx, notification)
	if err != nil {
		return &SendResult{
			Success: false,
			Error:   fmt.Errorf("APNs 推送失败: %w", err),
		}, nil
	}

	if resp.StatusCode != 200 {
		return &SendResult{
			Success:          false,
			Error:            fmt.Errorf("APNs 返回错误: status=%d, reason=%s", resp.StatusCode, resp.Reason),
			ProviderResponse: resp.Reason,
			MessageID:        resp.ApnsID,
		}, nil
	}

	return &SendResult{
		Success:   true,
		MessageID: resp.ApnsID,
	}, nil
}

func (a *APNsChannel) SendBatch(ctx context.Context, recipients []string, msg *Message) ([]*SendResult, error) {
	results := make([]*SendResult, len(recipients))
	for i, token := range recipients {
		result, _ := a.Send(ctx, token, msg)
		results[i] = result
	}
	return results, nil
}

func (a *APNsChannel) buildPayload(msg *Message) string {
	aps := map[string]interface{}{
		"alert": map[string]string{
			"title": msg.Title,
			"body":  msg.Body,
		},
		"sound": "default",
		"badge": 1,
	}

	payload := map[string]interface{}{
		"aps": aps,
	}

	if msg.Data != nil {
		for k, v := range msg.Data {
			if k != "aps" {
				payload[k] = v
			}
		}
	}

	b, _ := json.Marshal(payload)
	return string(b)
}