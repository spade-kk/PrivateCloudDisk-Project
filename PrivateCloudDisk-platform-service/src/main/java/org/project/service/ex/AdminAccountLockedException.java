package org.project.service.ex;

/**
 * 管理员账号已锁定异常
 */
public class AdminAccountLockedException extends AdminException {
    public AdminAccountLockedException(String message) {
        super(message);
    }
}