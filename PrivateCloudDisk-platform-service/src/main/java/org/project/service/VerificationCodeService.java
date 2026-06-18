package org.project.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.project.model.vo.VerificationSendVO;
import org.project.security.TurnstileCaptchaVerifier;
import org.project.service.ex.CaptchaVerificationException;
import org.project.service.ex.RateLimitExceededException;
import org.project.service.ex.ResendTokenExhaustedException;
import org.project.service.ex.ResendTokenInvalidException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 企业级验证码服务 —— 生成、存储、校验、发送、频率控制、token 管理。
 *
 * <p><b>分层职责</b>：本服务负责<b>全部</b>验证码业务逻辑，包括：
 * <ul>
 *   <li>人机验证码校验</li>
 *   <li>邮箱/手机号过滤（系统邮箱、禁止域名）</li>
 *   <li>验证码生成与 Redis 存储</li>
 *   <li>邮件/短信发送</li>
 *   <li>不透明 resend token 管理（Redis 存储，非 JWT）</li>
 *   <li>频率控制（每小时发送次数、60 秒间隔）</li>
 *   <li>注册接口防爆破</li>
 * </ul>
 *
 * <p><b>Token 设计</b>：使用 Redis 不透明 token（UUID），而非 JWT。
 * 因为 token 的生命周期（剩余次数、IP 绑定校验）完全依赖 Redis，
 * 使用 JWT 只会增加无意义的加解密开销，且无法真正"无状态"。
 *
 * <p>Redis Key 结构：
 * <pre>
 *   verif:code:{targetType}:{targetHash}:{purpose}         → 验证码（TTL: 5 分钟）
 *   verif:rate:{targetType}:{targetHash}:{purpose}          → 发送次数计数器（TTL: 1 小时）
 *   verif:last:{targetType}:{targetHash}:{purpose}          → 上次发送时间戳（TTL: 60 秒）
 *   verif:token:{tokenUUID}                                  → JSON 令牌状态（TTL: 10 分钟）
 *   verif:register:attempts:{ipHash}                         → 注册验证码失败次数（TTL: 15 分钟）
 * </pre>
 *
 * <p>时间参数说明：
 * <ul>
 *   <li>验证码有效期：5 分钟（300 秒）</li>
 *   <li>重新发送最小间隔：60 秒</li>
 *   <li>同一目标每小时最大发送次数：5 次</li>
 *   <li>重新发送 token 最大次数：8 次（10 分钟内）</li>
 * </ul>
 */
@Slf4j
@Service
public class VerificationCodeService {

    private final StringRedisTemplate redisTemplate;
    private final TurnstileCaptchaVerifier captchaVerifier;
    private final EmailService emailService;
    private final SmsService smsService;
    private final ObjectMapper objectMapper;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ==================== 配置项 ====================

    private final String systemEmail;
    private final String systemPhone;
    private final String blockedDomainsRaw;

    // ==================== 常量 ====================

    /** 验证码长度 */
    private static final int CODE_LENGTH = 6;

    /** 验证码有效期（秒） */
    public static final int CODE_EXPIRE_SECONDS = 300; // 5 分钟

    /** 重新发送最小间隔（秒） */
    private static final int RESEND_INTERVAL_SECONDS = 60;

    /** 同一目标每小时最大发送次数 */
    private static final int MAX_SENDS_PER_HOUR = 5;

    /** 重新发送最大次数 */
    public static final int MAX_RESENDS = 8;

    /** 重新发送 token 有效期（秒） */
    public static final int RESEND_TOKEN_TTL_SECONDS = 600; // 10 分钟

    /** 注册接口验证码失败最大次数（同一 IP） */
    private static final int MAX_REGISTER_CODE_FAILURES = 5;

    /** 注册接口验证码失败窗口（秒） */
    private static final int REGISTER_CODE_FAILURE_WINDOW_SECONDS = 900; // 15 分钟

    // ==================== Redis Key 前缀 ====================

