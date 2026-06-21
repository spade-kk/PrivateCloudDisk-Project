-- ============================================
-- 管理员用户表
-- ============================================
CREATE TABLE IF NOT EXISTS pcd_admin_user_table (
    admin_id            BINARY(16)      NOT NULL COMMENT '管理员ID (UUID)',
    admin_account       VARCHAR(50)     NOT NULL COMMENT '管理员账号',
    admin_name          VARCHAR(100)    NOT NULL COMMENT '管理员姓名',
    admin_email         VARCHAR(200)    DEFAULT NULL COMMENT '管理员邮箱',
    admin_phone_number  VARCHAR(20)     DEFAULT NULL COMMENT '管理员手机号',
    admin_password      VARCHAR(255)    NOT NULL COMMENT '管理员密码 (BCrypt)',
    admin_role          VARCHAR(20)     NOT NULL DEFAULT 'ADMIN' COMMENT '角色: SUPER_ADMIN/ADMIN/MODERATOR',
    admin_status        VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/DISABLED',
    admin_image_path    VARCHAR(500)    DEFAULT NULL COMMENT '管理员头像路径',
    admin_login_fail_count INT         DEFAULT 0 COMMENT '登录失败次数',
    admin_locked_until  DATETIME        DEFAULT NULL COMMENT '锁定截止时间',
    admin_last_login_at DATETIME        DEFAULT NULL COMMENT '最后登录时间',
    admin_last_login_ip VARCHAR(45)     DEFAULT NULL COMMENT '最后登录IP',
    admin_created_by    BINARY(16)      DEFAULT NULL COMMENT '创建者ID',
    admin_created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    admin_updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (admin_id),
    UNIQUE KEY uk_admin_account (admin_account),
    UNIQUE KEY uk_admin_email (admin_email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员用户表';

-- ============================================
-- 管理员审计日志表
-- ============================================
CREATE TABLE IF NOT EXISTS pcd_admin_audit_log_table (
    audit_id            BIGINT          NOT NULL AUTO_INCREMENT COMMENT '审计日志ID',
    audit_admin_id      BINARY(16)      DEFAULT NULL COMMENT '操作管理员ID',
    audit_admin_name    VARCHAR(100)    DEFAULT NULL COMMENT '操作管理员名称',
    audit_admin_role    VARCHAR(20)     DEFAULT NULL COMMENT '操作管理员角色',
    audit_action        VARCHAR(50)     NOT NULL COMMENT '操作类型: LOGIN/LOGOUT/CREATE/UPDATE/DELETE/VIEW/EXPORT',
    audit_resource      VARCHAR(100)    NOT NULL COMMENT '操作资源: ADMIN_USER/SYSTEM_CONFIG/IP_BLACKLIST/SECURITY_EVENT',
    audit_resource_id   VARCHAR(100)    DEFAULT NULL COMMENT '操作资源ID',
    audit_detail        TEXT            DEFAULT NULL COMMENT '操作详情 (JSON)',
    audit_request_method VARCHAR(10)    DEFAULT NULL COMMENT '请求方法',
    audit_request_path  VARCHAR(500)    DEFAULT NULL COMMENT '请求路径',
    audit_client_ip     VARCHAR(45)     DEFAULT NULL COMMENT '客户端IP',
    audit_user_agent    VARCHAR(500)    DEFAULT NULL COMMENT 'User-Agent',
    audit_status        VARCHAR(20)     NOT NULL DEFAULT 'SUCCESS' COMMENT '状态: SUCCESS/FAILURE',
    audit_error_message TEXT            DEFAULT NULL COMMENT '错误信息',
    audit_created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (audit_id),
    KEY idx_audit_admin_id (audit_admin_id),
    KEY idx_audit_action (audit_action),
    KEY idx_audit_resource (audit_resource),
    KEY idx_audit_status (audit_status),
    KEY idx_audit_created_at (audit_created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员审计日志表';

-- ============================================
-- 安全事件表
-- ============================================
CREATE TABLE IF NOT EXISTS pcd_security_event_table (
    event_id            BIGINT          NOT NULL AUTO_INCREMENT COMMENT '事件ID',
    event_type          VARCHAR(50)     NOT NULL COMMENT '事件类型: LOGIN_FAILURE/BRUTE_FORCE/IP_BLOCKED/CONFIG_CHANGE/ADMIN_ACTION',
    event_severity      VARCHAR(20)     NOT NULL DEFAULT 'MEDIUM' COMMENT '严重程度: LOW/MEDIUM/HIGH/CRITICAL',
    event_user_id       BINARY(16)      DEFAULT NULL COMMENT '关联用户ID',
    event_admin_id      BINARY(16)      DEFAULT NULL COMMENT '关联管理员ID',
    event_ip            VARCHAR(45)     DEFAULT NULL COMMENT '事件IP',
    event_description   TEXT            NOT NULL COMMENT '事件描述',
    event_handled       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否已处理: 0-未处理, 1-已处理',
    event_handled_by    BINARY(16)      DEFAULT NULL COMMENT '处理人ID',
    event_handled_at    DATETIME        DEFAULT NULL COMMENT '处理时间',
    event_resolution    TEXT            DEFAULT NULL COMMENT '处理方案',
    event_created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (event_id),
    KEY idx_event_type (event_type),
    KEY idx_event_severity (event_severity),
    KEY idx_event_handled (event_handled),
    KEY idx_event_created_at (event_created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='安全事件表';

-- ============================================
-- 系统配置表
-- ============================================
CREATE TABLE IF NOT EXISTS pcd_system_config_table (
    config_id           BIGINT          NOT NULL AUTO_INCREMENT COMMENT '配置ID',
    config_key          VARCHAR(100)    NOT NULL COMMENT '配置键',
    config_value        TEXT            NOT NULL COMMENT '配置值',
    config_group        VARCHAR(50)     NOT NULL DEFAULT 'general' COMMENT '配置分组',
    config_description  VARCHAR(500)    DEFAULT NULL COMMENT '配置描述',
    config_editable     TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否可编辑',
    config_version      INT             NOT NULL DEFAULT 1 COMMENT '配置版本号',
    config_updated_by   BINARY(16)      DEFAULT NULL COMMENT '最后更新人ID',
    config_updated_at   DATETIME        DEFAULT NULL COMMENT '最后更新时间',
    config_created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (config_id),
    UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- ============================================
-- IP黑名单表
-- ============================================
CREATE TABLE IF NOT EXISTS pcd_ip_blacklist_table (
    blacklist_id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '黑名单ID',
    blacklist_ip            VARCHAR(45) NOT NULL COMMENT 'IP地址',
    blacklist_reason        VARCHAR(500) DEFAULT NULL COMMENT '加入原因',
    blacklist_added_by      BINARY(16)  DEFAULT NULL COMMENT '添加人ID',
    blacklist_removed_by    BINARY(16)  DEFAULT NULL COMMENT '移除人ID',
    blacklist_removed_at    DATETIME    DEFAULT NULL COMMENT '移除时间',
    blacklist_status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/REMOVED',
    blacklist_expires_at    DATETIME    DEFAULT NULL COMMENT '过期时间',
    blacklist_created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (blacklist_id),
    KEY idx_blacklist_ip (blacklist_ip),
    KEY idx_blacklist_status (blacklist_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IP黑名单表';

-- ============================================
-- 插入默认超级管理员
-- 密码: admin123 (BCrypt 加密)
-- ============================================
INSERT INTO pcd_admin_user_table (admin_id, admin_account, admin_name, admin_email, admin_password, admin_role, admin_status)
VALUES (
    UNHEX(REPLACE('00000000-0000-0000-0000-000000000001', '-', '')),
    'superadmin',
    '超级管理员',
    'admin@privateclouddisk.com',
    '$2a$12$NnU8Fls3KxWGGqL9ccJreuL7q4qCq5q7q5q7q5q7q5q7q5q7q5q7q',
    'SUPER_ADMIN',
    'ACTIVE'
)
ON DUPLICATE KEY UPDATE admin_account = admin_account;

-- ============================================
-- 插入默认系统配置
-- ============================================
INSERT INTO pcd_system_config_table (config_key, config_value, config_group, config_description, config_editable)
VALUES
    ('system.name', 'PrivateCloudDisk', 'general', '系统名称', 1),
    ('system.version', '1.0.0', 'general', '系统版本', 0),
    ('system.max_file_size', '10737418240', 'storage', '最大文件大小 (字节)', 1),
    ('system.max_user_storage', '107374182400', 'storage', '用户最大存储空间 (字节)', 1),
    ('system.registration_enabled', 'true', 'user', '是否允许注册', 1),
    ('system.audit_log_retention_days', '90', 'security', '审计日志保留天数', 1),
    ('system.login_attempt_limit', '5', 'security', '登录尝试次数限制', 1),
    ('system.lock_duration_minutes', '30', 'security', '账号锁定时间 (分钟)', 1)
ON DUPLICATE KEY UPDATE config_key = config_key;
