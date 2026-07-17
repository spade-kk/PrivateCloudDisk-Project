package org.project.im.server.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.im.server.netty.handler.AuthHandler;
import org.project.im.server.netty.handler.HttpRequestInterceptor;
import org.project.im.server.netty.handler.MessageHandler;
import org.project.im.server.netty.handler.V2AuthHandler;
import org.project.im.server.netty.handler.V2MessageHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Netty WebSocket 服务器
 * <p>
 * 基于 Netty 的高性能 WebSocket 服务器，负责：
 * <ul>
 *   <li>监听 WebSocket 连接请求</li>
 *   <li>管理 Channel 管道（编解码、认证、心跳、消息处理）</li>
 *   <li>Boss/Worker 线程池管理</li>
 * </ul>
 * <p>
 * Channel Pipeline 结构：
 * <pre>
 * IdleStateHandler → HttpServerCodec → ChunkedWriteHandler
 *   → HttpObjectAggregator → WebSocketServerProtocolHandler
 *   → AuthHandler → MessageHandler → ExceptionHandler
 * </pre>
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NettyWebSocketServer {

    private final AuthHandler authHandler;
    private final MessageHandler messageHandler;

    // ===== V2 协议处理器 =====
    private final V2AuthHandler v2AuthHandler;
    private final V2MessageHandler v2MessageHandler;

    @Value("${netty.websocket.port:9090}")
    private int port;

    @Value("${netty.websocket.port-v2:9091}")
    private int v2Port;

    @Value("${netty.boss.threads:1}")
    private int bossThreads;

    @Value("${netty.worker.threads:8}")
    private int workerThreads;

    @Value("${netty.websocket-path:/ws}")
    private String websocketPath;

    @Value("${netty.websocket-path-v2:/ws/v2}")
    private String v2WebsocketPath;

    @Value("${im.protocol.v2.enabled:true}")
    private boolean v2Enabled;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private Channel v2ServerChannel;

    /**
     * 启动 Netty 服务器
     */
    @PostConstruct
    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(bossThreads);
        workerGroup = new NioEventLoopGroup(workerThreads);

        // V1 服务器（JSON 协议，兼容旧客户端）
        startV1Server();

        // V2 服务器（Protobuf 二进制协议，新客户端）
        if (v2Enabled) {
            startV2Server();
        }
    }

    private void startV1Server() throws InterruptedException {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_SNDBUF, 65536)
                .childOption(ChannelOption.SO_RCVBUF, 65536)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new IdleStateHandler(90, 30, 0, TimeUnit.SECONDS));
                        pipeline.addLast(new HttpServerCodec());
                        pipeline.addLast(new ChunkedWriteHandler());
                        pipeline.addLast(new HttpObjectAggregator(65536));
                        // 在 WebSocket 协议处理器之前拦截 HTTP 请求，剥离查询字符串
                        // 解决 ws://host:port/ws?token=xxx 握手失败的问题
                        pipeline.addLast(new HttpRequestInterceptor());
                        pipeline.addLast(new WebSocketServerProtocolHandler(websocketPath));
                        pipeline.addLast(authHandler);
                        pipeline.addLast(messageHandler);
                    }
                });

        ChannelFuture future = bootstrap.bind(port).sync();
        serverChannel = future.channel();
        log.info("Netty WebSocket V1 服务器启动，监听端口: {}", port);
    }

    private void startV2Server() throws InterruptedException {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_SNDBUF, 65536)
                .childOption(ChannelOption.SO_RCVBUF, 65536)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        // 心跳检测（读空闲 90s）
                        pipeline.addLast(new IdleStateHandler(90, 30, 0, TimeUnit.SECONDS));
                        // HTTP 编解码
                        pipeline.addLast(new HttpServerCodec());
                        pipeline.addLast(new ChunkedWriteHandler());
                        // HTTP 消息聚合（最大 256KB，V2 协议可能包含密钥交换数据）
                        pipeline.addLast(new HttpObjectAggregator(256 * 1024));
                        // 在 WebSocket 协议处理器之前拦截 HTTP 请求，剥离查询字符串
                        pipeline.addLast(new HttpRequestInterceptor());
                        // WebSocket 协议处理
                        pipeline.addLast(new WebSocketServerProtocolHandler(v2WebsocketPath,
                                null, true, 256 * 1024));
                        // V2 认证 + 密钥协商
                        pipeline.addLast(v2AuthHandler);
                        // V2 二进制消息处理
                        pipeline.addLast(v2MessageHandler);
                    }
                });

        ChannelFuture future = bootstrap.bind(v2Port).sync();
        v2ServerChannel = future.channel();
        log.info("Netty WebSocket V2 服务器启动，监听端口: {} (路径: {})",
                v2Port, v2WebsocketPath);
    }

    /**
     * 关闭 Netty 服务器
     */
    @PreDestroy
    public void stop() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (v2ServerChannel != null) {
            v2ServerChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("Netty WebSocket 服务器已关闭");
    }
}