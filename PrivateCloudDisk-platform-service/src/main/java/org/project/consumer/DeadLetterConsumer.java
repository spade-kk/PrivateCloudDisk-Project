package org.project.consumer;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.config.RabbitMQConifgure;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 死信队列（DLQ）消费者
 *
 * <p>所有业务队列中处理失败（basicNack）的消息会被转发到这里。
 *
 * <p>处理策略：
 * <ol>
 *   <li>记录详细日志（包含消息头、消息体、时间戳）</li>
 *   <li>直接ACK掉，不做任何业务处理</li>
 *   <li>由运维/开发人员通过日志或监控系统发现问题后人工介入</li>
 * </ol>
 *
 * <p>为什么不自动重试：
 * <ul>
 *   <li>消息处理失败通常意味着外部依赖故障（如SMTP服务器宕机、短信服务商返回错误等）</li>
 *   <li>自动立即重试几乎必然再次失败，徒增系统压力</li>
 *   <li>正确做法：由人工排查问题后，通过脚本或管理后台手动重发</li>
 * </ul>
 *
 * <p>未来可扩展：
 * <ul>
 *   <li>将死信消息持久化到数据库/Elasticsearch，方便检索分析</li>
 *   <li>集成告警通知（钉钉/飞书/邮件告警）</li>
 *   <li>提供管理后台页面供人工选择重试</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterConsumer {

    @RabbitListener(
            containerFactory = "manualRabbitListenerContainerFactory",
            queues = RabbitMQConifgure.QUEUE_BUSINESS_DLQ
    )
    public void consume(Message message,
                        Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            String receivedRoutingKey = message.getMessageProperties() != null
                    ? message.getMessageProperties().getReceivedRoutingKey()
                    : "unknown";
            String originalQueue = message.getMessageProperties() != null
                    ? message.getMessageProperties().getConsumerQueue()
                    : "unknown";
            String messageId = message.getMessageProperties() != null
                    ? message.getMessageProperties().getMessageId()
                    : String.valueOf(System.currentTimeMillis());

            String body = new String(message.getBody(), StandardCharsets.UTF_8);

            log.error("========== [死信消息告警] ==========");
            log.error(" messageId     : {}", messageId);
            log.error(" routingKey    : {}", receivedRoutingKey);
            log.error(" sourceQueue   : {}", originalQueue);
            log.error(" timestamp     : {}", java.time.LocalDateTime.now());
            log.error(" messageBody   : {}", body.length() > 2000 ? body.substring(0, 2000) : body);
            log.error("=======================================");

            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("[死信队列] 处理死信消息时发生异常. error={}", e.getMessage(), e);
            try {
                channel.basicAck(deliveryTag, false);
            } catch (Exception ackEx) {
                log.error("[死信队列] ACK失败. error={}", ackEx.getMessage());
            }
        }
    }
}
