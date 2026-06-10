package org.project.service;

import org.project.model.entity.UserEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * 用户管理服务接口
 */
public interface UserManageService {
    
    /**
     * 上传用户头像
     * @param user_id 用户ID
     * @param avatarFile 头像文件
     * @return 头像路径
     */
    String uploadAvatar(UUID user_id, MultipartFile avatarFile);
    
    /**
     * 修改用户密码
     * @param user_id 用户ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     */
    void changePassword(UUID user_id, String oldPassword, String newPassword);
    
    /**
     * 换绑邮箱
     * @param user_id 用户ID
     * @param newEmail 新邮箱
     * @param verificationCode 验证码
     */
    void changeEmail(UUID user_id, String newEmail, String verificationCode);
    
    /**
     * 换绑手机号
     * @param user_id 用户ID
     * @param newPhone 新手机号
     * @param verificationCode 验证码
     */
    void changePhone(UUID user_id, String newPhone, String verificationCode);
    
    /**
     * 获取用户个人信息
     * @param user_id 用户ID
     * @return 用户信息
     */
    UserEntity getUserInfo(UUID user_id);
    
    /**
     * 更新用户个人信息
     * @param user_id 用户ID
     * @param userName 用户名
     */
    void updateUserInfo(UUID user_id, String userName);
    
    /**
     * 发送邮箱验证码
     * @param email 邮箱地址
     */
    void sendEmailVerificationCode(String email);
    
    /**
     * 发送手机验证码
     * @param phone 手机号
     */
    void sendPhoneVerificationCode(String phone);
}
