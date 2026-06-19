package org.project.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.util.JwtUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminJwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    private static final String ADMIN_TOKEN_PREFIX = "admin:";
    private static final String BEARER_PREFIX = "Bearer ";

    private static final List<String> WHITE_LIST = List.of(
            "/business/admin/auth/login"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestURI = request.getRequestURI();

        if (WHITE_LIST.contains(requestURI)) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"缺少管理员认证令牌\"}");
            return false;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        String tokenValue = jwtUtil.verifyAccessToken(token);

        if (tokenValue == null || !tokenValue.startsWith(ADMIN_TOKEN_PREFIX)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"管理员令牌无效或已过期\"}");
            return false;
        }

        String adminId = tokenValue.substring(ADMIN_TOKEN_PREFIX.length());
        if (adminId == null || adminId.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"管理员令牌无效\"}");
            return false;
        }

        request.setAttribute("X-Admin-Id", adminId);
        return true;
    }
}