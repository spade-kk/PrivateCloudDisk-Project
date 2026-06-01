package org.project.privateclouddiskgatewayservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 路由配置（Java DSL 方式）
 * <p>
 * 注意：如果同时使用 YAML 和 Java DSL 定义路由，
 * YAML 配置会覆盖 Java DSL 的同名路由。
 * 建议统一使用一种方式。
 */
@Slf4j
@Configuration
public class RouteConfig {
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // ============================================
                // 业务服务路由
                // ============================================
                .route("business-service", r -> r
                        .path("/api/v1/business/**")
                        .filters(f -> f
                                .stripPrefix(2)                      // 去掉 /api /v1
                                .addResponseHeader("X-Gateway-Route", "business-service")
                                .retry(3)                            // 重试 3 次
                        )
                        .uri("http://localhost:8081")
                )
                // ============================================
                // 文件服务路由 (注意：文件上传/下载可能需要特殊配置)
                // ============================================
                .route("file-service", r -> r
                        .path("/api/v1/files/**")
                        .filters(f -> f
                                .stripPrefix(2)
                                .addResponseHeader("X-Gateway-Route", "file-service")
                                // 文件上传/下载超时配置
                                .metadata("response-timeout", 60000)
                                .metadata("connect-timeout", 30000)
                        )
                        .uri("http://localhost:8000")
                )
                .build();
    }
}
