package org.project.config;

import org.project.config.properties.ApiRateLimitProperties;
import org.project.config.properties.CaptchaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Security hardening beans that do not change the gateway authentication model.
 */
@Configuration
@EnableConfigurationProperties({CaptchaProperties.class, ApiRateLimitProperties.class})
public class SecurityHardeningConfigure {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
