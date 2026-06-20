// Package channel 提供短信推送实现。
// 支持通用 HTTP 短信网关，可扩展阿里云、腾讯云等厂商。
package channel

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"time"

	"github.com/privateclouddisk/notification-service/internal/config"
)

// SMSChannel 短信渠道
type SMSChannel struct {
	cfg        config.SMSConfig
	httpClient *http.Client
}

// NewSMSChannel 创建短信渠道
func NewSMSChannel(cfg config.SMSConfig) *SMSChannel {
	if cfg.Enabled {
		log.Printf("[SMS] 渠道初始化完成: provider=%s, base_url=%s", cfg.Provider, cfg.BaseURL)
	}
	return &SMSChannel{
		cfg: cfg,
		httpClient: &http.Client{
			Timeout: 15 * time.Second,
		},
	}
}

func (s *SMSChannel) Name() string    { return "sms" }
func (s *SMSChannel) IsEnabled() bool { return s.cfg.Enabled }

func (s *SMSChannel) Send(ctx context.Context, recipient string, msg *Message) (*SendResult, error) {
	if !s.cfg.Enabled {
		return &SendResult{Success: false, Error: fmt.Errorf("短信渠道未启用")}, nil
	}

	payload := map[string]interface{}{
		"phone":       recipient,
		"template_id": s.cfg.TemplateID,
		"sign_name":   s.cfg.SignName,
		"params": map[string]string{
			"content": msg.Body,
		},
	}

	bodyBytes, err := json.Marshal(payload)
	if err != nil {
		return &SendResult{Success: false, Error: fmt.Errorf("序列化请求失败: %w", err)}, nil
	}

	req, err := http.NewRequestWithContext(ctx, "POST", s.cfg.BaseURL, bytes.NewReader(bodyBytes))
	if err != nil {
		return &SendResult{Success: false, Error: fmt.Errorf("创建请求失败: %w", err)}, nil
	}

	req.Header.Set("Content-Type", "application/json; charset=UTF-8")
	req.Header.Set("Authorization", "Bearer "+s.cfg.APIKey)

	resp, err := s.httpClient.Do(req)
	if err != nil {
		return &SendResult{Success: false, Error: fmt.Errorf("短信发送失败: %w", err)}, nil
	}
	defer resp.Body.Close()

	respBody, _ := io.ReadAll(resp.Body)

	if resp.StatusCode >= 200 && resp.StatusCode < 300 {
		return &SendResult{
			Success:          true,
			ProviderResponse: string(respBody),
		}, nil
	}

	return &SendResult{
		Success:          false,
		Error:            fmt.Errorf("短信网关返回错误: status=%d, body=%s", resp.StatusCode, string(respBody)),
		ProviderResponse: string(respBody),
	}, nil
}

func (s *SMSChannel) SendBatch(ctx context.Context, recipients []string, msg *Message) ([]*SendResult, error) {
	results := make([]*SendResult, len(recipients))
	for i, recipient := range recipients {
		result, _ := s.Send(ctx, recipient, msg)
		results[i] = result
	}
	return results, nil
}