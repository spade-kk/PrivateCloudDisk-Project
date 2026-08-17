package org.project.plugin.model;

/** 插件平台统一响应；requestId 用于用户报错和分布式链路定位。 */
public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String requestId
) {
    public static <T> ApiResponse<T> ok(T data, String requestId) {
        return new ApiResponse<>("OK", "操作成功", data, requestId);
    }
}
