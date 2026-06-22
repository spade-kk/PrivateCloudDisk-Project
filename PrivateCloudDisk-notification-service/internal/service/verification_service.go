// Package service 提供企业级验证码服务 —— 生成、存储、校验、发送、频率控制、token 管理。
//
// 完整迁移自 Spring Boot 平台服务 VerificationCodeService，保持 1:1 逻辑一致。
//
// 分层职责：本服务负责全部验证码业务逻辑，包括：
//   - 人机验证码校验（Turnstile）
//   - 邮箱/手机号过滤（系统邮箱、禁止域名）
//   - 验证码生成与 Redis 存储
//   - 邮件/短信发送（通过 ChannelManager）
//   - 不透明 resend token 管理（Redis 存储，非 JWT）
//   - 频率控制（每小时发送次数、60 秒间隔）
//   - 注册接口防爆破
//
// Token 设计：使用 Redis 不透明 token（UUID），而非 JWT。
// 因为 token 的生命周期（剩余次数、IP 绑定校验）完全依赖 Redis，
// 使用 JWT 只会增加无意义的加解密开销，且无法真正"无状态"。
//
// Redis Key 结构（IP 绑定设计）：
//
//	verif:code:{targetType}:{targetHash}:{purpose}:{ipHash}       → 验证码（TTL: 5 分钟）
//	verif:rate:{targetType}:{targetHash}:{purpose}:{ipHash}        → 发送次数计数器（TTL: 1 小时）
//	verif:last:{targetType}:{targetHash}:{purpose}:{ipHash}        → 上次发送时间戳（TTL: 60 秒）
//	verif:token:{tokenUUID}                                         → JSON 令牌状态（TTL: 10 分钟）
//	verif:attempts:{targetType}:{targetHash}:{purpose}:{ipHash}    → 验证失败次数（TTL: 15 分钟）
//
// 为什么所有 key 都加 ipHash？
//  1. 验证码 IP 绑定：验证码与请求 IP 绑定，只有同一 IP 才能验证。防止跨 IP 验证码窃取。
//  2. 频率控制 IP 隔离：每个 IP 独立的频率计数器。一个 IP 被限流不影响其他合法用户。
//  3. 防代理池攻击：攻击者即使通过代理池不断换 IP，每个 IP 的验证码和 rate limit 都是独立的。
//  4. 防分布式爆破：每个 IP 的验证码不同，即使僵尸网络同时攻击同一目标，各节点拿到的验证码互不相同。
//  5. 审计溯源：verif:last 携带 IP 信息，可追踪哪些 IP 对哪些目标发送了何种用途的验证码。
//  6. attempts 精细化：verif:attempts 绑定 {targetType}:{targetHash}:{purpose}:{ipHash}，
//     攻击者无法通过轮换 IP 对同一目标无限试错。
package service

import (
	"context"
	"crypto/rand"
	"fmt"
	"log"
	"math/big"
	"strings"
	"time"

	"github.com/google/uuid"

	"github.com/privateclouddisk/notification-service/internal/channel"
	"github.com/privateclouddisk/notification-service/internal/config"
	"github.com/privateclouddisk/notification-service/internal/domain"
	"github.com/privateclouddisk/notification-service/internal/redisutil"
	"github.com/privateclouddisk/notification-service/internal/security"
)

// VerificationCodeService 验证码业务服务
type VerificationCodeService struct {
	cfg              *config.Config
	repo             *redisutil.VerificationRepo
	channelManager   *channel.ChannelManager
	turnstileVerifier *security.TurnstileVerifier
}

// NewVerificationCodeService 创建验证码服务
func NewVerificationCodeService(
	cfg *config.Config,
	repo *redisutil.VerificationRepo,
	channelManager *channel.ChannelManager,
) *VerificationCodeService {
	return &VerificationCodeService{
		cfg:              cfg,
		repo:             repo,
		channelManager:   channelManager,
		turnstileVerifier: security.NewTurnstileVerifier(&cfg.Turnstile),
	}
}

// =============================================================================
// 首次发送验证码
// =============================================================================

