package org.project.im.server.service;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.im.common.constant.ImConstants;
import org.project.im.common.mq.IMMQProto;
import org.project.im.common.protocol.v2.IMProtocolCodec;
import org.project.im.common.protocol.v2.IMProtocolV2;
import org.project.im.common.protocol.v2.MessageTypeDispatcher;
import org.project.im.common.security.IMSessionKeys;
import org.project.im.server.netty.SessionManager;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.project.im.common.constant.ImConstants.*;

// ============================================================
// 消息推送服务 v2.0 — V2 二进制协议
// ============================================================
// 重构要点：
//   1. 移除所有 V1 JSON 协议方法（handleMessage, handleAck, pushToUser 等）
//   2. 实现 V2 消息处理：发布 SendMessageCommand 到 MQ 命令队列
//   3. 已读回执发布 MessageReadEvent 到 MQ 事件队列
//   4. 离线消息补偿由 IM Business 通过 UserOnlineEvent 触发，不再由 IM Server 处理
//   5. 移除直接 WebSocket 推送（由 IM Router → gRPC → SessionManager 统一推送）
//   6. 消息送达即推送成功：不再等待客户端 ACK，推送后直接发布 MessageDeliveredEvent
//
// 消息链路：
//   客户端 → WebSocket Binary → V2MessageHandler → V2MessageRouter → MessagePushService
//     ├── SEND_MESSAGE → 发布 SendMessageCommand 到 MQ → IM Business 持久化
//     ├── READ_MESSAGE → 发布 MessageReadEvent 到 MQ
//     ├── RECALL_MESSAGE → 发布撤回命令到 MQ
//     └── TYPING → 直接转发给接收者
// ============================================================

