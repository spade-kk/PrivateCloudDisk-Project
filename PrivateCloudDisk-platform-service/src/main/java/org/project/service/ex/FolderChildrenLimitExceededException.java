package org.project.service.ex;

/**
 * 文件夹子节点数量超限异常
 */
public class FolderChildrenLimitExceededException extends ServiceException {
    public FolderChildrenLimitExceededException(String message) {
        super(message);
    }
}