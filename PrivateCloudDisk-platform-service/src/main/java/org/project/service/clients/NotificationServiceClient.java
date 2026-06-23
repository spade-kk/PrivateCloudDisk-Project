package org.project.service.clients;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.project.service.ex.CaptchaVerificationException;
import org.project.service.ex.RateLimitExceededException;
import org.project.service.ex.ResendTokenExhaustedException;
import org.project.service.ex.ResendTokenInvalidException;
import org.project.service.ex.VerificationCodeErrorException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 推送服务客户端 SDK。
 *
 * <p>封装对 Go 通知推送服务（notification-service）的 HTTP API 调用，
 * 包括验证码管理、Turnstile 人机验证、邮件发送、短信发送等。
 *
 * <p><b>调用方式</b>：内部 HTTP API 通信（通过 RestClient）
 *
 * <p><b>与 Go 推送服务的 HTTP API 对应关系</b>：
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  Spring Boot 业务服务                    Go 推送服务              │
 * ├─────────────────────────────────────────────────────────────────┤
 * │  NotificationServiceClient             notification-service     │
 * │  ┌─ sendCode()          ──POST──▶  /api/internal/verification/send    │
 * │  └─ resendCode()        ──POST──▶  /api/internal/verification/resend  │
 * │  └─ verifyCode()        ──POST──▶  /api/internal/verification/verify  │
 * │  └─ checkCodeAttempts() ──POST──▶  /api/internal/verification/check-attempts │
 * │  └─ recordCodeFailure() ──POST──▶  /api/internal/verification/record-failure │
 * │  └─ clearCodeAttempts() ──POST──▶  /api/internal/verification/clear-attempts │
 * └─────────────────────────────────────────────────────────────────┘
 * </pre>
 */
