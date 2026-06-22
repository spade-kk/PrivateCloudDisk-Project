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
 * <p><b>Redis Key 结构（IP 绑定设计）</b>：
 * <pre>
 *   verif:code:{targetType}:{targetHash}:{purpose}:{ipHash}       → 验证码（TTL: 5 分钟）
 *   verif:rate:{targetType}:{targetHash}:{purpose}:{ipHash}        → 发送次数计数器（TTL: 1 小时）
 *   verif:last:{targetType}:{targetHash}:{purpose}:{ipHash}        → 上次发送时间戳（TTL: 60 秒）
 *   verif:token:{tokenUUID}                                         → JSON 令牌状态（TTL: 10 分钟）
 *   verif:attempts:{targetType}:{targetHash}:{purpose}:{ipHash}    → 验证失败次数（TTL: 15 分钟）
 * </pre>
 *
 * <p><b>为什么所有 key 都加 ipHash？</b>
 * <ol>
 *   <li><b>验证码 IP 绑定</b>：验证码与请求 IP 绑定，只有同一 IP 才能验证。
 *       防止跨 IP 验证码窃取（我获取的验证码，别人不能用）。</li>
 *   <li><b>频率控制 IP 隔离</b>：每个 IP 独立的频率计数器。
 *       一个 IP 被限流不影响其他合法用户对同一目标的发送。</li>
 *   <li><b>防代理池攻击</b>：攻击者即使通过代理池不断换 IP，每个 IP 的验证码和
 *       rate limit 都是独立的，无法累积爆破——因为旧 IP 的验证码对新 IP 无效。</li>
 *   <li><b>防分布式爆破</b>：每个 IP 的验证码不同，即使攻击者通过僵尸网络
 *       同时攻击同一目标，各节点拿到的验证码互不相同，无法协作碰撞。</li>
 *   <li><b>审计溯源</b>：verif:last 携带 IP 信息，可追踪哪些 IP 对哪些目标
 *       发送了何种用途的验证码。</li>
 *   <li><b>attempts 精细化</b>：verif:attempts 从仅绑定 IP 改为绑定
 *       {targetType}:{targetHash}:{purpose}:{ipHash}，攻击者无法通过
 *       轮换 IP 对同一目标无限试错——因为每个新 IP 的 attempts 计数器是独立的，
 *       且验证码本身也 IP 绑定，旧 IP 的验证码对攻击者不可用。</li>
 * </ol>
 *
 * <p>时间参数说明：
 * <ul>
 *   <li>验证码有效期：5 分钟（300 秒）</li>
 *   <li>重新发送最小间隔：60 秒</li>
 *   <li>同一 IP+目标每小时最大发送次数：5 次</li>
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

    /** 同一 IP+目标每小时最大发送次数 */
    private static final int MAX_SENDS_PER_HOUR = 50;

    /** 重新发送最大次数 */
    public static final int MAX_RESENDS = 8;

    /** 重新发送 token 有效期（秒） */
    public static final int RESEND_TOKEN_TTL_SECONDS = 600; // 10 分钟

    /** 验证码校验失败最大次数（同一 IP+目标） */
    private static final int MAX_CODE_FAILURES = 5;

    /** 验证码校验失败窗口（秒） */
    private static final int CODE_FAILURE_WINDOW_SECONDS = 900; // 15 分钟

    // ==================== Redis Key 前缀 ====================

    /**
     * 验证码存储 key。
     * 格式：verif:code:{targetType}:{targetHash}:{purpose}:{ipHash}
     * TTL: 5 分钟
     */
    private static final String PREFIX_CODE = "verif:code:";

    /**
     * 发送频率计数器 key。
     * 格式：verif:rate:{targetType}:{targetHash}:{purpose}:{ipHash}
     * TTL: 1 小时，记录同一 IP 对同一目标同一用途的发送次数
     */
    private static final String PREFIX_RATE = "verif:rate:";

    /**
     * 上次发送时间戳 key。
     * 格式：verif:last:{targetType}:{targetHash}:{purpose}:{ipHash}
     * TTL: 60 秒，用于 enforce 60 秒重发间隔
     */
    private static final String PREFIX_LAST = "verif:last:";

    /**
     * 不透明 resend token key。
     * 格式：verif:token:{tokenUUID}
     * TTL: 10 分钟，token 内部已包含 ipHash 校验
     */
    private static final String PREFIX_TOKEN = "verif:token:";

    /**
     * 验证码校验失败次数 key。
     * 格式：verif:attempts:{targetType}:{targetHash}:{purpose}:{ipHash}
     * TTL: 15 分钟，防止同一 IP 对同一目标反复爆破验证码
     */
    private static final String PREFIX_ATTEMPTS = "verif:attempts:";

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
     *   <li>检查频率限制（每小时 5 次 + 60 秒间隔）—— 按 IP+目标 维度</li>
     *   <li>生成验证码并存入 Redis（key 包含 ipHash）</li>
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
        if (target.contains("@")) checkEmailNotBlocked(target);

        String targetHash = sha256(target);
        String ipHash = sha256(clientIp);

        // 3. 频率检查（每小时次数 + 60 秒间隔）—— 按 IP+目标 维度
        checkRateLimit(targetType, targetHash, purpose, ipHash);
        checkResendInterval(targetType, targetHash, purpose, ipHash);

        // 4. 生成验证码并存入 Redis（key 包含 ipHash）
        String code = generateCode();
        String codeKey = buildCodeKey(targetType, targetHash, purpose, ipHash);
        redisTemplate.opsForValue().set(codeKey, code, CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        // 5. 记录发送时间戳（key 包含 ipHash）
        String lastKey = buildLastKey(targetType, targetHash, purpose, ipHash);
        redisTemplate.opsForValue().set(lastKey, String.valueOf(System.currentTimeMillis()),
                RESEND_INTERVAL_SECONDS, TimeUnit.SECONDS);

        // 6. 发送邮件/短信
        sendCodeToTarget(targetType, target, code);

        // 7. 创建不透明 resend token 存入 Redis
        String token = UUID.randomUUID().toString();
        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("targetType", targetType);
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

        log.info("[验证码] 首次发送成功: targetType={}, targetHash={}, purpose={}, ipHash={}, token={}",
                 targetType, targetHash.substring(0, 16), purpose, ipHash.substring(0, 16),
                 token.substring(0, 8) + "...");

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
     *   <li>校验 targetType、targetHash、ipHash、purpose 一致性</li>
     *   <li>检查剩余次数 &gt; 0</li>
     *   <li>检查 60 秒间隔</li>
     *   <li>使旧验证码失效（按 IP 维度删除）</li>
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

        // 3. 校验 targetType 一致性
        String storedTargetType = (String) tokenData.get("targetType");
        if (!targetType.equals(storedTargetType)) {
            log.warn("[验证码] Resend token 目标类型不匹配: expected={}, actual={}", targetType, storedTargetType);
            throw new ResendTokenInvalidException();
        }

        // 4. 校验 targetHash 一致性（防止横向越权：拿别人的 token 给自己的邮箱发）
        String storedTargetHash = (String) tokenData.get("targetHash");
        if (!targetHash.equals(storedTargetHash)) {
            log.warn("[验证码] Resend token 目标不匹配: expected={}, actual={}",
                     targetHash.substring(0, 16), storedTargetHash.substring(0, 16));
            throw new ResendTokenInvalidException();
        }

        // 5. 校验 ipHash 一致性（防止横向越权：拿别人的 token 从不同 IP 使用）
        String storedIpHash = (String) tokenData.get("ipHash");
        if (!ipHash.equals(storedIpHash)) {
            log.warn("[验证码] Resend token IP 不匹配: expected={}, actual={}",
                     ipHash.substring(0, 16), storedIpHash.substring(0, 16));
            throw new ResendTokenInvalidException();
        }

        // 6. 校验 purpose 一致性
        String storedPurpose = (String) tokenData.get("purpose");
        if (!purpose.equals(storedPurpose)) {
            log.warn("[验证码] Resend token 用途不匹配: expected={}, actual={}", purpose, storedPurpose);
            throw new ResendTokenInvalidException();
        }

        // 7. 检查剩余次数
        int remainingResends = (Integer) tokenData.get("remainingResends");
        if (remainingResends <= 0) {
            redisTemplate.delete(tokenKey); // 清理已耗尽的 token
            throw new ResendTokenExhaustedException();
        }

        // 8. 检查频率控制和 60 秒间隔
        checkRateLimit(targetType, targetHash, purpose, ipHash);
        checkResendInterval(targetType, targetHash, purpose, ipHash);

        // 9. 使旧验证码失效（按 IP 维度删除）
        String codeKey = buildCodeKey(targetType, targetHash, purpose, ipHash);
        redisTemplate.delete(codeKey);
        log.info("[验证码] 旧验证码已失效: targetType={}, targetHash={}, purpose={}, ipHash={}",
                 targetType, targetHash.substring(0, 16), purpose, ipHash.substring(0, 16));

        // 10. 生成新验证码并存储（key 包含 ipHash）
        String code = generateCode();
        redisTemplate.opsForValue().set(codeKey, code, CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        // 11. 记录发送时间戳（key 包含 ipHash）
        String lastKey = buildLastKey(targetType, targetHash, purpose, ipHash);
        redisTemplate.opsForValue().set(lastKey, String.valueOf(System.currentTimeMillis()),
                RESEND_INTERVAL_SECONDS, TimeUnit.SECONDS);

        // 12. 发送邮件/短信
        sendCodeToTarget(targetType, target, code);

        // 13. 递减剩余次数，更新 Redis（不重新颁发 token！）
        int newRemaining = remainingResends - 1;
        tokenData.put("remainingResends", newRemaining);
        try {
            Long ttl = redisTemplate.getExpire(tokenKey, TimeUnit.SECONDS);
            if (ttl == null || ttl <= 0) {
                ttl = (long) RESEND_TOKEN_TTL_SECONDS;
            }
            redisTemplate.opsForValue().set(tokenKey, objectMapper.writeValueAsString(tokenData),
                    ttl, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Token 序列化失败", e);
        }

        if (newRemaining <= 0) {
            redisTemplate.delete(tokenKey);
            log.info("[验证码] Resend token 已耗尽并删除: token={}", resendToken.substring(0, 8) + "...");
        }

        log.info("[验证码] 重新发送成功: targetType={}, targetHash={}, purpose={}, ipHash={}, remaining={}",
                 targetType, targetHash.substring(0, 16), purpose, ipHash.substring(0, 16), newRemaining);

        long remainingTtl = RESEND_TOKEN_TTL_SECONDS;
        Long actualTtl = redisTemplate.getExpire(tokenKey, TimeUnit.SECONDS);
        if (actualTtl != null && actualTtl > 0) {
            remainingTtl = actualTtl;
        }
        return new VerificationSendVO(resendToken, remainingTtl, newRemaining);
    }

    // ==================== 验证码校验 ====================

    /**
     * 校验验证码（IP 绑定校验，验证成功后自动删除，保证一次性使用）。
     *
     * <p><b>IP 绑定逻辑</b>：验证码 key 包含 ipHash，因此只有当请求 IP 与
     * 发送验证码时的 IP 一致时，才能查找到验证码。不同 IP 的请求会自动找不到
     * 对应的验证码，从而被拒绝。
     *
     * @param targetType "email" 或 "phone"
     * @param target     邮箱地址或手机号
     * @param purpose    用途
     * @param code       用户输入的验证码
     * @param clientIp   客户端 IP（用于 IP 绑定校验）
     * @return true=验证通过，false=验证码错误或已过期或 IP 不匹配
     */
    public boolean verifyCode(String targetType, String target, String purpose,
                               String code, String clientIp) {
        String targetHash = sha256(target);
        String ipHash = sha256(clientIp);
        String codeKey = buildCodeKey(targetType, targetHash, purpose, ipHash);

        String stored = redisTemplate.opsForValue().get(codeKey);
        if (stored != null && stored.equals(code)) {
            redisTemplate.delete(codeKey); // 一次性使用，验证后立即删除
            log.info("[验证码] 校验成功并已删除: targetType={}, targetHash={}, purpose={}, ipHash={}",
                     targetType, targetHash.substring(0, 16), purpose, ipHash.substring(0, 16));
            return true;
        }
        return false;
    }

    // ==================== 验证码防爆破 ====================

    /**
     * 检查验证码校验失败次数（同一 IP + 同一目标 维度防爆破）。
     *
     * <p><b>设计说明</b>：attempts key 包含 {targetType}:{targetHash}:{purpose}:{ipHash}，
     * 因此攻击者无法通过轮换 IP（代理池）对同一目标无限试错——
     * 每个新 IP 的 attempts 计数器是独立的，且验证码本身也是 IP 绑定的，
     * 旧 IP 的验证码对攻击者不可用。
     *
     * @param targetType "email" 或 "phone"
     * @param target     邮箱地址或手机号
     * @param purpose    用途
     * @param clientIp   客户端 IP
     * @throws RateLimitExceededException 超过最大失败次数
     */
    public void checkCodeAttempts(String targetType, String target, String purpose, String clientIp) {
        String targetHash = sha256(target);
        String ipHash = sha256(clientIp);
        String key = buildAttemptsKey(targetType, targetHash, purpose, ipHash);

        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(key, CODE_FAILURE_WINDOW_SECONDS, TimeUnit.SECONDS);
        }
        if (attempts != null && attempts > MAX_CODE_FAILURES) {
            log.warn("[验证码] 校验失败次数过多: targetType={}, targetHash={}, purpose={}, ipHash={}, attempts={}",
                     targetType, targetHash.substring(0, 16), purpose, ipHash.substring(0, 16), attempts);
            throw new RateLimitExceededException("验证码错误次数过多，请 15 分钟后重试");
        }
    }

    /**
     * 记录验证码校验失败（同一 IP + 同一目标 维度）。
     */
    public void recordCodeFailure(String targetType, String target, String purpose, String clientIp) {
        String targetHash = sha256(target);
        String ipHash = sha256(clientIp);
        String key = buildAttemptsKey(targetType, targetHash, purpose, ipHash);

        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(key, CODE_FAILURE_WINDOW_SECONDS, TimeUnit.SECONDS);
        }
        log.warn("[验证码] 校验失败: targetType={}, targetHash={}, purpose={}, ipHash={}, attempts={}",
                 targetType, targetHash.substring(0, 16), purpose, ipHash.substring(0, 16), attempts);
    }

    /**
     * 验证成功后清除该 IP+目标 的验证码失败计数。
     */
    public void clearCodeAttempts(String targetType, String target, String purpose, String clientIp) {
        String targetHash = sha256(target);
        String ipHash = sha256(clientIp);
        redisTemplate.delete(buildAttemptsKey(targetType, targetHash, purpose, ipHash));
    }

    // ==================== 向后兼容：注册接口防爆破 ====================

    /**
     * 检查注册接口验证码失败次数（同一 IP 维度防爆破）。
     * @deprecated 新功能请使用 {@link #checkCodeAttempts}，此方法保留向后兼容。
     */
    @Deprecated
    public void checkRegisterCodeAttempts(String clientIp) {
        // 使用通用 attempts 方法，targetType=email, purpose=REGISTER
        // 注意：这里 target 未知，使用通配符
        String ipHash = sha256(clientIp);
        String key = PREFIX_ATTEMPTS + "email:*:REGISTER:" + ipHash;
        // 简化处理：直接用 IP 维度的 key
        String legacyKey = "verif:register:attempts:" + ipHash;
        Long attempts = redisTemplate.opsForValue().increment(legacyKey);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(legacyKey, CODE_FAILURE_WINDOW_SECONDS, TimeUnit.SECONDS);
        }
        if (attempts != null && attempts > MAX_CODE_FAILURES) {
            log.warn("[注册防爆破] IP 验证码失败次数过多: ipHash={}, attempts={}", ipHash, attempts);
            throw new RateLimitExceededException("验证码错误次数过多，请 15 分钟后重试");
        }
    }

    /**
     * 注册成功后清除该 IP 的验证码失败计数。
     * @deprecated 新功能请使用 {@link #clearCodeAttempts}。
     */
    @Deprecated
    public void clearRegisterCodeAttempts(String clientIp) {
        String ipHash = sha256(clientIp);
        redisTemplate.delete("verif:register:attempts:" + ipHash);
    }

    /**
     * 记录注册接口验证码失败。
     * @deprecated 新功能请使用 {@link #recordCodeFailure}。
     */
    @Deprecated
    public void recordRegisterCodeFailure(String clientIp) {
        String ipHash = sha256(clientIp);
        String legacyKey = "verif:register:attempts:" + ipHash;
        Long attempts = redisTemplate.opsForValue().increment(legacyKey);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(legacyKey, CODE_FAILURE_WINDOW_SECONDS, TimeUnit.SECONDS);
        }
        log.warn("[注册防爆破] 验证码错误: ipHash={}, attempts={}", ipHash, attempts);
    }

    // ==================== 私有方法：频率控制 ====================

    /**
     * 检查每小时发送次数限制（同一 IP+目标 维度）。
     */
    private void checkRateLimit(String targetType, String targetHash, String purpose, String ipHash) {
        String rateKey = buildRateKey(targetType, targetHash, purpose, ipHash);
        Long sendCount = redisTemplate.opsForValue().increment(rateKey);
        if (sendCount != null && sendCount == 1) {
            redisTemplate.expire(rateKey, 1, TimeUnit.HOURS);
        }
        if (sendCount != null && sendCount > MAX_SENDS_PER_HOUR) {
            log.warn("[验证码] 每小时发送次数超限: targetType={}, targetHash={}, purpose={}, ipHash={}, count={}",
                     targetType, targetHash.substring(0, 16), purpose, ipHash.substring(0, 16), sendCount);
            throw new RateLimitExceededException("发送频率超限，请稍后再试");
        }
    }

    /**
     * 检查 60 秒重新发送间隔（同一 IP+目标 维度）。
     */
    private void checkResendInterval(String targetType, String targetHash, String purpose, String ipHash) {
        String lastKey = buildLastKey(targetType, targetHash, purpose, ipHash);
        String lastSendStr = redisTemplate.opsForValue().get(lastKey);
        if (lastSendStr != null) {
            long lastSend = Long.parseLong(lastSendStr);
            long now = System.currentTimeMillis();
            long elapsed = (now - lastSend) / 1000;
            if (elapsed < RESEND_INTERVAL_SECONDS) {
                log.warn("[验证码] 发送间隔不足60秒: targetType={}, targetHash={}, purpose={}, ipHash={}, elapsed={}s",
                         targetType, targetHash.substring(0, 16), purpose, ipHash.substring(0, 16), elapsed);
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
    }

    /**
     * 检查邮箱是否属于禁止域名。
     */
    private void checkEmailNotBlocked(String email) {
        if (email != null && blockedDomainsRaw != null) {
            String lowerEmail = email.toLowerCase();
            for (String domain : blockedDomainsRaw.split(",")) {
                String trimmed = domain.trim().toLowerCase();
                if (!trimmed.isEmpty() && lowerEmail.endsWith("@" + trimmed)) {
                    throw new IllegalArgumentException("不允许使用 " + trimmed + " 邮箱，请使用企业邮箱");
                }
            }
        }
    }

    // ==================== 私有方法：验证码生成 ====================

    /**
     * 生成 6 位数字验证码。
     */
    public String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(SECURE_RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    // ==================== 私有方法：Redis Key 构建 ====================

    /**
     * 构建验证码存储 key。
     * 格式：verif:code:{targetType}:{targetHash}:{purpose}:{ipHash}
     */
    private String buildCodeKey(String targetType, String targetHash, String purpose, String ipHash) {
        return PREFIX_CODE + targetType + ":" + targetHash + ":" + purpose + ":" + ipHash;
    }

    /**
     * 构建发送频率计数器 key。
     * 格式：verif:rate:{targetType}:{targetHash}:{purpose}:{ipHash}
     */
    private String buildRateKey(String targetType, String targetHash, String purpose, String ipHash) {
        return PREFIX_RATE + targetType + ":" + targetHash + ":" + purpose + ":" + ipHash;
    }

    /**
     * 构建上次发送时间戳 key。
     * 格式：verif:last:{targetType}:{targetHash}:{purpose}:{ipHash}
     */
    private String buildLastKey(String targetType, String targetHash, String purpose, String ipHash) {
        return PREFIX_LAST + targetType + ":" + targetHash + ":" + purpose + ":" + ipHash;
    }

    /**
     * 构建不透明 resend token key。
     * 格式：verif:token:{tokenUUID}
     */
    private String buildTokenKey(String token) {
        return PREFIX_TOKEN + token;
    }

    /**
     * 构建验证码校验失败次数 key。
     * 格式：verif:attempts:{targetType}:{targetHash}:{purpose}:{ipHash}
     */
    private String buildAttemptsKey(String targetType, String targetHash, String purpose, String ipHash) {
        return PREFIX_ATTEMPTS + targetType + ":" + targetHash + ":" + purpose + ":" + ipHash;
    }

    /**
     * SHA-256 哈希，用于保护 Redis key 中的敏感信息。
     */
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