-- =====================================================================
-- PrivateCloudDisk - 管理后台数据库初始化脚本
-- 数据库: private_cloud_disk
-- 说明: 管理员系统、审计日志、安全事件、系统配置、IP黑名单
-- 依赖: database_init.sql 必须先执行（用户表、文件表等）
-- =====================================================================

USE private_cloud_disk;

-- =====================================================================
-- 1. 管理员用户表
-- 管理员与普通用户分离存储，支持多角色管理员
-- 角色: SUPER_ADMIN > ADMIN > MODERATOR
-- SUPER_ADMIN: 可管理其他管理员，系统配置
-- ADMIN: 可管理用户、文件、安全事件
-- MODERATOR: 只读查看，处理安全事件
-- =====================================================================
DROP TABLE IF EXISTS pcd_admin_user_table;
CREATE TABLE pcd_admin_user_table (
    admin_id                BINARY(16)      NOT NULL PRIMARY KEY                  COMMENT '管理员ID',
    admin_account           VARCHAR(70)     NOT NULL UNIQUE                       COMMENT '管理员账号',
    admin_name              VARCHAR(120)    NOT NULL                              COMMENT '管理员姓名',
    admin_email             VARCHAR(70)     NOT NULL UNIQUE                       COMMENT '管理员邮箱',
    admin_phone_number      VARCHAR(50)                                              COMMENT '管理员手机号',
    admin_password          VARCHAR(120)    NOT NULL                              COMMENT '管理员密码（BCrypt）',
    admin_role              ENUM('SUPER_ADMIN', 'ADMIN', 'MODERATOR')
                                            NOT NULL DEFAULT 'ADMIN'              COMMENT '管理员角色',
    admin_status            ENUM('ACTIVE', 'DISABLED')
                                            NOT NULL DEFAULT 'ACTIVE'             COMMENT '管理员状态',
    admin_image_path        VARCHAR(512)                                           COMMENT '管理员头像',
    admin_last_login_at     DATETIME                                               COMMENT '最后登录时间',
    admin_last_login_ip     VARCHAR(64)                                            COMMENT '最后登录IP',
    admin_login_fail_count  INT             NOT NULL DEFAULT 0                    COMMENT '连续登录失败次数',
    admin_locked_until      DATETIME                                               COMMENT '账号锁定截止时间',
    admin_two_factor_enabled TINYINT(1)     NOT NULL DEFAULT 0                    COMMENT '是否启用双因素认证',
    admin_two_factor_secret VARCHAR(64)                                            COMMENT '双因素认证密钥',
    admin_created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP     COMMENT '创建时间',
    admin_updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                                            ON UPDATE CURRENT_TIMESTAMP           COMMENT '更新时间',
    admin_created_by        BINARY(16)                                             COMMENT '创建者（SUPER_ADMIN）',
    INDEX idx_admin_role (admin_role),
    INDEX idx_admin_status (admin_status),
    INDEX idx_admin_email (admin_email),
    INDEX idx_admin_account (admin_account)
) COMMENT='管理员用户表';

