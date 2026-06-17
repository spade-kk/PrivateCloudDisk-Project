package org.project.config;

import org.project.interceptor.LoginInterceptor;
import org.project.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

/**
 * 拦截器注册配置。
 * <p>
 * 设备指纹验证已移至 Gateway 网关层统一处理，业务服务不再需要设备指纹拦截器。
 */
public class LoginInterceptorConfigure implements WebMvcConfigurer {
    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // JWT 登录认证拦截器
        HandlerInterceptor loginInterceptor = new LoginInterceptor(jwtUtil);

        List<String> patterns = new ArrayList<>();
        patterns.add("/api/user/login");
        patterns.add("/api/user/register");
        patterns.add("/internal/storage/**");

        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(patterns)
                .order(1);
    }
}