package org.project.model.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 管理员用户数据类
 */
@Data
public class AdminUserEntity implements Serializable {
    private UUID adminId;
    private String adminAccount;
    private String adminName;
    private String adminEmail;
    private String adminPhoneNumber;
    private String adminPassword;
    private String adminRole;          // SUPER_ADMIN / ADMIN / MODERATOR
    private String adminStatus;        // ACTIVE / DISABLED
    private String adminImagePath;
    private Integer adminLoginFailCount;
    private LocalDateTime adminLockedUntil;
    private LocalDateTime adminLastLoginAt;
    private String adminLastLoginIp;
    private UUID adminCreatedBy;
    private LocalDateTime adminCreatedAt;
    private LocalDateTime adminUpdatedAt;
}