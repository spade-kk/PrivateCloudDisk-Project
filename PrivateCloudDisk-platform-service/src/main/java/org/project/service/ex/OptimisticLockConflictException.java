package org.project.service.ex;

/**
 * 上传会话创建过程中的乐观锁冲突。
 *
 * 需求：前端识别明确的 409/OPTIMISTIC_LOCK_CONFLICT 后进行有限次透明重试。
 */
public class OptimisticLockConflictException extends ServiceException {
    public OptimisticLockConflictException(String message) {
        super(message);
    }
}
