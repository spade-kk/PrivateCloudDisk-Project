package org.project.privateclouddiskgatewayservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Spring Security 配置（WebFlux 版本）
 * <p>
 * 认证逻辑由 AuthGlobalFilter 处理，此处只需放行所有请求。
 * 注意：Spring Cloud Gateway WebFlux 必须使用 ServerHttpSecurity，
 * 而非传统的 HttpSecurity。
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
                // 网关层已由 AuthGlobalFilter 完成认证，此处放行所有请求
                .authorizeExchange(exchanges -> exchanges
                        .anyExchange().permitAll()
                )
                // API 网关场景禁用 CSRF
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // 禁用表单登录
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                // 禁用 HTTP Basic 认证
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable);

        return http.build();
    }
}
