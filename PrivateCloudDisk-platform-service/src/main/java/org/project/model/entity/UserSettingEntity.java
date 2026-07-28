package org.project.model.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户系统设置实体（User System Settings）
 *
 * <p>每个用户拥有一行记录，存储该用户的个性化系统设置。
 * 设置分为三个 JSON 分组：偏好设置（preferences）、通知设置（notification_settings）、
 * 外观设置（appearance），以及独立的语言字段（language）。
 *
 * <p>JSON 字段使用 MySQL JSON 类型存储，支持灵活扩展，无需频繁修改表结构。
 *
 * <p>数据库表：user_settings
 */
@Data
public class UserSettingEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（自增） */
    private Long id;

    /** 用户ID（UUID 二进制格式，关联 users 表） */
    private UUID userId;

    /**
     * 偏好设置（JSON）
     * <p>包含字段：defaultView, itemsPerPage, autoPlay, timezone 等
     */
    private String preferences;

    /**
     * 通知设置（JSON）
     * <p>包含字段：emailNotifications, pushNotifications, fileShared,
     * fileDownloaded, storageWarning, securityAlerts, marketingEmails, weeklyDigest 等
     */
    private String notificationSettings;

    /**
     * 外观设置（JSON）
     * <p>包含字段：theme, fontSize, density, sidebarCollapsed, animationEnabled
     */
    private String appearance;

    /** UI 语言（如 zh-CN, en-US） */
    private String language;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}