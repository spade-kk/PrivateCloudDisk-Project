package org.project.im.server.netty.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.im.common.protocol.v2.IMProtocolCodec;
import org.project.im.common.protocol.v2.IMProtocolV2;
import org.project.im.common.protocol.v2.MessageTypeDispatcher;
import org.project.im.common.security.IMAntiForgeryValidator;
import org.project.im.common.security.IMSessionKeyManager;
import org.project.im.common.security.IMSessionKeys;
import org.project.im.server.netty.SessionManager;
import org.project.im.server.service.EventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * IM v2 二进制消息处理器
 * <p>
 * 企业级协议处理器，替换原有的 JSON 文本协议，使用 Protobuf 二进制 + 双层加密。
 * <p>
 * <b>处理的 WebSocket 帧类型:</b>
 * <ul>
 *   <li>BinaryWebSocketFrame — v2 二进制协议帧（主要使用）</li>
 *   <li>TextWebSocketFrame — 仅用于密钥交换阶段的 JSON 消息</li>
 *   <li>PingWebSocketFrame / PongWebSocketFrame — 自动处理</li>
 * </ul>
 * <p>
 * <b>消息处理流程:</b>
 * <pre>
 * Binary Frame
 *   → IMProtocolCodec.decode()       [Layer 1 解密 + HMAC 验证]
 *   → MessageTypeDispatcher.dispatch() [Layer 2 解密 + 消息分发]
 *   → 路由到业务处理器
 * </pre>
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class V2MessageHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private final SessionManager sessionManager;
    private final IMSessionKeyManager keyManager = IMSessionKeyManager.getInstance();
    private final V2MessageRouter messageRouter;
    private final EventPublisher eventPublisher;

    /** 标记属性名，标识 Channel 是否已完成密钥协商 */
    private static final String KEY_ESTABLISHED = "v2:key_established";
    private static final String CONNECTION_ID = "v2:connection_id";
    /** 离线原因属性（1=正常, 2=心跳超时, 3=踢出, 4=异常） */
    private static final String OFFLINE_REASON = "v2:offline_reason";

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (frame instanceof BinaryWebSocketFrame binaryFrame) {
            handleBinaryFrame(ctx, binaryFrame);
        } else if (frame instanceof TextWebSocketFrame textFrame) {
            handleTextFrame(ctx, textFrame);
        }
        // 其他帧类型（Ping/Pong/Close）由 Netty 自动处理
    }

    // ==================== 二进制帧处理 ====================

    /**
     * 处理 v2 二进制协议帧
     */
    private void handleBinaryFrame(ChannelHandlerContext ctx, BinaryWebSocketFrame frame) {
        String connectionId = getConnectionId(ctx);
        if (connectionId == null) {
            sendError(ctx, "未完成密钥协商，请先发送 KeyExchangeRequest");
            return;
        }

        IMSessionKeys sessionKeys = keyManager.getByConnection(connectionId);
        if (sessionKeys == null) {
            sendError(ctx, "会话密钥已过期，请重新协商");
            return;
        }

        ByteBuf content = frame.content();
        byte[] frameBytes = new byte[content.readableBytes()];
        content.readBytes(frameBytes);

        try {
            // 1. 解码 + Layer 1 解密 + HMAC 验证
            IMProtocolV2.IMEnvelope envelope = IMProtocolCodec.decode(frameBytes, sessionKeys);

            // 2. 安全校验
            validateEnvelope(envelope, connectionId, sessionKeys);

            // 3. Layer 2 解密 + 消息分发
            MessageTypeDispatcher.DispatchedMessage dispatched =
                    MessageTypeDispatcher.dispatch(envelope, sessionKeys);

            // 4. 路由到业务处理器
            messageRouter.route(ctx, dispatched, sessionKeys);

            log.info("V2 message processed: type={}, messageId={}, sender={}",
                    envelope.getMessageType(), envelope.getMessageId(), envelope.getSenderId());

        } catch (IMProtocolCodec.ProtocolCodecException e) {
            log.warn("Protocol decode error: connectionId={}, error={}", connectionId, e.getMessage());
            sendError(ctx, "协议解码失败: " + e.getMessage());
        } catch (MessageTypeDispatcher.DispatchException e) {
            log.warn("Message dispatch error: connectionId={}, error={}", connectionId, e.getMessage());
            sendError(ctx, "消息分发失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected message processing error: connectionId={}", connectionId, e.getMessage());
            sendError(ctx, "消息处理异常");
        }
    }

    // ==================== 文本帧处理（密钥交换 + 兼容旧协议） ====================

    /**
     * 处理文本帧（用于密钥交换阶段的 JSON 消息）
     */
    private void handleTextFrame(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        String text = frame.text();
        if (text == null || text.isEmpty()) return;

        // 密钥交换的 JSON 消息由 V2AuthHandler 处理
        // 这里仅作为兼容入口
        log.debug("Received text frame on v2 handler, will be forwarded: {}",
                text.substring(0, Math.min(100, text.length())));
    }

    // ==================== 安全校验 ====================

    /**
     * 验证 Envelope 的合法性
     */
    private void validateEnvelope(IMProtocolV2.IMEnvelope envelope, String connectionId,
                                  IMSessionKeys sessionKeys) {
        // 验证消息 ID 格式
        if (!IMAntiForgeryValidator.isValidMessageId(envelope.getMessageId())) {
            throw new IMProtocolCodec.ProtocolCodecException(
                    "Invalid message ID: " + envelope.getMessageId());
        }

        // 验证发送者 ID 格式
        if (!IMAntiForgeryValidator.isValidUserId(envelope.getSenderId())) {
            throw new IMProtocolCodec.ProtocolCodecException(
                    "Invalid sender ID: " + envelope.getSenderId());
        }

        // AUDIT FIX [14.6,14.25] / IM-WEB-ENTERPRISE-20260809：原行为只校验 sender_id
        // 格式，任意已登录连接仍可伪造其他 UUID。新行为将信封身份绑定到 Token 协商出的
        // 会话身份；影响范围为所有 V2 命令（消息、已读、输入状态、心跳和通话信令）。
        if (sessionKeys == null || !envelope.getSenderId().equals(sessionKeys.getUserId())) {
            log.warn("V2 sender identity mismatch: connectionId={}, envelopeSender={}, sessionUser={}",
                    connectionId, envelope.getSenderId(), sessionKeys == null ? "null" : sessionKeys.getUserId());
            throw new IMProtocolCodec.ProtocolCodecException("Sender identity does not match authenticated session");
        }

        // 验证时间戳（防重放）
        if (!IMAntiForgeryValidator.isTimestampValid(envelope.getTimestamp())) {
            throw new IMProtocolCodec.ProtocolCodecException(
                    "Message timestamp out of window: " + envelope.getTimestamp()
                            + " (current: " + System.currentTimeMillis() + ")");
        }
    }

    // ==================== 心跳与连接管理 ====================

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent event) {
            if (event.state() == IdleState.READER_IDLE) {
                log.info("V2 客户端读空闲超时: {}", ctx.channel().remoteAddress());
                // 标记离线原因：心跳超时
                setOfflineReason(ctx, 2);
                cleanupConnection(ctx);
                ctx.close();
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // 获取用户信息（在 remove 之前获取，否则 Channel 已从映射中移除）
        String userId = sessionManager.getUserId(ctx.channel());
        String connectionId = getConnectionId(ctx);
        int offlineReason = getOfflineReason(ctx); // 默认 1=正常断开

        cleanupConnection(ctx);
        sessionManager.remove(ctx.channel());

        // 发布用户离线事件到 MQ（IM Business 消费后清理在线状态）
        if (userId != null) {
            eventPublisher.publishUserOfflineEvent(userId, connectionId, offlineReason);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("V2 消息处理器异常: {}", cause.getMessage());
        // 标记离线原因：异常断开
        setOfflineReason(ctx, 4);
        cleanupConnection(ctx);
        sessionManager.remove(ctx.channel());
        ctx.close();
    }

    // ==================== 私有方法 ====================

    private void cleanupConnection(ChannelHandlerContext ctx) {
        String connectionId = getConnectionId(ctx);
        if (connectionId != null) {
            keyManager.remove(connectionId);
        }
    }

    private String getConnectionId(ChannelHandlerContext ctx) {
        return (String) ctx.channel().attr(
                io.netty.util.AttributeKey.valueOf(CONNECTION_ID)).get();
    }

    private void setConnectionId(ChannelHandlerContext ctx, String connectionId) {
        ctx.channel().attr(
                io.netty.util.AttributeKey.valueOf(CONNECTION_ID)).set(connectionId);
    }

    /**
     * 设置离线原因（用于 channelInactive 时发布事件）
     */
    private void setOfflineReason(ChannelHandlerContext ctx, int reason) {
        ctx.channel().attr(
                io.netty.util.AttributeKey.valueOf(OFFLINE_REASON)).set(reason);
    }

    /**
     * 获取离线原因（默认 1=正常断开）
     */
    private int getOfflineReason(ChannelHandlerContext ctx) {
        Integer reason = (Integer) ctx.channel().attr(
                io.netty.util.AttributeKey.valueOf(OFFLINE_REASON)).get();
        return reason != null ? reason : 1;
    }

    /**
     * 发送错误响应（JSON 文本帧，与 V2AuthHandler 保持一致）
     *
     * <p>注意：错误消息必须以文本帧发送，而非二进制帧。
     * 因为客户端期望所有二进制帧都是加密格式（Total Length + Header Length + Encrypted Body + HMAC），
     * 发送原始 protobuf 字节作为二进制帧将导致客户端解析失败（Invalid total length in frame）。</p>
     */
    private void sendError(ChannelHandlerContext ctx, String message) {
        try {
            Map<String, Object> error = Map.of(
                    "type", "ERROR",
                    "code", 500,
                    "message", message
            );
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(error);
            ctx.writeAndFlush(new TextWebSocketFrame(json));
        } catch (Exception e) {
            log.error("发送错误消息失败", e);
        }
    }
}
