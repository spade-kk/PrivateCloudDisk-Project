package org.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.config.properties.SmsProperties;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 短信发送服务（通用HTTP实现）
 *
 * <p>设计说明：
 * <ul>
 *   <li>使用Java标准库的HttpURLConnection，避免引入重量级HTTP客户端依赖</li>
 *   <li>供应商通过配置项 {@link SmsProperties#getBaseUrl()} 和 {@link SmsProperties#getApiKey()} 配置</li>
 *   <li>开发环境可将 {@code enabled=false}，仅打印日志不发送</li>
 *   <li>失败时抛出 {@link RuntimeException}，由消费者捕获并标记为失败/进入DLQ</li>
 * </ul>
 *
 * <p>如未来切换到阿里云、腾讯云等特定供应商，只需新增一个实现类替换此类即可。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsService {

    private final SmsProperties smsProperties;
    private final ObjectMapper objectMapper;

    /**
     * 发送欢迎短信
     */
    public void sendWelcomeSms(String phone, String userName) {
        Map<String, String> params = new HashMap<>();
        params.put("name", userName);
        sendSms(phone, smsProperties.getTemplate().getWelcome(), params, "welcome");
    }

    /**
     * 发送验证码短信
     */
    public void sendVerificationSms(String phone, String code, int expireSeconds) {
        Map<String, String> params = new HashMap<>();
        params.put("code", code);
        params.put("expire", String.valueOf(expireSeconds / 60));
        sendSms(phone, smsProperties.getTemplate().getVerify(), params, "verification");
    }

    /**
     * 通用发送方法
     *
     * @param phone       手机号
     * @param templateId  模板ID
     * @param params      模板参数
     * @param typeLabel   类型标签
     */
    private void sendSms(String phone, String templateId, Map<String, String> params, String typeLabel) {
        if (!smsProperties.isEnabled()) {
            log.info("[短信服务-{}] sms.enabled=false，开发模式仅打印日志. phone={}, template={}, params={}",
                    typeLabel, maskPhone(phone), templateId, params);
            return;
        }

        String baseUrl = smsProperties.getBaseUrl();
        String apiKey = smsProperties.getApiKey();
        String signName = smsProperties.getSignName();

        log.info("[短信服务-{}] 开始发送. phone={}, template={}", typeLabel, maskPhone(phone), templateId);

        HttpURLConnection conn = null;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("phone", phone);
            payload.put("template_id", templateId);
            payload.put("sign_name", signName);
            payload.put("params", params);

            conn = (HttpURLConnection) new URL(baseUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] body = objectMapper.writeValueAsBytes(payload);
                os.write(body);
                os.flush();
            }

            int status = conn.getResponseCode();
            if (status >= 200 && status < 300) {
                log.info("[短信服务-{}] 发送成功. phone={}, status={}", typeLabel, maskPhone(phone), status);
            } else {
                // HTTP非2xx响应码，视为失败
                String errorMsg = "HTTP " + status;
                log.error("[短信服务-{}] 发送失败（HTTP错误）. phone={}, status={}", typeLabel, maskPhone(phone), status);
                throw new RuntimeException("Sms provider returned HTTP " + status + ": " + errorMsg);
            }
        } catch (RuntimeException e) {
            throw e; // 上面throw的直接向上抛出
        } catch (Exception e) {
            log.error("[短信服务-{}] 发送异常. phone={}, error={}", typeLabel, maskPhone(phone), e.getMessage(), e);
            throw new RuntimeException("Sms send failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** 手机号脱敏显示：138****8000 */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
