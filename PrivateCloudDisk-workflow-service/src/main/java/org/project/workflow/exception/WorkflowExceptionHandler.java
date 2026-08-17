package org.project.workflow.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.project.workflow.model.ApiResponse;
import org.springframework.http.ResponseEntity;
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

    private static String requestId(HttpServletRequest request) {
        String value = request.getHeader("X-Request-Id");
        return value == null ? "" : value;
    }
}
