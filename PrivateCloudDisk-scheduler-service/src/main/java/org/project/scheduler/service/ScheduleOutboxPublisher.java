package org.project.scheduler.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.project.scheduler.model.SchedulerModels.OutboxRow;
import org.project.scheduler.repository.SchedulerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 可靠发布 schedule fire；发布成功后标记 SENT，失败按数据库时间指数退避。 */
@Component
public class ScheduleOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(ScheduleOutboxPublisher.class);
    private final OutboxClaimService claimService;
    private final SchedulerMapper mapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public ScheduleOutboxPublisher(
            OutboxClaimService claimService,
            SchedulerMapper mapper,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper
    ) {
        this.claimService = claimService;
        this.mapper = mapper;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 500)
    public void publish() {
        mapper.recoverPublishing();
        for (int index = 0; index < 100; index++) {
            OutboxRow row = claimService.claim();
            if (row == null) {
                return;
            }
            try {
                Map<String, Object> event = objectMapper.readValue(
                        row.payloadJson(), new TypeReference<Map<String, Object>>() { }
                );
                rabbitTemplate.convertAndSend(
                        "pcd.workflow.exchange", "workflow.schedule.fire", event,
                        message -> {
                            message.getMessageProperties().setMessageId(row.eventId());
                            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                            message.getMessageProperties().setContentType("application/json");
                            return message;
                        }
                );
                mapper.markSent(row.eventId());
            } catch (Exception exception) {
                mapper.markFailed(row.eventId());
                log.error("schedule fire Outbox 发布失败 event_id={}", row.eventId(), exception);
            }
        }
    }
}
