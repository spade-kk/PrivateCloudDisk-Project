package org.project.plugin.exception;

import org.springframework.http.HttpStatus;

/** 插件服务稳定错误码异常，禁止把底层路径和堆栈直接返回客户端。 */
public class PluginApiException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public PluginApiException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
