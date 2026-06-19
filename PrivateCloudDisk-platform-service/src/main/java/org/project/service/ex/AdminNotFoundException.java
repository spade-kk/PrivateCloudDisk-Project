package org.project.service.ex;

/**
 * 管理员不存在异常
 */
public class AdminNotFoundException extends AdminException {
    public AdminNotFoundException(String message) {
        super(message);
    }
}