package org.project.privateclouddiskgatewayservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.privateclouddiskgatewayservice.dto.ApiErrorResponse;
import org.project.privateclouddiskgatewayservice.handler.AccessDeniedHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Spring Security 配置（WebFlux 版本）
 * <p>
 * 认证逻辑由 {@code AuthGlobalFilter} 处理，此处配置 Security 框架层面的行为。
 * <p>
 * 关键安全决策：
 * <ul>
 *   <li>未认证请求 → 401（由 AuthGlobalFilter 在路由匹配前拦截）</li>
 *   <li>已认证但无权限 → 403（由 AccessDeniedHandler 处理）</li>
 *   <li>CSRF → 禁用（API 网关使用 JWT，无状态）</li>
 *   <li>表单登录/HTTP Basic → 禁用</li>
 * </ul>
 * <p>
 * 注意：Spring Cloud Gateway WebFlux 必须使用 {@link ServerHttpSecurity}，
 * 而非传统的 {@code HttpSecurity}。
 */
@Slf4j
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AccessDeniedHandler accessDeniedHandler;

    /**
     * 配置 WebFlux 安全过滤链
     * <p>
     * 由于 AuthGlobalFilter（Order -100）已经完成了所有认证逻辑，
     * Spring Security 此处不再重复执行认证检查，仅保留授权检查能力。
     */
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
                // ═══════════════════════════════════════════════
                // 授权配置：放行所有请求
                // 实际认证由 AuthGlobalFilter 前置完成
                // ═══════════════════════════════════════════════
                .authorizeExchange(exchanges -> exchanges
                        .anyExchange().permitAll()
                )

                // ═══════════════════════════════════════════════
                // 异常处理：未认证 → 401 JSON 响应
                // ═══════════════════════════════════════════════
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((exchange, ex) -> {
                            log.warn("Spring Security 认证失败: {} {}",
                                    exchange.getRequest().getMethod(),
                                    exchange.getRequest().getURI().getPath());
                            return writeJsonError(
                                    exchange,
                                    HttpStatus.UNAUTHORIZED,
                                    "未授权，请先登录"
                            );
                        })
                        .accessDeniedHandler(accessDeniedHandler)
                )

                // ═══════════════════════════════════════════════
                // CSRF: 禁用（JWT 无状态 API 网关）
                // ═══════════════════════════════════════════════
                .csrf(ServerHttpSecurity.CsrfSpec::disable)

                // ═══════════════════════════════════════════════
                // 禁用不需要的认证方式
                // ═══════════════════════════════════════════════
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable);

        return http.build();
    }

    /**
     * 写入 JSON 格式的认证失败响应
     */
    private Mono<Void> writeJsonError(ServerWebExchange exchange,
                                       HttpStatus status,
                                       String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiErrorResponse errorBody = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(exchange.getRequest().getURI().getPath())
                .build();

        try {
            tools.jackson.databind.ObjectMapper mapper =
                    new tools.jackson.databind.ObjectMapper();
            byte[] bytes = mapper.writeValueAsBytes(errorBody);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception e) {
            String fallback = String.format(
                    "{\"code\":%d,\"message\":\"%s\",\"timestamp\":\"%s\"}",
                    status.value(), message, LocalDateTime.now()
            );
            DataBuffer buffer = exchange.getResponse().bufferFactory()
                    .wrap(fallback.getBytes(StandardCharsets.UTF_8));
            return exchange.getResponse().writeWith(Mono.just(buffer));
        }
    }
}