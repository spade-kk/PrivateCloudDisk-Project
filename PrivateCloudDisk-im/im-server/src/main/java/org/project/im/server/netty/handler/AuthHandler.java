package org.project.im.server.netty.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.im.common.constant.ImConstants;
import org.project.im.common.enums.ResponseCode;
import org.project.im.common.protocol.MessageProtocol;
import org.project.im.server.netty.SessionManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 认证 Handler
 * <p>
 * 处理 WebSocket 连接建立后的首次认证流程：
 * <ul>
 *   <li>从 URL 参数中提取 JWT Token</li>
 *   <li>验证 Token 有效性</li>
 *   <li>注册用户会话</li>
 *   <li>存储在线状态到 Redis</li>
 * </ul>
 * <p>
 * 注意：此 Handler 通过 {@code @ChannelHandler.Sharable} 标记为可共享，
 * 可被多个 Channel 共用，因此内部不保存状态。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class AuthHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private final SessionManager sessionManager;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 标记属性名，标识 Channel 是否已认证 */
    private static final String AUTH_ATTR = "authenticated";

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            // WebSocket 握手完成，提取 token 进行认证
            WebSocketServerProtocolHandler.HandshakeComplete handshake =
                    (WebSocketServerProtocolHandler.HandshakeComplete) evt;
            String uri = handshake.requestUri();
            String token = extractToken(uri);

            if (token == null) {
                log.warn("WebSocket 连接缺少 token: {}", ctx.channel().remoteAddress());
                sendError(ctx, ResponseCode.UNAUTHORIZED.getCode(), "缺少认证 Token");
                ctx.close();
                return;
            }
            // 模拟 token 验证（实际应从 JWT 解析 userId）
            String userId = validateToken(token);
            if (userId == null) {
                log.warn("Token 验证失败: {}", ctx.channel().remoteAddress());
                sendError(ctx, ResponseCode.TOKEN_INVALID.getCode(), "Token 无效或已过期");
                ctx.close();
                return;
            }
            // 注册会话
            if (!sessionManager.register(userId, ctx.channel())) {
                sendError(ctx, ResponseCode.CONNECTION_LIMIT_EXCEEDED.getCode(),
                        "连接数已达上限");
                ctx.close();
                return;
            }
            // 标记已认证
            ctx.channel().attr(io.netty.util.AttributeKey.valueOf(AUTH_ATTR)).set(true);
            // 存储在线状态到 Redis
            String key = String.format(ImConstants.REDIS_ONLINE_USERS, userId);
            redisTemplate.opsForHash().put(key, "status", "online");
            redisTemplate.expire(key, ImConstants.HEARTBEAT_TIMEOUT, TimeUnit.SECONDS);

            log.info("用户认证成功: userId={}, remoteAddr={}", userId, ctx.channel().remoteAddress());
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        Boolean authenticated = (Boolean) ctx.channel()
                .attr(io.netty.util.AttributeKey.valueOf(AUTH_ATTR)).get();
        if (authenticated == null || !authenticated) {
            sendError(ctx, ResponseCode.UNAUTHORIZED.getCode(), "请先完成认证");
            ctx.close();
            return;
        }
        // 已认证，传递给下一个 Handler
        ctx.fireChannelRead(frame.retain());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        sessionManager.remove(ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("认证处理器异常: {}", cause.getMessage());
        sessionManager.remove(ctx.channel());
        ctx.close();
    }

    // ==================== 私有方法 ====================

    /**
     * 从 URL 中提取 token
     */
    private String extractToken(String uri) {
        if (uri == null) return null;
        // 尝试从查询参数获取 token
        if (uri.contains("?")) {
            String query = uri.substring(uri.indexOf("?") + 1);
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && "token".equals(kv[0])) {
                    return kv[1];
                }
            }
        }
        return null;
    }

    /**
     * 验证 Token 并返回 userId
     * TODO: 实际项目中应集成 JWT 解析
     */
    private String validateToken(String token) {
        // 模拟 token 验证
        if (token == null || token.isEmpty()) return null;
        // 实际应使用 JWT 解析：
        // Claims claims = Jwts.parser().verifyWith(secretKey).build()
        //         .parseSignedClaims(token).getPayload();
        // return claims.getSubject();
        return "user_" + token.hashCode();
    }

    /**
     * 向客户端发送错误消息
     */
    private void sendError(ChannelHandlerContext ctx, int code, String message) {
        try {
            MessageProtocol protocol = MessageProtocol.builder()
                    .version(ImConstants.PROTOCOL_VERSION)
                    .command(902) // ERROR_NOTIFY
                    .timestamp(System.currentTimeMillis())
                    .payload(Map.of("code", code, "message", message))
                    .build();
            String json = objectMapper.writeValueAsString(protocol);
            ctx.writeAndFlush(new TextWebSocketFrame(json));
        } catch (Exception e) {
            log.error("发送错误消息失败", e);
        }
    }
}