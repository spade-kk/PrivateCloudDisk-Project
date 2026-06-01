package org.project.service.ex;

public class PhoneNumberDuplicatedException extends ServiceException{
    public PhoneNumberDuplicatedException() {
        super("这个手机号已经注册过或者绑定了账号！不得重复使用请更换手机号！");
    }
}
