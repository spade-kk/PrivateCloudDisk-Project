package org.project.workflow.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.project.workflow.model.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** 工作流公开 API 统一异常边界。 */
@RestControllerAdvice
public class WorkflowExceptionHandler {
    @ExceptionHandler(WorkflowApiException.class)
    ResponseEntity<ApiResponse<?>> workflow(WorkflowApiException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.status()).body(new ApiResponse<>(
                exception.code(), exception.getMessage(), null, requestId(request)
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<?>> validation(MethodArgumentNotValidException exception,
                                               HttpServletRequest request) {
        var errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "message", error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(new ApiResponse<>(
                "WF-VALIDATION-001", "请求参数不合法", errors, requestId(request)
        ));
    }

    /**
     * JSON 契约错误统一返回业务错误信封。
     *
     * <p>改动点（CLOUDFLOW-REQUEST-001）：原行为由 Spring
     * DefaultHandlerExceptionResolver 记录 WARN 并返回裸 400，前端无法区分“源码无效”和
     * “请求体格式错误”。新行为保留 400 状态码，但返回稳定错误码和修复提示，同时不暴露
     * Jackson 内部堆栈。</p>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<?>> unreadable(HttpMessageNotReadableException exception,
                                               HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(
                "WF-REQUEST-INVALID",
                "请求体无法解析，请确保 dsl 是字符串或包含 source/dsl/text 字段的源码包装对象",
                null,
                requestId(request)
        ));
    }

    private static String requestId(HttpServletRequest request) {
        String value = request.getHeader("X-Request-Id");
        return value == null ? "" : value;
    }
}
