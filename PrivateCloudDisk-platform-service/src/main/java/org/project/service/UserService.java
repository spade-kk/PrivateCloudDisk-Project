package org.project.service;

import org.project.model.entity.FolderNodeEntity;
import org.project.model.entity.UserEntity;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {
    /**
     * 业务层函数登录调用持久层函数的方法实现登录操作并返回用户数据
     * @param account 账号
     * @param phone_number 手机号
     * @param password 密码
     * @return 用户数据
     */
    UserEntity login(String account, String phone_number, String password);
    /**
     * 业务层函数实现注册用户功能
     * @param phone_number 手机号
     * @param password 密码
     * @param code 验证码
     * @param name 用户名
     * @return 新注册成功的用户账号
     */
    String register(String phone_number, String password, String code, String name);
     /**
      * 业务层函数根据用户ID查询用户根目录节点
      * @param user_id 用户ID
      * @return 用户根目录节点
      */
    FolderNodeEntity findRootFolderNodeByUserId(String user_id);
    /**
     * 业务层函数根据用户ID删除用户
     * @param user_id 用户ID
     */
    void deleteUserByUserId(String user_id);

    /**
     * 业务层函数根据用户ID更新用户信息
     * @param user_id 用户ID
     * @param new_email 新邮箱
     * @param new_phone_number 新手机号
     * @param new_name 新用户名
     */
    void updateUserInfo(String user_id, String new_email, String new_phone_number, String new_name);
    /**
     * 业务层函数根据用户ID查询用户信息
     * @param user_id 用户ID
     * @return 用户信息
     */
    UserEntity findUserInfoByUserId(String user_id);
    /**
     * 业务层函数根据用户ID更新用户密码
     * @param user_id 用户ID
     * @param user_password 用户密码
     * @param new_password 新密码
     */
    void updateUserPassword(String user_id, String user_password, String new_password);
    /**
     * 业务层函数根据用户ID上传用户头像
     * @param user_id 用户ID
     * @param avator_file 用户头像文件
     */
    void uploadUserAvator(String user_id, MultipartFile avator_file);
}
