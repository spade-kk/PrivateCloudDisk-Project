package org.project.privateclouddiskgatewayservice.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "gateway.rate-limit")
public class GatewayRateLimitProperties {
    private boolean enabled = true;
    private boolean failOpen = true;
    private List<Rule> rules = new ArrayList<>(List.of(
            Rule.of("public-login-ip", "/api/v1/business/users/login", "POST", KeyType.IP, 30, Duration.ofMinutes(1)),
            Rule.of("public-register-ip", "/api/v1/business/users/", "POST", KeyType.IP, 10, Duration.ofHours(1)),
            Rule.of("upload-session-user", "/api/v1/business/uploads/", "POST", KeyType.USER, 20, Duration.ofMinutes(1)),
            Rule.of("operation-token-issue-user", "/api/v1/files/operation-tokens", "POST", KeyType.USER, 30, Duration.ofMinutes(1)),
            Rule.of("operation-token-issue-ip", "/api/v1/files/operation-tokens", "POST", KeyType.IP, 120, Duration.ofMinutes(1)),
            Rule.of("operation-token-destroy-user", "/api/v1/files/operation-tokens", "DELETE", KeyType.USER, 60, Duration.ofMinutes(1)),
            Rule.of("operation-token-destroy-ip", "/api/v1/files/operation-tokens", "DELETE", KeyType.IP, 180, Duration.ofMinutes(1))
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

    public enum KeyType {
        IP,
        USER,
        USER_OR_IP
    }
}