-- =====================================================================
-- 2. 管理员操作审计日志表
-- 记录所有管理员操作，用于安全审计和追溯
-- =====================================================================
DROP TABLE IF EXISTS pcd_admin_audit_log_table;
CREATE TABLE pcd_admin_audit_log_table (
    audit_id                BIGINT          NOT NULL PRIMARY KEY AUTO_INCREMENT    COMMENT '审计日志ID',
    audit_admin_id          BINARY(16)      NOT NULL                              COMMENT '操作管理员ID',
    audit_admin_name        VARCHAR(120)    NOT NULL                              COMMENT '操作管理员姓名（冗余，方便查询）',
    audit_admin_role        VARCHAR(20)     NOT NULL                              COMMENT '操作管理员角色',
    audit_action            VARCHAR(100)    NOT NULL                              COMMENT '操作类型',
    audit_resource          VARCHAR(100)    NOT NULL                              COMMENT '操作资源',
    audit_resource_id       VARCHAR(64)                                            COMMENT '操作资源ID',
    audit_detail            TEXT                                                   COMMENT '操作详情（JSON）',
    audit_request_method    VARCHAR(10)                                            COMMENT '请求方法',
    audit_request_path      VARCHAR(256)                                           COMMENT '请求路径',
    audit_request_params    TEXT                                                   COMMENT '请求参数',
    audit_client_ip         VARCHAR(64)     NOT NULL                              COMMENT '客户端IP',
    audit_user_agent        VARCHAR(512)                                           COMMENT 'User-Agent',
    audit_status            ENUM('SUCCESS', 'FAILURE')
                                            NOT NULL DEFAULT 'SUCCESS'            COMMENT '操作结果',
    audit_error_message     TEXT                                                   COMMENT '错误信息',
    audit_duration_ms       INT                                                    COMMENT '操作耗时（毫秒）',
    audit_created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP     COMMENT '操作时间',
    FOREIGN KEY (audit_admin_id) REFERENCES pcd_admin_user_table(admin_id) ON DELETE CASCADE,
    INDEX idx_audit_admin_time (audit_admin_id, audit_created_at),
    INDEX idx_audit_action (audit_action),
    INDEX idx_audit_resource (audit_resource),
    INDEX idx_audit_status (audit_status),
    INDEX idx_audit_created (audit_created_at),
    INDEX idx_audit_ip_time (audit_client_ip, audit_created_at)
) COMMENT='管理员操作审计日志表';

-- =====================================================================
-- 3. 安全事件表
-- 记录系统中的安全事件，包括登录失败、暴力破解、可疑IP、病毒检测等
-- =====================================================================
DROP TABLE IF EXISTS pcd_security_event_table;
CREATE TABLE pcd_security_event_table (
    event_id                BIGINT          NOT NULL PRIMARY KEY AUTO_INCREMENT    COMMENT '事件ID',
    event_type              ENUM(
        'LOGIN_FAILURE',           -- 登录失败
        'BRUTE_FORCE',             -- 暴力破解
        'SUSPICIOUS_IP',           -- 可疑IP
        'UNAUTHORIZED_ACCESS',     -- 未授权访问
        'VIRUS_DETECTED',          -- 病毒检测
        'CONFIG_CHANGE',           -- 配置变更
        'ADMIN_ACTION',            -- 管理员操作
        'RATE_LIMIT_EXCEEDED',     -- 限流触发
        'INVALID_ADMIN_KEY'        -- 无效管理员密钥
    ) NOT NULL                                                                    COMMENT '事件类型',
    event_severity          ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')
                                            NOT NULL DEFAULT 'LOW'                COMMENT '严重级别',
    event_user_id           BINARY(16)                                             COMMENT '关联用户ID（可为空）',
    event_admin_id          BINARY(16)                                             COMMENT '关联管理员ID（可为空）',
    event_ip                VARCHAR(64)     NOT NULL                              COMMENT '来源IP',
    event_description       TEXT            NOT NULL                              COMMENT '事件描述',
    event_detail            TEXT                                                   COMMENT '事件详情（JSON）',
    event_handled           TINYINT(1)      NOT NULL DEFAULT 0                    COMMENT '是否已处理',
    event_handled_by        BINARY(16)                                             COMMENT '处理人ID',
    event_handled_at        DATETIME                                               COMMENT '处理时间',
    event_resolution        TEXT                                                   COMMENT '处理结果',
    event_created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP     COMMENT '事件时间',
    INDEX idx_event_type (event_type),
    INDEX idx_event_severity (event_severity),
    INDEX idx_event_handled (event_handled),
    INDEX idx_event_created (event_created_at),
    INDEX idx_event_ip (event_ip),
    INDEX idx_event_type_severity (event_type, event_severity)
) COMMENT='安全事件表';

