
/* 
    登录数据库 /usr/local/mysql-8.0.31-macos12-arm64/bin/mysql -u root -p
    /usr/local/mysql-8.0.31-macos12-arm64/bin/mysqldump -u root -p private_cloud_disk  > PrivateCloudDisk-infra/mysql/init.sql
    创建项目主数据库 
*/
CREATE DATABASE private_cloud_disk;

/* 创建数据库表格 */
USE private_cloud_disk;

CREATE TABLE pcd_user_info_table (
    user_name               VARCHAR(120)    NOT NULL        COMMENT '用户名',
    user_id                 BINARY(16)     NOT NULL PRIMARY KEY,
    user_phone_number       VARCHAR(50)     NOT NULL UNIQUE,
    user_image_path         VARCHAR(512)                    COMMENT '用户头像路径',
    user_password           VARCHAR(70)     NOT NULL        COMMENT '用户密码',
    user_account            VARCHAR(70)     NOT NULL UNIQUE COMMENT '用户账号',
    user_email              VARCHAR(70)     UNIQUE          COMMENT '用户邮箱'
) COMMENT='用户信息表';

CREATE TABLE pcd_user_device_table (
    device_id               BINARY(16)     NOT NULL PRIMARY KEY COMMENT '服务端生成的设备ID',
    device_user_id          BINARY(16)     NOT NULL             COMMENT '所属用户ID',
    device_client_type      VARCHAR(50)     NOT NULL             COMMENT '客户端类型，例如 WEB/IOS/MACOS/WECHAT/PC',
    device_client_name      VARCHAR(120)                         COMMENT '客户端展示名称',
    device_platform         VARCHAR(120)                         COMMENT '系统或平台信息',
    device_user_agent_hash  VARCHAR(64)                          COMMENT 'User-Agent规范化后的哈希',
    device_public_key       TEXT                                 COMMENT '设备密钥绑定的公钥，可选',
    device_created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    device_last_seen_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    device_status           ENUM('active', 'disabled', 'revoked') NOT NULL DEFAULT 'active',
    FOREIGN KEY (device_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    INDEX idx_device_user_status (device_user_id, device_status)
) COMMENT='用户登录设备表';

CREATE TABLE pcd_login_session_table (
    login_session_id             BINARY(16) NOT NULL PRIMARY KEY COMMENT '服务端签发的登录会话ID，即sid',
    login_session_user_id        BINARY(16) NOT NULL             COMMENT '登录用户ID',
    login_session_device_id      BINARY(16)                      COMMENT '关联设备ID',
    login_session_token_jti      BINARY(16)                      COMMENT '登录JWT jti',
    login_session_client_ip      VARCHAR(64)                      COMMENT '登录IP',
    login_session_user_agent     VARCHAR(512)                     COMMENT '登录User-Agent',
    login_session_started_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    login_session_expires_at     DATETIME    NOT NULL             COMMENT '会话过期时间',
    login_session_revoked_at     DATETIME                         COMMENT '会话撤销时间',
    login_session_status         ENUM('active', 'expired', 'revoked') NOT NULL DEFAULT 'active',
    FOREIGN KEY (login_session_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    FOREIGN KEY (login_session_device_id) REFERENCES pcd_user_device_table(device_id) ON DELETE SET NULL,
    INDEX idx_login_session_user_status (login_session_user_id, login_session_status),
    INDEX idx_login_session_device_status (login_session_device_id, login_session_status),
    INDEX idx_login_session_jti (login_session_token_jti)
) COMMENT='用户登录会话表';

CREATE TABLE pcd_login_audit_table (
    audit_id                 BIGINT       NOT NULL PRIMARY KEY AUTO_INCREMENT,
    audit_user_id            BINARY(16)                       COMMENT '匹配到的用户ID，失败时可为空',
    audit_account            VARCHAR(100)                      COMMENT '登录账号',
    audit_phone_number       VARCHAR(50)                       COMMENT '登录手机号',
    audit_success            TINYINT(1)   NOT NULL             COMMENT '是否登录成功',
    audit_failure_reason     VARCHAR(120)                      COMMENT '失败原因',
    audit_client_ip          VARCHAR(64)                       COMMENT '客户端IP',
    audit_user_agent         VARCHAR(512)                      COMMENT 'User-Agent',
    audit_created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (audit_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE SET NULL,
    INDEX idx_login_audit_user_time (audit_user_id, audit_created_at),
    INDEX idx_login_audit_account_time (audit_account, audit_created_at),
    INDEX idx_login_audit_ip_time (audit_client_ip, audit_created_at)
) COMMENT='登录审计表';

/* 文件上传分三大业务模块部分 第一文件内容完整数据上传到服务器 服务器处理上传的文件完整数据对它们进行整合标记文件为活跃更新用户网盘配额 文件正式可以访问可以下载可以CRUD
  第三文件上传整合完全落地服务器资源后 文件资源增强业务比方说智能文件处理基于文件内容的搜索索引建立文件智能打标签 略缩图视频转码等 
  而上传会话只负责第一部分模块的进行 对整个操作进行跟踪有状态的跟踪保证文件每一个分块 文件全部数据都提交到服务器 */
CREATE TABLE pcd_file_info_table (
    file_name               VARCHAR(150)    NOT NULL                COMMENT '文件名称',
    file_uploaded_time      TIMESTAMP       NOT NULL DEFAULT NOW()  COMMENT '文件上传时间',
    file_size               BIGINT          NOT NULL                COMMENT '文件大小',
    file_type               VARCHAR(60)     NOT NULL                COMMENT '文件类型',
    file_author_id          BINARY(16)     NOT NULL                COMMENT '文件作者ID',
    FOREIGN KEY (file_author_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    file_id                 BINARY(16)     NOT NULL PRIMARY KEY,
    file_checksum           VARCHAR(256)    NOT NULL                COMMENT '文件校验值',
    file_total_chunks       INT             NOT NULL                COMMENT '文件切片数目', --新增字段
    file_node_id            BINARY(16)     NOT NULL                COMMENT '文件所在目录节点ID',
    file_storage_path       VARCHAR(512)    NOT NULL                COMMENT '文件存储路径',
    file_status             ENUM('marging', 'merged',  'scaning', 'merge_failed', 'scan_failed', 'dangrous', 'active', 'deleted', 'trashed') NOT NULL DEFAULT 'active' COMMENT '文件状态'
    UNIQUE KEY uk_file_info (file_id, file_author_id, file_node_id),
) COMMENT='文件信息表';

-- =====================================================================
-- 分享链接管理表 pcd_share_link_table
-- 设计要点：
--   1. share_token 为公开访问的唯一凭证（随机 UUID），绝不暴露 file_id/node_id
--   2. 密码使用 BCrypt 哈希存储，不存明文
--   3. 支持文件和文件夹两种分享类型
--   4. 分享访问永为只读，无 CRUD 权限
--   5. 通过 share_token + 访问令牌验证，杜绝横向越权
-- =====================================================================
DROP TABLE IF EXISTS pcd_share_link_table;
CREATE TABLE pcd_share_link_table (
    share_id                BINARY(16)     NOT NULL PRIMARY KEY       COMMENT '分享ID（内部主键）',
    share_token             VARCHAR(36)     NOT NULL UNIQUE           COMMENT '分享访问令牌（UUID，对外暴露）',
    share_owner_id          BINARY(16)     NOT NULL                   COMMENT '分享者用户ID',
    FOREIGN KEY (share_owner_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    share_target_type       ENUM('file', 'folder') NOT NULL           COMMENT '分享目标类型',
    share_file_id           BINARY(16)     NULL                       COMMENT '分享的文件ID（分享文件时填写）',
    FOREIGN KEY (share_file_id) REFERENCES pcd_file_info_table(file_id) ON DELETE CASCADE,
    share_node_id           BINARY(16)     NULL                       COMMENT '分享的文件夹节点ID（分享文件夹时填写）',
    FOREIGN KEY (share_node_id) REFERENCES pcd_directory_tree_table(node_id) ON DELETE CASCADE,
    share_name              VARCHAR(200)    NOT NULL                   COMMENT '分享名称（用户自定义）',
    share_password          VARCHAR(120)    NULL                       COMMENT '提取码（BCrypt 哈希，NULL 表示无密码）',
    share_has_password      TINYINT(1)      NOT NULL DEFAULT 0         COMMENT '是否有密码保护',
    share_expires_at        DATETIME        NULL                       COMMENT '过期时间（NULL 表示永久有效）',
    share_view_count        INT             NOT NULL DEFAULT 0         COMMENT '浏览次数',
    share_status            ENUM('active', 'revoked', 'expired') NOT NULL DEFAULT 'active' COMMENT '分享状态',
    share_created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    share_updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_share_owner (share_owner_id, share_status),
    INDEX idx_share_token (share_token),
    INDEX idx_share_status (share_status),
    CONSTRAINT chk_share_target CHECK (
        (share_target_type = 'file' AND share_file_id IS NOT NULL AND share_node_id IS NULL) OR
        (share_target_type = 'folder' AND share_node_id IS NOT NULL AND share_file_id IS NULL)
    )
) COMMENT='分享链接管理表';

-- =====================================================================
-- 旧的分享表（替换为上面的新表）
-- =====================================================================
DROP TABLE IF EXISTS pcd_sharing_Link_mange_table;

/*

    用户上传一个文件 这个文件会被分割成不同的小部分 我们把这个小部分叫做切片 用户不是一次性把一个文件所有的内容传给我们的服务器 而是一个一个上传文件的切片
    给我们的服务器 服务器收到一个文件所有的切片数据之后才会做整理处理
    如果此时用户又上传一个同名文件同类型文件在同一个目录下服务器怎么做处理？
    如果用户从想要上传一个文件 但是上传切片是隔一天上传一个 怎么定义操作的超时时间？
    如果用户只上传了一部分文件切片数据就突然不见了 怎么回滚整个操作？
    服务器怎么知道用户什么时候上传完毕所有的文件切片？
    用户上传一个文件这一整个过程是一个文件上传的操作 我们需要一个东西去记录这个上传文件的操作
    我们把这个记录的东西叫做上传会话 用户上传一个文件的过程都是一个上传的会话它用于跟踪此次上传文件的操作

    用户想要下载一个文件 也不是一次性下载完所有整个的文件数据 而是一部分一部分接受文件切片
    服务器怎么知道用户是否全部接受完毕所有的文件切片数据？
    服务器怎么处理用户不断重复申请接受同一个索引切片数据？
    服务器怎么定义用户此次下载文件操作的超时时间？
    我们把这个记录的东西叫做下载会话 用户下载一个文件的过程都是一个下载的会话它用于跟踪此次下载文件的操作

 */

-- 文件上传的上传会话表 管理单文件的上传流程 主要是用来跟踪保障整个切片上传流程正确进行
CREATE TABLE pcd_uploads_session_table (
    uploads_id              BINARY(16)     NOT NULL PRIMARY KEY,
    uploads_user_id         BINARY(16)     NOT NULL                                                COMMENT '上传用户ID',
    FOREIGN KEY (uploads_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    uploads_total_chunks    INT             NOT NULL                                                COMMENT '上传切片总数',
    uploads_starting_time   TIMESTAMP       NOT NULL                            DEFAULT NOW()       COMMENT '上传开始时间',
    uploads_endding_time    TIMESTAMP       NOT NULL                                                COMMENT '上传结束时间',
    uploads_file_size       BIGINT          NOT NULL                                                COMMENT '文件大小',
    uploads_file_checksum   VARCHAR(256)    NOT NULL                                                COMMENT '文件校验值',
    uploads_chunks_max_size INT             NOT NULL                                                COMMENT '切片最大大小',
    uploads_file_name       VARCHAR(150)    NOT NULL                                                COMMENT '文件名称',
    uploads_file_type       VARCHAR(60)     NOT NULL                                                COMMENT '文件类型',
    uploads_node_id         BINARY(16)     NOT NULL                                                 COMMENT '文件所在目录节点ID',
    uploads_status          ENUM('uploading', 'merging', 'completed', 'canceled', 'failed', 'deleted') DEFAULT 'uploading' COMMENT '上传状态：uploading→merging→completed | uploading→canceled→deleted'
) COMMENT='文件上传会话表';

CREATE TABLE pcd_upload_chunks_table (
    chunk_uploads_id    BINARY(16)  NOT NULL                                                COMMENT '关联上传会话ID',
    FOREIGN KEY (chunk_uploads_id) REFERENCES pcd_uploads_session_table(uploads_id) ON DELETE CASCADE,
    chunk_index         INT          NOT NULL                                                COMMENT '切片索引',
    chunk_status        ENUM('pending' ,'uploading', 'uploaded', 'failed') DEFAULT 'pending' COMMENT '切片状态',
    chunk_storage_path  VARCHAR(512) NOT NULL                                                COMMENT '切片存储路径',
    chunk_uploaded_time TIMESTAMP    NOT NULL                              DEFAULT NOW()     COMMENT '切片上传时间',
    PRIMARY KEY (chunk_uploads_id, chunk_index)
) COMMENT='文件切片表';

-- 有点类似于文件夹的元数据表 准确来说是每一条表记录加上关联字段构成了一个目录结构表
-- 可以准确的描述整个文件夹的嵌套结构 利用node_id代替物化路径 提高了安全性和简化了客户端
-- node_status 节点的状态能够解决高并发的服务器环境下导致的异常 类似于逻辑锁 操作时锁定节点 node-parent表
CREATE TABLE pcd_directory_tree_table (
    node_id          BINARY(16)     NOT NULL PRIMARY KEY,
    node_user_id     BINARY(16)     NOT NULL          COMMENT '所属用户ID',
    FOREIGN KEY (node_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    node_parent_id   BINARY(16)                       COMMENT '父节点ID，根节点为NULL',
    FOREIGN KEY (node_parent_id) REFERENCES pcd_directory_tree_table(node_id) ON DELETE CASCADE,
    node_name        VARCHAR(200)    NOT NULL          COMMENT '节点名称',
    node_create_time TIMESTAMP       NOT NULL          COMMENT '节点创建时间'      DEFAULT NOW(),
    node_status      ENUM('lock', 'active', 'pending') COMMENT '节点状态'         DEFAULT 'active'
    UNIQUE KEY uk_directory_tree (node_id, node_user_id, node_parent_id),
) COMMENT='节点目录树表';

ALTER TABLE pcd_file_info_table
    ADD CONSTRAINT fk_file_info_directory_tree
    FOREIGN KEY (file_node_id) REFERENCES pcd_directory_tree_table(node_id) ON DELETE CASCADE;

ALTER TABLE pcd_uploads_session_table
    ADD CONSTRAINT fk_uploads_session_directory_tree
    FOREIGN KEY (uploads_node_id) REFERENCES pcd_directory_tree_table(node_id) ON DELETE CASCADE;

-- 闭包表 用于高效查询目录树的祖先和后代关系 适合频繁查询目录层级关系的场景
-- 注意维护 不要太深层次的目录树 否则会导致闭包表过大 影响性能
CREATE TABLE pcd_directory_closure_table (
    user_id          BINARY(16)     NOT NULL          COMMENT '所属用户ID',
    FOREIGN KEY (user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    ancestor_id      BINARY(16)     NOT NULL          COMMENT '祖先节点ID',
    FOREIGN KEY (ancestor_id) REFERENCES pcd_directory_tree_table(node_id) ON DELETE CASCADE,
    descendant_id    BINARY(16)     NOT NULL          COMMENT '后代节点ID',
    FOREIGN KEY (descendant_id) REFERENCES pcd_directory_tree_table(node_id) ON DELETE CASCADE,
    depth            INT             NOT NULL          COMMENT '祖先节点与后代节点的深度，父子关系为1，祖孙关系为2，以此类推',
    PRIMARY KEY (ancestor_id, descendant_id),
    UNIQUE KEY uk_descendant (user_id, descendant_id, ancestor_id),
    KEY idx_depth (depth)
) COMMENT='目录树闭包表';


CREATE TABLE pcd_user_quota_table (
    quota_id              BIGINT          PRIMARY KEY AUTO_INCREMENT,
    quota_user_id         BINARY(16)     NOT NULL UNIQUE COMMENT '用户ID，关联用户表',
    FOREIGN KEY (quota_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    quota_total_capacity  BIGINT          NOT NULL DEFAULT 10737418240 COMMENT '总额度（字节），默认10GB = 10*1024^3',
    quota_used_capacity   BIGINT          NOT NULL DEFAULT 0 COMMENT '已用容量（字节）',
    quota_released_capacity BIGINT        NOT NULL DEFAULT 0 COMMENT '预占容量（字节）：正在上传中尚未提交的文件容量',
    quota_file_count      INT             NOT NULL DEFAULT 0 COMMENT '已上传文件数量',
    quota_version         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    quota_created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    quota_updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (quota_user_id)
) COMMENT='用户存储配额表（预占+提交模式：available = total - (used + released)）';

CREATE TABLE pcd_user_quota_log_table (
    quota_log_id            BIGINT        PRIMARY KEY AUTO_INCREMENT,
    quota_log_user_id       BINARY(16)   NOT NULL COMMENT '用户ID，关联用户表',
    FOREIGN KEY (quota_log_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    quota_log_change_type   VARCHAR(20)   NOT NULL COMMENT '变更类型：EXPAND-扩容，REDUCE-缩容，FILE_UPLOAD-文件上传，FILE_DELETE-文件删除',
    quota_log_change_bytes  BIGINT        NOT NULL COMMENT '变更字节数（正为增加，负为减少）',
    quota_log_before_total  BIGINT        COMMENT '变更前总额度',
    quota_log_after_total   BIGINT        COMMENT '变更后总额度',
    quota_log_before_used   BIGINT        COMMENT '变更前已用',
    quota_log_after_used    BIGINT        COMMENT '变更后已用',
    quota_log_operator      VARCHAR(50)   COMMENT '操作人（管理员或系统）' DEFAULT 'SYSTEM',
    quota_log_created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id_time (quota_log_user_id, quota_log_created_at)
) COMMENT='配额变更日志';

-- 文件/文件夹收藏表
CREATE TABLE pcd_file_star_table (
    star_id             BIGINT          PRIMARY KEY AUTO_INCREMENT,
    star_user_id        BINARY(16)     NOT NULL COMMENT '用户ID',
    FOREIGN KEY (star_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    star_target_type    ENUM('file', 'folder') NOT NULL DEFAULT 'file' COMMENT '收藏目标类型：file=文件, folder=文件夹',
    star_file_id        BINARY(16)     NULL COMMENT '文件ID（收藏文件时填写）',
    FOREIGN KEY (star_file_id) REFERENCES pcd_file_info_table(file_id) ON DELETE CASCADE,
    star_node_id        BINARY(16)     NULL COMMENT '文件夹节点ID（收藏文件夹时填写）',
    FOREIGN KEY (star_node_id) REFERENCES pcd_directory_tree_table(node_id) ON DELETE CASCADE,
    star_starred_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    UNIQUE KEY uk_user_file_star (star_user_id, star_file_id),
    UNIQUE KEY uk_user_folder_star (star_user_id, star_node_id),
    INDEX idx_user_starred (star_user_id, star_starred_at)
) COMMENT='文件/文件夹收藏表';

-- 回收站文件表
CREATE TABLE pcd_trash_target_table (
    trash_id                BIGINT          PRIMARY KEY AUTO_INCREMENT,
    trash_target_id         BINARY(16)     NOT NULL COMMENT '原目标ID',
    trash_target_type       ENUM('file', 'folder') NOT NULL COMMENT '目标类型',
    trash_user_id           BINARY(16)     NOT NULL COMMENT '用户ID',
    FOREIGN KEY (trash_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    trash_target_name         VARCHAR(150)    NOT NULL COMMENT '文件名称',
    trash_file_type         VARCHAR(60)     NOT NULL COMMENT '文件类型',
    trash_target_size         BIGINT          NOT NULL COMMENT '文件大小',
    trash_original_node_id  BINARY(16)     NOT NULL COMMENT '原节点ID',
    trash_deleted_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '删除时间',
    trash_expires_at        DATETIME        NOT NULL COMMENT '过期时间（自动彻底删除时间）',
    INDEX idx_user_deleted (trash_user_id, trash_deleted_at),
    INDEX idx_expires (trash_expires_at)
) COMMENT='回收站表';

-- =====================================================================
-- 通知发送日志表 pcd_notification_send_log_table
-- 用途：记录邮件/短信等通知的发送状态，实现消费者幂等性，便于重试和排查
-- =====================================================================

DROP TABLE IF EXISTS `pcd_notification_send_log_table`;
CREATE TABLE `pcd_notification_send_log_table` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键自增ID',
    `event_id`    VARCHAR(255) NOT NULL                COMMENT '事件唯一ID（由发布方生成）',
    `channel`     VARCHAR(20)  NOT NULL                COMMENT '通道：EMAIL（邮件）、SMS（短信）',
    `receiver`    VARCHAR(255) NOT NULL                COMMENT '接收者：邮箱地址或手机号',
    `user_id`     BINARY(16) DEFAULT NULL            COMMENT '关联用户ID（可为空）',
    `status`      VARCHAR(20)  NOT NULL                COMMENT '状态：PENDING、SUCCESS、FAILED',
    `retry_count` INT          NOT NULL DEFAULT 0      COMMENT '重试次数',
    `error_message` VARCHAR(1000) DEFAULT NULL         COMMENT '错误信息（失败时记录，截断至1000字符）',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 唯一索引：同一事件+通道+接收者 只能有一条记录，保证数据库级的幂等性
    UNIQUE KEY `uk_event_channel_receiver` (`event_id`, `channel`, `receiver`),
    -- 状态索引：便于按状态查询（如查询所有FAILED记录进行人工重试）
    KEY `idx_status` (`status`),
    -- 时间索引：便于历史数据清理
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知发送日志表（邮件/短信，幂等性支持）';

-- =====================================================================
-- 说明：
--   1. (event_id, channel, receiver) 唯一索引是实现"消息不重复发送"的核心
--      当MQ消息因网络原因重复投递时，第一个消费者插入成功，后续会冲突
--   2. status 状态流转：
--      PENDING → SUCCESS（发送成功）
--      PENDING → FAILED（发送失败）
--      FAILED  → PENDING（人工触发重试，通过 update 语句重置）
--   3. 建议每日清理超过30天的 SUCCESS 状态记录，避免表无限膨胀
-- =====================================================================

-- ============================================================
-- 1. 用户标签表
-- ============================================================
DROP TABLE IF EXISTS pcd_tag_table;
CREATE TABLE pcd_tag_table (
    tag_id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '标签ID',
    tag_user_id         BINARY(16)      NOT NULL                COMMENT '所属用户ID',
    tag_name            VARCHAR(50)     NOT NULL                COMMENT '标签名称',
    tag_color           VARCHAR(7)      NOT NULL DEFAULT '#3B82F6' COMMENT '标签颜色（HEX）',
    tag_created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (tag_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_tag (tag_user_id, tag_name),
    INDEX idx_tag_user (tag_user_id)
) COMMENT='用户标签表';

-- ============================================================
-- 2. 文件标签关联表（多对多）
-- ============================================================
DROP TABLE IF EXISTS pcd_file_tag_table;
CREATE TABLE pcd_file_tag_table (
    ft_id               BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '关联ID',
    ft_user_id          BINARY(16)      NOT NULL                COMMENT '用户ID（冗余，加速查询）',
    ft_tag_id           BIGINT          NOT NULL                COMMENT '标签ID',
    ft_target_type      ENUM('file', 'folder') NOT NULL         COMMENT '目标类型',
    ft_file_id          BINARY(16)                              COMMENT '文件ID（target_type=file时）',
    ft_node_id          BINARY(16)                              COMMENT '文件夹节点ID（target_type=folder时）',
    ft_tagged_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '打标签时间',
    FOREIGN KEY (ft_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    FOREIGN KEY (ft_tag_id) REFERENCES pcd_tag_table(tag_id) ON DELETE CASCADE,
    UNIQUE KEY uk_file_tag (ft_user_id, ft_tag_id, ft_target_type, ft_file_id, ft_node_id),
    INDEX idx_tag_file (ft_tag_id),
    INDEX idx_file_tag (ft_file_id),
    INDEX idx_node_tag (ft_node_id),
    INDEX idx_user_tag (ft_user_id, ft_tag_id)
) COMMENT='文件标签关联表';

-- ============================================================
-- 3. 最近访问记录表
-- ============================================================
DROP TABLE IF EXISTS pcd_recent_access_table;
CREATE TABLE pcd_recent_access_table (
    ra_id               BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    ra_user_id          BINARY(16)      NOT NULL                COMMENT '用户ID',
    ra_target_type      ENUM('file', 'folder') NOT NULL         COMMENT '目标类型',
    ra_file_id          BINARY(16)                              COMMENT '文件ID',
    ra_node_id          BINARY(16)                              COMMENT '文件夹节点ID',
    ra_access_type      ENUM('upload', 'download', 'open') NOT NULL COMMENT '访问类型',
    ra_file_name        VARCHAR(255)    NOT NULL                COMMENT '文件/文件夹名称（冗余，避免JOIN）',
    ra_file_size        BIGINT          NOT NULL DEFAULT 0      COMMENT '文件大小（冗余）',
    ra_file_type        VARCHAR(60)     DEFAULT ''              COMMENT '文件类型（冗余）',
    ra_accessed_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '访问时间',
    FOREIGN KEY (ra_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    INDEX idx_ra_user_type_time (ra_user_id, ra_access_type, ra_accessed_at DESC),
    INDEX idx_ra_user_time (ra_user_id, ra_accessed_at DESC)
) COMMENT='最近访问记录表';

-- ============================================================
-- 4. 预设常用标签（新用户注册时自动创建）
-- ============================================================
-- 预设标签：合同、设计稿、项目A、财务报告、机密文件、个人文档、临时文件、重要资料

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

-- =====================================================================
-- 1. 空间主表
-- =====================================================================
CREATE TABLE IF NOT EXISTS pcd_space_table (
    space_id            BINARY(16)      NOT NULL PRIMARY KEY                  COMMENT '空间唯一ID',
    space_name          VARCHAR(200)     NOT NULL                              COMMENT '空间名称',
    space_type          ENUM('personal', 'enterprise', 'public', 'team')
                                        NOT NULL                              COMMENT '空间类型',
    space_owner_id      BINARY(16)      NOT NULL                              COMMENT '空间创建者/所有者',
    space_quota         BIGINT          NOT NULL DEFAULT 10737418240          COMMENT '空间配额（字节），默认10GB',
    space_used          BIGINT          NOT NULL DEFAULT 0                    COMMENT '已用容量（字节）',
    space_file_count    INT             NOT NULL DEFAULT 0                    COMMENT '文件数量',
    space_visibility    ENUM('private', 'public', 'whitelist', 'blacklist')
                                        NOT NULL DEFAULT 'private'            COMMENT '可见性控制',
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
    role                ENUM('owner', 'admin', 'editor', 'viewer')
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
ALTER TABLE pcd_file_info_table
    ADD COLUMN file_space_id BINARY(16) DEFAULT NULL COMMENT '所属空间ID',
    ADD INDEX idx_file_space (file_space_id, file_status);

ALTER TABLE pcd_directory_tree_table
    ADD COLUMN node_space_id BINARY(16) DEFAULT NULL COMMENT '所属空间ID',
    ADD INDEX idx_node_space (node_space_id, node_status);

-- =====================================================================
-- 7. 为现有上传会话表添加空间隔离字段
-- =====================================================================
ALTER TABLE pcd_uploads_session_table
    ADD COLUMN uploads_space_id BINARY(16) DEFAULT NULL COMMENT '所属空间ID',
    ADD INDEX idx_uploads_space (uploads_space_id, uploads_status);

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
    VALUES (space_id_bin, CONCAT(NEW.user_account, '的个人空间'), 'personal', NEW.user_id, 'private');

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