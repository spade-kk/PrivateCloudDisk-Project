package org.project.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.mapper.AdminUserMapper;
import org.project.model.dto.AdminLoginRequest;
import org.project.model.entity.AdminUserEntity;
import org.project.model.vo.AdminLoginVO;
import org.project.model.vo.AdminUserVO;
import org.project.service.AdminAuthService;
import org.project.service.ex.*;
import org.project.util.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthServiceImpl implements AdminAuthService {

    private final AdminUserMapper adminUserMapper;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    private static final String ADMIN_TOKEN_PREFIX = "admin:";

    @Override
    public AdminLoginVO login(AdminLoginRequest request, String clientIp, String adminKey) {
        log.info("管理员登录: account={}, ip={}", request.getAccount(), clientIp);

        AdminUserEntity admin = adminUserMapper.findByAdminAccount(request.getAccount());
        if (admin == null) {
            throw new AdminNotFoundException("管理员账号不存在");
        }

        if ("DISABLED".equals(admin.getAdminStatus())) {
            throw new AdminAccountLockedException("管理员账号已被禁用");
        }

        if (admin.getAdminLockedUntil() != null
                && admin.getAdminLockedUntil().isAfter(LocalDateTime.now())) {
            throw new AdminAccountLockedException("管理员账号已锁定至 " + admin.getAdminLockedUntil());
        }

        if (!passwordEncoder.matches(request.getPassword(), admin.getAdminPassword())) {
            int failCount = (admin.getAdminLoginFailCount() == null ? 0 : admin.getAdminLoginFailCount()) + 1;
            admin.setAdminLoginFailCount(failCount);
            if (failCount >= 5) {
                admin.setAdminLockedUntil(LocalDateTime.now().plusMinutes(30));
            }
            adminUserMapper.updateAdminUser(admin);
            throw new AdminPasswordNotMatchException("管理员密码错误");
        }

        admin.setAdminLoginFailCount(0);
        admin.setAdminLockedUntil(null);
        admin.setAdminLastLoginAt(LocalDateTime.now());
        admin.setAdminLastLoginIp(clientIp);
        adminUserMapper.updateAdminUser(admin);

        String accessToken = jwtUtil.generateAccessToken(ADMIN_TOKEN_PREFIX + admin.getAdminId().toString());
        String refreshToken = jwtUtil.generateAccessToken(ADMIN_TOKEN_PREFIX + "refresh:" + admin.getAdminId().toString());

        AdminLoginVO vo = new AdminLoginVO();
        vo.setAccessToken(accessToken);
        vo.setRefreshToken(refreshToken);
        vo.setAdminInfo(toAdminUserVO(admin));

        return vo;
    }

    @Override
    public void logout(UUID adminId, String clientIp) {
        log.info("管理员登出: adminId={}, ip={}", adminId, clientIp);
    }

    @Override
    public AdminLoginVO refreshToken(String refreshToken, String clientIp) {
        String tokenValue = jwtUtil.verifyAccessToken(refreshToken);
        if (tokenValue == null || !tokenValue.startsWith(ADMIN_TOKEN_PREFIX + "refresh:")) {
            throw new AdminException("RefreshToken 无效");
        }
        String adminIdStr = tokenValue.substring((ADMIN_TOKEN_PREFIX + "refresh:").length());
        AdminUserEntity admin = adminUserMapper.findByAdminId(UUID.fromString(adminIdStr));
        if (admin == null) {
            throw new AdminNotFoundException("管理员不存在");
        }

        String newAccessToken = jwtUtil.generateAccessToken(ADMIN_TOKEN_PREFIX + admin.getAdminId().toString());
        String newRefreshToken = jwtUtil.generateAccessToken(ADMIN_TOKEN_PREFIX + "refresh:" + admin.getAdminId().toString());

        AdminLoginVO vo = new AdminLoginVO();
        vo.setAccessToken(newAccessToken);
        vo.setRefreshToken(newRefreshToken);
        vo.setAdminInfo(toAdminUserVO(admin));
        return vo;
    }

    private AdminUserVO toAdminUserVO(AdminUserEntity entity) {
        AdminUserVO vo = new AdminUserVO();
        vo.setId(entity.getAdminId().toString());
        vo.setAccount(entity.getAdminAccount());
        vo.setName(entity.getAdminName());
        vo.setEmail(entity.getAdminEmail());
        vo.setPhoneNumber(entity.getAdminPhoneNumber());
        vo.setRole(entity.getAdminRole());
        vo.setStatus(entity.getAdminStatus());
        vo.setImagePath(entity.getAdminImagePath());
        vo.setLastLoginAt(entity.getAdminLastLoginAt());
        vo.setCreatedAt(entity.getAdminCreatedAt());
        return vo;
    }
}