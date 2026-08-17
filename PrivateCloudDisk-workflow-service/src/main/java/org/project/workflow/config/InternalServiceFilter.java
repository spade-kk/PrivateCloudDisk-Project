package org.project.workflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/** 内部能力投影接口只接受服务身份，且不会由公网 Gateway 暴露。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalServiceFilter extends OncePerRequestFilter {
    private final WorkflowProperties properties;
    private final ObjectMapper objectMapper;

    public InternalServiceFilter(WorkflowProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        byte[] expected = bytes(properties.internalServiceToken());
        byte[] presented = bytes(request.getHeader("X-PCD-Service-Token"));
        if (expected.length == 0 || !MessageDigest.isEqual(expected, presented)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            objectMapper.writeValue(response.getOutputStream(), Map.of(
                    "code", "AUTH-UNAUTHENTICATED",
                    "message", "内部服务认证失败"
            ));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static byte[] bytes(String value) {
        return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
    }
}
