package org.project.service.ex;

/**
 * 管理员权限不足异常
 */
public class AdminPermissionDeniedException extends AdminException {
    public AdminPermissionDeniedException(String message) {
        super(message);
    }
}