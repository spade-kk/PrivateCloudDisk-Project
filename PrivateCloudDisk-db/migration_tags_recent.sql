-- ============================================================
-- 迁移脚本：标签系统 + 最近访问
-- 版本: v2.0
-- 日期: 2026-06-28
-- 说明：
--   1. 标签表：用户自定义标签（合同、设计稿、项目A、财务、机密 等）
--   2. 文件标签关联表：文件/文件夹与标签的多对多关系
--   3. 最近访问表：记录用户最近打开、下载、上传的文件
-- ============================================================

USE private_cloud_disk;

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
    ra_space_id         BINARY(16)                              COMMENT '访问发生的空间ID',
    ra_access_source    VARCHAR(32)      NOT NULL DEFAULT 'space' COMMENT '访问来源：space/share',
    ra_share_resource_id VARCHAR(512)                              COMMENT '分享资源虚拟ID，不保存真实文件ID',
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
-- 注意：此部分在 Java 代码中通过 UserRegisteredEvent 消费者实现
-- 预设标签：合同、设计稿、项目A、财务报告、机密文件、个人文档、临时文件、重要资料
