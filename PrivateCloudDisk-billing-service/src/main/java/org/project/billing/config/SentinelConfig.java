package org.project.billing.config;

import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.SentinelWebInterceptor;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import lombok.extern.slf4j.Slf4j;
import org.project.billing.common.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sentinel 流量控制配置
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "spring.cloud.sentinel.enabled", havingValue = "true", matchIfMissing = true)
public class SentinelConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        SentinelWebInterceptor interceptor = new SentinelWebInterceptor();
        interceptor.setBlockExceptionHandler((request, response, e) -> {
            log.warn("Sentinel 限流/熔断触发: uri={}", request.getRequestURI());
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(429);
            response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后重试\"}");
        });
        registry.addInterceptor(interceptor).addPathPatterns("/**");
    }
}