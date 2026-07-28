package org.project.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ClientIpUtil {
    private ClientIpUtil() {
    }

    public static String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            log.info("客户端真实IP解析 来自: X-Forwarded-For: {}", forwardedFor.split(",")[0].trim());
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            log.info("客户端真实IP解析 来自: X-Real-IP: {}", realIp.trim());
            return realIp.trim();
        }
        String cfConnectingIp = request.getHeader("CF-Connecting-IP");
        if (StringUtils.hasText(cfConnectingIp)) {
            log.info("客户端真实IP解析 来自: CF-Connecting-IP: {}", cfConnectingIp.trim());
            return cfConnectingIp.trim();
        }
        log.info("客户端真实IP解析 直接解析: {}", request.getRemoteAddr());
        return request.getRemoteAddr();
    }
}
