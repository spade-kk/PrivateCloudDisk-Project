package org.project.service.ex;

/**
 * 配额不足异常
 * <p>当用户预占配额时可用容量不足则抛出此异常
 */
public class QuotaExceededException extends ServiceException {
    public QuotaExceededException(String message) {
        super(message);
    }

    public QuotaExceededException(long available, long requested) {
        super(String.format("配额不足：需要 %d 字节，可用 %d 字节", requested, available));
    }
}