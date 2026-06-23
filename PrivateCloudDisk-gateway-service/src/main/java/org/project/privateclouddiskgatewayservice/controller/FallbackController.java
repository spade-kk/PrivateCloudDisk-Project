package org.project.privateclouddiskgatewayservice.controller;

import org.project.privateclouddiskgatewayservice.dto.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 网关熔断降级控制器
 * <p>
 * 当上游服务不可用触发 CircuitBreaker 时，返回友好的降级响应。
 * 响应格式与其他所有错误一致，使用统一的 {@link ApiResponse} 格式。
 */
@RestController
public class FallbackController {

    /**
     * 业务服务熔断降级
     */
    @GetMapping(value = "/fallback/business", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<Void>> businessFallback() {
        return Mono.just(ApiResponse.serviceUnavailable("业务服务暂不可用，请稍后重试"));
    }

    /**
     * 文件服务熔断降级
     */
    @GetMapping(value = "/fallback/files", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<Void>> filesFallback() {
        return Mono.just(ApiResponse.serviceUnavailable("文件服务暂不可用，请稍后重试"));
    }

    /**
     * IM 服务熔断降级
     */
    @GetMapping(value = "/fallback/im", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<Void>> imFallback() {
        return Mono.just(ApiResponse.serviceUnavailable("IM 服务暂不可用，请稍后重试"));
    }

    /**
     * 通用熔断降级
     */
    @GetMapping(value = "/fallback/default", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<Void>> defaultFallback() {
        return Mono.just(ApiResponse.serviceUnavailable("服务暂不可用，请稍后重试"));
    }
}