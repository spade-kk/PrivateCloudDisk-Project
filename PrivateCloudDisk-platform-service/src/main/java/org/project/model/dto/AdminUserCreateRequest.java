package org.project.model.dto;

import lombok.Data;

/**
 * 创建管理员请求
 */
@Data
public class AdminUserCreateRequest {
    private String account;
    private String name;
    private String email;
    private String password;
    private String role;
}