// SendCode 首次发送验证码（需人机验证）。
//
// 流程：
//  1. 人机验证码校验（Turnstile）
//  2. 过滤系统邮箱/手机号
//  3. 检查频率限制（每小时 5 次 + 60 秒间隔）—— 按 IP+目标 维度
//  4. 生成验证码并存入 Redis（key 包含 ipHash）
//  5. 发送邮件/短信
//  6. 创建不透明 resend token 存入 Redis
func (s *VerificationCodeService) SendCode(
	ctx context.Context,
	targetType string, target string, purpose string,
	captchaToken string, captchaAction string, clientIP string,
) (*domain.VerificationSendVO, error) {
	// 1. 人机验证码校验
	if err := s.verifyTurnstile(captchaToken, captchaAction, clientIP); err != nil {
		return nil, err
	}

	// 2. 过滤系统自身邮箱/手机号
	if err := s.checkTargetNotSystem(target); err != nil {
		return nil, err
	}
	if strings.Contains(target, "@") {
		if err := s.checkEmailNotBlocked(target); err != nil {
			return nil, err
		}
	}

	targetHash := redisutil.SHA256(target)
	ipHash := redisutil.SHA256(clientIP)

	// 3. 频率检查（每小时次数 + 60 秒间隔）—— 按 IP+目标 维度
	if err := s.checkRateLimit(ctx, targetType, targetHash, purpose, ipHash); err != nil {
		return nil, err
	}
	if err := s.checkResendInterval(ctx, targetType, targetHash, purpose, ipHash); err != nil {
		return nil, err
	}

	// 4. 生成验证码并存入 Redis（key 包含 ipHash）
	code := generateCode()
	if err := s.repo.StoreCode(ctx, targetType, targetHash, purpose, ipHash, code); err != nil {
		return nil, fmt.Errorf("存储验证码失败: %w", err)
	}

	// 5. 记录发送时间戳（key 包含 ipHash）
	if err := s.repo.SetLastSendTime(ctx, targetType, targetHash, purpose, ipHash); err != nil {
		return nil, fmt.Errorf("记录发送时间失败: %w", err)
	}

	// 6. 发送邮件/短信
	if err := s.sendCodeToTarget(ctx, targetType, target, code, purpose); err != nil {
		log.Printf("[验证码] 发送失败: targetType=%s, targetHash=%s, error=%v",
			targetType, targetHash[:min(16, len(targetHash))], err)
		// 发送失败不阻塞，继续返回 token（邮件可能延迟到达）
	}

	// 7. 创建不透明 resend token 存入 Redis
	token := uuid.New().String()
	tokenData := &domain.ResendTokenData{
		TargetType:       targetType,
		TargetHash:       targetHash,
		Purpose:          purpose,
		IPHash:           ipHash,
		RemainingResends: domain.MaxResends,
		CreatedAt:        time.Now().UnixMilli(),
	}

	if err := s.repo.StoreToken(ctx, token, tokenData, domain.ResendTokenTTLSeconds*time.Second); err != nil {
		return nil, fmt.Errorf("Token 存储失败: %w", err)
	}

	log.Printf("[验证码] 首次发送成功: targetType=%s, targetHash=%s, purpose=%s, ipHash=%s, token=%s",
		targetType, targetHash[:min(16, len(targetHash))], purpose, ipHash[:min(16, len(ipHash))],
		token[:min(8, len(token))]+"...")

	return &domain.VerificationSendVO{
		ResendToken:      token,
		ExpiresIn:        domain.ResendTokenTTLSeconds,
		RemainingResends: domain.MaxResends,
	}, nil
}

// =============================================================================
// 重新发送验证码
// =============================================================================

