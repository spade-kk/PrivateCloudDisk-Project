package com.privateclouddisk.android.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * 通用 API 响应包装
 * 对应后端 Spring Boot 统一返回: { code, data, message }
 */
public class ApiResponse<T> {

    @SerializedName("code")
    private int code;

    @SerializedName("data")
    private T data;

    @SerializedName("message")
    private String message;

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public boolean isSuccess() {
        return code == 200 || code == 0;
    }

    /**
     * 获取 data，若失败则抛出异常
     */
    public T getDataOrThrow() throws ApiException {
        if (!isSuccess() || data == null) {
            throw new ApiException(code, message != null ? message : "请求失败");
        }
        return data;
    }
}