/**
 * 消息推送服务（V2 二进制协议）
 * <p>
 * 处理来自客户端的各类消息命令，将业务消息发布到 MQ 命令队列，
 * 由 IM Business 进行权限校验和持久化。
 * </p>
 *
 * <h3>职责边界</h3>
 * <ul>
 *   <li>IM Server：协议解码、命令分发、MQ 发布</li>
 *   <li>IM Business：权限校验、消息持久化、推送命令发布</li>
 *   <li>IM Router：消息路由、gRPC 推送、离线存储</li>
 * </ul>
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessagePushService {

    private final SessionManager sessionManager;
    private final RabbitTemplate rabbitTemplate;
    private final EventPublisher eventPublisher;

    // ============================================================
    // 消息发送 — 发布 SendMessageCommand 到 MQ
    // ============================================================

    /**
     * 处理 V2 协议发送消息命令
     * <p>
     * 客户端通过 WebSocket 发送 SEND_MESSAGE 命令后，IM Server 将原始消息
     * 封装为 SendMessageCommand 发布到 MQ 命令队列（im.message.send.command），
     * 由 IM Business 消费并执行权限校验、持久化、推送流程。
     * </p>
     *
     * <p>注意：IM Server 不负责权限校验和消息持久化，仅负责协议解码和命令转发。</p>
     */
    public void handleV2Message(
            ChannelHandlerContext ctx,
            IMProtocolV2.IMEnvelope envelope,
            MessageTypeDispatcher.DispatchedMessage dispatched,
            IMSessionKeys sessionKeys) {

        String messageId = envelope.getMessageId();
        String senderId = envelope.getSenderId();
        String receiverId = envelope.getReceiverId();

        log.info("V2 消息发送请求: messageId={}, sender={}, receiver={}, type={}",
                messageId, senderId, receiverId, envelope.getMessageType());

        try {
            // 1. 构建 SendMessageCommand
            IMMQProto.SendMessageCommand.Builder cmdBuilder = IMMQProto.SendMessageCommand.newBuilder()
                    .setHeader(buildMQHeader("im.message.send.command", messageId, sessionManager.getNodeId()))
                    .setSenderId(senderId)
                    .setReceiverId(receiverId)
                    .setConversationType(envelope.getConversationType().getNumber())
                    .setMessageType(envelope.getMessageType().getNumber())
                    .setClientSeq(envelope.getSeq())
                    .setSenderDeviceType(envelope.getSenderDeviceType().getNumber())
                    .setSenderPlatform(nullToEmpty(envelope.getSenderPlatform()));

            // 2. 设置消息负载（原始 Protobuf 字节）
            if (dispatched.payload() != null) {
                cmdBuilder.setPayloadBytes(
                        com.google.protobuf.ByteString.copyFrom(dispatched.payload().toByteArray()));
            }

            // 3. 设置引用消息（如果有）
            if (!envelope.getReplyTo().isEmpty()) {
                cmdBuilder.setReplyTo(envelope.getReplyTo());
            }

            // 4. 设置扩展字段
            if (!envelope.getExtraJson().isEmpty()) {
                cmdBuilder.setExtraJson(envelope.getExtraJson());
            }

            // 5. 发布到 MQ 命令队列
            byte[] cmdBytes = cmdBuilder.build().toByteArray();
            Message mqMessage = MessageBuilder
                    .withBody(cmdBytes)
                    .setContentType(MessageProperties.CONTENT_TYPE_BYTES)
                    .setHeader("event_type", "im.message.send.command")
                    .setMessageId(messageId)
                    .build();
            rabbitTemplate.send(MQ_EXCHANGE_COMMAND, MQ_ROUTING_SEND_COMMAND, mqMessage);

            log.debug("SendMessageCommand 已发布: messageId={}, sender={}, receiver={}",
                    messageId, senderId, receiverId);

        } catch (Exception e) {
            log.error("发布 SendMessageCommand 失败: messageId={}", messageId, e);
            sendErrorEnvelope(ctx, "消息发送失败，请重试", sessionKeys);
        }
    }

    // ============================================================
    // 消息撤回 — 发布撤回命令到 MQ
    // ============================================================

    /**
     * 处理 V2 协议消息撤回
     * <p>
     * 撤回请求转发到 IM Business 进行校验和处理。
     * </p>
     */
    public void handleV2Recall(
            ChannelHandlerContext ctx,
            MessageTypeDispatcher.DispatchedMessage dispatched) {

        IMProtocolV2.IMEnvelope envelope = dispatched.envelope();
        String messageId = envelope.getMessageId();

        log.info("V2 消息撤回请求: messageId={}, sender={}",
                messageId, envelope.getSenderId());

        // 撤回操作通过 HTTP API 处理，WebSocket 通道仅转发
        // 这里可以发布一个撤回事件到 MQ，由 IM Business 处理
        // 当前简化实现：直接日志记录
        log.debug("撤回请求已记录: messageId={}", messageId);
    }

    // ============================================================
    // 消息已读 — 发布 MessageReadEvent 到 MQ
    // ============================================================

    /**
     * 处理 V2 协议已读回执
     * <p>
     * 客户端上报已读回执，IM Server 发布 MessageReadEvent 到 MQ 事件队列，
     * IM Business 消费后更新消息已读状态和未读计数。
     * </p>
     */
    public void handleV2Read(
            ChannelHandlerContext ctx,
            MessageTypeDispatcher.DispatchedMessage dispatched) {

        IMProtocolV2.IMEnvelope envelope = dispatched.envelope();
        String userId = envelope.getSenderId();
        String conversationId = envelope.getConversationId();

        // 提取已读消息 ID 列表
        List<String> messageIds = List.of();
        if (dispatched.payload() instanceof IMProtocolV2.ReadReceiptPayload readPayload) {
            messageIds = readPayload.getMessageIdsList();
        }

        // 发布已读事件到 MQ
        eventPublisher.publishMessageReadEvent(userId, conversationId, messageIds);

        // 原链路只更新数据库，发送方客户端永远收不到
        // READ_MESSAGE。单聊场景下复用 Router 推送控制通知；该通知不产生二次回执，发送方
        // 离线时允许补偿，多端上线后也可同步已读状态。群聊的成员级已读列表需独立聚合模型。
        if (envelope.getConversationType() == IMProtocolV2.IMConversationType.PRIVATE
                && !envelope.getReceiverId().isEmpty()) {
            try {
                routeControlNotification(envelope, dispatched.payload(),
                        IMMQProto.MessageType.SYSTEM_NOTIFICATION, "im.message.read.notify.command");
            } catch (Exception e) {
                log.warn("V2 已读状态通知发送失败: reader={}, receiver={}, error={}",
                        userId, envelope.getReceiverId(), e.getMessage());
            }
        }

        log.debug("V2 已读回执: userId={}, conversationId={}, count={}",
                userId, conversationId, messageIds.size());
    }

    // ============================================================
    // 正在输入 — 直接转发给接收者
    // ============================================================

    /**
     * 处理 V2 协议正在输入提示
     * <p>
     * 实时性要求高，直接通过 WebSocket 转发给接收者，不经过 MQ。
     * </p>
     */
    public void handleV2Typing(
            ChannelHandlerContext ctx,
            MessageTypeDispatcher.DispatchedMessage dispatched) {

        IMProtocolV2.IMEnvelope envelope = dispatched.envelope();
        String receiverId = envelope.getReceiverId();

        try {
            // 原实现仅记录日志，且只看本节点在线状态，
            // 跨节点用户永远收不到 typing。新行为将解密后的临时负载交给 Router 定位目标节点，
            // 目标 IM Server 再使用接收者会话密钥重新加密；该消息标记为 CUSTOM_NOTIFICATION，
            // Router 会在接收者离线时直接丢弃，不进入离线队列，也不产生送达回执。
            routeControlNotification(envelope, dispatched.payload(),
                    IMMQProto.MessageType.CUSTOM_NOTIFICATION, "im.message.typing.command");
            log.debug("V2 typing 已交给 Router: sender={}, receiver={}",
                    envelope.getSenderId(), receiverId);
        } catch (Exception e) {
            // typing 是体验增强事件，失败不得中断聊天主链路。
            log.debug("V2 typing 临时事件转发失败: sender={}, receiver={}, error={}",
                    envelope.getSenderId(), receiverId, e.getMessage());
        }
    }

    // ============================================================
    // 会话查询 — 通过 HTTP API 调用 IM Business
    // ============================================================

    /**
     * 处理 V2 协议会话列表查询
     * <p>
     * 会话列表查询通过 HTTP API 调用 IM Business，WebSocket 通道仅作为入口。
     * 实际实现应通过 HTTP 客户端调用 IM Business 的 REST API。
     * </p>
     */
    public void handleV2GetConversations(
            ChannelHandlerContext ctx,
            MessageTypeDispatcher.DispatchedMessage dispatched) {

        IMProtocolV2.IMEnvelope envelope = dispatched.envelope();
        log.info("V2 会话列表查询: userId={}", envelope.getSenderId());
        // 实际实现：通过 HTTP 调用 IM Business API 并返回结果
        // 当前简化：客户端应使用 HTTP API 查询会话列表
    }

    /**
     * 处理 V2 协议历史消息查询
     */
    public void handleV2GetHistory(
            ChannelHandlerContext ctx,
            MessageTypeDispatcher.DispatchedMessage dispatched) {

        IMProtocolV2.IMEnvelope envelope = dispatched.envelope();
        log.info("V2 历史消息查询: userId={}", envelope.getSenderId());
        // 实际实现：通过 HTTP 调用 IM Business API 并返回结果
    }

    // ============================================================
    // 系统通知 — 转发给接收者
    // ============================================================

    /**
     * 处理 V2 协议系统通知
     */
    public void handleV2SystemNotify(
            ChannelHandlerContext ctx,
            MessageTypeDispatcher.DispatchedMessage dispatched) {

        IMProtocolV2.IMEnvelope envelope = dispatched.envelope();
        log.info("V2 系统通知: messageId={}, receiver={}",
                envelope.getMessageId(), envelope.getReceiverId());
        // 系统通知通过 IM Router 推送，不在此处理
    }

    // ============================================================
    // 离线消息同步 — 已由 IM Business 自动处理
    // ============================================================

    /**
     * 处理离线消息同步请求
     * <p>
     * V2.0 架构下，离线消息补偿由 IM Business 消费 UserOnlineEvent 自动触发，
     * 客户端无需主动请求同步。此方法保留向后兼容，实际为空操作。
     * </p>
     */
    public void handleSyncOffline(
            ChannelHandlerContext ctx,
            org.project.im.common.protocol.MessageProtocol protocol) {
        log.debug("V2 离线消息同步请求（已由 UserOnlineEvent 自动触发，无需主动同步）");
    }

    // ============================================================
    // 私有方法
    // ============================================================

    /**
     * 将已解密的控制类负载交给 Router，并由目标 IM Server 使用接收者密钥重新加密。
     */
    private void routeControlNotification(IMProtocolV2.IMEnvelope source,
                                          com.google.protobuf.MessageLite payload,
                                          IMMQProto.MessageType mqType,
                                          String eventType) {
        IMProtocolV2.IMEnvelope.Builder outbound = source.toBuilder()
                .clearEncryptedPayload()
                .setTimestamp(System.currentTimeMillis());
        if (payload != null) {
            outbound.setEncryptedPayload(com.google.protobuf.ByteString.copyFrom(payload.toByteArray()));
        }
        IMProtocolV2.IMEnvelope targetEnvelope = outbound.build();
        String messageId = targetEnvelope.getMessageId().isEmpty()
                ? "control-" + System.currentTimeMillis()
                : targetEnvelope.getMessageId();
        IMMQProto.PushMessageCommand command = IMMQProto.PushMessageCommand.newBuilder()
                .setHeader(buildMQHeader(eventType, messageId, sessionManager.getNodeId()))
                .setMessageId(messageId)
                .setReceiverId(targetEnvelope.getReceiverId())
                .setSenderId(targetEnvelope.getSenderId())
                .setConversationId(targetEnvelope.getConversationId())
                .setConversationType(targetEnvelope.getConversationType().getNumber())
                .setContentType(targetEnvelope.getMessageTypeValue())
                .setMessageType(mqType)
                .setMessageTimestamp(System.currentTimeMillis())
                .setEnvelopeBytes(com.google.protobuf.ByteString.copyFrom(targetEnvelope.toByteArray()))
                .build();
        Message mqMessage = MessageBuilder.withBody(command.toByteArray())
                .setContentType(MessageProperties.CONTENT_TYPE_BYTES)
                .setHeader("event_type", eventType)
                .setMessageId(messageId)
                .build();
        rabbitTemplate.send(MQ_EXCHANGE_COMMAND, MQ_ROUTING_PUSH_COMMAND, mqMessage);
    }

    /**
     * 构建 MQ 消息公共头部
     */
    private IMMQProto.MQMessageHeader buildMQHeader(String eventType, String messageId, String sourceNode) {
        return IMMQProto.MQMessageHeader.newBuilder()
                .setEventType(eventType)
                .setMessageId(nullToEmpty(messageId))
                .setTimestamp(System.currentTimeMillis())
                .setTraceId(java.util.UUID.randomUUID().toString().replace("-", ""))
                .setSourceNode(nullToEmpty(sourceNode))
                .setRetryCount(0)
                .build();
    }

    /**
     * 发送错误响应（二进制协议）
     */
    private void sendErrorEnvelope(ChannelHandlerContext ctx, String message, IMSessionKeys sessionKeys) {
        try {
            IMProtocolV2.IMEnvelope errorEnvelope = IMProtocolV2.IMEnvelope.newBuilder()
                    .setVersion(2)
                    .setMessageId("error-" + System.currentTimeMillis())
                    .setCommand(IMProtocolV2.IMCommandType.ERROR_NOTIFY)
                    .setMessageType(IMProtocolV2.IMMessageType.ERROR)
                    .setTimestamp(System.currentTimeMillis())
                    .setExtraJson("{\"error\":\"" + message + "\"}")
                    .build();

            // 按 V2 协议封装为加密帧后下发（与客户端 codec 对齐，避免原始 Protobuf 被误判）
            byte[] bytes = IMProtocolCodec.encode(errorEnvelope, sessionKeys);
            ctx.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(bytes)));
        } catch (Exception e) {
            log.error("发送错误消息失败", e);
        }
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
