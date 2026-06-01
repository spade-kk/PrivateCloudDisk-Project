package org.project.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

public class CorsConfigure {
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        // 允许前端域名访问（生产环境需指定具体域名，如"http://localhost:8081"）
        corsConfiguration.addAllowedOriginPattern("http://127.0.0.1:*");
        corsConfiguration.addAllowedOriginPattern("http://192.168.1.4:*");
        // 允许携带Cookie
        corsConfiguration.setAllowCredentials(true);
        // 允许所有请求方法（GET、POST、PUT等）
        corsConfiguration.addAllowedMethod("*");
        // 允许所有请求头
        corsConfiguration.addAllowedHeader("*");
        // 允许暴露的响应头（如token）
        corsConfiguration.addExposedHeader("Authorization");
        // 2. 配置URL映射
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 对所有接口生效
        source.registerCorsConfiguration("/**", corsConfiguration);
        // 3. 返回过滤器
        return new CorsFilter(source);
    }
}