-- =====================================================================
-- 4. IP 黑名单表
-- 存储被自动/手动封禁的IP，支持过期时间
-- =====================================================================
DROP TABLE IF EXISTS pcd_ip_blacklist_table;
CREATE TABLE pcd_ip_blacklist_table (
    blacklist_id            BIGINT          NOT NULL PRIMARY KEY AUTO_INCREMENT    COMMENT '黑名单ID',
    blacklist_ip            VARCHAR(64)     NOT NULL                              COMMENT '被封禁IP',
    blacklist_reason        VARCHAR(256)    NOT NULL                              COMMENT '封禁原因',
    blacklist_added_by      BINARY(16)                                             COMMENT '添加者（管理员ID，NULL表示自动封禁）',
    blacklist_created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP     COMMENT '添加时间',
    blacklist_expires_at    DATETIME                                               COMMENT '过期时间（NULL表示永久封禁）',
    blacklist_status        ENUM('ACTIVE', 'EXPIRED', 'REMOVED')
                                            NOT NULL DEFAULT 'ACTIVE'             COMMENT '封禁状态',
    blacklist_removed_by    BINARY(16)                                             COMMENT '移除者',
    blacklist_removed_at    DATETIME                                               COMMENT '移除时间',
    UNIQUE KEY uk_ip (blacklist_ip),
    INDEX idx_blacklist_status (blacklist_status),
    INDEX idx_blacklist_ip_status (blacklist_ip, blacklist_status)
) COMMENT='IP黑名单表';

-- =====================================================================
-- 5. 系统配置表
-- 存储系统全局配置，支持分组和版本控制
-- =====================================================================
DROP TABLE IF EXISTS pcd_system_config_table;
CREATE TABLE pcd_system_config_table (
    config_id               BIGINT          NOT NULL PRIMARY KEY AUTO_INCREMENT    COMMENT '配置ID',
    config_key              VARCHAR(100)    NOT NULL UNIQUE                       COMMENT '配置键',
    config_value            TEXT            NOT NULL                              COMMENT '配置值',
    config_type             VARCHAR(20)     NOT NULL DEFAULT 'STRING'             COMMENT '配置值类型',
    config_group            VARCHAR(50)     NOT NULL DEFAULT 'GENERAL'            COMMENT '配置分组',
    config_description      VARCHAR(256)                                           COMMENT '配置描述',
    config_editable         TINYINT(1)      NOT NULL DEFAULT 1                    COMMENT '是否可编辑',
    config_updated_by       BINARY(16)                                             COMMENT '最后更新者',
    config_updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                                            ON UPDATE CURRENT_TIMESTAMP           COMMENT '更新时间',
    config_version          INT             NOT NULL DEFAULT 1                    COMMENT '配置版本号',
    INDEX idx_config_group (config_group),
    INDEX idx_config_key (config_key)
) COMMENT='系统配置表';

-- =====================================================================
-- 6. 系统资源监控表（可选，用于存储历史资源数据）
-- =====================================================================
DROP TABLE IF EXISTS pcd_system_metrics_table;
CREATE TABLE pcd_system_metrics_table (
    metric_id               BIGINT          NOT NULL PRIMARY KEY AUTO_INCREMENT    COMMENT '指标ID',
    metric_type             VARCHAR(50)     NOT NULL                              COMMENT '指标类型',
    metric_value            DOUBLE          NOT NULL                              COMMENT '指标值',
    metric_unit             VARCHAR(20)                                            COMMENT '单位',
    metric_labels           JSON                                                   COMMENT '标签',
    metric_created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP     COMMENT '采集时间',
    INDEX idx_metric_type_time (metric_type, metric_created_at),
    INDEX idx_metric_created (metric_created_at)
) COMMENT='系统资源监控指标表';