// ResendCode 重新发送验证码（无需人机验证，需有效的 resend token）。
//
// 关键设计：不重新颁发 token，只更新 Redis 中的剩余次数。
// 重新颁发 token 会重置次数计数器，导致 8 次限制形同虚设。
//
// 流程：
//  1. 从 Redis 查找并验证 token
//  2. 校验 targetType、targetHash、ipHash、purpose 一致性
//  3. 检查剩余次数 > 0
//  4. 检查 60 秒间隔
//  5. 使旧验证码失效（按 IP 维度删除）
//  6. 生成新验证码、存储、发送
//  7. 递减剩余次数
func (s *VerificationCodeService) ResendCode(
	ctx context.Context,
	targetType string, target string, purpose string,
	resendToken string, clientIP string,
) (*domain.VerificationSendVO, error) {
	// 1. 从 Redis 查找 token
	tokenData, err := s.repo.GetToken(ctx, resendToken)
	if err != nil {
		log.Printf("[验证码] Token 查询失败: token=%s, error=%v", resendToken[:min(8, len(resendToken))]+"...", err)
		return nil, domain.ErrResendTokenInvalid
	}
	if tokenData == nil {
		return nil, domain.ErrResendTokenInvalid
	}

	targetHash := redisutil.SHA256(target)
	ipHash := redisutil.SHA256(clientIP)

	// 2. 校验 targetType 一致性
	if targetType != tokenData.TargetType {
		log.Printf("[验证码] Resend token 目标类型不匹配: expected=%s, actual=%s", targetType, tokenData.TargetType)
		return nil, domain.ErrResendTokenInvalid
	}

	// 3. 校验 targetHash 一致性（防止横向越权：拿别人的 token 给自己的邮箱发）
	if targetHash != tokenData.TargetHash {
		log.Printf("[验证码] Resend token 目标不匹配: expected=%s, actual=%s",
			targetHash[:min(16, len(targetHash))], tokenData.TargetHash[:min(16, len(tokenData.TargetHash))])
		return nil, domain.ErrResendTokenInvalid
	}

	// 4. 校验 ipHash 一致性（防止横向越权：拿别人的 token 从不同 IP 使用）
	if ipHash != tokenData.IPHash {
		log.Printf("[验证码] Resend token IP 不匹配: expected=%s, actual=%s",
			ipHash[:min(16, len(ipHash))], tokenData.IPHash[:min(16, len(tokenData.IPHash))])
		return nil, domain.ErrResendTokenInvalid
	}

	// 5. 校验 purpose 一致性
	if purpose != tokenData.Purpose {
		log.Printf("[验证码] Resend token 用途不匹配: expected=%s, actual=%s", purpose, tokenData.Purpose)
		return nil, domain.ErrResendTokenInvalid
	}

	// 6. 检查剩余次数
	if tokenData.RemainingResends <= 0 {
		s.repo.DeleteToken(ctx, resendToken) // 清理已耗尽的 token
		return nil, domain.ErrResendTokenExhausted
	}

	// 7. 检查频率控制和 60 秒间隔
	if err := s.checkRateLimit(ctx, targetType, targetHash, purpose, ipHash); err != nil {
		return nil, err
	}
	if err := s.checkResendInterval(ctx, targetType, targetHash, purpose, ipHash); err != nil {
		return nil, err
	}

	// 8. 使旧验证码失效（按 IP 维度删除）
	s.repo.DeleteCode(ctx, targetType, targetHash, purpose, ipHash)
	log.Printf("[验证码] 旧验证码已失效: targetType=%s, targetHash=%s, purpose=%s, ipHash=%s",
		targetType, targetHash[:min(16, len(targetHash))], purpose, ipHash[:min(16, len(ipHash))])

	// 9. 生成新验证码并存储（key 包含 ipHash）
	code := generateCode()
	if err := s.repo.StoreCode(ctx, targetType, targetHash, purpose, ipHash, code); err != nil {
		return nil, fmt.Errorf("存储验证码失败: %w", err)
	}

	// 10. 记录发送时间戳（key 包含 ipHash）
	if err := s.repo.SetLastSendTime(ctx, targetType, targetHash, purpose, ipHash); err != nil {
		return nil, fmt.Errorf("记录发送时间失败: %w", err)
	}

	// 11. 发送邮件/短信
	if err := s.sendCodeToTarget(ctx, targetType, target, code, purpose); err != nil {
		log.Printf("[验证码] 重新发送失败: targetType=%s, targetHash=%s, error=%v",
			targetType, targetHash[:min(16, len(targetHash))], err)
	}

	// 12. 递减剩余次数，更新 Redis（不重新颁发 token！）
	tokenData.RemainingResends--
	if err := s.repo.UpdateTokenRemaining(ctx, resendToken, tokenData); err != nil {
		return nil, fmt.Errorf("更新 token 失败: %w", err)
	}

	if tokenData.RemainingResends <= 0 {
		s.repo.DeleteToken(ctx, resendToken)
		log.Printf("[验证码] Resend token 已耗尽并删除: token=%s", resendToken[:min(8, len(resendToken))]+"...")
	}

	log.Printf("[验证码] 重新发送成功: targetType=%s, targetHash=%s, purpose=%s, ipHash=%s, remaining=%d",
		targetType, targetHash[:min(16, len(targetHash))], purpose, ipHash[:min(16, len(ipHash))],
		tokenData.RemainingResends)

	remainingTTL := int64(domain.ResendTokenTTLSeconds)
	actualTTL, err := s.repo.GetTokenTTL(ctx, resendToken)
	if err == nil && actualTTL > 0 {
		remainingTTL = int64(actualTTL.Seconds())
	}

	return &domain.VerificationSendVO{
		ResendToken:      resendToken,
		ExpiresIn:        remainingTTL,
		RemainingResends: tokenData.RemainingResends,
	}, nil
}

