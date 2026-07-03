package org.project.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.config.RabbitMQConifgure;
import org.project.model.dto.message.FileDownloadedEvent;
import org.project.service.RecentAccessService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 文件下载完成事件消费者
 * <p>
 * 监听存储服务发布的下拉完成事件，记录"最近下载"。
 * <p>
 * 幂等保证：通过 Redis 记录事件ID，防止重复消费。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileDownloadedConsumer {

    private static final String EVENT_IDEMPOTENT_PREFIX = "recent:event:file_downloaded:";
    private static final long IDEMPOTENT_TTL_HOURS = 72;

    private final RecentAccessService recentAccessService;
    private final RedisTemplate<String, String> redisTemplate;

    @RabbitListener(queues = RabbitMQConifgure.QUEUE_FILE_DOWNLOADED,
            containerFactory = "manualRabbitListenerContainerFactory")
    public void handleFileDownloaded(FileDownloadedEvent event,
                                      Channel channel,
                                      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("收到文件下载完成事件: eventId={}, fileId={}, fileName={}, userId={}",
                event.getEventId(), event.getFileId(), event.getFileName(), event.getUserId());

        try {
            // 幂等检查
            String idempotentKey = EVENT_IDEMPOTENT_PREFIX + event.getEventId();
            Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(idempotentKey, "1", IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS);
            if (Boolean.FALSE.equals(success)) {
                log.warn("文件下载事件已处理（幂等跳过）: eventId={}", event.getEventId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            UUID userId = UUID.fromString(event.getUserId());
            recentAccessService.recordAccess(
                    userId,
                    event.getFileId(),
                    "file",
                    "download",
                    event.getFileName(),
                    event.getFileSize(),
                    event.getFileType()
            );

            log.info("文件下载事件处理完成（已记录最近下载）: eventId={}, fileId={}",
                    event.getEventId(), event.getFileId());
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("文件下载事件处理失败: eventId={}, error={}", event.getEventId(), e.getMessage(), e);
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ioException) {
                log.error("basicNack 失败: eventId={}", event.getEventId(), ioException);
            }
        }
    }
}