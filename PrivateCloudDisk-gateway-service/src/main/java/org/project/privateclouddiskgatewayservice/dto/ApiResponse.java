package org.project.privateclouddiskgatewayservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应体（企业级标准化格式）
 * <p>
 * 所有经由网关的响应，无论成功或失败，都遵循此格式。
 * 这是整个微服务架构的"契约"——前端、移动端、第三方调用方
 * 都依赖此格式进行统一处理。
 *
 * <h2>响应格式</h2>
 * <pre>{@code
 * {
 *   "code": 200,             // 业务状态码（HTTP 状态码语义）
 *   "message": "success",    // 可读的提示信息
 *   "data": { ... }          // 业务数据（成功时有值，失败时为 null）
 * }
 * }</pre>
 *
 * <h2>设计原则</h2>
 * <ul>
 *   <li>{@code code} 使用 HTTP 状态码语义，200=成功，4xx=客户端错误，5xx=服务端错误</li>
 *   <li>{@code message} 始终提供人类可读的中文提示，方便前端直接展示或调试</li>
 *   <li>{@code data} 成功时携带业务数据，失败时为 {@code null}（配合 {@link JsonInclude#NON_NULL}）</li>
 *   <li>网关自身生成的错误（401/403/429）与上游服务返回的错误（404/500）统一使用此格式</li>
 * </ul>
 *
 * @param <T> 业务数据类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /**
     * 状态码（HTTP 语义）
     * <ul>
     *   <li>200 — 成功</li>
     *   <li>400 — 请求参数错误</li>
     *   <li>401 — 未认证</li>
     *   <li>403 — 无权限</li>
     *   <li>404 — 资源不存在</li>
     *   <li>405 — 方法不允许</li>
     *   <li>429 — 请求过于频繁</li>
     *   <li>500 — 服务器内部错误</li>
     *   <li>502 — 网关错误（上游服务不可用）</li>
     *   <li>503 — 服务暂不可用</li>
     *   <li>504 — 网关超时</li>
     * </ul>
     */
    private int code;

    /**
     * 可读的提示信息（中文）
     */
    private String message;

    /**
     * 业务数据
     * <p>成功时携带，失败时为 {@code null}（不序列化）</p>
     */
    private T data;

    // ──────────── 静态工厂方法 ────────────

    /**
     * 成功响应（带数据）
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code(200)
                .message("success")
                .data(data)
                .build();
    }

    /**
     * 成功响应（带数据和自定义消息）
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .code(200)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * 通用错误响应
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .build();
    }

    /**
     * 400 Bad Request
     */
    public static <T> ApiResponse<T> badRequest(String message) {
        return error(400, message);
    }

    /**
     * 401 Unauthorized
     */
    public static <T> ApiResponse<T> unauthorized(String message) {
        return error(401, message);
    }

    /**
     * 403 Forbidden
     */
    public static <T> ApiResponse<T> forbidden(String message) {
        return error(403, message);
    }

    /**
     * 404 Not Found
     */
    public static <T> ApiResponse<T> notFound(String message) {
        return error(404, message);
    }

    /**
     * 429 Too Many Requests
     */
    public static <T> ApiResponse<T> tooManyRequests(String message) {
        return error(429, message);
    }

    /**
     * 500 Internal Server Error
     */
    public static <T> ApiResponse<T> internalError(String message) {
        return error(500, message);
    }

    /**
     * 502 Bad Gateway
     */
    public static <T> ApiResponse<T> badGateway(String message) {
        return error(502, message);
    }

    /**
     * 503 Service Unavailable
     */
    public static <T> ApiResponse<T> serviceUnavailable(String message) {
        return error(503, message);
    }

    /**
     * 504 Gateway Timeout
     */
    public static <T> ApiResponse<T> gatewayTimeout(String message) {
        return error(504, message);
    }
}