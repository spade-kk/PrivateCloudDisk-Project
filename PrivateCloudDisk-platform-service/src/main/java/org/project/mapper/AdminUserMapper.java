package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.model.entity.AdminUserEntity;

import java.util.UUID;

@Mapper
public interface AdminUserMapper {
    AdminUserEntity findByAdminAccount(@Param("admin_account") String adminAccount);

    AdminUserEntity findByAdminEmail(@Param("admin_email") String adminEmail);

    AdminUserEntity findByAdminId(@Param("admin_id") UUID adminId);

    int insertAdminUser(AdminUserEntity entity);

    int updateAdminUser(AdminUserEntity entity);

    int updateAdminPassword(@Param("admin_id") UUID adminId, @Param("new_password") String newPassword);

    int deleteAdminById(@Param("admin_id") UUID adminId);
}