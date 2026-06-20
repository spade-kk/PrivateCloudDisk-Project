// Package channel 提供微信小程序订阅消息推送实现。
package channel

import (
	"context"
	"fmt"
	"log"

	"github.com/silenceper/wechat/v2"
	"github.com/silenceper/wechat/v2/cache"
	"github.com/silenceper/wechat/v2/miniprogram"
	miniConfig "github.com/silenceper/wechat/v2/miniprogram/config"
	"github.com/silenceper/wechat/v2/miniprogram/subscribe"

	"github.com/privateclouddisk/notification-service/internal/config"
)

// WeChatMPChannel 微信小程序渠道
type WeChatMPChannel struct {
	cfg      config.WeChatMPConfig
	miniProg *miniprogram.MiniProgram
}

// NewWeChatMPChannel 创建微信小程序渠道
func NewWeChatMPChannel(cfg config.WeChatMPConfig) *WeChatMPChannel {
	if cfg.Enabled {
		wc := wechat.NewWechat()
		memoryCache := cache.NewMemory()
		miniCfg := &miniConfig.Config{
			AppID:     cfg.AppID,
			AppSecret: cfg.AppSecret,
			Cache:     memoryCache,
		}
		miniProg := wc.GetMiniProgram(miniCfg)
		log.Printf("[WeChatMP] 渠道初始化完成: app_id=%s", cfg.AppID)
		return &WeChatMPChannel{cfg: cfg, miniProg: miniProg}
	}
	return &WeChatMPChannel{cfg: cfg}
}

func (w *WeChatMPChannel) Name() string    { return "wechat_mp" }
func (w *WeChatMPChannel) IsEnabled() bool { return w.cfg.Enabled }

func (w *WeChatMPChannel) Send(ctx context.Context, recipient string, msg *Message) (*SendResult, error) {
	if !w.cfg.Enabled {
		return &SendResult{Success: false, Error: fmt.Errorf("微信小程序渠道未启用")}, nil
	}

	// 微信小程序订阅消息需要: openid, template_id, page, data
	// recipient 应为 openid
	subscribeMsg := &subscribe.Message{
		ToUser:     recipient,
		TemplateID: "", // 从模板配置中获取
		Page:       "/pages/index/index",
		Data: map[string]*subscribe.DataItem{
			"thing1": {Value: msg.Title},
			"thing2": {Value: msg.Body},
		},
	}

	sub := w.miniProg.GetSubscribe()
	err := sub.Send(subscribeMsg)
	if err != nil {
		return &SendResult{
			Success: false,
			Error:   fmt.Errorf("微信小程序消息发送失败: %w", err),
		}, nil
	}

	return &SendResult{Success: true}, nil
}

func (w *WeChatMPChannel) SendBatch(ctx context.Context, recipients []string, msg *Message) ([]*SendResult, error) {
	results := make([]*SendResult, len(recipients))
	for i, recipient := range recipients {
		result, _ := w.Send(ctx, recipient, msg)
		results[i] = result
	}
	return results, nil
}