    private static final String PREFIX_CODE = "verif:code:";
    private static final String PREFIX_RATE = "verif:rate:";
    private static final String PREFIX_LAST = "verif:last:";
    private static final String PREFIX_TOKEN = "verif:token:";
    private static final String PREFIX_REGISTER_ATTEMPTS = "verif:register:attempts:";

    // ==================== 构造函数 ====================

    @Autowired
    public VerificationCodeService(StringRedisTemplate redisTemplate,
                                    TurnstileCaptchaVerifier captchaVerifier,
                                    EmailService emailService,
                                    SmsService smsService,
                                    ObjectMapper objectMapper,
                                    @Value("${app.mail.from}") String systemEmail,
                                    @Value("${app.sms.from-phone:}") String systemPhone,
                                    @Value("${app.verification.blocked-domains:qq.com,163.com,126.com}") String blockedDomainsRaw) {
        this.redisTemplate = redisTemplate;
        this.captchaVerifier = captchaVerifier;
        this.emailService = emailService;
        this.smsService = smsService;
        this.objectMapper = objectMapper;
        this.systemEmail = systemEmail;
        this.systemPhone = systemPhone;
        this.blockedDomainsRaw = blockedDomainsRaw;
    }

    // ==================== 首次发送验证码 ====================

    /**
     * 首次发送验证码（需人机验证）。
     *
     * <p>流程：
     * <ol>
     *   <li>校验 Turnstile 人机验证码</li>
     *   <li>过滤系统邮箱/手机号</li>
     *   <li>检查频率限制（每小时 5 次 + 60 秒间隔）</li>
     *   <li>生成验证码并存入 Redis</li>
     *   <li>发送邮件/短信</li>
     *   <li>创建不透明 resend token 存入 Redis</li>
     * </ol>
     *
     * @param targetType     "email" 或 "phone"
     * @param target         邮箱地址或手机号
     * @param purpose        用途（REGISTER/BIND/RESET）
     * @param captchaToken   Turnstile 人机验证 token
     * @param captchaAction  Turnstile action（可选）
     * @param clientIp       客户端 IP
     * @return VerificationSendVO（含 resendToken、有效期、剩余次数）
     * @throws CaptchaVerificationException 人机验证失败
     * @throws IllegalArgumentException     系统邮箱/禁止域名
     * @throws RateLimitExceededException   频率超限
     */
    public VerificationSendVO sendCode(String targetType, String target, String purpose,
                                              String captchaToken, String captchaAction, String clientIp) {
        // 1. 人机验证码校验
        String action = (captchaAction != null && !captchaAction.isBlank()) ? captchaAction : "verification_code";
        captchaVerifier.verify(captchaToken, action, clientIp);

        // 2. 过滤系统自身邮箱/手机号
        checkTargetNotSystem(target);

        String targetHash = sha256(target);
        String ipHash = sha256(clientIp);

        // 3. 频率检查（每小时次数 + 60 秒间隔）
        checkRateLimit(targetType, targetHash, purpose);
        checkResendInterval(targetType, targetHash, purpose);

        // 4. 生成验证码并存入 Redis
        String code = generateCode();
        String codeKey = buildCodeKey(targetType, targetHash, purpose);
        redisTemplate.opsForValue().set(codeKey, code, CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        // 5. 记录发送时间戳
        String lastKey = buildLastKey(targetType, targetHash, purpose);
        redisTemplate.opsForValue().set(lastKey, String.valueOf(System.currentTimeMillis()),
                RESEND_INTERVAL_SECONDS, TimeUnit.SECONDS);

        // 6. 发送邮件/短信
        sendCodeToTarget(targetType, target, code);

        // 7. 创建不透明 resend token 存入 Redis
        String token = UUID.randomUUID().toString();
        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("targetHash", targetHash);
        tokenData.put("purpose", purpose);
        tokenData.put("ipHash", ipHash);
        tokenData.put("remainingResends", MAX_RESENDS);
        tokenData.put("createdAt", System.currentTimeMillis());

        String tokenKey = buildTokenKey(token);
        try {
            redisTemplate.opsForValue().set(tokenKey, objectMapper.writeValueAsString(tokenData),
                    RESEND_TOKEN_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Token 序列化失败", e);
        }

        log.info("[验证码] 首次发送成功: targetType={}, targetHash={}, purpose={}, token={}, clientIp={}",
                 targetType, targetHash, purpose, token.substring(0, 8) + "...", clientIp);

        return new VerificationSendVO(token, RESEND_TOKEN_TTL_SECONDS, MAX_RESENDS);
    }

    // ==================== 重新发送验证码 ====================

    /**
     * 重新发送验证码（无需人机验证，需有效的 resend token）。
     *
     * <p><b>关键设计</b>：不重新颁发 token，只更新 Redis 中的剩余次数。
     * 重新颁发 token 会重置次数计数器，导致 8 次限制形同虚设。
     *
     * <p>流程：
     * <ol>
     *   <li>从 Redis 查找并验证 token</li>
     *   <li>校验 targetHash、ipHash、purpose 一致性</li>
     *   <li>检查剩余次数 &gt; 0</li>
     *   <li>检查 60 秒间隔</li>
     *   <li>使旧验证码失效</li>
     *   <li>生成新验证码、存储、发送</li>
     *   <li>递减剩余次数</li>
     * </ol>
     *
     * @param targetType   "email" 或 "phone"
     * @param target       邮箱地址或手机号
     * @param purpose      用途
     * @param resendToken  不透明 resend token
     * @param clientIp     客户端 IP
     * @return VerificationSendVO（含同一个 token、更新后的剩余次数）
     * @throws ResendTokenInvalidException   token 无效或已过期
     * @throws ResendTokenExhaustedException 剩余次数为 0
     * @throws RateLimitExceededException    60 秒间隔未到
     */
    public VerificationSendVO resendCode(String targetType, String target, String purpose,
                                                String resendToken, String clientIp) {
        // 1. 从 Redis 查找 token
        String tokenKey = buildTokenKey(resendToken);
        String tokenJson = redisTemplate.opsForValue().get(tokenKey);
        if (tokenJson == null) {
            throw new ResendTokenInvalidException();
        }

        // 2. 解析 token 数据
        Map<String, Object> tokenData;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(tokenJson, Map.class);
            tokenData = data;
        } catch (JsonProcessingException e) {
            throw new ResendTokenInvalidException();
        }

        String targetHash = sha256(target);
        String ipHash = sha256(clientIp);

        // 3. 校验 targetHash 一致性（防止横向越权：拿别人的 token 给自己的邮箱发）
        String storedTargetHash = (String) tokenData.get("targetHash");
        if (!targetHash.equals(storedTargetHash)) {
            log.warn("[验证码] Resend token 目标不匹配: expected={}, actual={}",
                     targetHash.substring(0, 16), storedTargetHash.substring(0, 16));
            throw new ResendTokenInvalidException();
        }

        // 4. 校验 ipHash 一致性（防止横向越权：拿别人的 token 从不同 IP 使用）
        String storedIpHash = (String) tokenData.get("ipHash");
        if (!ipHash.equals(storedIpHash)) {
            log.warn("[验证码] Resend token IP 不匹配: expected={}, actual={}",
                     ipHash.substring(0, 16), storedIpHash.substring(0, 16));
            throw new ResendTokenInvalidException();
        }

        // 5. 校验 purpose 一致性
        String storedPurpose = (String) tokenData.get("purpose");
        if (!purpose.equals(storedPurpose)) {
            log.warn("[验证码] Resend token 用途不匹配: expected={}, actual={}", purpose, storedPurpose);
            throw new ResendTokenInvalidException();
        }

        // 6. 检查剩余次数
        int remainingResends = (Integer) tokenData.get("remainingResends");
        if (remainingResends <= 0) {
            redisTemplate.delete(tokenKey); // 清理已耗尽的 token
            throw new ResendTokenExhaustedException();
        }

        // 7. 检查 60 秒间隔
        checkRateLimit(targetType, targetHash, purpose);
        checkResendInterval(targetType, targetHash, purpose);

        // 8. 使旧验证码失效
        String codeKey = buildCodeKey(targetType, targetHash, purpose);
        redisTemplate.delete(codeKey);
        log.info("[验证码] 旧验证码已失效: targetType={}, targetHash={}, purpose={}",
                 targetType, targetHash, purpose);

        // 9. 生成新验证码并存储
        String code = generateCode();
        redisTemplate.opsForValue().set(codeKey, code, CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        // 10. 记录发送时间戳
        String lastKey = buildLastKey(targetType, targetHash, purpose);
        redisTemplate.opsForValue().set(lastKey, String.valueOf(System.currentTimeMillis()),
                RESEND_INTERVAL_SECONDS, TimeUnit.SECONDS);

        // 11. 发送邮件/短信
        sendCodeToTarget(targetType, target, code);

        // 12. 递减剩余次数，更新 Redis（不重新颁发 token！）
        int newRemaining = remainingResends - 1;
        tokenData.put("remainingResends", newRemaining);
        try {
            // 获取 token 剩余 TTL
            Long ttl = redisTemplate.getExpire(tokenKey, TimeUnit.SECONDS);
            if (ttl == null || ttl <= 0) {
                ttl = (long) RESEND_TOKEN_TTL_SECONDS;
            }
            redisTemplate.opsForValue().set(tokenKey, objectMapper.writeValueAsString(tokenData),
                    ttl, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Token 序列化失败", e);
        }

        // 如果剩余次数为 0，删除 token
        if (newRemaining <= 0) {
            redisTemplate.delete(tokenKey);
            log.info("[验证码] Resend token 已耗尽并删除: token={}", resendToken.substring(0, 8) + "...");
        }

        log.info("[验证码] 重新发送成功: targetType={}, targetHash={}, purpose={}, remaining={}, clientIp={}",
                 targetType, targetHash, purpose, newRemaining, clientIp);

        // 返回同一个 token + 更新后的剩余次数
        long remainingTtl = RESEND_TOKEN_TTL_SECONDS;
        Long actualTtl = redisTemplate.getExpire(tokenKey, TimeUnit.SECONDS);
        if (actualTtl != null && actualTtl > 0) {
            remainingTtl = actualTtl;
        }
        return new VerificationSendVO(resendToken, remainingTtl, newRemaining);
    }

    // ==================== 验证码校验 ====================

    /**
     * 校验验证码（验证成功后自动删除，保证一次性使用）。
     *
     * @param targetType "email" 或 "phone"
     * @param target     邮箱地址或手机号
     * @param purpose    用途
     * @param code       用户输入的验证码
     * @return true=验证通过，false=验证码错误或已过期
     */
    public boolean verifyCode(String targetType, String target, String purpose, String code) {
        String targetHash = sha256(target);
        String codeKey = buildCodeKey(targetType, targetHash, purpose);
        String stored = redisTemplate.opsForValue().get(codeKey);
        if (stored != null && stored.equals(code)) {
            redisTemplate.delete(codeKey); // 一次性使用，验证后立即删除
            log.info("[验证码] 校验成功并已删除: targetType={}, targetHash={}, purpose={}",
                     targetType, targetHash, purpose);
            return true;
        }
        return false;
    }

    // ==================== 注册接口防爆破 ====================

    /**
     * 检查注册接口验证码失败次数（同一 IP 维度防爆破）。
     *
     * @param clientIp 客户端 IP
     * @throws RateLimitExceededException 超过最大失败次数
     */
    public void checkRegisterCodeAttempts(String clientIp) {
        String ipHash = sha256(clientIp);
        String key = PREFIX_REGISTER_ATTEMPTS + ipHash;
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(key, REGISTER_CODE_FAILURE_WINDOW_SECONDS, TimeUnit.SECONDS);
        }
        if (attempts != null && attempts > MAX_REGISTER_CODE_FAILURES) {
            log.warn("[注册防爆破] IP 验证码失败次数过多: ipHash={}, attempts={}", ipHash, attempts);
            throw new RateLimitExceededException("验证码错误次数过多，请 15 分钟后重试");
        }
    }

    /**
     * 注册成功后清除该 IP 的验证码失败计数。
     */
    public void clearRegisterCodeAttempts(String clientIp) {
        String ipHash = sha256(clientIp);
        redisTemplate.delete(PREFIX_REGISTER_ATTEMPTS + ipHash);
    }

    /**
     * 记录注册接口验证码失败。
     */
    public void recordRegisterCodeFailure(String clientIp) {
        String ipHash = sha256(clientIp);
        String key = PREFIX_REGISTER_ATTEMPTS + ipHash;
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(key, REGISTER_CODE_FAILURE_WINDOW_SECONDS, TimeUnit.SECONDS);
        }
        log.warn("[注册防爆破] 验证码错误: ipHash={}, attempts={}", ipHash, attempts);
    }

    // ==================== 私有方法：频率控制 ====================

    /**
     * 检查每小时发送次数限制（首次发送和重新发送共用）。
     */
    private void checkRateLimit(String targetType, String targetHash, String purpose) {
        String rateKey = buildRateKey(targetType, targetHash, purpose);
        Long sendCount = redisTemplate.opsForValue().increment(rateKey);
        if (sendCount != null && sendCount == 1) {
            redisTemplate.expire(rateKey, 1, TimeUnit.HOURS);
        }
        if (sendCount != null && sendCount > MAX_SENDS_PER_HOUR) {
            log.warn("[验证码] 每小时发送次数超限: targetType={}, targetHash={}, purpose={}, count={}",
                     targetType, targetHash, purpose, sendCount);
            throw new RateLimitExceededException("发送频率超限，请稍后再试");
        }
    }

    /**
     * 检查 60 秒重新发送间隔。
     */
    private void checkResendInterval(String targetType, String targetHash, String purpose) {
        String lastKey = buildLastKey(targetType, targetHash, purpose);
        String lastSendStr = redisTemplate.opsForValue().get(lastKey);
        if (lastSendStr != null) {
            long lastSend = Long.parseLong(lastSendStr);
            long now = System.currentTimeMillis();
            long elapsed = (now - lastSend) / 1000;
            if (elapsed < RESEND_INTERVAL_SECONDS) {
                log.warn("[验证码] 发送间隔不足60秒: targetType={}, targetHash={}, purpose={}, elapsed={}s",
                         targetType, targetHash, purpose, elapsed);
                throw new RateLimitExceededException("发送间隔不足60秒，请稍后再试");
            }
        }
    }

    // ==================== 私有方法：邮件/短信发送 ====================

    /**
     * 根据目标类型发送验证码。
     */
    private void sendCodeToTarget(String targetType, String target, String code) {
        if ("email".equals(targetType)) {
            emailService.sendVerificationEmail(target, code, CODE_EXPIRE_SECONDS, "REGISTER");
        } else {
            smsService.sendVerificationSms(target, code, CODE_EXPIRE_SECONDS);
        }
    }

    // ==================== 私有方法：邮箱/手机号过滤 ====================

    /**
     * 检查目标是否属于系统自身（防止用系统邮箱给自己发验证码）。
     *
     * @param target          邮箱或手机号
     * @param systemEmail     系统发件邮箱
     * @param systemPhone     系统发件手机号（可选）
     * @param blockedDomains  被禁止的邮箱域名列表
     * @throws IllegalArgumentException 如果是系统自身邮箱/手机号
     */
    private void checkTargetNotSystem(String target) {
        // 检查是否匹配系统邮箱
        if (systemEmail != null && !systemEmail.isBlank() && systemEmail.equalsIgnoreCase(target)) {
            log.warn("[安全过滤] 拒绝使用系统邮箱获取验证码: target={}", target);
            throw new IllegalArgumentException("不能使用系统邮箱获取验证码");
        }

        // 检查是否匹配系统手机号
        if (systemPhone != null && !systemPhone.isBlank() && systemPhone.equals(target)) {
            log.warn("[安全过滤] 拒绝使用系统手机号获取验证码: target={}", target);
            throw new IllegalArgumentException("不能使用系统手机号获取验证码");
        }

        // 检查邮箱域名是否在禁止列表中
        if (target.contains("@") && blockedDomainsRaw != null && !blockedDomainsRaw.isBlank()) {
            String domain = target.substring(target.indexOf("@") + 1).toLowerCase();
            String[] blockedDomains = blockedDomainsRaw.split(",");
            for (String blocked : blockedDomains) {
                if (domain.equals(blocked.trim().toLowerCase())) {
                    log.warn("[安全过滤] 拒绝使用禁止域名邮箱获取验证码: target={}, domain={}", target, domain);
                    throw new IllegalArgumentException("该邮箱域名不允许获取验证码");
                }
            }
        }
    }

    // ==================== 辅助方法 ====================

    public String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(SECURE_RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private String buildCodeKey(String targetType, String targetHash, String purpose) {
        return PREFIX_CODE + targetType + ":" + targetHash + ":" + purpose;
    }

    private String buildRateKey(String targetType, String targetHash, String purpose) {
        return PREFIX_RATE + targetType + ":" + targetHash + ":" + purpose;
    }

    private String buildLastKey(String targetType, String targetHash, String purpose) {
        return PREFIX_LAST + targetType + ":" + targetHash + ":" + purpose;
    }

    private String buildTokenKey(String token) {
        return PREFIX_TOKEN + token;
    }

    private static String sha256(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // ==================== 向后兼容方法（旧流程使用） ====================

    /** 旧版 Redis key 前缀（兼容事件消费者 EmailVerificationConsumer / PhoneVerificationConsumer） */
    private static final String PREFIX_EMAIL_LEGACY = "email_verification_code:";
    private static final String PREFIX_PHONE_LEGACY = "phone_verification_code:";
    private static final String SUFFIX_RATE_LEGACY = ":rate";

    /**
     * 存储邮箱验证码（旧版兼容，事件消费者使用）。
     * @deprecated 新功能请使用 {@link #sendCode}，此方法保留仅用于旧事件消费者。
     */
    @Deprecated
    public boolean storeEmailCode(String email, String code, int expireSeconds) {
        String key = PREFIX_EMAIL_LEGACY + email;
        if (!checkLegacyRateLimit(key)) {
            log.warn("[验证码] 旧版邮箱验证码发送频率超限. email={}", email);
            return false;
        }
        redisTemplate.opsForValue().set(key, code, expireSeconds, TimeUnit.SECONDS);
        return true;
    }

    /**
     * 存储手机验证码（旧版兼容，事件消费者使用）。
     * @deprecated 新功能请使用 {@link #sendCode}，此方法保留仅用于旧事件消费者。
     */
    @Deprecated
    public boolean storePhoneCode(String phone, String code, int expireSeconds) {
        String key = PREFIX_PHONE_LEGACY + phone;
        if (!checkLegacyRateLimit(key)) {
            log.warn("[验证码] 旧版手机验证码发送频率超限. phone={}", phone);
            return false;
        }
        redisTemplate.opsForValue().set(key, code, expireSeconds, TimeUnit.SECONDS);
        return true;
    }

    private boolean checkLegacyRateLimit(String verifyKey) {
        String rateKey = verifyKey + SUFFIX_RATE_LEGACY;
        Long count = redisTemplate.opsForValue().increment(rateKey);
        if (count == null || count == 1) {
            redisTemplate.expire(rateKey, 1, TimeUnit.HOURS);
        }
        if (count != null && count > MAX_SENDS_PER_HOUR) {
            return false;
        }
        return true;
    }
}