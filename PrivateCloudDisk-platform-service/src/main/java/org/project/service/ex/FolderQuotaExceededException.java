package org.project.service.ex;

/**
 * 文件夹配额超限异常
 */
public class FolderQuotaExceededException extends ServiceException {
    public FolderQuotaExceededException(String message) {
        super(message);
    }
}