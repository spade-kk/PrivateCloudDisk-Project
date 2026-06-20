package org.project.billing.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.billing.model.message.QuotaUpdateMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Platform Service 远程调用客户端
 * 用于与 platform-service 交互，实现配额更新等操作
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformServiceClient {

    private final RestTemplate restTemplate;

    @Value("${platform.service.url:http://PrivateCloudDisk-platform-service:8080}")
    private String platformServiceUrl;

    /**
     * 更新用户配额
     * 通过 HTTP 调用 platform-service 的配额更新接口
     */
    public boolean updateUserQuota(String userId, Long storageLimitBytes, Long maxFileSizeBytes) {
        try {
            String url = platformServiceUrl + "/api/internal/quota/update";
            Map<String, Object> request = Map.of(
                    "userId", userId,
                    "storageLimitBytes", storageLimitBytes,
                    "maxFileSizeBytes", maxFileSizeBytes
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            log.info("配额更新请求成功: userId={}, response={}", userId, response.getBody());
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("配额更新请求失败: userId={}", userId, e);
            return false;
        }
    }
}