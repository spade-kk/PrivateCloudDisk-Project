package org.project.privateclouddiskgatewayservice.sentinel;

import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.gateway.sc.exception.SentinelGatewayBlockExceptionHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.route.RouteDefinitionRouteLocator;
import com.alibaba.csp.sentinel.init.InitExecutor;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.result.view.ViewResolver;

import java.util.Collections;
import java.util.List;

/**
 * Sentinel 网关层配置。
 * <p>
 * 集成 Sentinel 的 Spring Cloud Gateway 适配器，提供系统级保护：
 * <ul>
 *   <li>网关流控规则（QPS 限流）</li>
 *   <li>熔断降级规则（慢调用/异常比例/异常数）</li>
 *   <li>热点参数限流（针对文件ID、用户ID等热点）</li>
 *   <li>系统自适应保护（LOAD/CPU/RT/线程数）</li>
 *   <li>授权规则（黑白名单）</li>
 * </ul>
 *
 * <p>
 * <b>与 GatewayRateLimitFilter 的职责划分：</b>
 * <table>
 *   <tr><th>组件</th><th>职责</th><th>触发条件</th></tr>
 *   <tr><td>GatewayRateLimitFilter</td><td>业务策略限流</td><td>基于路径/方法/IP/用户/设备指纹的固定窗口</td></tr>
 *   <tr><td>SentinelGatewayFilter</td><td>系统保护限流</td><td>全局 QPS/RT/LOAD/异常比例，热参数，熔断</td></tr>
 * </table>
 */
@Slf4j
@Configuration
public class SentinelGatewayConfig {

    private final List<ViewResolver> viewResolvers;
    private final ServerCodecConfigurer serverCodecConfigurer;

    public SentinelGatewayConfig(
            ObjectProvider<List<ViewResolver>> viewResolversProvider,
            ServerCodecConfigurer serverCodecConfigurer) {
        this.viewResolvers = viewResolversProvider.getIfAvailable(Collections::emptyList);
        this.serverCodecConfigurer = serverCodecConfigurer;
    }

    /**
     * Sentinel 网关过滤器 Bean。
     * order 设为 -1，在业务过滤器（如 AuthGlobalFilter order=0）之前执行，
     * 确保系统级保护先于业务逻辑。
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public GlobalFilter sentinelGatewayFilter() {
        log.info("Sentinel Gateway Filter registered (order=-1, system-level protection)");
        return new SentinelGatewayFilter();
    }

    /**
     * Sentinel 网关 Block 异常处理器。
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SentinelGatewayBlockExceptionHandler sentinelGatewayBlockExceptionHandler() {
        return new SentinelGatewayBlockExceptionHandler(viewResolvers, serverCodecConfigurer);
    }

    /**
     * 初始化 Sentinel 规则加载器。
     */
    @Bean
    public SentinelGatewayRulesLoader sentinelGatewayRulesLoader() {
        return new SentinelGatewayRulesLoader();
    }
}