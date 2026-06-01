package org.project.service.ex;

public class VerificationCodeErrorException extends ServiceException{
    public VerificationCodeErrorException() {
        super("验证码错误！");
    }
}
