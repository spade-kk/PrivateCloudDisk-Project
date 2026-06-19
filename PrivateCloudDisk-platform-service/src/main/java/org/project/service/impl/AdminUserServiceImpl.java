package org.project.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.mapper.AdminUserMapper;
import org.project.model.dto.AdminUserCreateRequest;
import org.project.model.entity.AdminUserEntity;
import org.project.model.vo.AdminUserVO;
import org.project.service.AdminUserService;
import org.project.service.ex.AdminNotFoundException;
import org.project.service.ex.AdminPermissionDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void createAdmin(AdminUserCreateRequest request, UUID creatorId) {
        AdminUserEntity creator = adminUserMapper.findByAdminId(creatorId);
        if (creator == null || !"SUPER_ADMIN".equals(creator.getAdminRole())) {
            throw new AdminPermissionDeniedException("仅超级管理员可以创建管理员");
        }

        AdminUserEntity existing = adminUserMapper.findByAdminAccount(request.getAccount());
        if (existing != null) {
            throw new AdminPermissionDeniedException("管理员账号已存在");
        }

        AdminUserEntity entity = new AdminUserEntity();
        entity.setAdminId(UUID.randomUUID());
        entity.setAdminAccount(request.getAccount());
        entity.setAdminName(request.getName());
        entity.setAdminEmail(request.getEmail());
        entity.setAdminPassword(passwordEncoder.encode(request.getPassword()));
        entity.setAdminRole(request.getRole() != null ? request.getRole() : "ADMIN");
        entity.setAdminStatus("ACTIVE");
        entity.setAdminCreatedBy(creatorId);
        entity.setAdminCreatedAt(LocalDateTime.now());
        entity.setAdminUpdatedAt(LocalDateTime.now());

        adminUserMapper.insertAdminUser(entity);
        log.info("管理员创建成功: account={}, createdBy={}", request.getAccount(), creatorId);
    }

    @Override
    public void updateAdmin(AdminUserCreateRequest request, UUID operatorId) {
        AdminUserEntity admin = adminUserMapper.findByAdminAccount(request.getAccount());
        if (admin == null) {
            throw new AdminNotFoundException("管理员不存在");
        }

        AdminUserEntity operator = adminUserMapper.findByAdminId(operatorId);
        if (operator == null || !"SUPER_ADMIN".equals(operator.getAdminRole())) {
            throw new AdminPermissionDeniedException("仅超级管理员可以修改管理员信息");
        }

        admin.setAdminName(request.getName());
        admin.setAdminEmail(request.getEmail());
        if (request.getRole() != null) {
            admin.setAdminRole(request.getRole());
        }
        admin.setAdminUpdatedAt(LocalDateTime.now());
        adminUserMapper.updateAdminUser(admin);
        log.info("管理员信息更新: account={}, operatorId={}", request.getAccount(), operatorId);
    }

    @Override
    public void deleteAdmin(UUID adminId, UUID operatorId) {
        AdminUserEntity operator = adminUserMapper.findByAdminId(operatorId);
        if (operator == null || !"SUPER_ADMIN".equals(operator.getAdminRole())) {
            throw new AdminPermissionDeniedException("仅超级管理员可以删除管理员");
        }

        if (adminId.equals(operatorId)) {
            throw new AdminPermissionDeniedException("不能删除自己");
        }

        adminUserMapper.deleteAdminById(adminId);
        log.info("管理员删除: adminId={}, operatorId={}", adminId, operatorId);
    }

    @Override
    public void resetAdminPassword(UUID adminId, String newPassword, UUID operatorId) {
        AdminUserEntity operator = adminUserMapper.findByAdminId(operatorId);
        if (operator == null || !"SUPER_ADMIN".equals(operator.getAdminRole())) {
            throw new AdminPermissionDeniedException("仅超级管理员可以重置密码");
        }

        adminUserMapper.updateAdminPassword(adminId, passwordEncoder.encode(newPassword));
        log.info("管理员密码重置: adminId={}, operatorId={}", adminId, operatorId);
    }

    @Override
    public AdminUserVO getAdminInfo(UUID adminId) {
        AdminUserEntity admin = adminUserMapper.findByAdminId(adminId);
        if (admin == null) {
            throw new AdminNotFoundException("管理员不存在");
        }

        AdminUserVO vo = new AdminUserVO();
        vo.setId(admin.getAdminId().toString());
        vo.setAccount(admin.getAdminAccount());
        vo.setName(admin.getAdminName());
        vo.setEmail(admin.getAdminEmail());
        vo.setPhoneNumber(admin.getAdminPhoneNumber());
        vo.setRole(admin.getAdminRole());
        vo.setStatus(admin.getAdminStatus());
        vo.setImagePath(admin.getAdminImagePath());
        vo.setLastLoginAt(admin.getAdminLastLoginAt());
        vo.setCreatedAt(admin.getAdminCreatedAt());
        return vo;
    }
}