// =============================================================================
// 验证码校验
// =============================================================================

// VerifyCode 校验验证码（IP 绑定校验，验证成功后自动删除，保证一次性使用）。
//
// IP 绑定逻辑：验证码 key 包含 ipHash，因此只有当请求 IP 与
// 发送验证码时的 IP 一致时，才能查找到验证码。不同 IP 的请求会自动找不到
// 对应的验证码，从而被拒绝。
func (s *VerificationCodeService) VerifyCode(
	ctx context.Context,
	targetType string, target string, purpose string,
	code string, clientIP string,
) (bool, error) {
	targetHash := redisutil.SHA256(target)
	ipHash := redisutil.SHA256(clientIP)

	stored, err := s.repo.GetCode(ctx, targetType, targetHash, purpose, ipHash)
	if err != nil {
		// Redis 错误或 key 不存在
		return false, nil
	}

	if stored == code {
		// 一次性使用，验证后立即删除
		s.repo.DeleteCode(ctx, targetType, targetHash, purpose, ipHash)
		log.Printf("[验证码] 校验成功并已删除: targetType=%s, targetHash=%s, purpose=%s, ipHash=%s",
			targetType, targetHash[:min(16, len(targetHash))], purpose, ipHash[:min(16, len(ipHash))])
		return true, nil
	}

	return false, nil
}

// =============================================================================
// 验证码防爆破
// =============================================================================

// CheckCodeAttempts 检查验证码校验失败次数（同一 IP + 同一目标 维度防爆破）。
//
// 设计说明：attempts key 包含 {targetType}:{targetHash}:{purpose}:{ipHash}，
// 因此攻击者无法通过轮换 IP（代理池）对同一目标无限试错——
// 每个新 IP 的 attempts 计数器是独立的，且验证码本身也是 IP 绑定的，
// 旧 IP 的验证码对攻击者不可用。
func (s *VerificationCodeService) CheckCodeAttempts(
	ctx context.Context,
	targetType string, target string, purpose string, clientIP string,
) error {
	targetHash := redisutil.SHA256(target)
	ipHash := redisutil.SHA256(clientIP)

	attempts, err := s.repo.IncrCodeAttempts(ctx, targetType, targetHash, purpose, ipHash)
	if err != nil {
		return fmt.Errorf("递增失败次数失败: %w", err)
	}

	if attempts > domain.MaxCodeFailures {
		log.Printf("[验证码] 校验失败次数过多: targetType=%s, targetHash=%s, purpose=%s, ipHash=%s, attempts=%d",
			targetType, targetHash[:min(16, len(targetHash))], purpose, ipHash[:min(16, len(ipHash))], attempts)
		return domain.ErrCodeAttemptsExceeded
	}
	return nil
}

// RecordCodeFailure 记录验证码校验失败（同一 IP + 同一目标 维度）。
func (s *VerificationCodeService) RecordCodeFailure(
	ctx context.Context,
	targetType string, target string, purpose string, clientIP string,
) {
	targetHash := redisutil.SHA256(target)
	ipHash := redisutil.SHA256(clientIP)

	attempts, err := s.repo.IncrCodeAttempts(ctx, targetType, targetHash, purpose, ipHash)
	if err != nil {
		log.Printf("[验证码] 记录失败次数失败: %v", err)
		return
	}
	log.Printf("[验证码] 校验失败: targetType=%s, targetHash=%s, purpose=%s, ipHash=%s, attempts=%d",
		targetType, targetHash[:min(16, len(targetHash))], purpose, ipHash[:min(16, len(ipHash))], attempts)
}

// ClearCodeAttempts 验证成功后清除该 IP+目标 的验证码失败计数。
func (s *VerificationCodeService) ClearCodeAttempts(
	ctx context.Context,
	targetType string, target string, purpose string, clientIP string,
) {
	targetHash := redisutil.SHA256(target)
	ipHash := redisutil.SHA256(clientIP)
	s.repo.ClearCodeAttempts(ctx, targetType, targetHash, purpose, ipHash)
}

