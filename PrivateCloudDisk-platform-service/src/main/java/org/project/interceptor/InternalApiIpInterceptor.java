package org.project.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 内部接口IP访问控制拦截器
 */
public class InternalApiIpInterceptor implements HandlerInterceptor {
    // 允许访问的IP列表（通过配置注入）
    private final List<String> allowedIps;
    private final String serviceToken;

    public InternalApiIpInterceptor(List<String> allowedIps, String serviceToken) {
        this.allowedIps = allowedIps;
        this.serviceToken = serviceToken == null ? "" : serviceToken;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        /*
         * Sprint 0 安全基线：
         * 原行为 protected path 与真实 /business/internal/** 路径不一致，且信任客户端可伪造
         * 的 X-Forwarded-For。新行为优先验证服务凭证；未配置凭证仅保留 loopback 开发兼容，
         * Docker/生产必须显式注入 PCD_INTERNAL_SERVICE_TOKEN。
         */
        String presentedToken = request.getHeader("X-PCD-Service-Token");
        if (!serviceToken.isBlank()
                && presentedToken != null
                && MessageDigest.isEqual(
                        serviceToken.getBytes(StandardCharsets.UTF_8),
                        presentedToken.getBytes(StandardCharsets.UTF_8))) {
            return true;
        }

        // 不信任代理转发头；内部路径不得经公网 Gateway 暴露。
        String clientIp = request.getRemoteAddr();

        // 检查IP是否在允许列表中
        if (serviceToken.isBlank() && allowedIps.contains(clientIp)) {
            return true; // 允许访问
        }

        // 不允许访问，返回403
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"message\":\"禁止访问：仅内部服务可调用该接口\"}");
        return false;
    }

}
