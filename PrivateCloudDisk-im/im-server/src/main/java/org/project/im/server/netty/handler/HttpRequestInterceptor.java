package org.project.im.server.netty.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;

/**
 * HTTP 请求拦截器
 * <p>
 * 在 WebSocket 握手之前拦截 HTTP 请求，剥离查询字符串，
 * 确保 {@link io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler}
 * 能够正确匹配 WebSocket 路径。
 * </p>
 *
 * <h3>背景</h3>
 * <p>
 * 客户端连接 WebSocket 时通常在 URL 中携带 token 参数，例如：
 * <pre>ws://localhost:9090/ws?token=eyJhbGciOi...</pre>
 * Netty 的 {@code WebSocketServerProtocolHandler} 在匹配路径时，
 * 会将 {@code /ws?token=eyJhbGciOi...} 与配置的 {@code /ws} 进行比较。
 * 虽然某些版本能正确处理查询字符串，但为了确保兼容性，
 * 本拦截器在 HTTP 请求到达 WebSocket 协议处理器之前，
 * 将 URI 重写为不含查询字符串的形式（{@code /ws}），
 * 同时将原始 URI（含查询参数）存储到 Channel 属性中，
 * 供后续 {@link AuthHandler} 提取 token 使用。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Slf4j
public class HttpRequestInterceptor extends ChannelInboundHandlerAdapter {

    /** Channel 属性键：存储原始请求 URI（含查询参数） */
    public static final String ORIGINAL_URI_ATTR = "originalRequestUri";

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest request) {
            String rawUri = request.uri();

            // 将原始 URI 保存到 Channel 属性，供 AuthHandler 提取 token
            ctx.channel().attr(
                    io.netty.util.AttributeKey.valueOf(ORIGINAL_URI_ATTR)
            ).set(rawUri);

            // 剥离查询字符串，只保留路径部分
            // 例如：/ws?token=xxx → /ws
            if (rawUri.contains("?")) {
                String pathOnly = rawUri.substring(0, rawUri.indexOf("?"));
                request.setUri(pathOnly);
                log.debug("剥离查询字符串: {} → {}", rawUri, pathOnly);
            }
        }
        // 传递给下一个 Handler（WebSocketServerProtocolHandler）
        super.channelRead(ctx, msg);
    }
}
