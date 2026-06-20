package org.project.billing.common;

import lombok.Getter;

/**
 * 计费业务异常
 */
@Getter
public class BillingException extends RuntimeException {
    private final int code;

    public BillingException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BillingException(String message) {
        super(message);
        this.code = 500;
    }

    public static BillingException notFound(String message) {
        return new BillingException(404, message);
    }

    public static BillingException badRequest(String message) {
        return new BillingException(400, message);
    }

    public static BillingException conflict(String message) {
        return new BillingException(409, message);
    }
}