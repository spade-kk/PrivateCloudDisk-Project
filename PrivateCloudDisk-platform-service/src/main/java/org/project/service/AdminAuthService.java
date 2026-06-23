package org.project.service;

import org.project.model.vo.AdminLoginVO;
import org.project.model.vo.AdminUserVO;
import org.project.model.dto.AdminLoginRequest;
import org.project.model.dto.AdminUserCreateRequest;

import java.util.List;
import java.util.UUID;

public interface AdminAuthService {

    AdminLoginVO login(AdminLoginRequest request, String clientIp, String adminKey);

    void logout(UUID adminId, String clientIp);

    AdminLoginVO refreshToken(String refreshToken, String clientIp);
}