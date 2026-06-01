package org.project.privateclouddiskgatewayservice.handler;

import lombok.extern.slf4j.Slf4j;
import org.project.privateclouddiskgatewayservice.dto.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import reactor.netty.channel.AbortedException;

import java.time.LocalDateTime;

/**
 * 全局异常处理器
 * 捕获网关层的所有未处理异常，返回统一 JSON 格式
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(ServerWebInputException ex) {
        log.warn("请求参数错误: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "请求参数格式错误");
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiErrorResponse> handleSecurityException(SecurityException ex) {
        log.warn("安全异常: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "访问被拒绝");
    }

    @ExceptionHandler(AbortedException.class)
    public Mono<Void> handleAbortedException(AbortedException ex) {
        // 客户端断开连接，仅记录警告即可
        log.warn("Client aborted connection: {}" ,ex.getMessage());
        return Mono.empty(); // 无需返回响应
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneralException(Exception ex) {
        log.error("网关内部错误", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "网关内部错误");
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(HttpStatus status, String message) {
        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .build();
        return new ResponseEntity<>(body, status);
    }
}
