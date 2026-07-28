-- ============================================================
-- 用户系统设置表 (User System Settings)
-- ============================================================
-- 每个用户一行记录，存储该用户的个性化系统设置。
-- 包括：偏好设置、通知设置、外观设置、语言等。
-- 使用 JSON 字段存储结构化设置，支持灵活扩展。
-- ============================================================

CREATE TABLE IF NOT EXISTS pcd_user_settings_table (
    -- 主键ID（自增）
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    -- 用户ID（UUID 二进制格式，与 users 表关联）
    user_id         BINARY(16)      NOT NULL                 COMMENT '用户ID（关联 users 表）',
    -- 偏好设置（JSON）：defaultView, itemsPerPage, autoPlay, language, timezone
    preferences     JSON                                     COMMENT '用户偏好设置（默认视图、每页条数、自动播放等）',
    -- 通知设置（JSON）：emailNotifications, pushNotifications, fileShared 等
    notification_settings JSON                              COMMENT '通知设置（邮件通知、推送通知、通知频率等）',
    -- 外观设置（JSON）：theme, fontSize, density, sidebarCollapsed, animationEnabled
    appearance      JSON                                     COMMENT '外观设置（主题、字体大小、布局密度、侧边栏折叠等）',
    -- 语言设置（独立字段，便于查询和索引）
    language        VARCHAR(10)     NOT NULL DEFAULT 'zh-CN' COMMENT 'UI 语言（如 zh-CN, en-US）',
    -- 创建时间
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    -- 更新时间
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 主键
    PRIMARY KEY (id),
    -- 唯一约束：每个用户只有一条设置记录
    UNIQUE KEY uk_user_id (user_id),
    -- 索引：按用户ID查询
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户系统设置表';