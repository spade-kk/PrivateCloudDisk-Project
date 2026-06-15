package org.project.privateclouddiskgatewayservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 统一 API 错误响应 DTO
 * <p>
 * 用于网关层所有错误响应的标准 JSON 格式。
 * 配合 Jackson 序列化，确保时间格式为 ISO 8601。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
}