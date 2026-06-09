package org.project.service.ex;

public class DeleteException extends ServiceException {
    public DeleteException(String message) { super(message);  }

    public DeleteException() { super("数据删除过程中出现未知异常！"); }
}
