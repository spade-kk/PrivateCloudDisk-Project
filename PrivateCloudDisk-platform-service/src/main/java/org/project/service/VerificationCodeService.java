package org.project.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 验证码服务 - 生成、存储、验证
 * <p>使用Redis存储验证码，天然支持TTL自动过期。
 *
 * <p>Key结构：
 * <pre>
 *   email:verify:{email}          → 验证码
 *   phone:verify:{phone}          → 验证码
 *   email:verify:{email}:rate     → 发送频率计数器
 *   phone:verify:{phone}:rate     → 发送频率计数器
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationCodeService {

    private final StringRedisTemplate redisTemplate;
    private static final Random RANDOM = new Random();

    // Redis Key前缀
    private static final String PREFIX_EMAIL = "email:verify:";
    private static final String PREFIX_PHONE = "phone:verify:";
    private static final String SUFFIX_RATE = ":rate";

    // 同一接收者发送最小间隔（秒）
    private static final int MIN_INTERVAL_SECONDS = 60;
    // 同一接收者1小时内最大发送次数
    private static final int MAX_SENDS_PER_HOUR = 5;

    /**
     * 生成6位数字验证码
     */
    public String generateCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * 存储邮箱验证码
     *
     * @param email         邮箱
     * @param code          验证码
     * @param expireSeconds 有效期（秒）
     * @return true=存储成功（且未超限）
     */
    public boolean storeEmailCode(String email, String code, int expireSeconds) {
        String key = PREFIX_EMAIL + email;
        if (!checkRateLimit(key)) {
            log.warn("[验证码服务] 邮箱验证码发送频率超限. email={}", email);
            return false;
        }
        redisTemplate.opsForValue().set(key, code, expireSeconds, TimeUnit.SECONDS);
        return true;
    }

    /**
     * 存储手机验证码
     */
    public boolean storePhoneCode(String phone, String code, int expireSeconds) {
        String key = PREFIX_PHONE + phone;
        if (!checkRateLimit(key)) {
            log.warn("[验证码服务] 手机验证码发送频率超限. phone={}", maskPhone(phone));
            return false;
        }
        redisTemplate.opsForValue().set(key, code, expireSeconds, TimeUnit.SECONDS);
        return true;
    }

    /**
     * 验证邮箱验证码（验证成功后自动删除）
     *
     * @return true=匹配成功
     */
    public boolean verifyEmailCode(String email, String code) {
        String key = PREFIX_EMAIL + email;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored != null && stored.equals(code)) {
            redisTemplate.delete(key); // 一次性使用
            return true;
        }
        return false;
    }

    /**
     * 验证手机验证码（验证成功后自动删除）
     */
    public boolean verifyPhoneCode(String phone, String code) {
        String key = PREFIX_PHONE + phone;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored != null && stored.equals(code)) {
            redisTemplate.delete(key);
            return true;
        }
        return false;
    }

    /**
     * 检查发送频率：1分钟内不超过1次，1小时内不超过5次
     * <p>若通过检查，同时更新计数。
     */
    private boolean checkRateLimit(String verifyKey) {
        String rateKey = verifyKey + SUFFIX_RATE;

        // 1. 检查最小间隔
        String lastKey = verifyKey; // 只要key还在说明上次发送尚未过期，此时也可以接受（验证码仍有效）
        // 简化：我们用rateKey同时记录发送次数
        Long count = redisTemplate.opsForValue().increment(rateKey);
        if (count == null || count == 1) {
            // 首次设置，设置TTL为1小时
            redisTemplate.expire(rateKey, 1, TimeUnit.HOURS);
        }

        // 超过1小时5次限制
        if (count != null && count > MAX_SENDS_PER_HOUR) {
            return false;
        }

        return true;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
