package org.project.service.ex;

public class PasswordNotMatchException extends ServiceException{
    public PasswordNotMatchException() {
        super("账号或者密码错误！");
    }
    public PasswordNotMatchException(String message) {
        super(message);
    }
}
