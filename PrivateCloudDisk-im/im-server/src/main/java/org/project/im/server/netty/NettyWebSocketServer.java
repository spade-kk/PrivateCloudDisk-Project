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
import org.project.im.server.netty.handler.HttpRequestInterceptor;
import org.project.im.server.netty.handler.V2AuthHandler;
import org.project.im.server.netty.handler.V2MessageHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

// ============================================================
// Netty WebSocket 服务器 v2.0 — 仅二进制协议
// ============================================================
// 重构要点：
//   1. 移除 V1 JSON 协议服务器（不再启动 9090 端口的 TextWebSocketFrame 处理）
//   2. 仅保留 V2 Protobuf 二进制协议服务器
//   3. 统一使用 /ws 路径（不再区分 /ws 和 /ws/v2）
//   4. Netty 线程模型优化：Boss 1 线程，Worker = CPU*2
//   5. 堆外内存管理：SO_SNDBUF/SO_RCVBUF 64KB，TCP_NODELAY
// ============================================================

/**
 * Netty WebSocket 服务器
 * <p>
 * 基于 Netty 的高性能 WebSocket 服务器，仅支持 V2 二进制协议。
 * </p>
 *
 * <h3>Channel Pipeline 结构</h3>
 * <pre>
 * IdleStateHandler(90s/30s) → HttpServerCodec → ChunkedWriteHandler
 *   → HttpObjectAggregator(256KB) → HttpRequestInterceptor
 *   → WebSocketServerProtocolHandler(/ws)
 *   → V2AuthHandler（JWT 认证 + ECDH 密钥协商）
 *   → V2MessageHandler（二进制消息处理 + 双层解密）
 * </pre>
 *
 * <h3>线程模型</h3>
 * <ul>
 *   <li>Boss Group: 1 线程（接收连接）</li>
 *   <li>Worker Group: 默认 8 线程（可配置，建议 = CPU 核心数 * 2）</li>
 *   <li>gRPC 线程池: 由 gRPC 框架管理（处理 IM Router 请求）</li>
 * </ul>
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NettyWebSocketServer {

    // ===== V2 协议处理器 =====
    private final V2AuthHandler v2AuthHandler;
    private final V2MessageHandler v2MessageHandler;
    private final SessionManager sessionManager;

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
     * 启动 Netty WebSocket 服务器（仅 V2 二进制协议）
     */
    @PostConstruct
    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(bossThreads);
        workerGroup = new NioEventLoopGroup(workerThreads);

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                // 连接队列 backlog
                .option(ChannelOption.SO_BACKLOG, 1024)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                // 堆外内存缓冲区
                .childOption(ChannelOption.SO_SNDBUF, 65536)
                .childOption(ChannelOption.SO_RCVBUF, 65536)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        // 心跳检测：读空闲 90s（超过则断开），写空闲 30s（触发心跳）
                        pipeline.addLast(new IdleStateHandler(90, 30, 0, TimeUnit.SECONDS));
                        // HTTP 编解码
                        pipeline.addLast(new HttpServerCodec());
                        pipeline.addLast(new ChunkedWriteHandler());
                        // HTTP 消息聚合（最大 256KB，V2 协议可能包含密钥交换数据）
                        pipeline.addLast(new HttpObjectAggregator(256 * 1024));
                        // 在 WebSocket 协议处理器之前拦截 HTTP 请求，剥离查询字符串
                        // 解决 ws://host:port/ws?token=xxx 握手失败的问题
                        pipeline.addLast(new HttpRequestInterceptor());
                        // WebSocket 协议处理（统一 /ws 路径）
                        pipeline.addLast(new WebSocketServerProtocolHandler(websocketPath,
                                null, true, 256 * 1024));
                        // V2 认证 + ECDH 密钥协商
                        pipeline.addLast(v2AuthHandler);
                        // V2 二进制消息处理（Protobuf + 双层加密）
                        pipeline.addLast(v2MessageHandler);
                    }
                });

        ChannelFuture future = bootstrap.bind(port).sync();
        serverChannel = future.channel();
        log.info("Netty WebSocket 服务器启动（V2 二进制协议），监听端口: {} (路径: {})",
                port, websocketPath);

        // Netty 端口绑定成功后，向 Redis 注册节点信息并启动心跳
        sessionManager.onServerStarted();
    }

    /**
     * 关闭 Netty 服务器
     * <p>
     * 关闭流程（与需求 6.3 对齐）：
     * ① 关闭 serverChannel（停止接受新连接）
     * ② 关闭 bossGroup/workerGroup（等待现有消息处理完成 + 关闭所有连接）
     * ③ 执行 Redis 注销（清理节点注册和用户映射）
     * </p>
     */
    @PreDestroy
    public void stop() {
        log.info("开始关闭 Netty WebSocket 服务器...");

        // ① 停止接受新连接
        if (serverChannel != null) {
            serverChannel.close();
        }

        // ② 关闭 EventLoopGroup（等待在途消息处理，通知所有连接断开）
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }

        log.info("Netty 连接已关闭，执行 Redis 注销...");

        // ③ 执行 Redis 注销（节点信息 + 用户映射清理）
        sessionManager.onServerShutdown();

        log.info("Netty WebSocket 服务器已关闭");
    }
}