-- =====================================================================
-- 7. 插入默认系统配置
-- =====================================================================
INSERT INTO pcd_system_config_table (config_key, config_value, config_type, config_group, config_description) VALUES
('site.name', 'PrivateCloudDisk', 'STRING', 'GENERAL', '站点名称'),
('site.description', '企业私有云盘系统', 'STRING', 'GENERAL', '站点描述'),
('site.logo_url', '', 'STRING', 'GENERAL', '站点Logo URL'),
('site.favicon_url', '', 'STRING', 'GENERAL', '站点Favicon URL'),
('site.contact_email', 'admin@privateclouddisk.com', 'STRING', 'GENERAL', '联系邮箱'),

('upload.max_file_size', '10737418240', 'LONG', 'UPLOAD', '单文件最大大小（字节，默认10GB）'),
('upload.allowed_types', '*', 'STRING', 'UPLOAD', '允许上传的文件类型（*表示全部）'),
('upload.max_concurrency', '3', 'INT', 'UPLOAD', '最大并发上传数'),
('upload.chunk_size', '5242880', 'INT', 'UPLOAD', '分片上传块大小（字节）'),
('upload.threshold', '10485760', 'LONG', 'UPLOAD', '分片上传阈值（字节）'),

('security.enable_registration', 'true', 'BOOLEAN', 'SECURITY', '是否允许注册'),
('security.enable_captcha', 'true', 'BOOLEAN', 'SECURITY', '是否启用人机验证'),
('security.enable_2fa', 'false', 'BOOLEAN', 'SECURITY', '是否启用双因素认证'),
('security.session_timeout', '1800', 'INT', 'SECURITY', '会话超时时间（秒）'),
('security.max_login_attempts', '5', 'INT', 'SECURITY', '最大登录失败次数'),
('security.password_min_length', '8', 'INT', 'SECURITY', '密码最小长度'),
('security.password_require_special', 'true', 'BOOLEAN', 'SECURITY', '密码是否要求特殊字符'),
('security.lockout_duration', '1800', 'INT', 'SECURITY', '账号锁定时间（秒）'),

('virus.scan_enabled', 'true', 'BOOLEAN', 'VIRUS', '是否启用病毒扫描'),
('virus.auto_scan_on_upload', 'true', 'BOOLEAN', 'VIRUS', '上传时自动扫描'),
('virus.quarantine_on_detect', 'true', 'BOOLEAN', 'VIRUS', '检测到病毒时隔离文件'),

('admin.rate_limit_per_second', '10', 'INT', 'ADMIN', '管理接口每秒请求限制'),
('admin.rate_limit_per_minute', '300', 'INT', 'ADMIN', '管理接口每分钟请求限制'),
('admin.auto_lock_after_failures', '5', 'INT', 'ADMIN', '管理员登录失败锁定次数'),
('admin.session_timeout', '3600', 'INT', 'ADMIN', '管理员会话超时（秒）'),

('notification.email_enabled', 'true', 'BOOLEAN', 'NOTIFICATION', '是否启用邮件通知'),
('notification.sms_enabled', 'false', 'BOOLEAN', 'NOTIFICATION', '是否启用短信通知'),
('notification.storage_alert_threshold', '0.9', 'DOUBLE', 'NOTIFICATION', '存储空间告警阈值'),

('maintenance.mode', 'false', 'BOOLEAN', 'MAINTENANCE', '是否开启维护模式'),
('maintenance.message', '系统维护中，请稍候...', 'STRING', 'MAINTENANCE', '维护模式提示信息');

-- =====================================================================
-- 8. 插入默认超级管理员（密码为 BCrypt 加密的 "admin123"）
-- 生产环境部署后请立即修改密码！
-- BCrypt hash for "admin123": $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
-- =====================================================================
INSERT INTO pcd_admin_user_table (
    admin_id,
    admin_account,
    admin_name,
    admin_email,
    admin_password,
    admin_role,
    admin_status
) VALUES (
    UNHEX(REPLACE('a1b2c3d4-e5f6-7890-abcd-ef1234567890', '-', '')),
    'superadmin',
    '超级管理员',
    'superadmin@privateclouddisk.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'SUPER_ADMIN',
    'ACTIVE'
);