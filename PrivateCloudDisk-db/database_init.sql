
/* 
    登录数据库 /usr/local/mysql-8.0.31-macos12-arm64/bin/mysql -u root -p
    创建项目主数据库 
*/
CREATE DATABASE private_cloud_disk;

/* 创建数据库表格 */
USE private_cloud_disk;

CREATE TABLE pcd_user_info_table (
    user_name               VARCHAR(120)    NOT NULL        COMMENT '用户名',
    user_id                 VARCHAR(36)     NOT NULL PRIMARY KEY,
    user_phone_number       VARCHAR(50)     NOT NULL UNIQUE,
    user_image_path         VARCHAR(512)                    COMMENT '用户头像路径',
    user_password           VARCHAR(70)     NOT NULL        COMMENT '用户密码',
    user_account            VARCHAR(70)     NOT NULL UNIQUE COMMENT '用户账号',
    user_email              VARCHAR(70)     UNIQUE          COMMENT '用户邮箱'
) COMMENT='用户信息表';

CREATE TABLE pcd_user_device_table (
    device_id               VARCHAR(36)     NOT NULL PRIMARY KEY COMMENT '服务端生成的设备ID',
    device_user_id          VARCHAR(36)     NOT NULL             COMMENT '所属用户ID',
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
    login_session_id             VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '服务端签发的登录会话ID，即sid',
    login_session_user_id        VARCHAR(36) NOT NULL             COMMENT '登录用户ID',
    login_session_device_id      VARCHAR(36)                      COMMENT '关联设备ID',
    login_session_token_jti      VARCHAR(36)                      COMMENT '登录JWT jti',
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
    audit_user_id            VARCHAR(36)                       COMMENT '匹配到的用户ID，失败时可为空',
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

CREATE TABLE pcd_file_info_table (
    file_name               VARCHAR(150)    NOT NULL                COMMENT '文件名称',
    file_uploaded_time      TIMESTAMP       NOT NULL DEFAULT NOW()  COMMENT '文件上传时间',
    file_size               BIGINT          NOT NULL                COMMENT '文件大小',
    file_type               VARCHAR(60)     NOT NULL                COMMENT '文件类型',
    file_author_id          VARCHAR(36)     NOT NULL                COMMENT '文件作者ID',
    FOREIGN KEY (file_author_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    file_id                 VARCHAR(36)     NOT NULL PRIMARY KEY,
    file_checksum           VARCHAR(256)    NOT NULL                COMMENT '文件校验值',
    file_total_chunks       INT             NOT NULL                COMMENT '文件切片数目', --新增字段
    file_node_id            VARCHAR(36)     NOT NULL                COMMENT '文件所在目录节点ID',
    file_storage_path       VARCHAR(512)    NOT NULL                COMMENT '文件存储路径'
) COMMENT='文件信息表';

CREATE TABLE pcd_sharing_Link_mange_table (
    sharing_link_id                     VARCHAR(36)     NOT NULL PRIMARY KEY,
    sharing_link_path                   VARCHAR(512)    NOT NULL                  COMMENT '分享链接路径',
    sharing_link_file_id                VARCHAR(36)     NOT NULL                  COMMENT '分享链接关联的文件ID',
    FOREIGN KEY (sharing_link_file_id) REFERENCES pcd_file_info_table(file_id) ON DELETE CASCADE,
    sharing_link_valid_starting_time    TIMESTAMP       NOT NULL    DEFAULT NOW() COMMENT '分享链接有效开始时间',
    sharing_link_valid_endding_time     TIMESTAMP       NOT NULL                  COMMENT '分享链接有效结束时间',
    sharing_link_password               VARCHAR(60)                               COMMENT '分享链接密码'
) COMMENT='文件分享链接管理表';

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
    uploads_id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    uploads_user_id         VARCHAR(36)     NOT NULL                                                COMMENT '上传用户ID',
    FOREIGN KEY (uploads_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    uploads_total_chunks    INT             NOT NULL                                                COMMENT '上传切片总数',
    uploads_starting_time   TIMESTAMP       NOT NULL                            DEFAULT NOW()       COMMENT '上传开始时间',
    uploads_endding_time    TIMESTAMP       NOT NULL                                                COMMENT '上传结束时间',
    uploads_file_size       BIGINT          NOT NULL                                                COMMENT '文件大小',
    uploads_file_checksum   VARCHAR(256)    NOT NULL                                                COMMENT '文件校验值',
    uploads_chunks_max_size INT             NOT NULL                                                COMMENT '切片最大大小',
    uploads_file_name       VARCHAR(150)    NOT NULL                                                COMMENT '文件名称',
    uploads_file_type       VARCHAR(60)     NOT NULL                                                COMMENT '文件类型',
    uploads_node_id         VARCHAR(36)     NOT NULL                                                COMMENT '文件所在目录节点ID',
    uploads_status          ENUM('uploading', 'merging', 'completed', 'failed') DEFAULT 'uploading' COMMENT '上传状态'
) COMMENT='文件上传会话表';

CREATE TABLE pcd_upload_chunks_table (
    chunk_uploads_id    VARCHAR(36)  NOT NULL                                                COMMENT '关联上传会话ID',
    FOREIGN KEY (chunk_uploads_id) REFERENCES pcd_uploads_session_table(uploads_id) ON DELETE CASCADE,
    chunk_index         INT          NOT NULL                                                COMMENT '切片索引',
    chunk_status        ENUM('pending' ,'uploading', 'uploaded', 'failed') DEFAULT 'pending' COMMENT '切片状态',
    chunk_storage_path  VARCHAR(512) NOT NULL                                                COMMENT '切片存储路径',
    chunk_uploaded_time TIMESTAMP    NOT NULL                              DEFAULT NOW()     COMMENT '切片上传时间',
    PRIMARY KEY (chunk_uploads_id, chunk_index)
) COMMENT='文件切片表';

-- 有点类似于文件夹的元数据表 准确来说是每一条表记录加上关联字段构成了一个目录结构表
-- 可以准确的描述整个文件夹的嵌套结构 利用node_id代替物化路径 提高了安全性和简化了客户端
-- node_status 节点的状态能够解决高并发的服务器环境下导致的异常 类似于逻辑锁 操作时锁定节点
CREATE TABLE pcd_directory_tree_table (
    node_id          VARCHAR(36)     NOT NULL PRIMARY KEY,
    node_user_id     VARCHAR(36)     NOT NULL          COMMENT '所属用户ID',
    FOREIGN KEY (node_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    node_parent_id   VARCHAR(36)                       COMMENT '父节点ID，根节点为NULL',
    FOREIGN KEY (node_parent_id) REFERENCES pcd_directory_tree_table(node_id) ON DELETE CASCADE,
    node_name        VARCHAR(200)    NOT NULL          COMMENT '节点名称',
    node_create_time TIMESTAMP       NOT NULL          COMMENT '节点创建时间'      DEFAULT NOW(),
    node_status      ENUM('lock', 'active', 'pending') COMMENT '节点状态'         DEFAULT 'active'
) COMMENT='节点目录树表';

ALTER TABLE pcd_file_info_table
    ADD CONSTRAINT fk_file_info_directory_tree
    FOREIGN KEY (file_node_id) REFERENCES pcd_directory_tree_table(node_id) ON DELETE CASCADE;

ALTER TABLE pcd_uploads_session_table
    ADD CONSTRAINT fk_uploads_session_directory_tree
    FOREIGN KEY (uploads_node_id) REFERENCES pcd_directory_tree_table(node_id) ON DELETE CASCADE;

CREATE TABLE pcd_user_quota_table (
    quota_id              BIGINT          PRIMARY KEY AUTO_INCREMENT,
    quota_user_id         VARCHAR(36)     NOT NULL UNIQUE COMMENT '用户ID，关联用户表',
    FOREIGN KEY (quota_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    quota_total_capacity  BIGINT          NOT NULL DEFAULT 10737418240 COMMENT '总额度（字节），默认10GB = 10*1024^3',
    quota_used_capacity   BIGINT          NOT NULL DEFAULT 0 COMMENT '已用容量（字节）',
    quota_file_count      INT             NOT NULL DEFAULT 0 COMMENT '已上传文件数量',
    quota_version         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    quota_created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    quota_updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (quota_user_id)
) COMMENT='用户存储配额表';

CREATE TABLE pcd_user_quota_log_table (
    quota_log_id            BIGINT        PRIMARY KEY AUTO_INCREMENT,
    quota_log_user_id       VARCHAR(36)   NOT NULL COMMENT '用户ID，关联用户表',
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

-- 文件收藏表
CREATE TABLE pcd_file_star_table (
    star_id             BIGINT          PRIMARY KEY AUTO_INCREMENT,
    star_user_id        VARCHAR(36)     NOT NULL COMMENT '用户ID',
    FOREIGN KEY (star_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    star_file_id        VARCHAR(36)     NOT NULL COMMENT '文件ID',
    FOREIGN KEY (star_file_id) REFERENCES pcd_file_info_table(file_id) ON DELETE CASCADE,
    star_starred_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    UNIQUE KEY uk_user_file (star_user_id, star_file_id),
    INDEX idx_user_starred (star_user_id, star_starred_at)
) COMMENT='文件收藏表';

-- 回收站文件表
CREATE TABLE pcd_trash_file_table (
    trash_id                BIGINT          PRIMARY KEY AUTO_INCREMENT,
    trash_file_id           VARCHAR(36)     NOT NULL COMMENT '原文件ID',
    trash_user_id           VARCHAR(36)     NOT NULL COMMENT '用户ID',
    FOREIGN KEY (trash_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    trash_file_name         VARCHAR(150)    NOT NULL COMMENT '文件名称',
    trash_file_type         VARCHAR(60)     NOT NULL COMMENT '文件类型',
    trash_file_size         BIGINT          NOT NULL COMMENT '文件大小',
    trash_original_node_id  VARCHAR(36)     NOT NULL COMMENT '原节点ID',
    trash_storage_path      VARCHAR(512)    NOT NULL COMMENT '文件存储路径',
    trash_file_checksum     VARCHAR(256)    NOT NULL COMMENT '文件校验值',
    trash_deleted_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '删除时间',
    trash_expires_at        DATETIME        NOT NULL COMMENT '过期时间（自动彻底删除时间）',
    INDEX idx_user_deleted (trash_user_id, trash_deleted_at),
    INDEX idx_expires (trash_expires_at)
) COMMENT='回收站文件表';

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
    `user_id`     VARCHAR(255) DEFAULT NULL            COMMENT '关联用户ID（可为空）',
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