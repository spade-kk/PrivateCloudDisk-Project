package org.project.plugin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.project.plugin.config.PluginProperties;
import org.project.plugin.model.PluginOutboxRow;
import org.project.plugin.repository.PluginManagementMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** 将插件能力可靠投影至 Workflow Capability Hub，失败由数据库时间指数退避。 */
@Component
@RequiredArgsConstructor
public class PluginCapabilityProjectionPublisher {
    private static final Logger log = LoggerFactory.getLogger(PluginCapabilityProjectionPublisher.class);
    private final PluginOutboxClaimService claimService;
    private final PluginManagementMapper mapper;
    private final RestClient.Builder restClientBuilder;
    private final PluginProperties properties;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 1000)
    public void publish() {
        mapper.recoverOutboxPublishing();
        for (int index = 0; index < 100; index++) {
            PluginOutboxRow row = claimService.claim();
            if (row == null) {
                return;
            }
            try {
                Map<String, Object> projection = objectMapper.readValue(
                        row.payloadJson(), new TypeReference<Map<String, Object>>() { }
                );
                restClientBuilder.clone().baseUrl(properties.workflowUrl()).build()
                        .post()
                        .uri("/internal/v1/capabilities/projections")
                        .body(projection)
                        .retrieve()
                        .toBodilessEntity();
                mapper.markOutboxSent(row.eventId());
            } catch (Exception exception) {
                mapper.markOutboxFailed(row.eventId());
                log.error("Capability Hub 投影失败 event_id={}", row.eventId(), exception);
            }
        }
    }
}
