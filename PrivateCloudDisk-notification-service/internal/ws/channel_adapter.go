package ws

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/privateclouddisk/notification-service/internal/channel"
	"github.com/privateclouddisk/notification-service/internal/domain"
)

// ChannelAdapter WS 渠道适配器，实现 channel.Channel 接口
type ChannelAdapter struct {
	hub *Hub
}

// NewChannelAdapter 创建 WS 渠道适配器
func NewChannelAdapter(hub *Hub) *ChannelAdapter {
	return &ChannelAdapter{hub: hub}
}

// Name 返回渠道名称
func (a *ChannelAdapter) Name() string {
	return string(domain.ChannelWS)
}

// Send 发送单条消息
func (a *ChannelAdapter) Send(ctx context.Context, recipient string, msg *channel.Message) (*channel.SendResult, error) {
	wsMsg := &domain.WSSystemMessage{
		ID:        fmt.Sprintf("ws-%d", time.Now().UnixNano()),
		Type:      "notification",
		Title:     msg.Title,
		Body:      msg.Body,
		Priority:  msg.Priority,
		Data:      msg.Data,
		Timestamp: time.Now().Unix(),
	}

	// 默认缓存策略：持久化
	cacheStrategy := domain.WSCachePersist

	a.hub.broadcast <- &broadcastMsg{
		UserID:        recipient,
		Message:       mustJSONMarshal(wsMsg),
		CacheStrategy: cacheStrategy,
	}

	return &channel.SendResult{Success: true}, nil
}

// SendBatch 批量发送消息
func (a *ChannelAdapter) SendBatch(ctx context.Context, recipients []string, msg *channel.Message) ([]*channel.SendResult, error) {
	results := make([]*channel.SendResult, len(recipients))
	for i, recipient := range recipients {
		result, _ := a.Send(ctx, recipient, msg)
		results[i] = result
	}
	return results, nil
}

// IsEnabled 检查渠道是否启用
func (a *ChannelAdapter) IsEnabled() bool {
	return a.hub != nil
}

// -------- 工具函数 --------

func mustJSONMarshal(v interface{}) []byte {
	data, _ := json.Marshal(v)
	return data
}