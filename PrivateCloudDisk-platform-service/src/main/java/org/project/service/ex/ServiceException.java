package org.project.service.ex;

/**
 * 业务层发生异常的基类
 */
public class ServiceException extends RuntimeException{
    public ServiceException(String message) {
        super(message);
    }
}
