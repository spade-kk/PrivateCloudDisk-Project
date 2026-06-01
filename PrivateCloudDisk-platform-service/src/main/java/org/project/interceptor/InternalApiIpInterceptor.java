package org.project.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 内部接口IP访问控制拦截器
 */
public class InternalApiIpInterceptor implements HandlerInterceptor {
    // 允许访问的IP列表（通过配置注入）
    private final List<String> allowedIps;

    public InternalApiIpInterceptor(List<String> allowedIps) {
        this.allowedIps = allowedIps;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取请求的真实IP
        String clientIp = getRealClientIp(request);

        // 检查IP是否在允许列表中
        if (allowedIps.contains(clientIp)) {
            return true; // 允许访问
        }

        // 不允许访问，返回403
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"message\":\"禁止访问：仅内部服务可调用该接口\"}");
        return false;
    }

    /**
     * 获取真实客户端IP（处理代理场景）
     */
    private String getRealClientIp(HttpServletRequest request) {
        // 优先从代理头获取真实IP（适用于有反向代理的场景）
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }

        // 如果以上都没有，直接获取远程地址
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // 处理多代理场景（X-Forwarded-For可能包含多个IP，取第一个）
        if (ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        // 特殊处理IPv6本地回环地址（转换为IPv4的127.0.0.1）
        if ("0:0:0:0:0:0:0:1".equals(ip)) {
            ip = "127.0.0.1";
        }

        return ip;
    }
}
