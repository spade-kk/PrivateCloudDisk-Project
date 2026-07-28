package org.project.service;

import org.project.model.entity.FolderNodeEntity;
import org.project.model.entity.UserEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface UserService {
    /**
     * 用户登录（完整业务逻辑）。
     * <p>包含：人机验证 → 防滥用检查 → 账号认证 → JWT 生成 → 记录成功/失败。
     * 接口层负责从 Request DTO 中提取参数并传入，业务层不依赖任何 Request DTO。
     *
     * @param account 账号（可选，与 phoneNumber 二选一）
     * @param phoneNumber 手机号（可选，与 account 二选一）
     * @param email 邮箱（可选）
     * @param password 密码
     * @param captchaToken 人机验证 token
     * @param captchaAction 人机验证动作
     * @param clientIp 客户端 IP
     * @return JWT 令牌
     */
    String login(String account, String phoneNumber, String email, String password,
                 String captchaToken, String captchaAction, String clientIp);

    /**
     * 用户注册（完整业务逻辑）。
     * <p>包含：防滥用检查 → 验证码防爆破检查 → 验证码校验 → 创建用户 → 清除尝试计数 / 记录失败。
     * 接口层负责从 Request DTO 中提取参数并传入，业务层不依赖任何 Request DTO。
     *
     * @param phoneNumber 手机号（可选，与 email 二选一）
     * @param email 邮箱（可选，与 phoneNumber 二选一）
     * @param password 密码
     * @param code 验证码
     * @param name 用户名
     * @param clientIp 客户端 IP
     * @return 新注册成功的用户账号
     */
    String register(String phoneNumber, String email, String password, String code, String name, String clientIp);
     /**
      * 业务层函数根据用户ID查询用户根目录节点
      * @param user_id 用户ID
      * @return 用户根目录节点
      */
    FolderNodeEntity findRootFolderNodeByUserId(UUID user_id);
    /**
     * 业务层函数根据用户ID删除用户
     * @param user_id 用户ID
     */
    void deleteUserByUserId(UUID user_id);

    /**
     * 业务层函数根据用户ID更新用户信息
     * @param user_id 用户ID
     * @param new_email 新邮箱
     * @param new_phone_number 新手机号
     * @param new_name 新用户名
     */
    void updateUserInfo(UUID user_id, String new_email, String new_phone_number, String new_name);
    /**
     * 业务层函数根据用户ID查询用户信息
     * @param user_id 用户ID
     * @return 用户信息
     */
    UserEntity findUserInfoByUserId(UUID user_id);
    /**
     * 业务层函数根据用户ID更新用户密码
     * @param user_id 用户ID
     * @param user_password 用户密码
     * @param new_password 新密码
     */
    void updateUserPassword(UUID user_id, String user_password, String new_password);
    /**
     * 业务层函数根据用户ID上传用户头像
     * @param user_id 用户ID
     * @param avator_file 用户头像文件
     */
    void uploadUserAvator(UUID user_id, MultipartFile avator_file);
}