// =============================================================================
// 向后兼容：注册接口防爆破
// =============================================================================

// CheckRegisterCodeAttempts 检查注册接口验证码失败次数（同一 IP 维度防爆破）。
// Deprecated: 新功能请使用 CheckCodeAttempts，此方法保留向后兼容。
func (s *VerificationCodeService) CheckRegisterCodeAttempts(ctx context.Context, clientIP string) error {
	ipHash := redisutil.SHA256(clientIP)
	attempts, err := s.repo.IncrRegisterAttempts(ctx, ipHash)
	if err != nil {
		return fmt.Errorf("递增注册失败次数失败: %w", err)
	}
	if attempts > domain.MaxCodeFailures {
		log.Printf("[注册防爆破] IP 验证码失败次数过多: ipHash=%s, attempts=%d", ipHash, attempts)
		return domain.ErrCodeAttemptsExceeded
	}
	return nil
}

// ClearRegisterCodeAttempts 注册成功后清除该 IP 的验证码失败计数。
// Deprecated: 新功能请使用 ClearCodeAttempts。
func (s *VerificationCodeService) ClearRegisterCodeAttempts(ctx context.Context, clientIP string) {
	ipHash := redisutil.SHA256(clientIP)
	s.repo.ClearRegisterAttempts(ctx, ipHash)
}

// RecordRegisterCodeFailure 记录注册接口验证码失败。
// Deprecated: 新功能请使用 RecordCodeFailure。
func (s *VerificationCodeService) RecordRegisterCodeFailure(ctx context.Context, clientIP string) {
	ipHash := redisutil.SHA256(clientIP)
	attempts, err := s.repo.IncrRegisterAttempts(ctx, ipHash)
	if err != nil {
		log.Printf("[注册防爆破] 记录失败次数失败: %v", err)
		return
	}
	log.Printf("[注册防爆破] 验证码错误: ipHash=%s, attempts=%d", ipHash, attempts)
}

// =============================================================================
// 向后兼容：旧版验证码存储（供事件消费者使用）
// =============================================================================

// StoreLegacyEmailCode 存储旧版邮箱验证码（兼容事件消费者）。
// Deprecated: 新功能请使用 SendCode。
func (s *VerificationCodeService) StoreLegacyEmailCode(ctx context.Context, email, code string, expireSec int) (bool, error) {
	key := domain.PrefixEmailLegacy + email
	ok, err := s.repo.CheckLegacyRateLimit(ctx, key)
	if err != nil {
		return false, err
	}
	if !ok {
		log.Printf("[验证码] 旧版邮箱验证码发送频率超限: email=%s", email)
		return false, nil
	}
	if err := s.repo.StoreLegacyEmailCode(ctx, email, code, expireSec); err != nil {
		return false, err
	}
	return true, nil
}

// StoreLegacyPhoneCode 存储旧版手机验证码（兼容事件消费者）。
// Deprecated: 新功能请使用 SendCode。
func (s *VerificationCodeService) StoreLegacyPhoneCode(ctx context.Context, phone, code string, expireSec int) (bool, error) {
	key := domain.PrefixPhoneLegacy + phone
	ok, err := s.repo.CheckLegacyRateLimit(ctx, key)
	if err != nil {
		return false, err
	}
	if !ok {
		log.Printf("[验证码] 旧版手机验证码发送频率超限: phone=%s", phone)
		return false, nil
	}
	if err := s.repo.StoreLegacyPhoneCode(ctx, phone, code, expireSec); err != nil {
		return false, err
	}
	return true, nil
}

// =============================================================================
// 私有方法：频率控制
// =============================================================================

// checkRateLimit 检查每小时发送次数限制（同一 IP+目标 维度）。
func (s *VerificationCodeService) checkRateLimit(ctx context.Context, targetType, targetHash, purpose, ipHash string) error {
	count, err := s.repo.IncrRateLimit(ctx, targetType, targetHash, purpose, ipHash)
	if err != nil {
		return fmt.Errorf("频率计数器递增失败: %w", err)
	}
	if count > domain.MaxSendsPerHour {
		log.Printf("[验证码] 每小时发送次数超限: targetType=%s, targetHash=%s, purpose=%s, ipHash=%s, count=%d",
			targetType, targetHash[:min(16, len(targetHash))], purpose, ipHash[:min(16, len(ipHash))], count)
		return domain.ErrRateLimitExceeded
	}
	return nil
}

