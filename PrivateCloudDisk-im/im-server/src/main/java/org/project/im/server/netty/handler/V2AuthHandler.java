package org.project.im.server.netty.handler;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.im.common.constant.ImConstants;
import org.project.im.common.enums.ResponseCode;
import org.project.im.common.security.IMCryptoCodec;
import org.project.im.common.security.IMSessionKeyManager;
import org.project.im.common.security.IMSessionKeys;
import org.project.im.server.netty.SessionManager;
import org.project.im.server.security.JwtTokenVerifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * IM v2 认证处理器
 * <p>
 * 处理 WebSocket 连接建立后的认证与密钥协商流程：
 * <ol>
 *   <li>WebSocket 握手完成 → 提取 JWT Token</li>
 *   <li>验证 Token 有效性</li>
 *   <li>等待客户端发送密钥交换请求</li>
 *   <li>执行 ECDH 密钥协商 → 生成 Session Keys</li>
 *   <li>返回密钥交换响应（含服务端公钥 + RSA 签名）</li>
 *   <li>标记连接已认证 → 传递给 V2MessageHandler</li>
 * </ol>
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class V2AuthHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private final SessionManager sessionManager;
    private final IMSessionKeyManager keyManager = IMSessionKeyManager.createIMSessionKeyManager();
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final JwtTokenVerifier jwtTokenVerifier;

    /** 标记属性名 */
    private static final String AUTH_ATTR = "v2:authenticated";
    private static final String CONNECTION_ID = "v2:connection_id";
    private static final String USER_ID = "v2:user_id";

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            // 从 Channel 属性中提取 token（由 HttpRequestInterceptor 保存）
            String token = extractTokenFromChannel(ctx);

            if (token == null) {
                log.warn("V2 WebSocket 连接缺少 token: {}", ctx.channel().remoteAddress());
                sendError(ctx, ResponseCode.UNAUTHORIZED.getCode(), "缺少认证 Token");
                ctx.close();
                return;
            }

            // 使用 JWT 验证 Token（RSA 公钥验证签名，提取 subject 中的 userId）
            String userId = jwtTokenVerifier.verifyToken(token);
            if (userId == null) {
                log.warn("V2 Token 验证失败: {}", ctx.channel().remoteAddress());
                sendError(ctx, ResponseCode.TOKEN_INVALID.getCode(), "Token 无效或已过期");
                ctx.close();
                return;
            }

            // 生成连接 ID
            String connectionId = UUID.randomUUID().toString().replace("-", "");
            ctx.channel().attr(io.netty.util.AttributeKey.valueOf(CONNECTION_ID)).set(connectionId);
            ctx.channel().attr(io.netty.util.AttributeKey.valueOf(USER_ID)).set(userId);

            // 注册会话
            if (!sessionManager.register(userId, ctx.channel())) {
                sendError(ctx, ResponseCode.CONNECTION_LIMIT_EXCEEDED.getCode(),
                        "连接数已达上限");
                ctx.close();
                return;
            }

            // 存储在线状态
            String key = String.format(ImConstants.REDIS_ONLINE_USERS, userId);
            redisTemplate.opsForHash().put(key, "status", "online");
            redisTemplate.expire(key, ImConstants.HEARTBEAT_TIMEOUT, TimeUnit.SECONDS);

            log.info("V2 用户认证成功: userId={}, connectionId={}, remoteAddr={}",
                    userId, connectionId, ctx.channel().remoteAddress());

            // 发送服务端公钥（触发客户端密钥交换）
            sendServerPublicKey(ctx, connectionId);
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        String text = frame.text();
        if (text == null || text.isEmpty()) return;

        String userId = getUserId(ctx);
        String connectionId = getConnectionId(ctx);

        if (userId == null || connectionId == null) {
            sendError(ctx, ResponseCode.UNAUTHORIZED.getCode(), "请先完成认证");
            ctx.close();
            return;
        }

        try {
            // 解析密钥交换请求
            Map<String, Object> keyExchangeReq = objectMapper.readValue(text, Map.class);
            String type = (String) keyExchangeReq.get("type");

            if ("KEY_EXCHANGE".equals(type)) {
                handleKeyExchange(ctx, userId, connectionId, keyExchangeReq);
            } else {
                // 非密钥交换消息，检查是否已认证
                Boolean authenticated = (Boolean) ctx.channel()
                        .attr(io.netty.util.AttributeKey.valueOf(AUTH_ATTR)).get();
                if (authenticated == null || !authenticated) {
                    sendError(ctx, ResponseCode.UNAUTHORIZED.getCode(),
                            "请先完成密钥协商");
                    return;
                }
                // 已认证，传递给下一个 Handler
                ctx.fireChannelRead(frame.retain());
            }
        } catch (Exception e) {
            log.error("V2 认证消息处理失败: {}", e.getMessage());
            sendError(ctx, ResponseCode.INTERNAL_ERROR.getCode(),
                    "消息格式错误: " + e.getMessage());
        }
    }

    // ==================== 密钥交换 ====================

    /**
     * 处理客户端密钥交换请求
     */
    private void handleKeyExchange(ChannelHandlerContext ctx, String userId,
                                   String connectionId, Map<String, Object> request) {
        try {
            // 获取客户端公钥
            String clientPublicKeyBase64 = (String) request.get("clientPublicKey");
            if (clientPublicKeyBase64 == null) {
                sendError(ctx, ResponseCode.BAD_REQUEST.getCode(), "缺少客户端公钥");
                return;
            }
            byte[] clientPublicKey = java.util.Base64.getDecoder().decode(clientPublicKeyBase64);

            // 执行 ECDH 密钥协商
            IMSessionKeys sessionKeys = keyManager.negotiate(userId, connectionId, clientPublicKey);

            // 标记已认证
            ctx.channel().attr(io.netty.util.AttributeKey.valueOf(AUTH_ATTR)).set(true);

            // 构建密钥交换响应
            Map<String, Object> response = Map.of(
                    "type", "KEY_EXCHANGE_RESPONSE",
                    "serverPublicKey", java.util.Base64.getEncoder().encodeToString(
                            keyManager.getServerPublicKey()),
                    "sessionKeyId", sessionKeys.getKeyId(),
                    "algorithm", 1, // AES-256-GCM
                    "expireAt", sessionKeys.getExpireAt().toEpochMilli(),
                    "signature", java.util.Base64.getEncoder().encodeToString(
                            keyManager.sign(sessionKeys.getSessionKey().getEncoded()))
            );

            String jsonResponse = objectMapper.writeValueAsString(response);
            ctx.writeAndFlush(new TextWebSocketFrame(jsonResponse));

            log.info("V2 密钥协商完成: userId={}, connectionId={}, keyId={}",
                    userId, connectionId, sessionKeys.getKeyId());

        } catch (Exception e) {
            log.error("密钥协商失败: userId={}", userId, e);
            sendError(ctx, ResponseCode.INTERNAL_ERROR.getCode(),
                    "密钥协商失败: " + e.getMessage());
        }
    }

    /**
     * 发送服务端公钥（触发客户端发起密钥交换）
     */
    private void sendServerPublicKey(ChannelHandlerContext ctx, String connectionId) {
        try {
            Map<String, Object> message = Map.of(
                    "type", "SERVER_HELLO",
                    "connectionId", connectionId,
                    "serverPublicKey", java.util.Base64.getEncoder().encodeToString(
                            keyManager.getServerPublicKey()),
                    "supportedAlgorithms", java.util.List.of(1) // AES-256-GCM
            );
            String json = objectMapper.writeValueAsString(message);
            ctx.writeAndFlush(new TextWebSocketFrame(json));
        } catch (Exception e) {
            log.error("发送服务端公钥失败", e);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        cleanupConnection(ctx);
        sessionManager.remove(ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("V2 认证处理器异常: {}", cause.getMessage());
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

    private String getUserId(ChannelHandlerContext ctx) {
        return (String) ctx.channel().attr(
                io.netty.util.AttributeKey.valueOf(USER_ID)).get();
    }

    private String extractTokenFromChannel(ChannelHandlerContext ctx) {
        // 从 HttpRequestInterceptor 保存的 Channel 属性中获取原始 URI
        String originalUri = (String) ctx.channel()
                .attr(io.netty.util.AttributeKey.valueOf(HttpRequestInterceptor.ORIGINAL_URI_ATTR))
                .get();

        if (originalUri != null) {
            String token = extractTokenFromUri(originalUri);
            if (token != null) {
                return token;
            }
        }

        log.warn("V2 Channel 属性中未找到原始 URI");
        return null;
    }

    private String extractTokenFromUri(String uri) {
        if (uri == null || uri.isEmpty()) return null;

        try {
            String query;
            if (uri.startsWith("ws://") || uri.startsWith("wss://") || uri.startsWith("http://") || uri.startsWith("https://")) {
                URI fullUri = URI.create(uri);
                query = fullUri.getRawQuery();
            } else {
                int queryIndex = uri.indexOf('?');
                query = queryIndex >= 0 ? uri.substring(queryIndex + 1) : null;
            }

            if (query == null || query.isEmpty()) return null;

            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && "token".equals(kv[0])) {
                    return java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            log.warn("V2 从 URI 提取 token 失败: uri={}, error={}", uri, e.getMessage());
        }

        return null;
    }

    private void sendError(ChannelHandlerContext ctx, int code, String message) {
        try {
            Map<String, Object> error = Map.of(
                    "type", "ERROR",
                    "code", code,
                    "message", message
            );
            String json = objectMapper.writeValueAsString(error);
            ctx.writeAndFlush(new TextWebSocketFrame(json));
        } catch (Exception e) {
            log.error("发送错误消息失败", e);
        }
    }
}