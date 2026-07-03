// Package channel 提供多渠道推送实现。
// 支持 APNs (iOS)、FCM (Android)、微信/支付宝小程序模板消息、Web Push、邮件、短信。
package channel

import (
	"context"
	"fmt"

	"github.com/privateclouddisk/notification-service/internal/domain"
)

// Message 推送消息结构
type Message struct {
	Title    string
	Body     string
	HTMLBody string                 // HTML 邮件内容
	Data     map[string]interface{} // 自定义数据
	Priority int
}

// SendResult 发送结果
type SendResult struct {
	Success          bool
	ProviderResponse string
	Error            error
	MessageID        string // 第三方返回的消息 ID
}

// Channel 渠道接口
type Channel interface {
	// Name 返回渠道名称
	Name() string

	// Send 发送单条消息
	Send(ctx context.Context, recipient string, msg *Message) (*SendResult, error)

	// SendBatch 批量发送消息
	SendBatch(ctx context.Context, recipients []string, msg *Message) ([]*SendResult, error)

	// IsEnabled 检查渠道是否启用
	IsEnabled() bool
}

// =============================================================================
// ChannelManager 渠道管理器
// =============================================================================
type ChannelManager struct {
	channels map[string]Channel
}

// NewChannelManager 创建渠道管理器
func NewChannelManager() *ChannelManager {
	return &ChannelManager{
		channels: make(map[string]Channel),
	}
}

// Register 注册渠道
func (m *ChannelManager) Register(ch Channel) {
	m.channels[ch.Name()] = ch
}

// Get 获取渠道
func (m *ChannelManager) Get(name string) Channel {
	return m.channels[name]
}

// Send 通过指定渠道发送消息
func (m *ChannelManager) Send(ctx context.Context, channelName string, recipient string, msg *Message) (*SendResult, error) {
	ch := m.Get(channelName)
	if ch == nil {
		return nil, fmt.Errorf("渠道 %s 未注册", channelName)
	}
	if !ch.IsEnabled() {
		return &SendResult{Success: false, Error: fmt.Errorf("渠道 %s 未启用", channelName)}, nil
	}
	return ch.Send(ctx, recipient, msg)
}

// SendBatch 通过指定渠道批量发送
func (m *ChannelManager) SendBatch(ctx context.Context, channelName string, recipients []string, msg *Message) ([]*SendResult, error) {
	ch := m.Get(channelName)
	if ch == nil {
		return nil, fmt.Errorf("渠道 %s 未注册", channelName)
	}
	if !ch.IsEnabled() {
		return nil, fmt.Errorf("渠道 %s 未启用", channelName)
	}
	return ch.SendBatch(ctx, recipients, msg)
}

// GetEnabledChannels 获取所有启用的渠道
func (m *ChannelManager) GetEnabledChannels() []string {
	var enabled []string
	for name, ch := range m.channels {
		if ch.IsEnabled() {
			enabled = append(enabled, name)
		}
	}
	return enabled
}

// Dispatch 根据事件渠道分发到具体渠道
func (m *ChannelManager) Dispatch(
	ctx context.Context,
	channelName string,
	msg *Message,
	deviceTokens []string,
	email string,
	phone string,
) (*SendResult, error) {
	switch channelName {
	case string(domain.ChannelAPNs), string(domain.ChannelFCM), string(domain.ChannelWebPush), "push":
		// 推送渠道：使用 device tokens
		if len(deviceTokens) == 0 {
			return nil, fmt.Errorf("无可用设备 Token")
		}
		var results []*SendResult
		var err error
		if channelName == "push" {
			// push 是通用渠道，逐一尝试 APNs → FCM → WebPush
			for _, chName := range []string{string(domain.ChannelAPNs), string(domain.ChannelFCM), string(domain.ChannelWebPush)} {
				results, err = m.SendBatch(ctx, chName, deviceTokens, msg)
				if err == nil && len(results) > 0 {
					return results[0], nil
				}
			}
			return nil, fmt.Errorf("所有推送渠道均失败")
		}
		results, err = m.SendBatch(ctx, channelName, deviceTokens, msg)
		if err != nil {
			return nil, err
		}
		if len(results) > 0 {
			return results[0], nil
		}
		return nil, fmt.Errorf("发送失败")

	case string(domain.ChannelEmail):
		return m.Send(ctx, channelName, email, msg)

	case string(domain.ChannelSMS):
		return m.Send(ctx, channelName, phone, msg)

	case string(domain.ChannelWeChatMP), string(domain.ChannelAlipayMP):
		// 小程序模板消息直接使用 userID
		return m.Send(ctx, channelName, "", msg)

	case string(domain.ChannelWS):
		// WebSocket 系统推送：使用 userID 作为 recipient
		return m.Send(ctx, channelName, "", msg)

	default:
		return nil, fmt.Errorf("不支持的渠道: %s", channelName)
	}
}