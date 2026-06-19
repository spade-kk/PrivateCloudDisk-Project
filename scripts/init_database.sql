-- =====================================================================
-- PrivateCloudDisk - 完整数据库初始化脚本
-- =====================================================================
-- 数据库: private_cloud_disk
-- 用途:   一键初始化项目所有表结构、默认配置和测试账号
-- 使用:   mysql -u root -p < scripts/init_database.sql
--
-- 注意:   此脚本会 DROP 已存在的同名表，请勿在生产环境执行！
--
-- 包含:
--   1. 用户系统（用户表、设备表、登录会话、登录审计）
--   2. 文件系统（文件信息、目录树、闭包表、上传会话、切片表）
--   3. 收藏 & 回收站
--   4. 配额管理（配额表、配额变更日志）
--   5. 分享链接
--   6. 通知发送日志
--   7. 管理员系统（管理员用户、审计日志、安全事件、系统配置、IP黑名单、系统监控指标）
--   8. 默认超级管理员（密码: admin123）
--   9. 默认测试用户（密码: test123456，含根目录、闭包表、配额）
--   10. 默认系统配置
--
-- 密码哈希说明:
--   前端 PBKDF2-SHA256(60万次, pepper="clouddrive-pbkdf2-v1-pepper") → hex
--   后端 BCrypt(12 rounds) → 数据库存储
--   如需生成其他密码的哈希，请运行:
--     python3 scripts/generate_admin_password.py <密码>
-- =====================================================================

-- 创建数据库
DROP DATABASE IF EXISTS private_cloud_disk;
CREATE DATABASE private_cloud_disk
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE private_cloud_disk;

-- =====================================================================
-- 第一部分：用户系统
-- =====================================================================

