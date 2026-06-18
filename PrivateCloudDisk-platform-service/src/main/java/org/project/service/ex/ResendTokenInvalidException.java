package org.project.service.ex;

/**
 * 重新发送令牌无效或已过期异常。
 * <p>当客户端携带的 resend token 在 Redis 中不存在或已过期时抛出。
 */
public class ResendTokenInvalidException extends ServiceException {
    public ResendTokenInvalidException() {
        super("重新发送令牌无效或已过期，请重新获取验证码并完成人机验证");
    }

    public ResendTokenInvalidException(String message) {
        super(message);
    }
}