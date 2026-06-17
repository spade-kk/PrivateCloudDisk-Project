package org.project.im.server.netty.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.im.common.constant.ImConstants;
import org.project.im.common.enums.CommandType;
import org.project.im.common.protocol.MessageProtocol;
import org.project.im.server.netty.SessionManager;
import org.project.im.server.service.MessagePushService;
import org.springframework.stereotype.Component;

/**
 * 消息处理 Handler
 * <p>
 * 处理 WebSocket 文本帧，根据命令字分发到不同的业务逻辑：
 * <ul>
 *   <li>心跳：返回 PONG</li>
 *   <li>发送消息：路由到消息推送服务</li>
 *   <li>消息 ACK：确认消息送达</li>
 *   <li>已读：标记消息已读</li>
 *   <li>正在输入：转发输入状态</li>
 * </ul>
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class MessageHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private final SessionManager sessionManager;
    private final MessagePushService messagePushService;
    private final ObjectMapper objectMapper;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        String text = frame.text();
        if (text == null || text.isEmpty()) return;

        try {
            MessageProtocol protocol = objectMapper.readValue(text, MessageProtocol.class);
            if (protocol.getCommand() == null) {
                sendError(ctx, "命令字不能为空");
                return;
            }
            String userId = sessionManager.getUserId(ctx.channel());
            protocol.setSenderId(userId);

            // 根据命令字分发
            CommandType command = CommandType.fromCode(protocol.getCommand());
            if (command == null) {
                sendError(ctx, "未知命令字: " + protocol.getCommand());
                return;
            }
            switch (command) {
                case HEARTBEAT -> handleHeartbeat(ctx, protocol);
                case SEND_MESSAGE -> messagePushService.handleMessage(ctx, protocol);
                case MESSAGE_ACK -> messagePushService.handleAck(ctx, protocol);
                case READ_MESSAGE -> messagePushService.handleRead(ctx, protocol);
                case TYPING -> messagePushService.handleTyping(ctx, protocol);
                case SYNC_OFFLINE_MESSAGES -> messagePushService.handleSyncOffline(ctx, protocol);
                default -> sendError(ctx, "不支持的命令: " + command.getDescription());
            }
        } catch (Exception e) {
            log.error("消息处理异常: {}", e.getMessage());
            sendError(ctx, "消息格式错误: " + e.getMessage());
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent event) {
            if (event.state() == IdleState.READER_IDLE) {
                // 读空闲超时，断开连接
                log.info("客户端读空闲超时: {}", ctx.channel().remoteAddress());
                sessionManager.remove(ctx.channel());
                ctx.close();
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        sessionManager.remove(ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("消息处理器异常: {}", cause.getMessage());
        sessionManager.remove(ctx.channel());
        ctx.close();
    }

    // ==================== 私有方法 ====================

    /**
     * 处理心跳
     */
    private void handleHeartbeat(ChannelHandlerContext ctx, MessageProtocol request) {
        MessageProtocol response = MessageProtocol.builder()
                .version(ImConstants.PROTOCOL_VERSION)
                .command(CommandType.HEARTBEAT.getCode())
                .timestamp(System.currentTimeMillis())
                .seq(request.getSeq())
                .payload("PONG")
                .build();
        writeResponse(ctx, response);
    }

    /**
     * 发送错误响应
     */
    private void sendError(ChannelHandlerContext ctx, String message) {
        try {
            MessageProtocol response = MessageProtocol.builder()
                    .version(ImConstants.PROTOCOL_VERSION)
                    .command(CommandType.ERROR_NOTIFY.getCode())
                    .timestamp(System.currentTimeMillis())
                    .payload(message)
                    .build();
            writeResponse(ctx, response);
        } catch (Exception e) {
            log.error("发送错误消息失败", e);
        }
    }

    /**
     * 写入响应
     */
    private void writeResponse(ChannelHandlerContext ctx, MessageProtocol protocol) {
        try {
            String json = objectMapper.writeValueAsString(protocol);
            ctx.writeAndFlush(new TextWebSocketFrame(json));
        } catch (Exception e) {
            log.error("序列化响应失败", e);
        }
    }
}