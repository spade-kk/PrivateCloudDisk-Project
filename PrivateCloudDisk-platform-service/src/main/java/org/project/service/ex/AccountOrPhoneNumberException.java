package org.project.service.ex;

public class AccountOrPhoneNumberException extends ServiceException{
    public AccountOrPhoneNumberException() {
        super("账号和手机号不能都为空！");
    }

    public AccountOrPhoneNumberException(String message) {
        super(message);
    }
}
