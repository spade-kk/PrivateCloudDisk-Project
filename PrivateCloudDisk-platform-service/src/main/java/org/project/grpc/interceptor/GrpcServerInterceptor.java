package org.project.grpc.interceptor;

import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import org.project.grpc.config.GrpcServerProperties;

import java.util.UUID;

/**
 * gRPC 服务端拦截器 —— 统一处理认证、日志、异常。
 *
 * <p>职责：
 * <ul>
 *   <li>提取请求元数据中的内部认证 Token 并校验</li>
 *   <li>为每个请求生成 traceId 并注入 Context</li>
 *   <li>记录请求耗时日志（慢请求告警）</li>
 *   <li>统一异常转换，将业务异常映射为 gRPC Status</li>
 * </ul>
 *
 * <p>认证机制：
 * <ul>
 *   <li>从 gRPC metadata 中读取 {@code authorization} 头</li>
 *   <li>格式: {@code Bearer <internal-auth-token>}</li>
 *   <li>开发环境（internalAuthToken 为空）跳过认证</li>
 *   <li>生产环境通过 mTLS + Token 双重认证</li>
 * </ul>
 */
@Slf4j
public class GrpcServerInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private static final Context.Key<String> TRACE_ID_KEY =
            Context.key("traceId");

    private final GrpcServerProperties properties;

    public GrpcServerInterceptor(GrpcServerProperties properties) {
        this.properties = properties;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String traceId = UUID.randomUUID().toString().substring(0, 8);
        String methodName = call.getMethodDescriptor().getFullMethodName();
        long startNanos = System.nanoTime();

        // 1. 认证检查
        String authToken = properties.getInternalAuthToken();
        if (authToken != null && !authToken.isEmpty()) {
            String authHeader = headers.get(AUTHORIZATION_KEY);
            if (authHeader == null || !authHeader.equals("Bearer " + authToken)) {
                log.warn("[gRPC] 认证失败 | method={} | traceId={}", methodName, traceId);
                call.close(Status.UNAUTHENTICATED
                        .withDescription("Internal auth token is invalid or missing"), headers);
                return new ServerCall.Listener<>() {};
            }
        }

        // 2. 注入 traceId 到 Context
        Context ctx = Context.current().withValue(TRACE_ID_KEY, traceId);

        // 3. 委托给下一个处理器
        ServerCall.Listener<ReqT> listener =
                Contexts.interceptCall(ctx, call, headers, next);

        // 4. 包装 ServerCall 以记录耗时和异常
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(listener) {
            @Override
            public void onComplete() {
                long elapsed = (System.nanoTime() - startNanos) / 1_000_000;
                if (elapsed > 5000) {
                    log.warn("[gRPC] 慢请求 | method={} | elapsed={}ms | traceId={}",
                            methodName, elapsed, traceId);
                } else {
                    log.debug("[gRPC] 请求完成 | method={} | elapsed={}ms | traceId={}",
                            methodName, elapsed, traceId);
                }
                super.onComplete();
            }

            @Override
            public void onHalfClose() {
                try {
                    super.onHalfClose();
                } catch (Exception e) {
                    log.error("[gRPC] 请求处理异常 | method={} | traceId={} | error={}",
                            methodName, traceId, e.getMessage());
                    Status status = mapExceptionToStatus(e);
                    call.close(status, headers);
                }
            }
        };
    }

    /**
     * 将业务异常映射为 gRPC Status。
     */
    private Status mapExceptionToStatus(Exception e) {
        if (e instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT.withDescription(e.getMessage());
        }
        return Status.INTERNAL.withDescription("Internal server error: " + e.getMessage());
    }

    /**
     * 从当前 Context 中获取 traceId（供上层业务代码使用）。
     */
    public static String currentTraceId() {
        return TRACE_ID_KEY.get();
    }
}