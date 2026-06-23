package org.project.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.config.RabbitMQConifgure;
import org.project.model.dto.message.UploadSessionDeletedEvent;
import org.project.service.UserQuotaService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 上传会话已删除事件消费者
 * <p>
 * 监听文件存储服务完成物理文件删除后发布的事件，释放配额：
 * <pre>
 *   released -= fileSize
 * </pre>
 * <p>
 * 幂等保证：通过 Redis 记录事件ID，防止重复消费。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadSessionDeletedConsumer {

    private static final String EVENT_IDEMPOTENT_PREFIX = "quota:event:session_deleted:";
    private static final long IDEMPOTENT_TTL_HOURS = 72;

    private final UserQuotaService userQuotaService;
    private final RedisTemplate<String, String> redisTemplate;

    @RabbitListener(queues = RabbitMQConifgure.QUEUE_UPLOADS_SESSION_DELETED,
            containerFactory = "manualRabbitListenerContainerFactory")
    public void handleUploadSessionDeleted(UploadSessionDeletedEvent event,
                                            Channel channel,
                                            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("收到上传会话已删除事件: eventId={}, uploadsSessionId={}, userId={}, fileSize={}",
                event.getEventId(), event.getUploadsSessionId(), event.getUserId(), event.getFileSize());

        try {
            // 幂等检查
            String idempotentKey = EVENT_IDEMPOTENT_PREFIX + event.getEventId();
            Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(idempotentKey, "1", IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS);
            if (Boolean.FALSE.equals(success)) {
                log.warn("上传会话已删除事件已处理（幂等跳过）: eventId={}", event.getEventId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            userQuotaService.rollbackQuota(event.getUserId(), event.getFileSize());

            log.info("上传会话已删除事件处理完成（配额已释放）: eventId={}, uploadsSessionId={}",
                    event.getEventId(), event.getUploadsSessionId());
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("上传会话已删除事件处理失败: eventId={}, error={}", event.getEventId(), e.getMessage(), e);
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ioException) {
                log.error("basicNack 失败: eventId={}", event.getEventId(), ioException);
            }
        }
    }
}