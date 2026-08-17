package org.project.config;

import org.project.config.properties.InternalApiProperties;
import org.project.interceptor.InternalApiIpInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 内部接口访问控制配置
 */
@Configuration
@EnableConfigurationProperties(InternalApiProperties.class) // 启用配置属性
public class InternalApiSecurityConfigure implements WebMvcConfigurer {

    // 注入IP配置
    private final InternalApiProperties internalApiProperties;

    public InternalApiSecurityConfigure(InternalApiProperties internalApiProperties) {
        this.internalApiProperties = internalApiProperties;
    }

    /**
     * 注册IP拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InternalApiIpInterceptor(
                        internalApiProperties.getAllowedIps(),
                        internalApiProperties.getServiceToken()))
                .addPathPatterns(internalApiProperties.getProtectedPaths()) // 拦截需要保护的内部接口路径
                .excludePathPatterns(internalApiProperties.getExcludePaths()); // 排除不需要拦截的路径（可选）
    }
}
