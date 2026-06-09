package org.project.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.config.RabbitMQConifgure;
import org.project.event.AvatarReviewEvent;
import org.project.model.entity.NotificationSendLogEntity;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 头像审核消费者
 * <p>消费头像上传事件，执行以下操作：
 * <ol>
 *   <li>文件格式校验（是否为允许的图片格式）</li>
 *   <li>文件大小检查</li>
 *   <li>图片压缩（生成标准尺寸的缩略图）</li>
 *   <li>未来可扩展：病毒扫描、内容审核</li>
 * </ol>
 *
 * <p>注意：此消费者不通过 NotificationSendLogRepository 做幂等，
 * 因为头像审核处理的是文件（不是通知）。使用 eventId + userId 的维度来做去重。
 * 为简化，此处使用一个简易的内存去重（实际生产中可用数据库或Redis）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvatarReviewConsumer {

    // 支持的图片格式
    private static final String[] ALLOWED_FORMATS = {"jpg", "jpeg", "png", "gif", "webp"};
    // 压缩后目标尺寸（200x200）
    private static final int TARGET_SIZE = 200;
    // 最大文件大小（5MB）
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    @RabbitListener(
            containerFactory = "manualRabbitListenerContainerFactory",
            queues = RabbitMQConifgure.QUEUE_AVATAR_REVIEW
    )
    public void consume(AvatarReviewEvent event,
                        Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("[头像审核] 收到事件. eventId={}, userId={}, file={}, size={}",
                event.getEventId(), event.getUserId(), event.getAvatarPath(), event.getFileSize());

        try {
            // 步骤1：参数校验
            if (event.getAvatarPath() == null || event.getAvatarPath().isEmpty()) {
                log.warn("[头像审核] 文件路径为空，跳过. eventId={}", event.getEventId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            Path filePath = Paths.get(event.getAvatarPath());
            if (!Files.exists(filePath)) {
                log.warn("[头像审核] 文件不存在. eventId={}, path={}", event.getEventId(), event.getAvatarPath());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 步骤2：文件格式校验
            String fileName = event.getOriginalFileName();
            String extension = getExtension(fileName).toLowerCase();
            boolean formatOk = false;
            for (String allowed : ALLOWED_FORMATS) {
                if (allowed.equals(extension)) {
                    formatOk = true;
                    break;
                }
            }
            if (!formatOk) {
                log.warn("[头像审核] 不支持的图片格式. eventId={}, ext={}", event.getEventId(), extension);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 步骤3：文件大小校验
            long fileSize = Files.size(filePath);
            if (fileSize > MAX_FILE_SIZE) {
                log.warn("[头像审核] 文件超过最大大小限制. eventId={}, size={}", event.getEventId(), fileSize);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 步骤4：图片压缩（生成标准尺寸缩略图）
            try {
                processAndCompressImage(filePath.toFile(), extension);
                log.info("[头像审核] 图片压缩完成. eventId={}, path={}", event.getEventId(), event.getAvatarPath());
            } catch (Exception imgEx) {
                log.warn("[头像审核] 图片处理失败（忽略，保留原图）. eventId={}, error={}",
                        event.getEventId(), imgEx.getMessage());
            }

            // 步骤5：成功 - 确认消息
            channel.basicAck(deliveryTag, false);
            log.info("[头像审核] 处理完成，消息已确认. eventId={}", event.getEventId());

        } catch (Exception e) {
            log.error("[头像审核] 处理失败. eventId={}, error={}", event.getEventId(), e.getMessage(), e);
            try {
                // basicNack → 进入死信队列
                channel.basicNack(deliveryTag, false, false);
            } catch (Exception channelEx) {
                log.error("[头像审核] Nack异常. eventId={}, error={}",
                        event.getEventId(), channelEx.getMessage(), channelEx);
            }
        }
    }

    /**
     * 压缩图片：读取 → 缩放到 TARGET_SIZE x TARGET_SIZE → 写回（覆盖原文件）
     */
    private void processAndCompressImage(File inputFile, String extension) throws Exception {
        BufferedImage original = ImageIO.read(inputFile);
        if (original == null) {
            throw new IllegalStateException("ImageIO 无法读取图片: " + inputFile);
        }

        // 如果原图已经小于目标尺寸，不处理
        if (original.getWidth() <= TARGET_SIZE && original.getHeight() <= TARGET_SIZE) {
            return;
        }

        // 等比缩放
        int newWidth, newHeight;
        if (original.getWidth() >= original.getHeight()) {
            newWidth = TARGET_SIZE;
            newHeight = (int) Math.round(original.getHeight() * (double) TARGET_SIZE / original.getWidth());
        } else {
            newHeight = TARGET_SIZE;
            newWidth = (int) Math.round(original.getWidth() * (double) TARGET_SIZE / original.getHeight());
        }

        Image scaled = original.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
        BufferedImage output = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = output.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, newWidth, newHeight);
            g.drawImage(scaled, 0, 0, null);
        } finally {
            g.dispose();
        }

        String format = "jpeg".equals(extension) || "jpg".equals(extension) ? "jpg" : extension;
        ImageIO.write(output, format, inputFile);
    }

    private String getExtension(String fileName) {
        if (fileName == null) return "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fileName.length() - 1) return "";
        return fileName.substring(lastDot + 1);
    }
}
