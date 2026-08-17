package org.project.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.config.RabbitMQConifgure;
import org.project.context.SpaceContextHolder;
import org.project.model.dto.message.FileMergeFailedEvent;
import org.project.service.SpacePermissionService;
import org.project.service.UserQuotaService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 文件合并失败事件消费者
 * <p>
 * 监听文件分块合并失败事件，回滚配额：
 * <pre>
 *   released -= fileSize
 * </pre>
 * <p>
 * 幂等保证：通过 Redis 记录事件ID，防止重复消费。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileMergeFailedConsumer {

    private static final String EVENT_IDEMPOTENT_PREFIX = "quota:event:merge_failed:";
    private static final long IDEMPOTENT_TTL_HOURS = 72;

    private final UserQuotaService userQuotaService;
    private final SpacePermissionService spacePermissionService;
    private final RedisTemplate<String, String> redisTemplate;

    @RabbitListener(queues = RabbitMQConifgure.QUEUE_FILE_MERGE_FAILED,
            containerFactory = "manualRabbitListenerContainerFactory")
    public void handleFileMergeFailed(FileMergeFailedEvent event,
                                       Channel channel,
                                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("收到文件合并失败事件: eventId={}, spaceId={}, fileId={}, fileName={}, fileSize={}, reason={}",
                event.getEventId(), event.getSpaceId(), event.getFileId(), event.getFileName(),
                event.getFileSize(), event.getFailReason());

        try {
            // 幂等检查
            String idempotentKey = EVENT_IDEMPOTENT_PREFIX + event.getEventId();
            Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(idempotentKey, "1", IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS);
            if (Boolean.FALSE.equals(success)) {
                log.warn("文件合并失败事件已处理（幂等跳过）: eventId={}", event.getEventId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            UUID userId = UUID.fromString(event.getUserId());
            /*
             * 空间管理能力全量集成（需求五-9/10）：
             * 原失败事件只按 userId 回滚个人配额；新事件恢复 spaceId 后复用同一配额服务。
             */
            SpaceContextHolder.set(spacePermissionService.resolveContext(userId, event.getSpaceId()));
            try {
                userQuotaService.rollbackQuota(userId, event.getFileSize());
            } finally {
                SpaceContextHolder.clear();
            }

            log.info("文件合并失败事件处理完成（配额已回滚）: eventId={}, fileId={}, reason={}",
                    event.getEventId(), event.getFileId(), event.getFailReason());
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("文件合并失败事件处理失败: eventId={}, error={}", event.getEventId(), e.getMessage(), e);
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ioException) {
                log.error("basicNack 失败: eventId={}", event.getEventId(), ioException);
            }
        }
    }
}
