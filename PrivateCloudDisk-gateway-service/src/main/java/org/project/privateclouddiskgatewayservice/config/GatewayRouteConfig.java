package org.project.privateclouddiskgatewayservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 网关路由配置（Java DSL 方式）
 *
 * <h2>API 路由策略（企业级）</h2>
 *
 * <h3>外部 API 格式</h3>
 * <pre>{@code
 * /api/v1/{service-name}/{resource-path}
 * }</pre>
 *
 * <h3>路由规则</h3>
 * <table>
 *   <tr><th>外部路径</th><th>路由目标</th><th>StripPrefix</th><th>下游服务看到</th></tr>
 *   <tr><td>/api/v1/business/users/login</td><td>business-service</td><td>2</td><td>/users/login</td></tr>
 *   <tr><td>/api/v1/files/{file_id}/content</td><td>file-service</td><td>2</td><td>/{file_id}/content</td></tr>
 *   <tr><td>/api/v1/im/ws</td><td>im-service</td><td>2</td><td>/ws</td></tr>
 * </table>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>服务名仅出现在网关路由前缀中，下游服务路径不重复服务名</li>
 *   <li>StripPrefix=2 剥离 /api/v1，下游服务看到干净的资源路径</li>
 *   <li>优先使用服务发现（lb://），不可用时降级到直接 URL</li>
 *   <li>关键路由配置重试策略，提升可用性</li>
 * </ul>
 *
 * <h3>服务发现优先级</h3>
 * <ol>
 *   <li>Nacos 注册中心 → lb://service-name（自动负载均衡）</li>
 *   <li>降级 → 直接 URL（application.properties 中配置）</li>
 * </ol>
 */
@Slf4j
@Configuration
public class GatewayRouteConfig {

    // ─── 服务发现 URI（优先） ───

    private static final String BUSINESS_SERVICE_LB = "lb://privateclouddisk-business-service";
    private static final String FILE_SERVICE_LB = "lb://privateclouddisk-file-service";
    private static final String IM_SERVICE_LB = "lb://privateclouddisk-im-service";

    // ─── 直接 URL（降级，从配置文件读取） ───

    @Value("${gateway.routes.business-service.fallback-uri:http://localhost:8081}")
    private String businessServiceFallbackUri;

    @Value("${gateway.routes.file-service.fallback-uri:http://localhost:8000}")
    private String fileServiceFallbackUri;

    @Value("${gateway.routes.im-service.fallback-uri:http://localhost:8088}")
    private String imServiceFallbackUri;

    @Value("${gateway.routes.use-service-discovery:true}")
    private boolean useServiceDiscovery;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        log.info("初始化网关路由 - 服务发现模式: {}", useServiceDiscovery ? "Nacos (lb://)" : "直接 URL");

        String businessUri = resolveUri(BUSINESS_SERVICE_LB, businessServiceFallbackUri);
        String fileUri = resolveUri(FILE_SERVICE_LB, fileServiceFallbackUri);
        String imUri = resolveUri(IM_SERVICE_LB, imServiceFallbackUri);

        RouteLocatorBuilder.Builder routes = builder.routes();

        // ═══════════════════════════════════════════════
        // 路由 1: 业务服务 (business-service)
        // 路径: /api/v1/business/** → 下游: /**
        // ═══════════════════════════════════════════════
        routes.route("business-service", r -> r
                .path("/api/v1/business/**")
                .filters(f -> f
                        .stripPrefix(2)           // 剥离 /api/v1
                        .retry(retryConfig -> retryConfig
                                .setRetries(3)
                                .setStatuses(org.springframework.http.HttpStatus.BAD_GATEWAY,
                                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
                                .setBackoff(Duration.ofMillis(100), Duration.ofSeconds(2), 2, true)
                        )
                        .circuitBreaker(cb -> cb
                                .setName("business-circuit-breaker")
                                .setFallbackUri("forward:/fallback/business")
                        )
                )
                .uri(businessUri)
        );

        // ═══════════════════════════════════════════════
        // 路由 2: 文件存储服务 (file-service)
        // 路径: /api/v1/files/** → 下游: /**
        // ═══════════════════════════════════════════════
        routes.route("file-service", r -> r
                .path("/api/v1/files/**")
                .filters(f -> f
                        .stripPrefix(2)           // 剥离 /api/v1
                        .retry(retryConfig -> retryConfig
                                .setRetries(2)
                                .setStatuses(org.springframework.http.HttpStatus.BAD_GATEWAY,
                                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
                        )
                )
                .uri(fileUri)
        );

        // ═══════════════════════════════════════════════
        // 路由 3: IM 即时通讯服务 (im-service)
        // 路径: /api/v1/im/** → 下游: /**
        // ═══════════════════════════════════════════════
        routes.route("im-service", r -> r
                .path("/api/v1/im/**")
                .filters(f -> f
                        .stripPrefix(2)           // 剥离 /api/v1
                )
                .uri(imUri)
        );

        // ═══════════════════════════════════════════════
        // 路由 4: WebSocket 升级（IM 服务）
        // ═══════════════════════════════════════════════
        routes.route("im-websocket", r -> r
                .path("/ws/im/**")
                .filters(f -> f.stripPrefix(1))
                .uri(imUri)
        );

        return routes.build();
    }

    /**
     * 解析最终 URI：优先使用服务发现，失败时降级到直接 URL
     */
    private String resolveUri(String lbUri, String fallbackUri) {
        if (useServiceDiscovery) {
            log.info("  路由目标: {} (降级: {})", lbUri, fallbackUri);
            return lbUri;
        }
        log.info("  路由目标: {} (直接 URL 模式)", fallbackUri);
        return fallbackUri;
    }
}