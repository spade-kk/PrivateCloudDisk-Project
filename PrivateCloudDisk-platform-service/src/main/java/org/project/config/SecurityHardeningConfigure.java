package org.project.config;

import org.project.config.properties.ApiRateLimitProperties;
import org.project.config.properties.CaptchaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 安全加固 Bean 配置。
 * <p>
 * 启用以下配置属性绑定：
 * <ul>
 *   <li>{@link CaptchaProperties} — 人机验证（Turnstile）</li>
 *   <li>{@link ApiRateLimitProperties} — API 限流</li>
 * </ul>
 * <p>
 * 设备指纹验证已移至 Gateway 网关层统一处理。
 */
@Configuration
@EnableConfigurationProperties({
        CaptchaProperties.class,
        ApiRateLimitProperties.class
})
public class SecurityHardeningConfigure {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
