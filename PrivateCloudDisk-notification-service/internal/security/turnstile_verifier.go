// Package security 提供人机验证码（CAPTCHA）校验能力。
//
// 完整迁移自 Spring Boot 平台服务 TurnstileCaptchaVerifier，
// 保持 1:1 逻辑一致，包括：
//   - enabled 开关控制
//   - secretKey 未配置时抛出异常
//   - token 为空时拒绝
//   - 向 Cloudflare siteverify 发起 POST 请求
//   - 校验 success 字段
//   - 可选的 action 一致性校验
//   - 可选的 hostname 一致性校验
//   - 幂等键（idempotency_key）防重复提交
//   - 可配置超时时间
package security

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"time"

	"github.com/google/uuid"

	"github.com/privateclouddisk/notification-service/internal/config"
	"github.com/privateclouddisk/notification-service/internal/domain"
)

// =============================================================================
// 接口定义
// =============================================================================

// CaptchaVerifier 人机验证码校验接口（对齐 Java CaptchaVerifier 接口）
type CaptchaVerifier interface {
	Verify(token, expectedAction, remoteIP string) error
}

// =============================================================================
// Turnstile 实现
// =============================================================================

// TurnstileVerifier Cloudflare Turnstile 人机验证码校验器
type TurnstileVerifier struct {
	cfg        *config.TurnstileConfig
	httpClient *http.Client
}

// NewTurnstileVerifier 创建 Turnstile 校验器
func NewTurnstileVerifier(cfg *config.TurnstileConfig) *TurnstileVerifier {
	timeout := time.Duration(cfg.TimeoutSec) * time.Second
	if timeout <= 0 {
		timeout = 3 * time.Second
	}

	return &TurnstileVerifier{
		cfg: cfg,
		httpClient: &http.Client{
			Timeout: timeout,
		},
	}
}

// Verify 校验 Turnstile token
//
// 校验流程（对齐 Java TurnstileCaptchaVerifier.verify）：
//  1. enabled=false 时直接放行
//  2. secretKey 为空时抛出异常
//  3. token 为空时抛出异常
//  4. 向 Cloudflare siteverify 发送 POST 请求
//  5. 校验 success 字段
//  6. 可选校验 action 一致性
//  7. 可选校验 hostname 一致性
func (v *TurnstileVerifier) Verify(token, expectedAction, remoteIP string) error {
	// 1. 未启用时直接放行
	if !v.cfg.Enabled {
		log.Printf("[Turnstile] 人机验证未启用，跳过: remoteIP=%s", remoteIP)
		return nil
	}

	// 2. 未配置密钥时抛异常（对齐 Java 行为）
	if v.cfg.SecretKey == "" {
		return &domain.VerificationError{
			Code:    500,
			Message: "人机验证码服务未配置密钥",
		}
	}

	// 3. token 为空时拒绝
	if token == "" {
		return &domain.VerificationError{
			Code:    400,
			Message: "请先完成人机验证",
		}
	}

	// 4. 向 Cloudflare siteverify 发起 POST 请求
	response, err := v.requestSiteVerify(token, remoteIP)
	if err != nil {
		log.Printf("[Turnstile] siteverify 请求失败: remoteIP=%s, error=%v", remoteIP, err)
		return &domain.VerificationError{
			Code:    500,
			Message: "人机验证码服务暂不可用",
		}
	}

	// 5. 校验 success 字段
	if response == nil || !response.Success {
		errorCodes := []string{}
		if response != nil {
			errorCodes = response.ErrorCodes
		}
		log.Printf("[Turnstile] 验证被拒绝: action=%s, remoteIP=%s, errors=%v",
			expectedAction, remoteIP, errorCodes)
		return &domain.VerificationError{
			Code:    400,
			Message: "人机验证失败，请刷新后重试",
		}
	}

	// 6. 可选校验 action 一致性
	if v.cfg.ValidateAction && expectedAction != "" {
		if expectedAction != response.Action {
			log.Printf("[Turnstile] action 不匹配: expected=%s, actual=%s, remoteIP=%s",
				expectedAction, response.Action, remoteIP)
			return &domain.VerificationError{
				Code:    400,
				Message: "人机验证码动作不匹配",
			}
		}
	}

	// 7. 可选校验 hostname 一致性
	if v.cfg.ExpectedHostname != "" {
		if v.cfg.ExpectedHostname != response.Hostname {
			log.Printf("[Turnstile] hostname 不匹配: expected=%s, actual=%s, remoteIP=%s",
				v.cfg.ExpectedHostname, response.Hostname, remoteIP)
			return &domain.VerificationError{
				Code:    400,
				Message: "人机验证码来源不匹配",
			}
		}
	}

	log.Printf("[Turnstile] 验证通过: action=%s, hostname=%s, remoteIP=%s",
		response.Action, response.Hostname, remoteIP)
	return nil
}

// requestSiteVerify 向 Cloudflare Turnstile siteverify 端点发起 POST 请求
func (v *TurnstileVerifier) requestSiteVerify(token, remoteIP string) (*TurnstileSiteVerifyResponse, error) {
	// 构建请求体（对齐 Java TurnstileCaptchaVerifier.requestSiteVerify）
	body := map[string]string{
		"secret":           v.cfg.SecretKey,
		"response":         token,
		"idempotency_key":  uuid.New().String(),
	}
	if remoteIP != "" {
		body["remoteip"] = remoteIP
	}

	bodyBytes, err := json.Marshal(body)
	if err != nil {
		return nil, fmt.Errorf("序列化请求体失败: %w", err)
	}

	req, err := http.NewRequestWithContext(
		context.Background(),
		http.MethodPost,
		v.cfg.SiteverifyURL,
		bytes.NewReader(bodyBytes),
	)
	if err != nil {
		return nil, fmt.Errorf("创建请求失败: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := v.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("HTTP 请求失败: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("siteverify 返回非 200: %d", resp.StatusCode)
	}

	var result TurnstileSiteVerifyResponse
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, fmt.Errorf("解析响应失败: %w", err)
	}

	return &result, nil
}

// =============================================================================
// 响应模型（对齐 Java TurnstileSiteVerifyResponse）
// =============================================================================

// TurnstileSiteVerifyResponse Cloudflare Turnstile siteverify 响应
type TurnstileSiteVerifyResponse struct {
	Success     bool     `json:"success"`
	ChallengeTs string   `json:"challenge_ts"`
	Hostname    string   `json:"hostname"`
	Action      string   `json:"action"`
	CData       string   `json:"cdata"`
	ErrorCodes  []string `json:"error-codes"`
}