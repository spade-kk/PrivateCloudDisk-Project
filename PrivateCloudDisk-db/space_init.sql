-- =====================================================================
-- PrivateCloudDisk 空间系统 — 数据库初始化
-- =====================================================================
-- 设计理念：
--   每个空间是一个独立的网盘，拥有独立的配额、文件隔离和权限控制。
--   用户可拥有多个空间（个人空间、企业空间、公共空间、团队空间）。
--
-- 空间类型：
--   personal   — 个人主网盘，每个用户注册时自动创建，默认私有
--   enterprise — 企业空间，支持细粒度权限管理和容量扩展
--   public     — 公共空间，暴露给平台用户，支持白名单/黑名单可见性
--   team       — 团队空间，类似群聊，成员加入需审批，自动创建IM群组
--
-- 权限模型：
--   role（角色）+ permission（细粒度权限）双层控制
--   角色：owner > admin > editor > viewer
--   细粒度权限：can_read / can_write / can_delete / can_share / can_invite / can_manage
-- =====================================================================

USE private_cloud_disk;

-- =====================================================================
-- 1. 空间主表
-- =====================================================================
CREATE TABLE IF NOT EXISTS pcd_space_table (
    space_id            BINARY(16)      NOT NULL PRIMARY KEY                  COMMENT '空间唯一ID',
    space_name          VARCHAR(200)     NOT NULL                              COMMENT '空间名称',
    space_type          ENUM('personal', 'private', 'enterprise', 'public', 'team')
                                        NOT NULL                              COMMENT '空间类型',
    space_owner_id      BINARY(16)      NOT NULL                              COMMENT '空间创建者/所有者',
    space_quota         BIGINT          NOT NULL DEFAULT 10737418240          COMMENT '空间配额（字节），默认10GB',
    space_used          BIGINT          NOT NULL DEFAULT 0                    COMMENT '已用容量（字节）',
    space_reserved      BIGINT          NOT NULL DEFAULT 0                    COMMENT '上传中预占容量（字节）',
    space_file_count    INT             NOT NULL DEFAULT 0                    COMMENT '文件数量',
    space_visibility    ENUM('private', 'public', 'visible', 'hidden', 'whitelist', 'blacklist')
                                        NOT NULL DEFAULT 'private'            COMMENT '可见性控制',
    join_policy        ENUM('open', 'approval_required', 'invite_only')
                                        NOT NULL DEFAULT 'invite_only'        COMMENT '加入策略',
    allow_public_browse TINYINT(1)      NOT NULL DEFAULT 1                    COMMENT '公开仓库是否允许浏览',
    allow_public_download TINYINT(1)   NOT NULL DEFAULT 1                    COMMENT '公开仓库是否允许下载',
    allow_public_upload TINYINT(1)     NOT NULL DEFAULT 0                    COMMENT '公开仓库是否允许登录用户上传',
    space_description   TEXT                                                    COMMENT '空间描述',
    space_avatar_path   VARCHAR(512)                                            COMMENT '空间头像路径',
    space_im_group_id   VARCHAR(100)                                            COMMENT '关联IM群组ID（企业/团队空间自动创建）',
    space_created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP    COMMENT '创建时间',
    space_updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                                        ON UPDATE CURRENT_TIMESTAMP           COMMENT '更新时间',
    space_status        ENUM('active', 'disabled', 'deleted')
                                        NOT NULL DEFAULT 'active'             COMMENT '空间状态',
    FOREIGN KEY (space_owner_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    -- 同一用户下的空间名唯一，但不同用户可以创建同名空间（公共空间名全局唯一见下方）
    UNIQUE KEY uk_space_name_owner (space_name, space_owner_id),
    -- 公共空间的名字全局唯一，用于外部用户通过空间名访问
    INDEX idx_space_type (space_type, space_status),
    INDEX idx_space_owner (space_owner_id, space_status),
    INDEX idx_space_visibility (space_visibility, space_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='空间主表';

-- =====================================================================
-- 2. 空间成员表
-- =====================================================================
CREATE TABLE IF NOT EXISTS pcd_space_member_table (
    member_id           BIGINT          PRIMARY KEY AUTO_INCREMENT            COMMENT '成员记录ID',
    space_id            BINARY(16)      NOT NULL                              COMMENT '空间ID',
    user_id             BINARY(16)      NOT NULL                              COMMENT '用户ID',
    role                ENUM('owner', 'admin', 'editor', 'viewer', 'custom')
                                        NOT NULL DEFAULT 'viewer'             COMMENT '成员角色',
    joined_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP    COMMENT '加入时间',
    invited_by          BINARY(16)                                              COMMENT '邀请人ID',
    FOREIGN KEY (space_id) REFERENCES pcd_space_table(space_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    FOREIGN KEY (invited_by) REFERENCES pcd_user_info_table(user_id) ON DELETE SET NULL,
    UNIQUE KEY uk_space_user (space_id, user_id),
    INDEX idx_user_spaces (user_id, space_id),
    INDEX idx_space_role (space_id, role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='空间成员表';

-- =====================================================================
-- 3. 空间细粒度权限表
--    覆盖角色默认权限，支持自定义每个用户对特定文件/目录的权限
-- =====================================================================
CREATE TABLE IF NOT EXISTS pcd_space_permission_table (
    permission_id       BIGINT          PRIMARY KEY AUTO_INCREMENT            COMMENT '权限记录ID',
    space_id            BINARY(16)      NOT NULL                              COMMENT '空间ID',
    user_id             BINARY(16)      NOT NULL                              COMMENT '用户ID',
    target_node_id      BINARY(16)      DEFAULT NULL                          COMMENT '目标节点ID（NULL=空间级权限，非NULL=目录级权限）',
    can_read            TINYINT(1)      NOT NULL DEFAULT 1                    COMMENT '读权限',
    can_write           TINYINT(1)      NOT NULL DEFAULT 0                    COMMENT '写权限（创建/修改文件）',
    can_delete          TINYINT(1)      NOT NULL DEFAULT 0                    COMMENT '删除权限',
    can_share           TINYINT(1)      NOT NULL DEFAULT 0                    COMMENT '分享权限',
    can_invite          TINYINT(1)      NOT NULL DEFAULT 0                    COMMENT '邀请成员权限',
    can_manage          TINYINT(1)      NOT NULL DEFAULT 0                    COMMENT '管理权限（修改空间设置）',
    can_view            TINYINT(1)      NOT NULL DEFAULT 0                    COMMENT '浏览空间权限',
    can_download        TINYINT(1)      NOT NULL DEFAULT 0                    COMMENT '下载原始文件权限',
    can_upload          TINYINT(1)      NOT NULL DEFAULT 0                    COMMENT '上传文件权限',
    can_edit            TINYINT(1)      NOT NULL DEFAULT 0                    COMMENT '编辑文件元数据权限',
    can_manage_members  TINYINT(1)      NOT NULL DEFAULT 0                    COMMENT '成员管理权限',
    can_manage_plugins  TINYINT(1)      NOT NULL DEFAULT 0                    COMMENT '插件管理权限',
    can_manage_settings TINYINT(1)      NOT NULL DEFAULT 0                    COMMENT '空间设置权限',
    granted_by          BINARY(16)                                              COMMENT '授权人ID',
    granted_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP    COMMENT '授权时间',
    FOREIGN KEY (space_id) REFERENCES pcd_space_table(space_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    FOREIGN KEY (target_node_id) REFERENCES pcd_directory_tree_table(node_id) ON DELETE CASCADE,
    FOREIGN KEY (granted_by) REFERENCES pcd_user_info_table(user_id) ON DELETE SET NULL,
    UNIQUE KEY uk_space_user_node (space_id, user_id, target_node_id),
    INDEX idx_space_user (space_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='空间细粒度权限表';

-- =====================================================================
-- 4. 空间加入申请表（团队/企业空间）
-- =====================================================================
CREATE TABLE IF NOT EXISTS pcd_space_join_request_table (
    request_id          BIGINT          PRIMARY KEY AUTO_INCREMENT            COMMENT '申请记录ID',
    space_id            BINARY(16)      NOT NULL                              COMMENT '空间ID',
    user_id             BINARY(16)      NOT NULL                              COMMENT '申请人ID',
    request_message     TEXT                                                    COMMENT '申请留言',
    status              ENUM('pending', 'approved', 'rejected')
                                        NOT NULL DEFAULT 'pending'            COMMENT '申请状态',
    reviewed_by         BINARY(16)                                              COMMENT '审批人ID',
    reviewed_at         DATETIME                                                COMMENT '审批时间',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP    COMMENT '申请时间',
    FOREIGN KEY (space_id) REFERENCES pcd_space_table(space_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    FOREIGN KEY (reviewed_by) REFERENCES pcd_user_info_table(user_id) ON DELETE SET NULL,
    UNIQUE KEY uk_space_user_pending (space_id, user_id),
    INDEX idx_space_pending (space_id, status),
    INDEX idx_user_requests (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='空间加入申请表';

-- =====================================================================
-- 5. 空间可见性白名单/黑名单表（公共空间）
-- =====================================================================
CREATE TABLE IF NOT EXISTS pcd_space_visibility_table (
    visibility_id       BIGINT          PRIMARY KEY AUTO_INCREMENT            COMMENT '可见性记录ID',
    space_id            BINARY(16)      NOT NULL                              COMMENT '空间ID',
    user_id             BINARY(16)      NOT NULL                              COMMENT '用户ID',
    list_type           ENUM('whitelist', 'blacklist')
                                        NOT NULL                              COMMENT '名单类型',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP    COMMENT '添加时间',
    FOREIGN KEY (space_id) REFERENCES pcd_space_table(space_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    UNIQUE KEY uk_space_user_list (space_id, user_id, list_type),
    INDEX idx_space_whitelist (space_id, list_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='空间可见性白名单/黑名单表';

-- =====================================================================
-- 6. 为现有文件表和目录树表添加空间隔离字段
-- =====================================================================
-- 空间管理能力全量集成（需求六）：
-- 新安装由 database_init.sql 直接创建空间字段；存量库统一执行
-- 007_space_context_full_integration.sql，以幂等方式补列、回填和创建索引。

-- =====================================================================
-- 7. 为现有上传会话表添加空间隔离字段
-- =====================================================================
-- uploads_space_id 同样由主初始化脚本或 007 迁移维护，避免重复执行本脚本时加列失败。

-- =====================================================================
-- 8. 触发器：自动创建个人空间（用户注册时）
-- =====================================================================
DELIMITER //

DROP TRIGGER IF EXISTS trg_create_personal_space//

CREATE TRIGGER trg_create_personal_space
AFTER INSERT ON pcd_user_info_table
FOR EACH ROW
BEGIN
    DECLARE space_id_bin BINARY(16);
    SET space_id_bin = UUID_TO_BIN(UUID());

    -- 创建个人空间
    INSERT INTO pcd_space_table (space_id, space_name, space_type, space_owner_id, space_visibility)
    VALUES (space_id_bin, '我的网盘', 'personal', NEW.user_id, 'private');

    -- 自动将用户添加为空间所有者
    INSERT INTO pcd_space_member_table (space_id, user_id, role)
    VALUES (space_id_bin, NEW.user_id, 'owner');
END//

DELIMITER ;

-- =====================================================================
-- 角色默认权限映射（参考）
-- =====================================================================
-- owner:   can_read=1, can_write=1, can_delete=1, can_share=1, can_invite=1, can_manage=1
-- admin:   can_read=1, can_write=1, can_delete=1, can_share=1, can_invite=1, can_manage=1
-- editor:  can_read=1, can_write=1, can_delete=1, can_share=1, can_invite=0, can_manage=0
-- viewer:  can_read=1, can_write=0, can_delete=0, can_share=0, can_invite=0, can_manage=0
