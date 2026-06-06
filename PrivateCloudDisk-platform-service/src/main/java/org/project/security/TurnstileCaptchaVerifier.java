package org.project.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.project.config.properties.CaptchaProperties;
import org.project.service.ex.CaptchaVerificationException;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class TurnstileCaptchaVerifier implements CaptchaVerifier {
    private final CaptchaProperties properties;
    private final RestClient restClient;

    public TurnstileCaptchaVerifier(CaptchaProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = (int) Math.min(Integer.MAX_VALUE, properties.getTimeout().toMillis());
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public void verify(String token, String expectedAction, String remoteIp) {
        if (!properties.isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(properties.getSecretKey())) {
            throw new CaptchaVerificationException("人机验证码服务未配置密钥");
        }
        if (!StringUtils.hasText(token)) {
            throw new CaptchaVerificationException("请先完成人机验证");
        }

        TurnstileSiteVerifyResponse response = requestSiteVerify(token, remoteIp);
        if (response == null || !Boolean.TRUE.equals(response.getSuccess())) {
            List<String> errorCodes = response == null ? Collections.emptyList() : response.getErrorCodes();
            log.warn("Turnstile rejected captcha action={}, remoteIp={}, errors={}", expectedAction, remoteIp, errorCodes);
            throw new CaptchaVerificationException("人机验证失败，请刷新后重试");
        }
        if (properties.isValidateAction()
                && StringUtils.hasText(expectedAction)
                && !expectedAction.equals(response.getAction())) {
            log.warn("Turnstile action mismatch expected={}, actual={}", expectedAction, response.getAction());
            throw new CaptchaVerificationException("人机验证码动作不匹配");
        }
        if (StringUtils.hasText(properties.getExpectedHostname())
                && !properties.getExpectedHostname().equalsIgnoreCase(response.getHostname())) {
            log.warn("Turnstile hostname mismatch expected={}, actual={}",
                    properties.getExpectedHostname(), response.getHostname());
            throw new CaptchaVerificationException("人机验证码来源不匹配");
        }
    }

    private TurnstileSiteVerifyResponse requestSiteVerify(String token, String remoteIp) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("secret", properties.getSecretKey());
        body.put("response", token);
        body.put("idempotency_key", UUID.randomUUID().toString());
        if (StringUtils.hasText(remoteIp)) {
            body.put("remoteip", remoteIp);
        }

        try {
            return restClient.post()
                    .uri(properties.getSiteverifyUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(TurnstileSiteVerifyResponse.class);
        } catch (RestClientException e) {
            log.warn("Turnstile siteverify request failed", e);
            throw new CaptchaVerificationException("人机验证码服务暂不可用");
        }
    }

    @Data
    public static class TurnstileSiteVerifyResponse {
        private Boolean success;
        @JsonProperty("challenge_ts")
        private String challengeTs;
        private String hostname;
        private String action;
        private String cdata;
        @JsonProperty("error-codes")
        private List<String> errorCodes = Collections.emptyList();
    }
}
