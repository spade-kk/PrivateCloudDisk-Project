package org.project.service;

import org.project.model.vo.AdminUserVO;
import org.project.model.dto.AdminUserCreateRequest;

import java.util.UUID;

public interface AdminUserService {

    void createAdmin(AdminUserCreateRequest request, UUID creatorId);

    void updateAdmin(AdminUserCreateRequest request, UUID operatorId);

    void deleteAdmin(UUID adminId, UUID operatorId);

    void resetAdminPassword(UUID adminId, String newPassword, UUID operatorId);

    AdminUserVO getAdminInfo(UUID adminId);
}