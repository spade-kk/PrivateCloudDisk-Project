package org.project.im.platform.exception;

import lombok.Getter;

/**
 * IM 业务异常
 * <p>
 * 用于在 Service 层抛出业务异常，由全局异常处理器统一捕获
 * 并转换为标准 Result 响应。避免在各层散布 try-catch 和错误码。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Getter
public class ImBusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 业务错误码 */
    private final int code;

    /**
     * 构造函数
     *
     * @param code    错误码
     * @param message 错误消息
     */
    public ImBusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 构造函数（带 cause）
     *
     * @param code    错误码
     * @param message 错误消息
     * @param cause   原始异常
     */
    public ImBusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}