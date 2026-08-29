package org.project.im.server.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

// ============================================================
// gRPC 服务端配置 v2.0 — IM Server gRPC 服务启动
// ============================================================
// 职责：
//   1. 在 IM Server 启动时同时启动 gRPC 服务端
//   2. 监听 gRPC 端口，接收 IM Router 的 PushMessage 等请求
//   3. 优雅停止：等待在途 RPC 完成
//
// 端口规划：
//   - WebSocket 端口（9091）— 客户端连接
//   - gRPC 端口（9092）— IM Router → IM Server 内部通信
// ============================================================

/**
 * gRPC 服务端配置
 * <p>
 * 在 Spring Boot 启动时创建并启动 gRPC Server，
 * 注册 {@link IMServerServiceImpl} 处理 IM Router 的请求。
 * </p>
 *
 * <h3>启动流程</h3>
 * <pre>
 * Spring Boot 启动
 *   → @PostConstruct start()
 *     → ServerBuilder.forPort(grpcPort)
 *       .addService(IMServerServiceImpl)
 *       .maxInboundMessageSize(4MB)
 *       .build()
 *       .start()
 *     → 注册 JVM 钩子，确保 JVM 异常退出时关闭 gRPC
 * </pre>
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrpcServerConfig {

    private final IMServerServiceImpl imServerService;

    /** gRPC 服务端口 */
    @Value("${im.server.grpc-port:9092}")
    private int grpcPort;

    /** 最大入站消息大小（默认 4MB） */
    @Value("${im.server.grpc-max-inbound-size:4194304}")
    private int maxInboundMessageSize;

    /** gRPC Server 实例 */
    private Server grpcServer;

    /**
     * 启动 gRPC 服务
     */
    @PostConstruct
    public void start() throws IOException {
        grpcServer = ServerBuilder.forPort(grpcPort)
                .addService(imServerService)
                .maxInboundMessageSize(maxInboundMessageSize)
                // 使用默认的线程池（ForkJoinPool），gRPC 内部会管理并发
                .build()
                .start();

        // 注册 JVM 关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("JVM 关闭钩子触发，停止 gRPC 服务...");
            stop();
        }));

        log.info("gRPC 服务已启动，监听端口: {} (maxInboundSize={}bytes)",
                grpcPort, maxInboundMessageSize);
    }

    /**
     * 优雅停止 gRPC 服务
     * <p>
     * 等待在途 RPC 请求完成（最长 30 秒），然后关闭。
     * </p>
     */
    @PreDestroy
    public void stop() {
        if (grpcServer == null) return;

        try {
            log.info("开始优雅停止 gRPC 服务...");
            grpcServer.shutdown();
            if (!grpcServer.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("gRPC 服务在 30s 内未完成停止，强制关闭");
                grpcServer.shutdownNow();
            }
            log.info("gRPC 服务已停止");
        } catch (InterruptedException e) {
            log.error("gRPC 停止被中断", e);
            Thread.currentThread().interrupt();
            grpcServer.shutdownNow();
        }
    }

    /**
     * 阻塞等待 gRPC 服务终止（用于 main 方法场景）
     */
    public void blockUntilShutdown() throws InterruptedException {
        if (grpcServer != null) {
            grpcServer.awaitTermination();
        }
    }
}
