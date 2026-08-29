package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.model.entity.UserEntity;

import java.util.UUID;
import java.util.List;

@Mapper
public interface UserMapper {
    /**
     * 通过用户账号查询用户信息
     * @param acccount 用户账号
     * @return 用户数据
     */
    UserEntity findUserByAccount(@Param("account") String acccount);

    /**
     * 通过用户手机号查询用户信息
     * @param phone_number 用户手机号
     * @return 用户数据
     */
    UserEntity findUserByPhoneNumber(@Param("phone_number") String phone_number);
    /**
     * 通过用户ID查询用户信息
     * @param user_id 用户ID
     * @return 用户数据
     */
    UserEntity findUserById(@Param("user_id") UUID user_id);

    UserEntity findUserByNameOrAccount(@Param("username") String username);

    /**
     * [USER-DIRECTORY-20260810] 平台统一公开用户目录搜索；只返回公开资料所需字段。
     * 邮箱仅用于匹配，不作为返回字段。
     */
    List<UserEntity> searchPublicUsers(@Param("keyword") String keyword,
                                       @Param("offset") int offset,
                                       @Param("size") int size);

    /**
     * 插入用户数据
     * @param userData
     * @return 插入了数据行数
     */
    int insertUser(UserEntity userData);
    /**
     * 更新用户数据
     * @param userData 用户数据对象
     * @return 更新了数据行数
     */
    int updateUserEntity(UserEntity userData);
    /**
     * 更新用户密码
     * @param user_id 用户ID
     * @param new_password 新密码
     * @return 更新了数据行数
     */
    int updateUserPassword(@Param("user_id") UUID user_id, @Param("new_password") String new_password);
    /**
     * 删除用户
     * @param user_id 用户ID
     * @return 删除了数据行数
     */
    int deleteUserById(@Param("user_id") UUID user_id);
    
    /**
     * 通过用户邮箱查询用户信息
     * @param email 用户邮箱
     * @return 用户数据
     */
    UserEntity findUserByEmail(@Param("email") String email);
    
    /**
     * 通过用户手机号查询用户信息（别名方法）
     * @param phone 用户手机号
     * @return 用户数据
     */
    default UserEntity findUserByPhone(@Param("phone") String phone) {
        return findUserByPhoneNumber(phone);
    }
    
    /**
     * 更新用户信息
     * @param userData 用户数据对象
     * @return 更新了数据行数
     */
    default int updateUserInfo(UserEntity userData) {
        return updateUserEntity(userData);
    }
}
