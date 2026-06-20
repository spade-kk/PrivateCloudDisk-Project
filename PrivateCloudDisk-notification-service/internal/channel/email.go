// Package channel 提供邮件推送实现。
// 基于 SMTP 协议发送邮件，支持 HTML 富文本和纯文本。
package channel

import (
	"context"
	"crypto/tls"
	"fmt"
	"log"
	"net/smtp"
	"strings"

	"github.com/jordan-wright/email"

	"github.com/privateclouddisk/notification-service/internal/config"
)

// EmailChannel 邮件渠道
type EmailChannel struct {
	cfg config.EmailConfig
}

// NewEmailChannel 创建邮件渠道
func NewEmailChannel(cfg config.EmailConfig) *EmailChannel {
	if cfg.Enabled {
		log.Printf("[Email] 渠道初始化完成: smtp=%s:%d, from=%s", cfg.SMTPHost, cfg.SMTPPort, cfg.FromAddr)
	}
	return &EmailChannel{cfg: cfg}
}

func (e *EmailChannel) Name() string    { return "email" }
func (e *EmailChannel) IsEnabled() bool { return e.cfg.Enabled }

func (e *EmailChannel) Send(ctx context.Context, recipient string, msg *Message) (*SendResult, error) {
	if !e.cfg.Enabled {
		return &SendResult{Success: false, Error: fmt.Errorf("邮件渠道未启用")}, nil
	}

	em := email.NewEmail()
	em.From = fmt.Sprintf("%s <%s>", e.cfg.FromName, e.cfg.FromAddr)
	em.To = []string{recipient}
	em.Subject = msg.Title

	if msg.HTMLBody != "" {
		em.HTML = []byte(msg.HTMLBody)
		em.Text = []byte(stripHTML(msg.HTMLBody))
	} else {
		em.Text = []byte(msg.Body)
	}

	addr := fmt.Sprintf("%s:%d", e.cfg.SMTPHost, e.cfg.SMTPPort)
	auth := smtp.PlainAuth("", e.cfg.Username, e.cfg.Password, e.cfg.SMTPHost)

	var err error
	if e.cfg.UseTLS {
		tlsConfig := &tls.Config{
			ServerName:         e.cfg.SMTPHost,
			InsecureSkipVerify: false,
		}
		err = em.SendWithTLS(addr, auth, tlsConfig)
	} else {
		err = em.Send(addr, auth)
	}

	if err != nil {
		return &SendResult{
			Success: false,
			Error:   fmt.Errorf("邮件发送失败: %w", err),
		}, nil
	}

	return &SendResult{
		Success: true,
	}, nil
}

func (e *EmailChannel) SendBatch(ctx context.Context, recipients []string, msg *Message) ([]*SendResult, error) {
	results := make([]*SendResult, len(recipients))
	for i, recipient := range recipients {
		result, _ := e.Send(ctx, recipient, msg)
		results[i] = result
	}
	return results, nil
}

// stripHTML 简单去除 HTML 标签，用作纯文本 fallback
func stripHTML(html string) string {
	var result strings.Builder
	inTag := false
	for _, r := range html {
		switch r {
		case '<':
			inTag = true
		case '>':
			inTag = false
		default:
			if !inTag {
				result.WriteRune(r)
			}
		}
	}
	return result.String()
}