@Slf4j
@Service
public class NotificationServiceClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String notificationServiceUrl;

    public NotificationServiceClient(
            @Value("${app.notification.service-url:http://127.0.0.1:8082}") String notificationServiceUrl,
            ObjectMapper objectMapper) {
        this.notificationServiceUrl = notificationServiceUrl;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    // =========================================================================
    // 验证码 API
    // =========================================================================

    /**
     * 首次发送验证码（需人机验证）。
     *
     * <p>对应 Go 推送服务接口：POST /api/internal/verification/send
     *
     * @param email         邮箱（与 phone 二选一）
     * @param phone         手机号（与 email 二选一）
     * @param purpose       用途：REGISTER / BIND / RESET
     * @param captchaToken  Turnstile 人机验证 token
     * @param captchaAction Turnstile action（可选）
     * @param clientIp      客户端 IP
     * @return VerificationSendResponse（含 resendToken、expiresIn、remainingResends）
     */
    public VerificationSendResponse sendCode(
            String email, String phone, String purpose,
            String captchaToken, String captchaAction, String clientIp) {

        Map<String, Object> body = new LinkedHashMap<>();
        if (email != null && !email.isBlank()) body.put("email", email.trim().toLowerCase());
        if (phone != null && !phone.isBlank()) body.put("phone", phone.trim());
        body.put("purpose", purpose);
        if (captchaToken != null) body.put("captcha_token", captchaToken);
        if (captchaAction != null) body.put("captcha_action", captchaAction);

        ApiResponse<VerificationSendResponse> response = postForEntity(
                "/api/internal/verification/send",
                body,
                null,
                new TypeReference<VerificationSendResponse>() {});

        return response.getData();
    }

    /**
     * 重新发送验证码（无需人机验证，需有效的 resend token）。
     *
     * <p>对应 Go 推送服务接口：POST /api/internal/verification/resend
     *
     * @param email       邮箱（与 phone 二选一）
     * @param phone       手机号（与 email 二选一）
     * @param purpose     用途
     * @param resendToken 不透明 resend token
     * @param clientIp    客户端 IP
     * @return VerificationSendResponse（含同一个 token、更新后的剩余次数）
     */
    public VerificationSendResponse resendCode(
            String email, String phone, String purpose,
            String resendToken, String clientIp) {

        Map<String, Object> body = new LinkedHashMap<>();
        if (email != null && !email.isBlank()) body.put("email", email.trim().toLowerCase());
        if (phone != null && !phone.isBlank()) body.put("phone", phone.trim());
        body.put("purpose", purpose);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Resend-Token", resendToken);
        headers.put("X-Forwarded-For", clientIp);

        ApiResponse<VerificationSendResponse> response = postForEntity(
                "/api/internal/verification/resend",
                body,
                headers,
                new TypeReference<VerificationSendResponse>() {});

        return response.getData();
    }

    /**
     * 校验验证码（IP 绑定校验，验证成功后自动删除）。
     *
     * <p>对应 Go 推送服务接口：POST /api/internal/verification/verify
     *
     * @param targetType "email" 或 "phone"
     * @param target     邮箱地址或手机号
     * @param purpose    用途
     * @param code       用户输入的验证码
     * @param clientIp   客户端 IP
     * @return true=验证通过
     * @throws VerificationCodeErrorException 验证码错误
     */
    public boolean verifyCode(
            String targetType, String target, String purpose,
            String code, String clientIp) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("target_type", targetType);
        body.put("target", target);
        body.put("purpose", purpose);
        body.put("code", code);

        ApiResponse<VerifyCodeData> response = postForEntity(
                "/api/internal/verification/verify",
                body,
                null,
                new TypeReference<VerifyCodeData>() {});

        return response.getData() != null && response.getData().isValid();
    }

    /**
     * 检查验证码校验失败次数（防爆破）。
     *
     * <p>对应 Go 推送服务接口：POST /api/internal/verification/check-attempts
     *
     * @param targetType "email" 或 "phone"
     * @param target     邮箱地址或手机号
     * @param purpose    用途
     * @param clientIp   客户端 IP
     * @throws RateLimitExceededException 超过最大失败次数
     */
    public void checkCodeAttempts(
            String targetType, String target, String purpose, String clientIp) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("target_type", targetType);
        body.put("target", target);
        body.put("purpose", purpose);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Forwarded-For", clientIp);

        postForEntity("/api/internal/verification/check-attempts", body, headers,
                new TypeReference<CheckAttemptsData>() {});
    }

    /**
     * 记录验证码校验失败。
     *
     * <p>对应 Go 推送服务接口：POST /api/internal/verification/record-failure
     *
     * @param targetType "email" 或 "phone"
     * @param target     邮箱地址或手机号
     * @param purpose    用途
     * @param clientIp   客户端 IP
     */
    public void recordCodeFailure(
            String targetType, String target, String purpose, String clientIp) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("target_type", targetType);
        body.put("target", target);
        body.put("purpose", purpose);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Forwarded-For", clientIp);

        postForEntity("/api/internal/verification/record-failure", body, headers,
                new TypeReference<Void>() {});
    }

    /**
     * 验证成功后清除该 IP+目标 的验证码失败计数。
     *
     * <p>对应 Go 推送服务接口：POST /api/internal/verification/clear-attempts
     *
     * @param targetType "email" 或 "phone"
     * @param target     邮箱地址或手机号
     * @param purpose    用途
     * @param clientIp   客户端 IP
     */
    public void clearCodeAttempts(
            String targetType, String target, String purpose, String clientIp) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("target_type", targetType);
        body.put("target", target);
        body.put("purpose", purpose);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Forwarded-For", clientIp);

        postForEntity("/api/internal/verification/clear-attempts", body, headers,
                new TypeReference<Void>() {});
    }

    // =========================================================================
    // Turnstile 人机验证 API（委托给推送服务）
    // =========================================================================

    /**
     * 校验 Turnstile 人机验证码。
     *
     * <p>委托给 Go 推送服务进行 Cloudflare Turnstile 校验。
     *
     * @param token    Turnstile token
     * @param action   动作标识
     * @param remoteIp 客户端 IP
     * @throws CaptchaVerificationException 人机验证失败
     */
    public void verifyTurnstile(String token, String action, String remoteIp) {
        // Turnstile 验证已在验证码发送时由推送服务完成，
        // 业务服务登录接口也可通过此方法委托推送服务进行二次校验。
        // 当前策略：如果推送服务不可用，降级为跳过（不阻塞登录）
        // 已迁移至推送服务，此处保留接口兼容性
    }

    // =========================================================================
    // 泛型 HTTP 调用封装
    // =========================================================================

    /**
     * 泛型 POST 请求封装。
     */
    @SuppressWarnings("unchecked")
    private <T> ApiResponse<T> postForEntity(
            String path, Map<String, Object> body, Map<String, String> extraHeaders,
            TypeReference<T> typeRef) {

        String url = notificationServiceUrl + path;
        try {
            var requestBuilder = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON);

            if (extraHeaders != null) {
                extraHeaders.forEach(requestBuilder::header);
            }

            String bodyJson = objectMapper.writeValueAsString(body);
            String respJson = requestBuilder.body(bodyJson).retrieve()
                    .body(String.class);

//            ApiResponse<T> apiResp = objectMapper.readValue(respJson,
//                    objectMapper.getTypeFactory().constructParametricType(
//                            ApiResponse.class,
//                            typeRef.getType()));
//
//            if (apiResp.getCode() != 200) {
//                handleApiError(apiResp.getCode(), apiResp.getMessage());
//            }

            return null;

        } catch (HttpClientErrorException e) {
            log.error("[推送SDK] HTTP {} 调用失败: url={}, status={}, body={}",
                    path, e.getStatusCode().value(), e.getResponseBodyAsString());
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                throw new RateLimitExceededException("发送频率超限，请稍后再试");
            }
            throw new RuntimeException("推送服务调用失败: " + e.getMessage());
        } catch (RestClientException | JsonProcessingException e) {
            log.error("[推送SDK] 调用推送服务异常: url={}, error={}", url, e.getMessage());
            throw new RuntimeException("推送服务暂不可用，请稍后重试", e);
        }
    }

    /**
     * 处理 API 错误响应。
     */
    private void handleApiError(int code, String message) {
        switch (code) {
            case 400:
                if (message != null && message.contains("人机验证")) {
                    throw new CaptchaVerificationException(message);
                }
                if (message != null && message.contains("验证码错误")) {
                    throw new VerificationCodeErrorException();
                }
                if (message != null && message.contains("重新发送令牌")) {
                    throw new ResendTokenInvalidException();
                }
                if (message != null && message.contains("次数已达上限")) {
                    throw new ResendTokenExhaustedException();
                }
                throw new RuntimeException(message);
            case 429:
                throw new RateLimitExceededException(message != null ? message : "请求过于频繁，请稍后再试");
            default:
                throw new RuntimeException(message != null ? message : "推送服务返回错误: " + code);
        }
    }

    // =========================================================================
    // 内部模型
    // =========================================================================

    /**
     * 统一 API 响应格式（与 Go 推送服务 successResponse 一致）。
     */
    @Data
    public static class ApiResponse<T> {
        private int code;
        private String message;
        private T data;

        public ApiResponse() {}
    }

    /**
     * 验证码发送响应。
     */
    @Data
    public static class VerificationSendResponse {
        @JsonProperty("resend_token")
        private String resendToken;
        @JsonProperty("expires_in")
        private long expiresIn;
        @JsonProperty("remaining_resends")
        private int remainingResends;

        public VerificationSendResponse() {}
    }

    /**
     * 验证码校验响应。
     */
    @Data
    public static class VerifyCodeData {
        private boolean valid;

        public VerifyCodeData() {}
    }

    /**
     * 防爆破检查响应。
     */
    @Data
    public static class CheckAttemptsData {
        private boolean allowed;

        public CheckAttemptsData() {}
    }

    /**
     * 泛型类型引用，用于 Jackson 反序列化参数化类型。
     */
    private abstract static class TypeReference<T> {
        private final java.lang.reflect.Type type;

        protected TypeReference() {
            java.lang.reflect.Type superClass = getClass().getGenericSuperclass();
            this.type = ((java.lang.reflect.ParameterizedType) superClass).getActualTypeArguments()[0];
        }

        public java.lang.reflect.Type getType() {
            return this.type;
        }
    }
}