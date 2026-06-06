package org.project.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "security.captcha.turnstile")
public class CaptchaProperties {
    private boolean enabled = false;
    private String secretKey = "";
    private String siteverifyUrl = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    private String expectedHostname = "";
    private boolean validateAction = true;
    private Duration timeout = Duration.ofSeconds(3);
}
