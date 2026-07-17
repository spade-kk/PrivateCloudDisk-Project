package org.project.grpc.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.grpc.config.GrpcServerProperties;
import org.project.grpc.interceptor.GrpcServerInterceptor;
import org.project.grpc.service.InternalStorageGrpcService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * gRPC 服务端启动器。
 *
 * <p>在 Spring Boot 应用启动后自动启动 gRPC Server，独立于 Tomcat HTTP 端口运行。
 *
 * <p>架构设计：
 * <ul>
 *   <li>gRPC Server 与 HTTP Server 共享同一个 Spring Context</li>
 *   <li>gRPC 服务实现类直接注入 Spring Bean，复用现有 Service 层</li>
 *   <li>通过 {@link GrpcServerProperties} 控制端口、TLS、反射等配置</li>
 *   <li>优雅关闭：先拒接新请求，等待进行中请求完成再关闭</li>
 * </ul>
 *
 * <p>生命周期：
 * <pre>
 *   Spring Boot start → @PostConstruct → startGrpcServer()
 *   Spring Boot stop  → @PreDestroy   → shutdownGrpcServer()
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrpcServerLauncher {

    private final GrpcServerProperties properties;
    private final InternalStorageGrpcService internalStorageGrpcService;
    private final GrpcServerInterceptor grpcInterceptor;

    private Server server;

    /**
     * Spring Boot 启动后自动启动 gRPC Server。
     */
    @PostConstruct
    public void start() throws IOException {
        if (!properties.isEnabled()) {
            log.info("[gRPC] gRPC Server 已禁用 (grpc.server.enabled=false)");
            return;
        }

        ServerBuilder<?> builder = ServerBuilder.forPort(properties.getPort())
                // 注册内部存储服务
                .addService(internalStorageGrpcService)
                // 全局拦截器（认证 + 日志 + traceId）
                .intercept(grpcInterceptor);

        // 启用 gRPC 反射服务（方便 grpcurl 等工具调试）
        if (properties.isReflectionEnabled()) {
            builder.addService(ProtoReflectionService.newInstance());
            log.info("[gRPC] 反射服务已启用（grpcurl 可用）");
        }

        server = builder.build().start();
        log.info("[gRPC] gRPC Server 已启动 | 端口: {} | TLS: {} | 反射: {}",
                properties.getPort(),
                properties.isTlsEnabled() ? "启用" : "禁用",
                properties.isReflectionEnabled() ? "启用" : "禁用");

        // 注册 JVM 关闭钩子（兜底）
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("[gRPC] JVM 关闭钩子触发，正在停止 gRPC Server...");
            shutdown();
        }));
    }

    /**
     * Spring Boot 关闭时优雅停止 gRPC Server。
     */
    @PreDestroy
    public void shutdown() {
        if (server != null && !server.isShutdown()) {
            log.info("[gRPC] 正在优雅关闭 gRPC Server...");
            try {
                server.shutdown();
                if (!server.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warn("[gRPC] gRPC Server 未在 10 秒内完成关闭，强制终止");
                    server.shutdownNow();
                }
                log.info("[gRPC] gRPC Server 已停止");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                server.shutdownNow();
                log.warn("[gRPC] gRPC Server 关闭被中断，已强制终止");
            }
        }
    }
}