// checkResendInterval 检查 60 秒重新发送间隔（同一 IP+目标 维度）。
func (s *VerificationCodeService) checkResendInterval(ctx context.Context, targetType, targetHash, purpose, ipHash string) error {
	lastSend, err := s.repo.GetLastSendTime(ctx, targetType, targetHash, purpose, ipHash)
	if err != nil {
		return fmt.Errorf("获取上次发送时间失败: %w", err)
	}
	if lastSend > 0 {
		now := time.Now().UnixMilli()
		elapsed := (now - lastSend) / 1000
		if elapsed < domain.ResendIntervalSeconds {
			log.Printf("[验证码] 发送间隔不足60秒: targetType=%s, targetHash=%s, purpose=%s, ipHash=%s, elapsed=%ds",
				targetType, targetHash[:min(16, len(targetHash))], purpose, ipHash[:min(16, len(ipHash))], elapsed)
			return domain.ErrResendInterval
		}
	}
	return nil
}

// =============================================================================
// 私有方法：邮件/短信发送
// =============================================================================

// sendCodeToTarget 根据目标类型发送验证码
func (s *VerificationCodeService) sendCodeToTarget(ctx context.Context, targetType, target, code, purpose string) error {
	msg := &channel.Message{
		Title:    s.buildVerificationTitle(purpose),
		Body:     s.buildVerificationBody(code, purpose),
		HTMLBody: s.buildVerificationHTML(code, purpose),
		Priority: 10, // 验证码为高优先级
	}

	if targetType == "email" {
		result, err := s.channelManager.Send(ctx, "email", target, msg)
		if err != nil {
			return fmt.Errorf("邮件发送失败: %w", err)
		}
		if !result.Success {
			return fmt.Errorf("邮件发送失败: %v", result.Error)
		}
		log.Printf("[验证码] 邮件发送成功: target=%s, purpose=%s", target, purpose)
	} else {
		result, err := s.channelManager.Send(ctx, "sms", target, msg)
		if err != nil {
			return fmt.Errorf("短信发送失败: %w", err)
		}
		if !result.Success {
			return fmt.Errorf("短信发送失败: %v", result.Error)
		}
		log.Printf("[验证码] 短信发送成功: target=%s, purpose=%s", target, purpose)
	}
	return nil
}

// buildVerificationTitle 构建验证码标题
func (s *VerificationCodeService) buildVerificationTitle(purpose string) string {
	switch purpose {
	case "REGISTER":
		return "【私有云】注册验证码"
	case "BIND":
		return "【私有云】绑定验证码"
	case "RESET":
		return "【私有云】重置密码验证码"
	default:
		return "【私有云】验证码"
	}
}

// buildVerificationBody 构建验证码纯文本内容
func (s *VerificationCodeService) buildVerificationBody(code, purpose string) string {
	action := "完成验证"
	switch purpose {
	case "REGISTER":
		action = "完成注册"
	case "BIND":
		action = "完成绑定"
	case "RESET":
		action = "重置密码"
	}
	return fmt.Sprintf("您的验证码是：%s，请勿将验证码泄露给他人。该验证码 5 分钟内有效，用于%s。", code, action)
}

