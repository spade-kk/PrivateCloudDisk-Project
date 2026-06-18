package org.project.service.ex;

/**
 * 重新发送次数已耗尽异常。
 * <p>当 resend token 的剩余重新发送次数为 0 时抛出，客户端需重新走首次发送流程（含人机验证）。
 */
public class ResendTokenExhaustedException extends ServiceException {
    public ResendTokenExhaustedException() {
        super("重新发送次数已用完，请重新获取验证码并完成人机验证");
    }

    public ResendTokenExhaustedException(String message) {
        super(message);
    }
}