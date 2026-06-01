package org.project.service.ex;

public class UserNotFoundException extends ServiceException{
    public UserNotFoundException() {
        super("用户不存在！");
    }
}
