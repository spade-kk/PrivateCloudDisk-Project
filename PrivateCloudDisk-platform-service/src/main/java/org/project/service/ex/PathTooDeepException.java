package org.project.service.ex;

/**
 * 路径深度超限异常
 */
public class PathTooDeepException extends ServiceException {
    public PathTooDeepException(String message) {
        super(message);
    }
}