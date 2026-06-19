package org.project.service.ex;

/**
 * 管理员密码错误异常
 */
public class AdminPasswordNotMatchException extends AdminException {
    public AdminPasswordNotMatchException(String message) {
        super(message);
    }
}