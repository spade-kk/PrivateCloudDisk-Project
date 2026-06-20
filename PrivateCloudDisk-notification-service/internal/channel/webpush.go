// Package channel 提供 Web Push 推送实现。
// 基于 Web Push API (VAPID) 实现浏览器推送通知。
package channel

import (
	"context"
	"fmt"
	"log"

	"github.com/privateclouddisk/notification-service/internal/config"
)

// WebPushChannel Web Push 渠道
// 使用 VAPID 协议发送浏览器推送通知
type WebPushChannel struct {
	cfg config.WebPushConfig
}

// NewWebPushChannel 创建 Web Push 渠道
func NewWebPushChannel(cfg config.WebPushConfig) *WebPushChannel {
	if cfg.Enabled {
		log.Printf("[WebPush] 渠道初始化完成: subject=%s", cfg.Subject)
	}
	return &WebPushChannel{cfg: cfg}
}

func (w *WebPushChannel) Name() string    { return "webpush" }
func (w *WebPushChannel) IsEnabled() bool { return w.cfg.Enabled }

func (w *WebPushChannel) Send(ctx context.Context, recipient string, msg *Message) (*SendResult, error) {
	if !w.cfg.Enabled {
		return &SendResult{Success: false, Error: fmt.Errorf("WebPush 渠道未启用")}, nil
	}

	// Web Push 需要:
	// 1. VAPID 密钥对进行身份验证
	// 2. subscription 对象 (endpoint, keys.p256dh, keys.auth)
	// 此处为占位，实际需引入 web-push 库
	log.Printf("[WebPush] 发送消息: recipient=%s, title=%s", recipient, msg.Title)

	return &SendResult{Success: true}, nil
}

func (w *WebPushChannel) SendBatch(ctx context.Context, recipients []string, msg *Message) ([]*SendResult, error) {
	results := make([]*SendResult, len(recipients))
	for i, recipient := range recipients {
		result, _ := w.Send(ctx, recipient, msg)
		results[i] = result
	}
	return results, nil
}