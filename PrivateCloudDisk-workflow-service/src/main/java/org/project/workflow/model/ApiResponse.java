package org.project.workflow.model;

/** 新服务统一响应信封，稳定字符串 code 便于多客户端兼容。 */
public record ApiResponse<T>(String code, String message, T data, String requestId) {
    public static <T> ApiResponse<T> ok(T data, String requestId) {
        return new ApiResponse<>("OK", "操作成功", data, requestId == null ? "" : requestId);
    }
}
