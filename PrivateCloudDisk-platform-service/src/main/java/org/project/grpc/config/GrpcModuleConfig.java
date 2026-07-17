package org.project.grpc.config;

import lombok.RequiredArgsConstructor;
import org.project.grpc.interceptor.GrpcServerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * gRPC 模块 Spring 配置。
 *
 * <p>负责创建 gRPC 相关的 Spring Bean（拦截器等）。
 * gRPC Server 自身的启动由 {@link org.project.grpc.server.GrpcServerLauncher} 管理。
 */
@Configuration
@RequiredArgsConstructor
public class GrpcModuleConfig {

    private final GrpcServerProperties grpcServerProperties;

    /**
     * gRPC 服务端拦截器 Bean。
     * <p>全局应用于所有 gRPC 服务方法，提供认证、日志、traceId 注入。
     */
    @Bean
    public GrpcServerInterceptor grpcServerInterceptor() {
        return new GrpcServerInterceptor(grpcServerProperties);
    }
}