package com.privateclouddisk.android.data.model;

/**
 * API 异常
 */
public class ApiException extends Exception {
    private final int code;

    public ApiException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() { return code; }

    public boolean isUnauthorized() {
        return code == 401;
    }

    public boolean isForbidden() {
        return code == 403;
    }

    public boolean isNotFound() {
        return code == 404;
    }

    public boolean isServerError() {
        return code >= 500;
    }
}