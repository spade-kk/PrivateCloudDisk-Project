package org.project.plugin.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.project.plugin.model.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** 对外错误统一脱敏；完整异常仅进入服务端日志。 */
@RestControllerAdvice
public class PluginExceptionHandler {
    @ExceptionHandler(PluginApiException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handlePlugin(
            PluginApiException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(exception.status()).body(new ApiResponse<>(
                exception.code(),
                exception.getMessage(),
                Map.of(),
                requestId(request)
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + "：" + error.getDefaultMessage())
                .orElse("请求参数不合法");
        return ResponseEntity.unprocessableEntity().body(new ApiResponse<>(
                "PLG-REQUEST-INVALID",
                message,
                Map.of(),
                requestId(request)
        ));
    }

    private static String requestId(HttpServletRequest request) {
        String value = request.getHeader("X-Request-Id");
        return value == null ? "" : value;
    }
}
