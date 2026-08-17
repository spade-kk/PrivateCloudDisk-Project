package org.project.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.config.RabbitMQConifgure;
import org.project.context.SpaceContextHolder;
import org.project.model.dto.message.FileAvailableEvent;
import org.project.service.UserQuotaService;
import org.project.service.RecentAccessService;
import org.project.service.SpacePermissionService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 文件可获得事件消费者
 * <p>
 * 监听文件完成合并+扫毒后发布的可获得事件：
 * <ol>
 *   <li>提交配额：released -= fileSize, used += fileSize, file_count += 1</li>
 *   <li>记录最近上传</li>
 * </ol>
 * <p>
 * 幂等保证：通过 Redis 记录事件ID，防止重复消费。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileAvailableConsumer {

    private static final String EVENT_IDEMPOTENT_PREFIX = "quota:event:file_available:";
    private static final long IDEMPOTENT_TTL_HOURS = 72;

    private final UserQuotaService userQuotaService;
    private final RecentAccessService recentAccessService;
    private final SpacePermissionService spacePermissionService;
    private final RedisTemplate<String, String> redisTemplate;

    @RabbitListener(queues = RabbitMQConifgure.QUEUE_FILE_AVAILABLE,
            containerFactory = "manualRabbitListenerContainerFactory")
    public void handleFileAvailable(FileAvailableEvent event,
                                     Channel channel,
                                     @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("收到文件可获得事件: eventId={}, spaceId={}, fileId={}, fileName={}, fileSize={}, userId={}",
                event.getEventId(), event.getSpaceId(), event.getFileId(), event.getFileName(),
                event.getFileSize(), event.getUserId());

        try {
            // 幂等检查
            String idempotentKey = EVENT_IDEMPOTENT_PREFIX + event.getEventId();
            Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(idempotentKey, "1", IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS);
            if (Boolean.FALSE.equals(success)) {
                log.warn("文件可获得事件已处理（幂等跳过）: eventId={}", event.getEventId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            UUID userId = UUID.fromString(event.getUserId());
            /*
             * 空间管理能力全量集成（需求五-9）：
             * 原行为 MQ 消费线程没有 HTTP 请求上下文，最近访问只能写入用户总表；
             * 新行为从事件恢复空间上下文，原配额提交和最近访问业务调用保持不变。
             */
            SpaceContextHolder.set(spacePermissionService.resolveContext(userId, event.getSpaceId()));
            try {
                userQuotaService.commitQuota(userId, event.getFileSize());

                // 记录最近上传
                recentAccessService.recordAccess(
                        userId,
                        event.getFileId(),
                        "file",
                        "upload",
                        event.getFileName(),
                        event.getFileSize(),
                        event.getFileType()
                );
            } finally {
                SpaceContextHolder.clear();
            }

            log.info("文件可获得事件处理完成（配额已提交 + 最近上传已记录）: eventId={}, fileId={}", event.getEventId(), event.getFileId());
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("文件可获得事件处理失败: eventId={}, error={}", event.getEventId(), e.getMessage(), e);
            /*
             * 文件生命周期可靠性修复：
             * 原行为在业务事务执行前写入 Redis 幂等键，后续数据库异常时该键仍保留，
             * 消息重试会被直接 ACK，导致文件永久无法提交配额/最近访问。新行为失败时
             * 释放本次占位，使同一稳定 eventId 可以安全重试。长期方案仍是数据库 Inbox。
             */
            redisTemplate.delete(EVENT_IDEMPOTENT_PREFIX + event.getEventId());
            try {
                // 不重新入队，交给死信队列
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ioException) {
                log.error("basicNack 失败: eventId={}", event.getEventId(), ioException);
            }
        }
    }
}
