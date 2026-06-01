package org.project.service.ex;

public class InsertException extends ServiceException{
    public InsertException(){
        super("数据插入过程中出现未知异常！");
    }
}
