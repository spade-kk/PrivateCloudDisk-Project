package org.project.security;

import lombok.RequiredArgsConstructor;
import org.project.config.properties.ApiRateLimitProperties;
import org.project.model.dto.LoginRequest;
import org.project.service.ex.RateLimitExceededException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApiAbuseProtectionService {
    private final RedisRateLimiterService rateLimiterService;
    private final ApiRateLimitProperties properties;

    public void checkLoginStart(LoginRequest request, String clientIp) {
        String identity = loginIdentity(request);
        // 登录入口洪峰限制在 Gateway；应用层只保留登录失败锁定这种业务语义限制。
        if (rateLimiterService.current(key("auth:login:failure:ip", clientIp))
                >= properties.getLoginFailureIp().getLimit()) {
            throw new RateLimitExceededException("该网络环境登录失败次数过多，请稍后再试");
        }
        if (rateLimiterService.current(key("auth:login:failure:identity", identity))
                >= properties.getLoginFailureIdentity().getLimit()) {
            throw new RateLimitExceededException("该账号登录失败次数过多，请稍后再试");
        }
    }

    public void recordLoginFailure(LoginRequest request, String clientIp) {
        String identity = loginIdentity(request);
        rateLimiterService.increment(key("auth:login:failure:ip", clientIp),
                properties.getLoginFailureIp().getWindow());
        rateLimiterService.increment(key("auth:login:failure:identity", identity),
                properties.getLoginFailureIdentity().getWindow());
    }

    public void recordLoginSuccess(LoginRequest request, String clientIp) {
        String identity = loginIdentity(request);
        rateLimiterService.delete(key("auth:login:failure:ip", clientIp));
        rateLimiterService.delete(key("auth:login:failure:identity", identity));
    }

    public void checkRegisterStart(String phoneNumber, String clientIp) {
        // 注册 IP 入口频率由 Gateway 处理；应用层保留手机号维度，防止重复打同一业务资源。
        rateLimiterService.check(key("auth:register:phone", phoneNumber),
                properties.getRegisterPhone(),
                "该手机号注册请求过于频繁，请稍后再试");
    }

    public void checkUploadSessionCreate(String userId, String nodeId, String clientIp) {
        // 用户级上传会话洪峰限制在 Gateway；应用层保留同用户同目录维度。
        rateLimiterService.check(key("uploads:create:node", userId, nodeId),
                properties.getUploadSessionNode(),
                "当前目录创建上传会话过于频繁，请稍后再试");
    }

    private String loginIdentity(LoginRequest request) {
        if (StringUtils.hasText(request.getAccount())) {
            return "account:" + request.getAccount();
        }
        return "phone:" + request.getPhone_number();
    }

    private String key(String namespace, String... parts) {
        String joined = Arrays.stream(parts)
                .map(part -> part == null ? "null" : part)
                .collect(Collectors.joining("|"));
        return "pcd:rate-limit:" + namespace + ":" + sha256(joined);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
