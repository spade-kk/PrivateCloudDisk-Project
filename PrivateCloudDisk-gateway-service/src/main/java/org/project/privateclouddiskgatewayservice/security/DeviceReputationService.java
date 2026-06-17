package org.project.privateclouddiskgatewayservice.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * 设备声誉评分服务。
 *
 * <h2>为什么需要声誉系统？</h2>
 * <p>
 * 单次验证只能判断当前请求的合法性，无法判断设备的长期行为模式。
 * 一个通过验证的设备可能突然开始恶意行为（如账号枚举、爬取数据）。
 * 声誉系统通过持续追踪设备行为，累积信任评分，实现动态信任评估。
 * </p>
 *
 * <h2>评分机制</h2>
 * <table>
 *   <tr><th>事件</th><th>分数变化</th><th>说明</th></tr>
 *   <tr><td>初始注册</td><td>+30</td><td>新设备基础分</td></tr>
 *   <tr><td>挑战-应答通过</td><td>+3</td><td>每次成功通过挑战</td></tr>
 *   <tr><td>平台证明通过</td><td>+10</td><td>硬件级证明通过</td></tr>
 *   <tr><td>正常请求</td><td>+0.1</td><td>每次正常 API 请求（慢速累积）</td></tr>
 *   <tr><td>登录成功</td><td>+2</td><td>成功登录</td></tr>
 *   <tr><td>登录失败</td><td>-5</td><td>单次登录失败</td></tr>
 *   <tr><td>验证失败</td><td>-10</td><td>挑战/证明失败</td></tr>
 *   <tr><td>频繁失败</td><td>-20</td><td>短时间内多次失败</td></tr>
 *   <tr><td>行为异常</td><td>-15</td><td>触发行为分析异常</td></tr>
 *   <tr><td>自然衰减</td><td>-0.5/h</td><td>不活跃设备缓慢降分</td></tr>
 * </table>
 *
 * <h2>信任等级映射</h2>
 * <table>
 *   <tr><th>分数</th><th>等级</th><th>含义</th></tr>
 *   <tr><td>80-100</td><td>TRUSTED</td><td>长期良好行为 + 硬件证明</td></tr>
 *   <tr><td>50-79</td><td>NEUTRAL</td><td>正常设备</td></tr>
 *   <tr><td>20-49</td><td>SUSPICIOUS</td><td>有可疑行为</td></tr>
 *   <tr><td>0-19</td><td>MALICIOUS</td><td>已确认恶意，自动拦截</td></tr>
 * </table>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceReputationService {

    private final ReactiveStringRedisTemplate redisTemplate;

    // ─── Redis Key 前缀 ───

    private static final String REPUTATION_PREFIX = "pcd:reputation:";
    private static final String HISTORY_PREFIX = "pcd:reputation:history:";
    private static final String BLOCKLIST_PREFIX = "pcd:blocklist:device:";

    // ─── 评分参数 ───

    private static final int INITIAL_SCORE = 30;
    private static final int MAX_SCORE = 100;
    private static final int MIN_SCORE = 0;

    // 事件分数
    private static final double CHALLENGE_PASS_SCORE = 3.0;
    private static final double ATTESTATION_PASS_SCORE = 10.0;
    private static final double NORMAL_REQUEST_SCORE = 0.1;
    private static final double LOGIN_SUCCESS_SCORE = 2.0;
    private static final double LOGIN_FAILURE_SCORE = -5.0;
    private static final double VERIFICATION_FAILURE_SCORE = -10.0;
    private static final double BURST_FAILURE_SCORE = -20.0;
    private static final double BEHAVIOR_ANOMALY_SCORE = -15.0;

    // 自动衰减
    private static final double DECAY_PER_HOUR = 0.5;
    private static final Duration REPUTATION_TTL = Duration.ofDays(90);

    // 自动封禁阈值
    private static final int AUTO_BLOCK_THRESHOLD = 10;

    /**
     * 获取设备声誉评分。
     */
    public Mono<Integer> getScore(String deviceFingerprintHash) {
        if (deviceFingerprintHash == null) return Mono.just(0);
        String key = REPUTATION_PREFIX + deviceFingerprintHash;
        return redisTemplate.opsForValue().get(key)
                .map(score -> {
                    try {
                        return Integer.parseInt(score);
                    } catch (NumberFormatException e) {
                        return INITIAL_SCORE;
                    }
                })
                .defaultIfEmpty(INITIAL_SCORE);
    }

    /**
     * 初始化设备声誉。
     */
    public Mono<Integer> initialize(String deviceFingerprintHash, String platform) {
        if (deviceFingerprintHash == null) return Mono.just(0);
        String key = REPUTATION_PREFIX + deviceFingerprintHash;
        return redisTemplate.opsForValue()
                .setIfAbsent(key, String.valueOf(INITIAL_SCORE), REPUTATION_TTL)
                .flatMap(absorbed -> {
                    // 记录首次出现时间
                    String historyKey = HISTORY_PREFIX + deviceFingerprintHash + ":firstSeen";
                    return redisTemplate.opsForValue()
                            .setIfAbsent(historyKey, Instant.now().toString(), REPUTATION_TTL)
                            .thenReturn(INITIAL_SCORE);
                })
                .switchIfEmpty(getScore(deviceFingerprintHash));
    }

    /**
     * 调整设备声誉评分。
     *
     * @param deviceFingerprintHash 设备指纹哈希
     * @param delta                 分数变化（正数增加，负数减少）
     * @param reason                变更原因
     */
    public Mono<Integer> adjustScore(String deviceFingerprintHash, double delta, String reason) {
        if (deviceFingerprintHash == null) return Mono.just(0);

        String key = REPUTATION_PREFIX + deviceFingerprintHash;

        return redisTemplate.opsForValue().get(key)
                .defaultIfEmpty(String.valueOf(INITIAL_SCORE))
                .flatMap(scoreStr -> {
                    int currentScore;
                    try {
                        currentScore = Integer.parseInt(scoreStr);
                    } catch (NumberFormatException e) {
                        currentScore = INITIAL_SCORE;
                    }

                    int newScore = (int) Math.max(MIN_SCORE, Math.min(MAX_SCORE, currentScore + delta));

                    // 记录评分变更历史
                    String historyEntry = String.format("%d|%+.1f|%s|%s",
                            newScore, delta, reason, Instant.now().toString());
                    String historyKey = HISTORY_PREFIX + deviceFingerprintHash + ":log";

                    return redisTemplate.opsForList()
                            .leftPush(historyKey, historyEntry)
                            .flatMap(count -> {
                                // 限制历史记录长度
                                if (count > 100) {
                                    redisTemplate.opsForList().trim(historyKey, 0, 99).subscribe();
                                }
                                return redisTemplate.opsForValue()
                                        .set(key, String.valueOf(newScore), REPUTATION_TTL);
                            })
                            .then(Mono.just(newScore));
                })
                .doOnNext(score -> {
                    if (score <= AUTO_BLOCK_THRESHOLD) {
                        String blockKey = BLOCKLIST_PREFIX + deviceFingerprintHash;
                        redisTemplate.opsForValue()
                                .set(blockKey, "auto-blocked-" + Instant.now().toString(), Duration.ofDays(30))
                                .subscribe();
                        log.warn("设备自动封禁 fingerprint={} score={} reason={}",
                                deviceFingerprintHash, score, reason);
                    }
                    if (delta < 0) {
                        log.info("设备声誉降低 fingerprint={} score={} delta={} reason={}",
                                deviceFingerprintHash, score, delta, reason);
                    }
                });
    }

    /**
     * 检查设备是否被封禁。
     */
    public Mono<Boolean> isBlocked(String deviceFingerprintHash) {
        if (deviceFingerprintHash == null) return Mono.just(false);
        String key = BLOCKLIST_PREFIX + deviceFingerprintHash;
        return redisTemplate.hasKey(key);
    }

    /**
     * 获取设备历史请求总数。
     */
    public Mono<Long> getTotalRequests(String deviceFingerprintHash) {
        if (deviceFingerprintHash == null) return Mono.just(0L);
        String key = HISTORY_PREFIX + deviceFingerprintHash + ":count";
        return redisTemplate.opsForValue().get(key)
                .map(count -> {
                    try { return Long.parseLong(count); }
                    catch (NumberFormatException e) { return 0L; }
                })
                .defaultIfEmpty(0L);
    }

    /**
     * 递增请求计数。
     */
    public Mono<Long> incrementRequestCount(String deviceFingerprintHash) {
        if (deviceFingerprintHash == null) return Mono.just(0L);
        String key = HISTORY_PREFIX + deviceFingerprintHash + ":count";
        return redisTemplate.opsForValue().increment(key);
    }

    /**
     * 记录事件并调整声誉。
     */
    public Mono<Integer> recordEvent(String deviceFingerprintHash, ReputationEvent event) {
        if (deviceFingerprintHash == null) return Mono.just(0);

        double delta = switch (event) {
            case CHALLENGE_PASS -> CHALLENGE_PASS_SCORE;
            case ATTESTATION_PASS -> ATTESTATION_PASS_SCORE;
            case NORMAL_REQUEST -> NORMAL_REQUEST_SCORE;
            case LOGIN_SUCCESS -> LOGIN_SUCCESS_SCORE;
            case LOGIN_FAILURE -> LOGIN_FAILURE_SCORE;
            case VERIFICATION_FAILURE -> VERIFICATION_FAILURE_SCORE;
            case BURST_FAILURE -> BURST_FAILURE_SCORE;
            case BEHAVIOR_ANOMALY -> BEHAVIOR_ANOMALY_SCORE;
        };

        return adjustScore(deviceFingerprintHash, delta, event.name());
    }

    /**
     * 计算声誉得分（0-5 分，用于 EnhancedDeviceIdentity 的 reputationScore）。
     * <p>
     * 声誉原始分 0-100 → 映射到 0-5 分：
     * 90-100 → 5, 70-89 → 4, 50-69 → 3, 30-49 → 2, 10-29 → 1, 0-9 → 0
     */
    public Mono<Integer> computeReputationScore(String deviceFingerprintHash) {
        return getScore(deviceFingerprintHash)
                .map(rawScore -> {
                    if (rawScore >= 90) return 5;
                    if (rawScore >= 70) return 4;
                    if (rawScore >= 50) return 3;
                    if (rawScore >= 30) return 2;
                    if (rawScore >= 10) return 1;
                    return 0;
                });
    }

    // ─── 枚举 ───

    public enum ReputationEvent {
        CHALLENGE_PASS,
        ATTESTATION_PASS,
        NORMAL_REQUEST,
        LOGIN_SUCCESS,
        LOGIN_FAILURE,
        VERIFICATION_FAILURE,
        BURST_FAILURE,
        BEHAVIOR_ANOMALY
    }
}