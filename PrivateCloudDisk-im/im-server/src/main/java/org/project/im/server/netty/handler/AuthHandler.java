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
import org.project.im.server.security.JwtTokenVerifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 认证 Handler
 * <p>
 * 处理 WebSocket 连接建立后的首次认证流程：
 * <ul>
 *   <li>从 URL 参数中提取 JWT Token</li>
 *   <li>使用 RSA 公钥验证 Token 有效性（与 platform-service 共享密钥对）</li>
 *   <li>从 Token subject 中提取用户 ID</li>
 *   <li>注册用户会话</li>
 *   <li>存储在线状态到 Redis</li>
 * </ul>
 * </p>
 *
 * <h3>认证流程</h3>
 * <pre>
 * 客户端连接 ws://host:9090/ws?token=eyJhbGciOi...
 *    ↓
 * HttpRequestInterceptor 剥离查询字符串，保存原始 URI 到 Channel 属性
 *    ↓
 * WebSocketServerProtocolHandler 完成 WebSocket 握手
 *    ↓
 * AuthHandler.userEventTriggered(HandshakeComplete)
 *    ├── 从 Channel 属性读取原始 URI
 *    ├── 从查询参数中提取 token
 *    ├── JwtTokenVerifier 验证 RSA 签名 + 提取 userId
 *    ├── SessionManager 注册用户会话
 *    └── Redis 标记用户在线
 * </pre>
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
    private final JwtTokenVerifier jwtTokenVerifier;

    /** 标记属性名，标识 Channel 是否已认证 */
    private static final String AUTH_ATTR = "authenticated";

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            // WebSocket 握手完成，提取 token 进行认证
            String token = extractTokenFromChannel(ctx);

            if (token == null) {
                log.warn("WebSocket 连接缺少 token: {}", ctx.channel().remoteAddress());
                sendError(ctx, ResponseCode.UNAUTHORIZED.getCode(), "缺少认证 Token");
                ctx.close();
                return;
            }

            // 使用 JWT 验证 Token（RSA 公钥验证签名，提取 subject 中的 userId）
            String userId = jwtTokenVerifier.verifyToken(token);
            if (userId == null) {
                log.warn("Token 验证失败: remoteAddr={}", ctx.channel().remoteAddress());
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
     * 从 Channel 属性中提取 token
     * <p>
     * {@link HttpRequestInterceptor} 在握手前将原始请求 URI（含查询参数）
     * 保存到 Channel 属性中。这里读取该 URI 并提取 token 参数。
     * </p>
     *
     * @param ctx Channel 上下文
     * @return token 字符串，如果不存在返回 null
     */
    private String extractTokenFromChannel(ChannelHandlerContext ctx) {
        // 优先从 HttpRequestInterceptor 保存的 Channel 属性中获取
        String originalUri = (String) ctx.channel()
                .attr(io.netty.util.AttributeKey.valueOf(HttpRequestInterceptor.ORIGINAL_URI_ATTR))
                .get();

        if (originalUri != null) {
            String token = extractTokenFromUri(originalUri);
            if (token != null) {
                return token;
            }
        }

        // 降级：从 HandshakeComplete 事件中获取（某些 Netty 版本可能保留原始 URI）
        // 注意：此分支是兜底逻辑，正常情况下不会走到这里
        log.warn("Channel 属性中未找到原始 URI，尝试从 HandshakeComplete 获取");
        return null;
    }

    /**
     * 从 URI 中提取 token 查询参数
     * <p>
     * 支持以下格式：
     * <ul>
     *   <li>{@code /ws?token=eyJhbGciOi...}</li>
     *   <li>{@code /ws?token=eyJhbGciOi...&userId=xxx}</li>
     *   <li>{@code /ws?userId=xxx&token=eyJhbGciOi...}</li>
     * </ul>
     * </p>
     *
     * @param uri 请求 URI
     * @return token 字符串，如果不存在返回 null
     */
    private String extractTokenFromUri(String uri) {
        if (uri == null || uri.isEmpty()) {
            return null;
        }

        try {
            // 如果是完整 URL（ws://host:port/ws?token=xxx），解析为 URI
            String query;
            if (uri.startsWith("ws://") || uri.startsWith("wss://") || uri.startsWith("http://") || uri.startsWith("https://")) {
                URI fullUri = URI.create(uri);
                query = fullUri.getRawQuery();
            } else {
                // 相对路径（/ws?token=xxx）
                int queryIndex = uri.indexOf('?');
                query = queryIndex >= 0 ? uri.substring(queryIndex + 1) : null;
            }

            if (query == null || query.isEmpty()) {
                return null;
            }

            // 遍历查询参数，查找 token
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && "token".equals(kv[0])) {
                    return java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            log.warn("从 URI 提取 token 失败: uri={}, error={}", uri, e.getMessage());
        }

        return null;
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
