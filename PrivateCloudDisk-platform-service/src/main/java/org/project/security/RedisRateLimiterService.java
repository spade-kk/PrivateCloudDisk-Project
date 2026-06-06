package org.project.security;

import lombok.RequiredArgsConstructor;
import org.project.config.properties.ApiRateLimitProperties;
import org.project.service.ex.RateLimitExceededException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisRateLimiterService {
    private static final String FIXED_WINDOW_SCRIPT = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
            end
            return current
            """;
    private static final DefaultRedisScript<Long> FIXED_WINDOW_REDIS_SCRIPT =
            new DefaultRedisScript<>(FIXED_WINDOW_SCRIPT, Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ApiRateLimitProperties properties;

    public void check(String key, ApiRateLimitProperties.Rule rule, String message) {
        if (!properties.isEnabled() || !isRuleEnabled(rule)) {
            return;
        }
        long count = increment(key, rule.getWindow());
        if (count > rule.getLimit()) {
            throw new RateLimitExceededException(message);
        }
    }

    public long increment(String key, Duration window) {
        long windowSeconds = Math.max(1L, window.toSeconds());
        Long result = stringRedisTemplate.execute(
                FIXED_WINDOW_REDIS_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(windowSeconds)
        );
        return result == null ? 0 : result;
    }

    public long current(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            stringRedisTemplate.delete(key);
            return 0;
        }
    }

    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    private boolean isRuleEnabled(ApiRateLimitProperties.Rule rule) {
        return rule != null
                && rule.getLimit() > 0
                && rule.getWindow() != null
                && rule.getWindow().toMillis() > 0;
    }
}
