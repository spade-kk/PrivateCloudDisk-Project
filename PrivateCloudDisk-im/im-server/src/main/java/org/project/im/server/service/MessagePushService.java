package org.project.im.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.im.common.dto.MessageDTO;
import org.project.im.common.protocol.MessageProtocol;
import org.project.im.server.netty.SessionManager;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.project.im.common.constant.ImConstants.*;

/**
 * 消息推送服务
 * <p>
 * 负责将消息从发送者推送到接收者：
 * <ul>
 *   <li>在线用户：直接通过 WebSocket 推送</li>
 *   <li>离线用户：存储到 Redis 离线队列，等待用户上线后同步</li>
 *   <li>群聊消息：查询群成员列表，逐一推送</li>
 * </ul>
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessagePushService {

    private final SessionManager sessionManager;
    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 处理客户端发送的消息
     * <p>
     * 流程：
     * 1. 解析消息内容
     * 2. 投递到 RabbitMQ（im-platform 消费后持久化）
     * 3. 在线推送（直接通过 WebSocket 发送）
     * 4. 离线存储（写入 Redis 离线队列）
     * </p>
     */
    public void handleMessage(ChannelHandlerContext ctx, MessageProtocol protocol) {
        try {
            String senderId = protocol.getSenderId();
            String receiverId = protocol.getReceiverId();
            String payloadJson = objectMapper.writeValueAsString(protocol.getPayload());

            // 1. 投递到 RabbitMQ 进行持久化
            rabbitTemplate.convertAndSend(MQ_EXCHANGE_MESSAGE,
                    MQ_ROUTING_PRIVATE, payloadJson);

            // 2. 尝试在线推送
            if (sessionManager.isOnline(receiverId)) {
                sessionManager.sendToUser(receiverId, payloadJson);
                log.debug("消息在线推送: {} → {}", senderId, receiverId);
            } else {
                // 3. 离线存储
                storeOfflineMessage(receiverId, payloadJson);
                log.debug("消息离线存储: {} → {}", senderId, receiverId);
            }
            // 4. 发送 ACK 给发送者
            sendAck(ctx, protocol.getSeq());
        } catch (Exception e) {
            log.error("消息处理失败", e);
        }
    }

    /**
     * 处理消息确认（ACK）
     */
    public void handleAck(ChannelHandlerContext ctx, MessageProtocol protocol) {
        String userId = protocol.getSenderId();
        Long originalSeq = protocol.getSeq();
        log.debug("收到 ACK: userId={}, seq={}", userId, originalSeq);
    }

    /**
     * 处理消息已读
     */
    public void handleRead(ChannelHandlerContext ctx, MessageProtocol protocol) {
        try {
            String conversationId = (String) protocol.getPayload();
            String userId = protocol.getSenderId();
            if (conversationId != null) {
                // 通知会话清零未读
                MessageProtocol readNotify = MessageProtocol.builder()
                        .version(PROTOCOL_VERSION)
                        .command(204)
                        .payload(conversationId)
                        .senderId(userId)
                        .timestamp(System.currentTimeMillis())
                        .build();
                sessionManager.sendToUser(userId, objectMapper.writeValueAsString(readNotify));
            }
        } catch (Exception e) {
            log.error("已读处理失败", e);
        }
    }

    /**
     * 处理正在输入
     */
    public void handleTyping(ChannelHandlerContext ctx, MessageProtocol protocol) {
        String receiverId = protocol.getReceiverId();
        if (sessionManager.isOnline(receiverId)) {
            try {
                String json = objectMapper.writeValueAsString(protocol);
                sessionManager.sendToUser(receiverId, json);
            } catch (Exception e) {
                log.error("typing 推送失败", e);
            }
        }
    }

    /**
     * 处理离线消息同步
     */
    public void handleSyncOffline(ChannelHandlerContext ctx, MessageProtocol protocol) {
        String userId = protocol.getSenderId();
        String offlineKey = String.format(REDIS_OFFLINE_QUEUE, userId);
        try {
            List<String> messages = redisTemplate.opsForList().range(offlineKey, 0, -1);
            if (messages != null && !messages.isEmpty()) {
                for (String msg : messages) {
                    ctx.writeAndFlush(new io.netty.handler.codec.http.websocketx.TextWebSocketFrame(msg));
                }
                redisTemplate.delete(offlineKey);
                log.info("离线消息同步完成: userId={}, count={}", userId, messages.size());
            }
        } catch (Exception e) {
            log.error("离线消息同步失败: userId={}", userId, e);
        }
    }

    /**
     * 推送消息到指定用户（由 RabbitMQ 消费者调用）
     */
    public void pushToUser(MessageDTO messageDTO) {
        String receiverId = messageDTO.getReceiverId();
        if (sessionManager.isOnline(receiverId)) {
            try {
                String json = objectMapper.writeValueAsString(messageDTO);
                sessionManager.sendToUser(receiverId, json);
                log.debug("消息已推送至用户: {}", receiverId);
            } catch (Exception e) {
                log.error("消息推送失败: receiverId={}", receiverId, e);
            }
        } else {
            // 离线存储
            try {
                String json = objectMapper.writeValueAsString(messageDTO);
                storeOfflineMessage(receiverId, json);
            } catch (Exception e) {
                log.error("离线消息存储失败", e);
            }
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 存储离线消息到 Redis
     */
    private void storeOfflineMessage(String userId, String messageJson) {
        String offlineKey = String.format(REDIS_OFFLINE_QUEUE, userId);
        redisTemplate.opsForList().rightPush(offlineKey, messageJson);
        // 限制离线消息最大条数
        redisTemplate.opsForList().trim(offlineKey, -MAX_OFFLINE_MESSAGES, -1);
        redisTemplate.expire(offlineKey, 7, TimeUnit.DAYS);
    }

    /**
     * 发送 ACK 确认
     */
    private void sendAck(ChannelHandlerContext ctx, Long seq) {
        try {
            MessageProtocol ack = MessageProtocol.builder()
                    .version(PROTOCOL_VERSION)
                    .command(202)
                    .seq(seq)
                    .timestamp(System.currentTimeMillis())
                    .payload("ACK")
                    .build();
            String json = objectMapper.writeValueAsString(ack);
            ctx.writeAndFlush(new io.netty.handler.codec.http.websocketx.TextWebSocketFrame(json));
        } catch (Exception e) {
            log.error("ACK 发送失败", e);
        }
    }

    // ==================== V2 协议方法 ====================

    /**
     * 处理 V2 协议发送消息
     */
    public void handleV2Message(ChannelHandlerContext ctx,
                                 org.project.im.common.protocol.v2.IMProtocolV2.IMEnvelope envelope,
                                 org.project.im.common.protocol.v2.MessageTypeDispatcher.DispatchedMessage dispatched,
                                 org.project.im.common.security.IMSessionKeys sessionKeys) {
        log.info("V2 handleMessage: messageId={}, type={}, sender={}",
                envelope.getMessageId(), envelope.getMessageType(), envelope.getSenderId());
        // TODO: 实现 V2 消息持久化与推送
    }

    public void handleV2Ack(ChannelHandlerContext ctx,
                             org.project.im.common.protocol.v2.MessageTypeDispatcher.DispatchedMessage dispatched) {
        log.debug("V2 handleAck: messageId={}", dispatched.envelope().getMessageId());
    }

    public void handleV2Recall(ChannelHandlerContext ctx,
                                org.project.im.common.protocol.v2.MessageTypeDispatcher.DispatchedMessage dispatched) {
        log.info("V2 handleRecall: messageId={}", dispatched.envelope().getMessageId());
    }

    public void handleV2Read(ChannelHandlerContext ctx,
                              org.project.im.common.protocol.v2.MessageTypeDispatcher.DispatchedMessage dispatched) {
        log.debug("V2 handleRead: messageId={}", dispatched.envelope().getMessageId());
    }

    public void handleV2Typing(ChannelHandlerContext ctx,
                                org.project.im.common.protocol.v2.MessageTypeDispatcher.DispatchedMessage dispatched) {
        log.debug("V2 handleTyping: sender={}", dispatched.envelope().getSenderId());
    }

    public void handleV2GetConversations(ChannelHandlerContext ctx,
                                          org.project.im.common.protocol.v2.MessageTypeDispatcher.DispatchedMessage dispatched) {
        log.info("V2 handleGetConversations: userId={}", dispatched.envelope().getSenderId());
    }

    public void handleV2GetHistory(ChannelHandlerContext ctx,
                                    org.project.im.common.protocol.v2.MessageTypeDispatcher.DispatchedMessage dispatched) {
        log.info("V2 handleGetHistory: userId={}", dispatched.envelope().getSenderId());
    }

    public void handleV2SystemNotify(ChannelHandlerContext ctx,
                                      org.project.im.common.protocol.v2.MessageTypeDispatcher.DispatchedMessage dispatched) {
        log.info("V2 handleSystemNotify: messageId={}", dispatched.envelope().getMessageId());
    }
}