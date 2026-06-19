package org.project.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 管理员用户信息 VO
 */
@Data
public class AdminUserVO {
    private String id;
    private String account;
    private String name;
    private String email;
    private String phoneNumber;
    private String role;
    private String status;
    private String imagePath;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}