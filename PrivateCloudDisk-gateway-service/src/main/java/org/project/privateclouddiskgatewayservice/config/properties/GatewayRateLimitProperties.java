package org.project.privateclouddiskgatewayservice.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 网关限流配置属性。
 * <p>
 * 支持四种限流维度：
 * <ul>
 *   <li>{@link KeyType#IP} — 基于客户端 IP</li>
 *   <li>{@link KeyType#USER} — 基于已认证用户 ID</li>
 *   <li>{@link KeyType#USER_OR_IP} — 优先用户 ID，降级 IP</li>
 *   <li>{@link KeyType#FINGERPRINT} — 基于设备指纹，降级 IP（新增）</li>
 *   <li>{@link KeyType#FINGERPRINT_OR_IP} — 优先指纹，降级 IP（新增）</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "gateway.rate-limit")
public class GatewayRateLimitProperties {
    private boolean enabled = true;
    private boolean failOpen = true;
    private List<Rule> rules = new ArrayList<>(List.of(
            // --- 登录/注册：指纹 + IP 双维度限流 ---
            Rule.of("public-login-fp", "/api/v1/business/users/login", "POST",
                    KeyType.FINGERPRINT_OR_IP, 20, Duration.ofMinutes(1)),
            Rule.of("public-login-ip", "/api/v1/business/users/login", "POST",
                    KeyType.IP, 30, Duration.ofMinutes(1)),
            Rule.of("public-register-fp", "/api/v1/business/users/", "POST",
                    KeyType.FINGERPRINT_OR_IP, 5, Duration.ofHours(1)),
            Rule.of("public-register-ip", "/api/v1/business/users/", "POST",
                    KeyType.IP, 10, Duration.ofHours(1)),

            // --- 上传/操作：用户维度 ---
            Rule.of("upload-session-user", "/api/v1/business/uploads/", "POST",
                    KeyType.USER, 20, Duration.ofMinutes(1)),
            Rule.of("operation-token-issue-user", "/api/v1/files/operation-tokens", "POST",
                    KeyType.USER, 30, Duration.ofMinutes(1)),
            Rule.of("operation-token-issue-ip", "/api/v1/files/operation-tokens", "POST",
                    KeyType.IP, 120, Duration.ofMinutes(1)),
            Rule.of("operation-token-destroy-user", "/api/v1/files/operation-tokens", "DELETE",
                    KeyType.USER, 60, Duration.ofMinutes(1)),
            Rule.of("operation-token-destroy-ip", "/api/v1/files/operation-tokens", "DELETE",
                    KeyType.IP, 180, Duration.ofMinutes(1))
    ));

    @Data
    public static class Rule {
        private String name;
        private String pathPattern;
        private String method = "*";
        private KeyType keyType = KeyType.IP;
        private int limit;
        private Duration window = Duration.ofMinutes(1);
        private boolean enabled = true;

        public static Rule of(String name, String pathPattern, String method, KeyType keyType,
                              int limit, Duration window) {
            Rule rule = new Rule();
            rule.setName(name);
            rule.setPathPattern(pathPattern);
            rule.setMethod(method);
            rule.setKeyType(keyType);
            rule.setLimit(limit);
            rule.setWindow(window);
            return rule;
        }
    }

    /**
     * 限流 Key 类型枚举。
     */
    public enum KeyType {
        /** 基于客户端 IP 限流 */
        IP,
        /** 基于已认证用户 ID 限流（未认证则跳过） */
        USER,
        /** 优先用户 ID，未认证时降级为 IP */
        USER_OR_IP,
        /** 基于设备指纹限流（X-Device-Fingerprint 头） */
        FINGERPRINT,
        /** 优先设备指纹，缺失时降级为 IP */
        FINGERPRINT_OR_IP
    }
}
