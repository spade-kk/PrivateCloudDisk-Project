package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.data.UserData;

@Mapper
public interface UserMapper {
    /**
     * 通过用户账号查询用户信息
     * @param acccount 用户账号
     * @return 用户数据
     */
    UserData findUserByAccount(String acccount);

    /**
     * 通过用户手机号查询用户信息
     * @param phone_number 用户手机号
     * @return 用户数据
     */
    UserData findUserByPhoneNumber(String phone_number);
    /**
     * 通过用户ID查询用户信息
     * @param user_id 用户ID
     * @return 用户数据
     */
    UserData findUserById(String user_id);

    /**
     * 插入用户数据
     * @param userData
     * @return 插入了数据行数
     */
    int insertUser(UserData userData);
    /**
     * 更新用户数据
     * @param userData 用户数据对象
     * @return 更新了数据行数
     */
    int updateUserData(UserData userData);
    /**
     * 更新用户密码
     * @param user_id 用户ID
     * @param new_password 新密码
     * @return 更新了数据行数
     */
    int updateUserPassword(@Param("user_id") String user_id, @Param("new_password") String new_password);
    /**
     * 删除用户
     * @param user_id 用户ID
     * @return 删除了数据行数
     */
    int deleteUserById(String user_id);
}
