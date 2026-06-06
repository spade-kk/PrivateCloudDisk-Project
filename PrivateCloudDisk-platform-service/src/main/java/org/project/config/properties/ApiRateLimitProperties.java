package org.project.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "security.api-rate-limit")
public class ApiRateLimitProperties {
    private boolean enabled = true;
    private Rule loginIp = new Rule(30, Duration.ofMinutes(1));
    private Rule loginIdentity = new Rule(10, Duration.ofMinutes(1));
    private Rule loginFailureIp = new Rule(30, Duration.ofMinutes(15));
    private Rule loginFailureIdentity = new Rule(5, Duration.ofMinutes(15));
    private Rule registerIp = new Rule(10, Duration.ofHours(1));
    private Rule registerPhone = new Rule(3, Duration.ofHours(1));
    private Rule uploadSessionIp = new Rule(60, Duration.ofMinutes(1));
    private Rule uploadSessionUser = new Rule(20, Duration.ofMinutes(1));
    private Rule uploadSessionNode = new Rule(8, Duration.ofMinutes(1));

    @Data
    public static class Rule {
        private int limit = 1;
        private Duration window = Duration.ofMinutes(1);

        public Rule() {
        }

        public Rule(int limit, Duration window) {
            this.limit = limit;
            this.window = window;
        }
    }
}