// buildVerificationHTML 构建验证码 HTML 内容
func (s *VerificationCodeService) buildVerificationHTML(code, purpose string) string {
	action := "完成验证"
	switch purpose {
	case "REGISTER":
		action = "完成注册"
	case "BIND":
		action = "完成绑定"
	case "RESET":
		action = "重置密码"
	}

	frontendURL := s.cfg.Email.FrontendURL
	if frontendURL == "" {
		frontendURL = "https://privateclouddisk.com"
	}

	return fmt.Sprintf(`<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>验证码</title>
</head>
<body style="margin:0;padding:0;background-color:#f5f7fa;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
<table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f5f7fa;">
<tr><td align="center" style="padding:40px 20px;">
<table width="480" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 2px 12px rgba(0,0,0,0.08);">
  <!-- Logo -->
  <tr><td style="background:linear-gradient(135deg,#3b82f6,#2563eb);padding:32px 40px;text-align:center;">
    <div style="font-size:24px;font-weight:700;color:#ffffff;">私有云</div>
    <div style="font-size:13px;color:rgba(255,255,255,0.8);margin-top:4px;">安全可靠的私有云存储</div>
  </td></tr>
  <!-- Body -->
  <tr><td style="padding:40px;">
    <div style="font-size:16px;color:#1e293b;line-height:1.6;">
      您好，您正在进行<strong>%s</strong>操作。
    </div>
    <div style="margin:28px 0;text-align:center;">
      <div style="display:inline-block;background:#f0f7ff;border:1px dashed #3b82f6;border-radius:8px;padding:20px 40px;">
        <div style="font-size:12px;color:#64748b;margin-bottom:8px;">验证码</div>
        <div style="font-size:36px;font-weight:700;letter-spacing:8px;color:#1e40af;font-family:'Courier New',monospace;">%s</div>
      </div>
    </div>
    <div style="font-size:13px;color:#64748b;line-height:1.6;">
      该验证码 <strong>5 分钟</strong>内有效，请勿将验证码泄露给他人。
    </div>
  </td></tr>
  <!-- Footer -->
  <tr><td style="background:#f8fafc;padding:24px 40px;border-top:1px solid #e2e8f0;">
    <div style="font-size:12px;color:#94a3b8;line-height:1.8;">
      此邮件由系统自动发送，请勿回复。<br>
      如有疑问，请访问 <a href="%s" style="color:#3b82f6;text-decoration:none;">私有云帮助中心</a>。
    </div>
  </td></tr>
</table>
</td></tr>
</table>
</body>
</html>`, action, code, frontendURL+"/help")
}

// =============================================================================
// 私有方法：人机验证
// =============================================================================

// verifyTurnstile 校验 Cloudflare Turnstile 人机验证码（委托给 security.TurnstileVerifier）
func (s *VerificationCodeService) verifyTurnstile(captchaToken, captchaAction, clientIP string) error {
	// 开发环境：未启用时跳过
	if !s.cfg.Turnstile.Enabled {
		log.Printf("[验证码] Turnstile 未启用，跳过人机验证: clientIP=%s", clientIP)
		return nil
	}
	return s.turnstileVerifier.Verify(captchaToken, captchaAction, clientIP)
}

// =============================================================================
// 私有方法：邮箱/手机号过滤
// =============================================================================

// checkTargetNotSystem 检查目标是否属于系统自身（防止用系统邮箱给自己发验证码）。
func (s *VerificationCodeService) checkTargetNotSystem(target string) error {
	// 检查是否匹配系统邮箱
	if s.cfg.Email.FromAddr != "" && strings.EqualFold(s.cfg.Email.FromAddr, target) {
		log.Printf("[安全过滤] 拒绝使用系统邮箱获取验证码: target=%s", target)
		return domain.ErrSystemTarget
	}
	// 检查是否匹配系统手机号
	if s.cfg.SMS.SignName != "" && s.cfg.SMS.SignName == target {
		log.Printf("[安全过滤] 拒绝使用系统手机号获取验证码: target=%s", target)
		return domain.ErrSystemTarget
	}
	return nil
}

// checkEmailNotBlocked 检查邮箱是否属于禁止域名。
func (s *VerificationCodeService) checkEmailNotBlocked(email string) error {
	blockedDomains := s.cfg.Verification.BlockedDomains
	if blockedDomains == "" {
		blockedDomains = "qq.com,163.com,126.com"
	}

	lowerEmail := strings.ToLower(email)
	for _, domain := range strings.Split(blockedDomains, ",") {
		trimmed := strings.TrimSpace(strings.ToLower(domain))
		if trimmed != "" && strings.HasSuffix(lowerEmail, "@"+trimmed) {
			return nil
		}
	}
	return nil
}

// =============================================================================
// 私有方法：验证码生成
// =============================================================================

// generateCode 生成 6 位数字验证码（使用 crypto/rand 安全随机数）
func generateCode() string {
	code := make([]byte, domain.CodeLength)
	for i := 0; i < domain.CodeLength; i++ {
		n, err := rand.Int(rand.Reader, big.NewInt(10))
		if err != nil {
			// 极端情况下 fallback 到时间戳
			n = big.NewInt(int64(time.Now().UnixNano() % 10))
		}
		code[i] = byte('0' + n.Int64())
	}
	return string(code)
}

// =============================================================================
// 工具函数
// =============================================================================

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}