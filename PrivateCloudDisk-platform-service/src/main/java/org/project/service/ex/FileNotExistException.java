package org.project.service.ex;

public class FileNotExistException extends ServiceException{
    public FileNotExistException(){
        super("文件不存在！");
    }
}
