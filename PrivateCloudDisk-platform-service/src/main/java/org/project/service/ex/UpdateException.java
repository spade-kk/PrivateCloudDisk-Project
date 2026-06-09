package org.project.service.ex;

public class UpdateException extends ServiceException {
    public UpdateException(String message) { super(message); }

    public UpdateException() { super("数据更新过程中出现未知异常！"); }
}
