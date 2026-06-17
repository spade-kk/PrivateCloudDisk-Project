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
import org.project.im.server.netty.handler.MessageHandler;
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

    @Value("${netty.websocket.port:9090}")
    private int port;

    @Value("${netty.boss.threads:1}")
    private int bossThreads;

    @Value("${netty.worker.threads:8}")
    private int workerThreads;

    @Value("${netty.websocket-path:/ws}")
    private String websocketPath;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    /**
     * 启动 Netty 服务器
     */
    @PostConstruct
    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(bossThreads);
        workerGroup = new NioEventLoopGroup(workerThreads);

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
                        // 心跳检测（读空闲 90s 触发断开）
                        pipeline.addLast(new IdleStateHandler(90, 30, 0, TimeUnit.SECONDS));
                        // HTTP 编解码
                        pipeline.addLast(new HttpServerCodec());
                        // 支持大数据流写入
                        pipeline.addLast(new ChunkedWriteHandler());
                        // HTTP 消息聚合（最大 65536 字节）
                        pipeline.addLast(new HttpObjectAggregator(65536));
                        // WebSocket 协议处理
                        pipeline.addLast(new WebSocketServerProtocolHandler(websocketPath));
                        // 自定义认证 Handler
                        pipeline.addLast(authHandler);
                        // 自定义消息处理 Handler
                        pipeline.addLast(messageHandler);
                    }
                });

        ChannelFuture future = bootstrap.bind(port).sync();
        serverChannel = future.channel();

        log.info("Netty WebSocket 服务器启动成功，监听端口: {}", port);
    }

    /**
     * 关闭 Netty 服务器
     */
    @PreDestroy
    public void stop() {
        if (serverChannel != null) {
            serverChannel.close();
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