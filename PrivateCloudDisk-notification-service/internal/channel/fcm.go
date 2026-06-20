// Package channel 提供 FCM (Firebase Cloud Messaging) 推送实现。
package channel

import (
	"context"
	"fmt"
	"log"

	firebase "firebase.google.com/go/v4"
	"firebase.google.com/go/v4/messaging"
	"google.golang.org/api/option"

	"github.com/privateclouddisk/notification-service/internal/config"
)

// FCMChannel Firebase Cloud Messaging 渠道
type FCMChannel struct {
	cfg    config.FCMConfig
	client *messaging.Client
}

// NewFCMChannel 创建 FCM 渠道
func NewFCMChannel(cfg config.FCMConfig) (*FCMChannel, error) {
	if !cfg.Enabled {
		return &FCMChannel{cfg: cfg}, nil
	}

	opt := option.WithCredentialsFile(cfg.CredentialsPath)
	app, err := firebase.NewApp(context.Background(), nil, opt)
	if err != nil {
		return nil, fmt.Errorf("初始化 Firebase App 失败: %w", err)
	}

	client, err := app.Messaging(context.Background())
	if err != nil {
		return nil, fmt.Errorf("初始化 FCM 客户端失败: %w", err)
	}

	log.Println("[FCM] 渠道初始化完成")
	return &FCMChannel{cfg: cfg, client: client}, nil
}

func (f *FCMChannel) Name() string    { return "fcm" }
func (f *FCMChannel) IsEnabled() bool { return f.cfg.Enabled }

func (f *FCMChannel) Send(ctx context.Context, recipient string, msg *Message) (*SendResult, error) {
	if !f.cfg.Enabled {
		return &SendResult{Success: false, Error: fmt.Errorf("FCM 未启用")}, nil
	}

	message := &messaging.Message{
		Token: recipient,
		Notification: &messaging.Notification{
			Title: msg.Title,
			Body:  msg.Body,
		},
		Data: make(map[string]string),
	}

	if msg.Data != nil {
		for k, v := range msg.Data {
			message.Data[k] = fmt.Sprintf("%v", v)
		}
	}

	// Android 优先级
	if msg.Priority >= 10 {
		message.Android = &messaging.AndroidConfig{
			Priority: "high",
		}
	}
	// iOS APNs 优先级（通过 FCM 代理）
	message.APNS = &messaging.APNSConfig{
		Payload: &messaging.APNSPayload{
			Aps: &messaging.Aps{
				Sound: "default",
			},
		},
	}

	resp, err := f.client.Send(ctx, message)
	if err != nil {
		return &SendResult{
			Success: false,
			Error:   fmt.Errorf("FCM 推送失败: %w", err),
		}, nil
	}

	return &SendResult{
		Success:   true,
		MessageID: resp,
	}, nil
}

func (f *FCMChannel) SendBatch(ctx context.Context, recipients []string, msg *Message) ([]*SendResult, error) {
	if !f.cfg.Enabled {
		return nil, fmt.Errorf("FCM 未启用")
	}

	// 构建 multicast message
	message := &messaging.MulticastMessage{
		Tokens: recipients,
		Notification: &messaging.Notification{
			Title: msg.Title,
			Body:  msg.Body,
		},
		Data: make(map[string]string),
	}

	if msg.Data != nil {
		for k, v := range msg.Data {
			message.Data[k] = fmt.Sprintf("%v", v)
		}
	}

	resp, err := f.client.SendEachForMulticast(ctx, message)
	if err != nil {
		return nil, fmt.Errorf("FCM 批量推送失败: %w", err)
	}

	results := make([]*SendResult, len(resp.Responses))
	for i, r := range resp.Responses {
		results[i] = &SendResult{
			Success:   r.Success,
			MessageID: r.MessageID,
		}
		if r.Error != nil {
			results[i].Error = r.Error
		}
	}

	return results, nil
}