package org.project.im.server.netty.handler;

import com.google.protobuf.Message;
import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.im.common.protocol.v2.IMProtocolV2;
import org.project.im.common.protocol.v2.MessageTypeDispatcher;
import org.project.im.common.security.IMSessionKeys;
import org.project.im.server.netty.SessionManager;
import org.project.im.server.service.MessagePushService;
import org.project.im.server.signaling.handler.SignalingHandler;
import org.springframework.stereotype.Component;

/**
 * IM v2 消息路由器
 * <p>
 * 根据消息类型和命令将解密后的消息分发到对应的业务处理器。
 * <p>
 * 路由表：
 * <pre>
 * 命令类型               → 处理器
 * ─────────────────────────────────────
 * HEARTBEAT              → 心跳响应
 * SEND_MESSAGE           → messagePushService.handleV2Message()
 * MESSAGE_ACK            → messagePushService.handleV2Ack()
 * READ_MESSAGE, TYPING   → messagePushService
 * SYNC_OFFLINE           → messagePushService.handleSyncOffline()
 * CALL_* / SIGNALING_*   → signalingHandler.handleV2()
 * 群组操作                → (预留) groupService
 * 会话操作                → (预留) conversationService
 * </pre>
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class V2MessageRouter {

    private final SessionManager sessionManager;
    private final MessagePushService messagePushService;
    private final SignalingHandler signalingHandler;

    /**
     * 路由消息到对应的业务处理器
     *
     * @param ctx         Netty Channel 上下文
     * @param dispatched  已解密的消息（含 envelope 和 payload）
     * @param sessionKeys 会话密钥
     */
    public void route(
            ChannelHandlerContext ctx,
            MessageTypeDispatcher.DispatchedMessage dispatched,
            IMSessionKeys sessionKeys) {

        IMProtocolV2.IMEnvelope envelope = dispatched.envelope();
        IMProtocolV2.IMCommandType command = envelope.getCommand();

        if (command == null || command == IMProtocolV2.IMCommandType.COMMAND_UNSPECIFIED) {
            log.warn("Unknown command in message: messageId={}", envelope.getMessageId());
            return;
        }

        switch (command) {
            // ---- 连接管理 ----
            case HEARTBEAT -> handleHeartbeat(ctx, envelope, sessionKeys);

            // ---- 消息收发 ----
            case SEND_MESSAGE -> messagePushService.handleV2Message(ctx, envelope, dispatched, sessionKeys);
            case MESSAGE_ACK -> messagePushService.handleV2Ack(ctx, dispatched);
            case RECALL_MESSAGE -> messagePushService.handleV2Recall(ctx, dispatched);
            case READ_MESSAGE -> messagePushService.handleV2Read(ctx, dispatched);
            case TYPING -> messagePushService.handleV2Typing(ctx, dispatched);

            // ---- 会话管理 ----
            case GET_CONVERSATIONS -> messagePushService.handleV2GetConversations(ctx, dispatched);
            case GET_HISTORY -> messagePushService.handleV2GetHistory(ctx, dispatched);

            // ---- 离线同步 ----
            case SYNC_OFFLINE -> messagePushService.handleSyncOffline(ctx, null);

            // ---- WebRTC 信令 ----
            case CALL_INVITE, CALL_ACCEPT, CALL_REJECT, CALL_CANCEL, CALL_HANGUP,
                 CALL_BUSY, CALL_TIMEOUT,
                 SIGNALING_OFFER, SIGNALING_ANSWER, SIGNALING_ICE,
                 CALL_QUALITY_REPORT,
                 CALL_SCREEN_SHARE_START, CALL_SCREEN_SHARE_STOP,
                 CALL_MUTE_TOGGLE, CALL_CAMERA_TOGGLE,
                 CALL_SWITCH_TO_VOICE, CALL_SWITCH_TO_VIDEO,
                 CALL_ROOM_CREATE, CALL_ROOM_JOIN, CALL_ROOM_LEAVE,
                 CALL_ICE_SERVERS ->
                    signalingHandler.handleV2(ctx, envelope, dispatched, sessionKeys);

            // ---- 系统通知 ----
            case SYSTEM_NOTIFY -> messagePushService.handleV2SystemNotify(ctx, dispatched);

            default -> log.warn("Unsupported command: {} in message: {}",
                    command, envelope.getMessageId());
        }
    }

    // ==================== 心跳 ====================

    private void handleHeartbeat(
            ChannelHandlerContext ctx,
            IMProtocolV2.IMEnvelope envelope,
            IMSessionKeys sessionKeys) {

        try {
            IMProtocolV2.IMEnvelope heartbeat = IMProtocolV2.IMEnvelope.newBuilder()
                    .setVersion(2)
                    .setMessageId("hb-" + System.currentTimeMillis())
                    .setCommand(IMProtocolV2.IMCommandType.HEARTBEAT)
                    .setMessageType(IMProtocolV2.IMMessageType.MSG_HEARTBEAT)
                    .setSenderId("SERVER")
                    .setTimestamp(System.currentTimeMillis())
                    .setSeq(envelope.getSeq())
                    .build();

            byte[] frame = org.project.im.common.protocol.v2.IMProtocolCodec.encode(heartbeat, sessionKeys);
            ctx.writeAndFlush(new io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame(
                    io.netty.buffer.Unpooled.wrappedBuffer(frame)));
        } catch (Exception e) {
            log.error("Heartbeat response failed", e);
        }
    }
}