-- 1.1 用户信息表
DROP TABLE IF EXISTS pcd_user_info_table;
CREATE TABLE pcd_user_info_table (
    user_name               VARCHAR(120)    NOT NULL            COMMENT '用户名',
    user_id                 BINARY(16)      NOT NULL PRIMARY KEY COMMENT '用户ID (UUID)',
    user_phone_number       VARCHAR(50)     NOT NULL UNIQUE     COMMENT '手机号',
    user_image_path         VARCHAR(512)                        COMMENT '用户头像路径',
    user_password           VARCHAR(120)    NOT NULL            COMMENT '用户密码 (BCrypt二次哈希)',
    user_account            VARCHAR(70)     NOT NULL UNIQUE     COMMENT '用户账号',
    user_email              VARCHAR(70)     UNIQUE              COMMENT '用户邮箱'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户信息表';

-- 1.2 用户设备表
DROP TABLE IF EXISTS pcd_user_device_table;
CREATE TABLE pcd_user_device_table (
    device_id               BINARY(16)      NOT NULL PRIMARY KEY COMMENT '设备ID',
    device_user_id          BINARY(16)      NOT NULL             COMMENT '所属用户ID',
    device_client_type      VARCHAR(50)     NOT NULL             COMMENT '客户端类型: WEB/IOS/MACOS/WECHAT/PC',
    device_client_name      VARCHAR(120)                        COMMENT '客户端展示名称',
    device_platform         VARCHAR(120)                        COMMENT '系统或平台信息',
    device_user_agent_hash  VARCHAR(64)                         COMMENT 'User-Agent规范化后的哈希',
    device_public_key       TEXT                                COMMENT '设备密钥绑定的公钥',
    device_created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    device_last_seen_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    device_status           ENUM('active','disabled','revoked') NOT NULL DEFAULT 'active',
    FOREIGN KEY (device_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    INDEX idx_device_user_status (device_user_id, device_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户登录设备表';

-- 1.3 登录会话表
DROP TABLE IF EXISTS pcd_login_session_table;
CREATE TABLE pcd_login_session_table (
    login_session_id             BINARY(16) NOT NULL PRIMARY KEY COMMENT '会话ID (sid)',
    login_session_user_id        BINARY(16) NOT NULL             COMMENT '用户ID',
    login_session_device_id      BINARY(16)                     COMMENT '关联设备ID',
    login_session_token_jti      BINARY(16)                     COMMENT 'JWT jti',
    login_session_client_ip      VARCHAR(64)                    COMMENT '登录IP',
    login_session_user_agent     VARCHAR(512)                   COMMENT 'User-Agent',
    login_session_started_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    login_session_expires_at     DATETIME    NOT NULL            COMMENT '会话过期时间',
    login_session_revoked_at     DATETIME                       COMMENT '会话撤销时间',
    login_session_status         ENUM('active','expired','revoked') NOT NULL DEFAULT 'active',
    FOREIGN KEY (login_session_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    FOREIGN KEY (login_session_device_id) REFERENCES pcd_user_device_table(device_id) ON DELETE SET NULL,
    INDEX idx_session_user_status (login_session_user_id, login_session_status),
    INDEX idx_session_device_status (login_session_device_id, login_session_status),
    INDEX idx_session_jti (login_session_token_jti)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户登录会话表';

-- 1.4 登录审计表
DROP TABLE IF EXISTS pcd_login_audit_table;
CREATE TABLE pcd_login_audit_table (
    audit_id                 BIGINT       NOT NULL PRIMARY KEY AUTO_INCREMENT,
    audit_user_id            BINARY(16)                         COMMENT '匹配到的用户ID',
    audit_account            VARCHAR(100)                       COMMENT '登录账号',
    audit_phone_number       VARCHAR(50)                        COMMENT '登录手机号',
    audit_success            TINYINT(1)   NOT NULL              COMMENT '是否登录成功',
    audit_failure_reason     VARCHAR(120)                       COMMENT '失败原因',
    audit_client_ip          VARCHAR(64)                        COMMENT '客户端IP',
    audit_user_agent         VARCHAR(512)                       COMMENT 'User-Agent',
    audit_created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (audit_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE SET NULL,
    INDEX idx_audit_user_time (audit_user_id, audit_created_at),
    INDEX idx_audit_account_time (audit_account, audit_created_at),
    INDEX idx_audit_ip_time (audit_client_ip, audit_created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录审计表';


-- =====================================================================
-- 第二部分：文件系统
-- =====================================================================

-- 2.1 目录树节点表（文件夹/根目录节点）
DROP TABLE IF EXISTS pcd_directory_tree_table;
CREATE TABLE pcd_directory_tree_table (
    node_id          BINARY(16)      NOT NULL PRIMARY KEY       COMMENT '节点ID',
    node_user_id     BINARY(16)      NOT NULL                   COMMENT '所属用户ID',
    node_parent_id   BINARY(16)                                COMMENT '父节点ID（根节点为NULL）',
    node_name        VARCHAR(200)    NOT NULL                   COMMENT '节点名称',
    node_create_time TIMESTAMP       NOT NULL DEFAULT NOW()     COMMENT '节点创建时间',
    node_status      ENUM('lock','active','pending','trashed','deleted')
                                      NOT NULL DEFAULT 'active' COMMENT '节点状态',
    FOREIGN KEY (node_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    FOREIGN KEY (node_parent_id) REFERENCES pcd_directory_tree_table(node_id) ON DELETE CASCADE,
    UNIQUE KEY uk_directory_tree (node_id, node_user_id, node_parent_id),
    INDEX idx_node_user (node_user_id),
    INDEX idx_node_parent (node_parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='节点目录树表';

-- 2.2 目录闭包表（高效查询祖先/后代关系）
DROP TABLE IF EXISTS pcd_directory_closure_table;
CREATE TABLE pcd_directory_closure_table (
    user_id          BINARY(16)      NOT NULL                   COMMENT '所属用户ID',
    ancestor_id      BINARY(16)      NOT NULL                   COMMENT '祖先节点ID',
    descendant_id    BINARY(16)      NOT NULL                   COMMENT '后代节点ID',
    depth            INT             NOT NULL                   COMMENT '深度（父子=1，自引用=0）',
    PRIMARY KEY (ancestor_id, descendant_id),
    UNIQUE KEY uk_descendant (user_id, descendant_id, ancestor_id),
    KEY idx_depth (depth),
    FOREIGN KEY (user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    FOREIGN KEY (ancestor_id) REFERENCES pcd_directory_tree_table(node_id) ON DELETE CASCADE,
    FOREIGN KEY (descendant_id) REFERENCES pcd_directory_tree_table(node_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='目录树闭包表';

-- 2.3 文件信息表
DROP TABLE IF EXISTS pcd_file_info_table;
CREATE TABLE pcd_file_info_table (
    file_name               VARCHAR(150)    NOT NULL                COMMENT '文件名称',
    file_uploaded_time      TIMESTAMP       NOT NULL DEFAULT NOW()  COMMENT '文件上传时间',
    file_size               BIGINT          NOT NULL                COMMENT '文件大小',
    file_type               VARCHAR(120)    NOT NULL                COMMENT '文件类型',
    file_author_id          BINARY(16)      NOT NULL                COMMENT '文件作者ID',
    file_id                 BINARY(16)      NOT NULL PRIMARY KEY    COMMENT '文件ID',
    file_checksum           VARCHAR(256)    NOT NULL                COMMENT '文件校验值',
    file_total_chunks       INT             NOT NULL                COMMENT '文件切片数目',
    file_node_id            BINARY(16)      NOT NULL                COMMENT '文件所在目录节点ID',
    file_storage_path       VARCHAR(512)                            COMMENT '文件存储路径',
    file_status             ENUM('merging','merged','merge_failed','scanning','scan_failed','reject','active','deleted','trashed')
                                            NOT NULL DEFAULT 'active' COMMENT '文件状态',
    FOREIGN KEY (file_author_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    FOREIGN KEY (file_node_id) REFERENCES pcd_directory_tree_table(node_id) ON DELETE CASCADE,
    UNIQUE KEY uk_file_info (file_id, file_author_id, file_node_id),
    INDEX idx_file_author (file_author_id),
    INDEX idx_file_node (file_node_id),
    INDEX idx_file_status (file_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件信息表';

-- 2.4 上传会话表
DROP TABLE IF EXISTS pcd_uploads_session_table;
CREATE TABLE pcd_uploads_session_table (
    uploads_id              BINARY(16)      NOT NULL PRIMARY KEY    COMMENT '上传会话ID',
    uploads_user_id         BINARY(16)      NOT NULL                COMMENT '上传用户ID',
    uploads_total_chunks    INT             NOT NULL                COMMENT '上传切片总数',
    uploads_starting_time   TIMESTAMP       NOT NULL DEFAULT NOW()  COMMENT '上传开始时间',
    uploads_endding_time    TIMESTAMP       NOT NULL                COMMENT '上传结束时间',
    uploads_file_size       BIGINT          NOT NULL                COMMENT '文件大小',
    uploads_file_checksum   VARCHAR(256)    NOT NULL                COMMENT '文件校验值',
    uploads_chunks_max_size INT             NOT NULL                COMMENT '切片最大大小',
    uploads_file_name       VARCHAR(150)    NOT NULL                COMMENT '文件名称',
    uploads_file_type       VARCHAR(60)     NOT NULL                COMMENT '文件类型',
    uploads_node_id         BINARY(16)      NOT NULL                COMMENT '文件所在目录节点ID',
    uploads_status          ENUM('uploading','completed','failed') DEFAULT 'uploading' COMMENT '上传状态',
    FOREIGN KEY (uploads_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    FOREIGN KEY (uploads_node_id) REFERENCES pcd_directory_tree_table(node_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件上传会话表';

-- 2.5 上传切片表
DROP TABLE IF EXISTS pcd_upload_chunks_table;
CREATE TABLE pcd_upload_chunks_table (
    chunk_uploads_id    BINARY(16)      NOT NULL                    COMMENT '关联上传会话ID',
    chunk_index         INT             NOT NULL                    COMMENT '切片索引',
    chunk_status        ENUM('pending','uploading','uploaded','failed') DEFAULT 'pending' COMMENT '切片状态',
    chunk_storage_path  VARCHAR(512)    NOT NULL                    COMMENT '切片存储路径',
    chunk_uploaded_time TIMESTAMP       NOT NULL DEFAULT NOW()      COMMENT '切片上传时间',
    PRIMARY KEY (chunk_uploads_id, chunk_index),
    FOREIGN KEY (chunk_uploads_id) REFERENCES pcd_uploads_session_table(uploads_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件切片表';


-- =====================================================================
-- 第三部分：收藏 & 回收站
-- =====================================================================

-- 3.1 文件/文件夹收藏表
DROP TABLE IF EXISTS pcd_file_star_table;
CREATE TABLE pcd_file_star_table (
    star_id             BIGINT          PRIMARY KEY AUTO_INCREMENT,
    star_user_id        BINARY(16)      NOT NULL                    COMMENT '用户ID',
    star_target_type    ENUM('file','folder') NOT NULL DEFAULT 'file' COMMENT '收藏目标类型',
    star_file_id        BINARY(16)                                  COMMENT '文件ID（收藏文件时填写）',
    star_node_id        BINARY(16)                                  COMMENT '文件夹节点ID（收藏文件夹时填写）',
    star_starred_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    FOREIGN KEY (star_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    FOREIGN KEY (star_file_id) REFERENCES pcd_file_info_table(file_id) ON DELETE CASCADE,
    FOREIGN KEY (star_node_id) REFERENCES pcd_directory_tree_table(node_id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_file_star (star_user_id, star_file_id),
    UNIQUE KEY uk_user_folder_star (star_user_id, star_node_id),
    INDEX idx_user_starred (star_user_id, star_starred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件/文件夹收藏表';

-- 3.2 回收站表
DROP TABLE IF EXISTS pcd_trash_target_table;
CREATE TABLE pcd_trash_target_table (
    trash_id                BIGINT          PRIMARY KEY AUTO_INCREMENT,
    trash_target_id         BINARY(16)      NOT NULL                COMMENT '原目标ID',
    trash_target_type       ENUM('file','folder') NOT NULL          COMMENT '目标类型',
    trash_user_id           BINARY(16)      NOT NULL                COMMENT '用户ID',
    trash_target_name       VARCHAR(150)    NOT NULL                COMMENT '文件名称',
    trash_original_node_id  BINARY(16)      NOT NULL                COMMENT '原节点ID',
    trash_deleted_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '删除时间',
    trash_expires_at        DATETIME        NOT NULL                COMMENT '过期时间（自动彻底删除）',
    FOREIGN KEY (trash_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    INDEX idx_user_deleted (trash_user_id, trash_deleted_at),
    INDEX idx_expires (trash_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='回收站表';


-- =====================================================================
-- 第四部分：配额管理
-- =====================================================================

-- 4.1 用户存储配额表
DROP TABLE IF EXISTS pcd_user_quota_table;
CREATE TABLE pcd_user_quota_table (
    quota_id              BIGINT          PRIMARY KEY AUTO_INCREMENT,
    quota_user_id         BINARY(16)      NOT NULL UNIQUE             COMMENT '用户ID',
    quota_total_capacity  BIGINT          NOT NULL DEFAULT 10737418240 COMMENT '总额度（字节，默认10GB）',
    quota_used_capacity   BIGINT          NOT NULL DEFAULT 0          COMMENT '已用容量（字节）',
    quota_file_count      INT             NOT NULL DEFAULT 0          COMMENT '已上传文件数量',
    quota_version         INT             NOT NULL DEFAULT 0          COMMENT '乐观锁版本号',
    quota_created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    quota_updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (quota_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    INDEX idx_user_id (quota_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户存储配额表';

-- 4.2 配额变更日志表
DROP TABLE IF EXISTS pcd_user_quota_log_table;
CREATE TABLE pcd_user_quota_log_table (
    quota_log_id            BIGINT        PRIMARY KEY AUTO_INCREMENT,
    quota_log_user_id       BINARY(16)    NOT NULL                    COMMENT '用户ID',
    quota_log_change_type   VARCHAR(20)   NOT NULL                    COMMENT '变更类型: EXPAND/REDUCE/FILE_UPLOAD/FILE_DELETE',
    quota_log_change_bytes  BIGINT        NOT NULL                    COMMENT '变更字节数（正为增加，负为减少）',
    quota_log_before_total  BIGINT                                    COMMENT '变更前总额度',
    quota_log_after_total   BIGINT                                    COMMENT '变更后总额度',
    quota_log_before_used   BIGINT                                    COMMENT '变更前已用',
    quota_log_after_used    BIGINT                                    COMMENT '变更后已用',
    quota_log_operator      VARCHAR(50)   DEFAULT 'SYSTEM'            COMMENT '操作人',
    quota_log_created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (quota_log_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    INDEX idx_user_id_time (quota_log_user_id, quota_log_created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配额变更日志';


-- =====================================================================
-- 第五部分：分享链接
-- =====================================================================

DROP TABLE IF EXISTS pcd_share_link_table;
CREATE TABLE pcd_share_link_table (
    share_id                BINARY(16)      NOT NULL PRIMARY KEY       COMMENT '分享ID',
    share_token             VARCHAR(36)     NOT NULL UNIQUE           COMMENT '分享令牌（UUID，对外暴露）',
    share_owner_id          BINARY(16)      NOT NULL                   COMMENT '分享者用户ID',
    share_target_type       ENUM('file','folder') NOT NULL            COMMENT '分享目标类型',
    share_file_id           BINARY(16)                                COMMENT '分享的文件ID',
    share_node_id           BINARY(16)                                COMMENT '分享的文件夹节点ID',
    share_name              VARCHAR(200)    NOT NULL                   COMMENT '分享名称',
    share_password          VARCHAR(120)                              COMMENT '提取码（BCrypt哈希，NULL=无密码）',
    share_has_password      TINYINT(1)      NOT NULL DEFAULT 0        COMMENT '是否有密码保护',
    share_expires_at        DATETIME                                  COMMENT '过期时间（NULL=永久有效）',
    share_view_count        INT             NOT NULL DEFAULT 0        COMMENT '浏览次数',
    share_status            ENUM('active','revoked','expired') NOT NULL DEFAULT 'active' COMMENT '分享状态',
    share_created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    share_updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (share_owner_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    FOREIGN KEY (share_file_id) REFERENCES pcd_file_info_table(file_id) ON DELETE CASCADE,
    FOREIGN KEY (share_node_id) REFERENCES pcd_directory_tree_table(node_id) ON DELETE CASCADE,
    INDEX idx_share_owner (share_owner_id, share_status),
    INDEX idx_share_token (share_token),
    INDEX idx_share_status (share_status),
    CONSTRAINT chk_share_target CHECK (
        (share_target_type = 'file' AND share_file_id IS NOT NULL AND share_node_id IS NULL) OR
        (share_target_type = 'folder' AND share_node_id IS NOT NULL AND share_file_id IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分享链接管理表';


-- =====================================================================
-- 第六部分：通知发送日志
-- =====================================================================

DROP TABLE IF EXISTS pcd_notification_send_log_table;
CREATE TABLE pcd_notification_send_log_table (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键自增ID',
    event_id        VARCHAR(255)    NOT NULL                COMMENT '事件唯一ID',
    channel         VARCHAR(20)     NOT NULL                COMMENT '通道：EMAIL/SMS',
    receiver        VARCHAR(255)    NOT NULL                COMMENT '接收者：邮箱地址或手机号',
    user_id         BINARY(16)      DEFAULT NULL            COMMENT '关联用户ID',
    status          VARCHAR(20)     NOT NULL                COMMENT '状态: PENDING/SUCCESS/FAILED',
    retry_count     INT             NOT NULL DEFAULT 0      COMMENT '重试次数',
    error_message   VARCHAR(1000)   DEFAULT NULL            COMMENT '错误信息',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_channel_receiver (event_id, channel, receiver),
    KEY idx_status (status),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知发送日志表';


-- =====================================================================
-- 第七部分：管理员系统
-- =====================================================================

-- 7.1 管理员用户表
DROP TABLE IF EXISTS pcd_admin_user_table;
CREATE TABLE pcd_admin_user_table (
    admin_id                BINARY(16)      NOT NULL PRIMARY KEY       COMMENT '管理员ID',
    admin_account           VARCHAR(70)     NOT NULL UNIQUE           COMMENT '管理员账号',
    admin_name              VARCHAR(120)    NOT NULL                   COMMENT '管理员姓名',
    admin_email             VARCHAR(70)     NOT NULL UNIQUE           COMMENT '管理员邮箱',
    admin_phone_number      VARCHAR(50)                                COMMENT '管理员手机号',
    admin_password          VARCHAR(120)    NOT NULL                   COMMENT '管理员密码（BCrypt二次哈希）',
    admin_role              ENUM('SUPER_ADMIN','ADMIN','MODERATOR')
                                            NOT NULL DEFAULT 'ADMIN'   COMMENT '管理员角色',
    admin_status            ENUM('ACTIVE','DISABLED')
                                            NOT NULL DEFAULT 'ACTIVE'  COMMENT '管理员状态',
    admin_image_path        VARCHAR(512)                               COMMENT '管理员头像',
    admin_last_login_at     DATETIME                                   COMMENT '最后登录时间',
    admin_last_login_ip     VARCHAR(64)                                COMMENT '最后登录IP',
    admin_login_fail_count  INT             NOT NULL DEFAULT 0         COMMENT '连续登录失败次数',
    admin_locked_until      DATETIME                                   COMMENT '账号锁定截止时间',
    admin_two_factor_enabled TINYINT(1)     NOT NULL DEFAULT 0         COMMENT '是否启用双因素认证',
    admin_two_factor_secret VARCHAR(64)                                COMMENT '双因素认证密钥',
    admin_created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    admin_updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    admin_created_by        BINARY(16)                                 COMMENT '创建者ID',
    INDEX idx_admin_role (admin_role),
    INDEX idx_admin_status (admin_status),
    INDEX idx_admin_email (admin_email),
    INDEX idx_admin_account (admin_account)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员用户表';

-- 7.2 管理员审计日志表
DROP TABLE IF EXISTS pcd_admin_audit_log_table;
CREATE TABLE pcd_admin_audit_log_table (
    audit_id                BIGINT          NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '审计日志ID',
    audit_admin_id          BINARY(16)      NOT NULL                            COMMENT '操作管理员ID',
    audit_admin_name        VARCHAR(120)    NOT NULL                            COMMENT '操作管理员姓名',
    audit_admin_role        VARCHAR(20)     NOT NULL                            COMMENT '操作管理员角色',
    audit_action            VARCHAR(100)    NOT NULL                            COMMENT '操作类型',
    audit_resource          VARCHAR(100)    NOT NULL                            COMMENT '操作资源',
    audit_resource_id       VARCHAR(64)                                        COMMENT '操作资源ID',
    audit_detail            TEXT                                               COMMENT '操作详情（JSON）',
    audit_request_method    VARCHAR(10)                                        COMMENT '请求方法',
    audit_request_path      VARCHAR(256)                                       COMMENT '请求路径',
    audit_request_params    TEXT                                               COMMENT '请求参数',
    audit_client_ip         VARCHAR(64)     NOT NULL                            COMMENT '客户端IP',
    audit_user_agent        VARCHAR(512)                                       COMMENT 'User-Agent',
    audit_status            ENUM('SUCCESS','FAILURE')
                                            NOT NULL DEFAULT 'SUCCESS'          COMMENT '操作结果',
    audit_error_message     TEXT                                               COMMENT '错误信息',
    audit_duration_ms       INT                                                COMMENT '操作耗时（毫秒）',
    audit_created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '操作时间',
    FOREIGN KEY (audit_admin_id) REFERENCES pcd_admin_user_table(admin_id) ON DELETE CASCADE,
    INDEX idx_audit_admin_time (audit_admin_id, audit_created_at),
    INDEX idx_audit_action (audit_action),
    INDEX idx_audit_resource (audit_resource),
    INDEX idx_audit_status (audit_status),
    INDEX idx_audit_created (audit_created_at),
    INDEX idx_audit_ip_time (audit_client_ip, audit_created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员操作审计日志表';

-- 7.3 安全事件表
DROP TABLE IF EXISTS pcd_security_event_table;
CREATE TABLE pcd_security_event_table (
    event_id                BIGINT          NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '事件ID',
    event_type              ENUM(
        'LOGIN_FAILURE',
        'BRUTE_FORCE',
        'SUSPICIOUS_IP',
        'UNAUTHORIZED_ACCESS',
        'VIRUS_DETECTED',
        'CONFIG_CHANGE',
        'ADMIN_ACTION',
        'RATE_LIMIT_EXCEEDED',
        'INVALID_ADMIN_KEY'
    ) NOT NULL                                                                  COMMENT '事件类型',
    event_severity          ENUM('LOW','MEDIUM','HIGH','CRITICAL')
                                            NOT NULL DEFAULT 'LOW'              COMMENT '严重级别',
    event_user_id           BINARY(16)                                         COMMENT '关联用户ID',
    event_admin_id          BINARY(16)                                         COMMENT '关联管理员ID',
    event_ip                VARCHAR(64)     NOT NULL                            COMMENT '来源IP',
    event_description       TEXT            NOT NULL                            COMMENT '事件描述',
    event_detail            TEXT                                               COMMENT '事件详情（JSON）',
    event_handled           TINYINT(1)      NOT NULL DEFAULT 0                  COMMENT '是否已处理',
    event_handled_by        BINARY(16)                                         COMMENT '处理人ID',
    event_handled_at        DATETIME                                           COMMENT '处理时间',
    event_resolution        TEXT                                               COMMENT '处理结果',
    event_created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '事件时间',
    INDEX idx_event_type (event_type),
    INDEX idx_event_severity (event_severity),
    INDEX idx_event_handled (event_handled),
    INDEX idx_event_created (event_created_at),
    INDEX idx_event_ip (event_ip),
    INDEX idx_event_type_severity (event_type, event_severity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='安全事件表';

-- 7.4 IP黑名单表
DROP TABLE IF EXISTS pcd_ip_blacklist_table;
CREATE TABLE pcd_ip_blacklist_table (
    blacklist_id            BIGINT          NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '黑名单ID',
    blacklist_ip            VARCHAR(64)     NOT NULL                            COMMENT '被封禁IP',
    blacklist_reason        VARCHAR(256)    NOT NULL                            COMMENT '封禁原因',
    blacklist_added_by      BINARY(16)                                         COMMENT '添加者',
    blacklist_created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '添加时间',
    blacklist_expires_at    DATETIME                                           COMMENT '过期时间（NULL=永久）',
    blacklist_status        ENUM('ACTIVE','EXPIRED','REMOVED')
                                            NOT NULL DEFAULT 'ACTIVE'           COMMENT '封禁状态',
    blacklist_removed_by    BINARY(16)                                         COMMENT '移除者',
    blacklist_removed_at    DATETIME                                           COMMENT '移除时间',
    UNIQUE KEY uk_ip (blacklist_ip),
    INDEX idx_blacklist_status (blacklist_status),
    INDEX idx_blacklist_ip_status (blacklist_ip, blacklist_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IP黑名单表';

-- 7.5 系统配置表
DROP TABLE IF EXISTS pcd_system_config_table;
CREATE TABLE pcd_system_config_table (
    config_id               BIGINT          NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '配置ID',
    config_key              VARCHAR(100)    NOT NULL UNIQUE                     COMMENT '配置键',
    config_value            TEXT            NOT NULL                            COMMENT '配置值',
    config_type             VARCHAR(20)     NOT NULL DEFAULT 'STRING'           COMMENT '配置值类型',
    config_group            VARCHAR(50)     NOT NULL DEFAULT 'GENERAL'          COMMENT '配置分组',
    config_description      VARCHAR(256)                                       COMMENT '配置描述',
    config_editable         TINYINT(1)      NOT NULL DEFAULT 1                  COMMENT '是否可编辑',
    config_updated_by       BINARY(16)                                         COMMENT '最后更新者',
    config_updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    config_version          INT             NOT NULL DEFAULT 1                  COMMENT '配置版本号',
    INDEX idx_config_group (config_group),
    INDEX idx_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- 7.6 系统资源监控指标表
DROP TABLE IF EXISTS pcd_system_metrics_table;
CREATE TABLE pcd_system_metrics_table (
    metric_id               BIGINT          NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '指标ID',
    metric_type             VARCHAR(50)     NOT NULL                            COMMENT '指标类型',
    metric_value            DOUBLE          NOT NULL                            COMMENT '指标值',
    metric_unit             VARCHAR(20)                                        COMMENT '单位',
    metric_labels           JSON                                               COMMENT '标签',
    metric_created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '采集时间',
    INDEX idx_metric_type_time (metric_type, metric_created_at),
    INDEX idx_metric_created (metric_created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统资源监控指标表';


-- =====================================================================
-- 第八部分：插入默认数据
-- =====================================================================

-- -------------------------------------------------------------------
-- 8.1 默认超级管理员
--     账号: superadmin
--     密码: admin123
--     角色: SUPER_ADMIN（最高权限）
--     密码哈希算法: BCrypt( PBKDF2-SHA256("admin123", pepper) )
--     如需更换密码，请运行:
--       python3 scripts/generate_admin_password.py <新密码>
--     然后将生成的 bcrypt_hash 替换下面的 admin_password 值
-- -------------------------------------------------------------------
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
    -- 密码: admin123 → PBKDF2 → BCrypt
    '$2b$12$jKWWnXEsIn7Be2RHkpsTb.wk252WlxUfAy3bYyjjsJgF/P0FIP9Hi',
    'SUPER_ADMIN',
    'ACTIVE'
);

-- -------------------------------------------------------------------
-- 8.2 默认测试用户
--     账号: pcd_test001
--     密码: test123456
--     此用户拥有完整的网盘功能：根目录、闭包表、配额
-- -------------------------------------------------------------------

-- 8.2.1 创建测试用户
INSERT INTO pcd_user_info_table (
    user_name,
    user_id,
    user_phone_number,
    user_password,
    user_account,
    user_email
) VALUES (
    '测试用户',
    UNHEX(REPLACE('b1b2b3b4-c5c6-7890-abcd-ef1234567890', '-', '')),
    '13800138000',
    -- 密码: test123456 → PBKDF2 → BCrypt
    '$2b$12$EF3X1buHdm/0Ke/hJYeJwe2Oet17BSSCEbPFjAQpLmlD/Py5WzGbG',
    'pcd_test001',
    'test@privateclouddisk.com'
);

-- 8.2.2 创建测试用户根目录节点
INSERT INTO pcd_directory_tree_table (
    node_id,
    node_user_id,
    node_parent_id,
    node_name,
    node_status
) VALUES (
    UNHEX(REPLACE('c1c2c3c4-d5d6-7890-abcd-ef1234567890', '-', '')),
    UNHEX(REPLACE('b1b2b3b4-c5c6-7890-abcd-ef1234567890', '-', '')),
    NULL,
    '#root',
    'active'
);

-- 8.2.3 创建测试用户根目录闭包关系（自引用，depth=0）
INSERT INTO pcd_directory_closure_table (
    user_id,
    ancestor_id,
    descendant_id,
    depth
) VALUES (
    UNHEX(REPLACE('b1b2b3b4-c5c6-7890-abcd-ef1234567890', '-', '')),
    UNHEX(REPLACE('c1c2c3c4-d5d6-7890-abcd-ef1234567890', '-', '')),
    UNHEX(REPLACE('c1c2c3c4-d5d6-7890-abcd-ef1234567890', '-', '')),
    0
);

-- 8.2.4 创建测试用户配额（默认10GB）
INSERT INTO pcd_user_quota_table (
    quota_user_id,
    quota_total_capacity,
    quota_used_capacity,
    quota_file_count
) VALUES (
    UNHEX(REPLACE('b1b2b3b4-c5c6-7890-abcd-ef1234567890', '-', '')),
    10737418240,   -- 10GB
    0,
    0
);


-- =====================================================================
-- 第九部分：插入默认系统配置
-- =====================================================================

INSERT INTO pcd_system_config_table (config_key, config_value, config_type, config_group, config_description) VALUES
-- 站点配置
('site.name', 'PrivateCloudDisk', 'STRING', 'GENERAL', '站点名称'),
('site.description', '企业私有云盘系统', 'STRING', 'GENERAL', '站点描述'),
('site.logo_url', '', 'STRING', 'GENERAL', '站点Logo URL'),
('site.favicon_url', '', 'STRING', 'GENERAL', '站点Favicon URL'),
('site.contact_email', 'admin@privateclouddisk.com', 'STRING', 'GENERAL', '联系邮箱'),

-- 上传配置
('upload.max_file_size', '10737418240', 'LONG', 'UPLOAD', '单文件最大大小（字节，默认10GB）'),
('upload.allowed_types', '*', 'STRING', 'UPLOAD', '允许上传的文件类型'),
('upload.max_concurrency', '3', 'INT', 'UPLOAD', '最大并发上传数'),
('upload.chunk_size', '5242880', 'INT', 'UPLOAD', '分片上传块大小（字节）'),
('upload.threshold', '10485760', 'LONG', 'UPLOAD', '分片上传阈值（字节）'),

-- 安全配置
('security.enable_registration', 'true', 'BOOLEAN', 'SECURITY', '是否允许注册'),
('security.enable_captcha', 'true', 'BOOLEAN', 'SECURITY', '是否启用人机验证'),
('security.enable_2fa', 'false', 'BOOLEAN', 'SECURITY', '是否启用双因素认证'),
('security.session_timeout', '1800', 'INT', 'SECURITY', '会话超时时间（秒）'),
('security.max_login_attempts', '5', 'INT', 'SECURITY', '最大登录失败次数'),
('security.password_min_length', '8', 'INT', 'SECURITY', '密码最小长度'),
('security.password_require_special', 'true', 'BOOLEAN', 'SECURITY', '密码是否要求特殊字符'),
('security.lockout_duration', '1800', 'INT', 'SECURITY', '账号锁定时间（秒）'),

-- 病毒扫描配置
('virus.scan_enabled', 'true', 'BOOLEAN', 'VIRUS', '是否启用病毒扫描'),
('virus.auto_scan_on_upload', 'true', 'BOOLEAN', 'VIRUS', '上传时自动扫描'),
('virus.quarantine_on_detect', 'true', 'BOOLEAN', 'VIRUS', '检测到病毒时隔离文件'),

-- 管理员配置
('admin.rate_limit_per_second', '10', 'INT', 'ADMIN', '管理接口每秒请求限制'),
('admin.rate_limit_per_minute', '300', 'INT', 'ADMIN', '管理接口每分钟请求限制'),
('admin.auto_lock_after_failures', '5', 'INT', 'ADMIN', '管理员登录失败锁定次数'),
('admin.session_timeout', '3600', 'INT', 'ADMIN', '管理员会话超时（秒）'),

-- 通知配置
('notification.email_enabled', 'true', 'BOOLEAN', 'NOTIFICATION', '是否启用邮件通知'),
('notification.sms_enabled', 'false', 'BOOLEAN', 'NOTIFICATION', '是否启用短信通知'),
('notification.storage_alert_threshold', '0.9', 'DOUBLE', 'NOTIFICATION', '存储空间告警阈值'),

-- 维护模式
('maintenance.mode', 'false', 'BOOLEAN', 'MAINTENANCE', '是否开启维护模式'),
('maintenance.message', '系统维护中，请稍候...', 'STRING', 'MAINTENANCE', '维护模式提示信息');

-- =====================================================================
-- 初始化完成
-- =====================================================================
-- 测试账号:
--   管理员登录: POST /api/v1/business/admin/auth/login
--     Body: { "account": "superadmin", "password": "admin123" }
--     Header: X-Admin-Key: changeme-admin-key
--
--   用户登录:   POST /api/v1/business/user/login
--     Body: { "account": "pcd_test001", "password": "test123456" }
--
-- 注意: 生产环境请立即修改默认密码！
-- =====================================================================