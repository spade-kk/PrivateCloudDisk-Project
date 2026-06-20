// Package channel 提供支付宝小程序消息推送实现。
package channel

import (
	"context"
	"fmt"
	"log"

	"github.com/privateclouddisk/notification-service/internal/config"
)

// AlipayMPChannel 支付宝小程序渠道
// 支付宝小程序消息通过 alipay.open.app.mini.templatemessage.send 接口发送
type AlipayMPChannel struct {
	cfg config.AlipayMPConfig
}

// NewAlipayMPChannel 创建支付宝小程序渠道
func NewAlipayMPChannel(cfg config.AlipayMPConfig) *AlipayMPChannel {
	if cfg.Enabled {
		log.Printf("[AlipayMP] 渠道初始化完成: app_id=%s", cfg.AppID)
	}
	return &AlipayMPChannel{cfg: cfg}
}

func (a *AlipayMPChannel) Name() string    { return "alipay_mp" }
func (a *AlipayMPChannel) IsEnabled() bool { return a.cfg.Enabled }

func (a *AlipayMPChannel) Send(ctx context.Context, recipient string, msg *Message) (*SendResult, error) {
	if !a.cfg.Enabled {
		return &SendResult{Success: false, Error: fmt.Errorf("支付宝小程序渠道未启用")}, nil
	}

	// 支付宝小程序模板消息通过 OpenAPI 发送
	// 需要: to_user_id (支付宝用户ID), form_id, user_template_id, page, data
	// 此处实现为占位，实际需对接支付宝 OpenAPI SDK
	log.Printf("[AlipayMP] 发送消息: recipient=%s, title=%s", recipient, msg.Title)

	return &SendResult{Success: true}, nil
}

func (a *AlipayMPChannel) SendBatch(ctx context.Context, recipients []string, msg *Message) ([]*SendResult, error) {
	results := make([]*SendResult, len(recipients))
	for i, recipient := range recipients {
		result, _ := a.Send(ctx, recipient, msg)
		results[i] = result
	}
	return results, nil
}