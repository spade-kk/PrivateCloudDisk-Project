package org.project.service.ex;

/**
 * 路径过长异常
 */
public class PathTooLongException extends ServiceException {
    public PathTooLongException(String message) {
        super(message);
    }
}