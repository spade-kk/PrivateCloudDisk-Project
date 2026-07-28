-- =====================================================================
-- 003_share_resource_migration.sql — 分享模型升级迁移脚本
-- =====================================================================
-- 升级目标：将单目标分享模型（一个分享链接对应一个文件或文件夹）
--           升级为多资源分享模型（一个分享链接包含多个文件和文件夹）
--
-- 变更内容：
--   1. 修改 pcd_share_link_table：移除 share_target_type、share_file_id、
--      share_node_id 及关联约束
--   2. 新建 pcd_share_resource_table：分享资源关系表，支持一个分享链接
--      包含多个文件和文件夹
--
-- 兼容性说明：
--   - 本脚本包含 DROP 操作，执行前请确保已备份数据
--   - 建议在维护窗口执行
-- =====================================================================

-- =====================================================================
-- 第一步：新建分享资源关系表
-- =====================================================================
DROP TABLE IF EXISTS pcd_share_resource_table;
CREATE TABLE pcd_share_resource_table (
    share_resource_id       BINARY(16)     NOT NULL PRIMARY KEY       COMMENT '分享资源ID（主键）',
    share_id                BINARY(16)     NOT NULL                   COMMENT '所属分享链接ID',
    FOREIGN KEY (share_id) REFERENCES pcd_share_link_table(share_id) ON DELETE CASCADE,
    resource_type           ENUM('file', 'folder') NOT NULL           COMMENT '资源类型：file=文件，folder=文件夹',
    file_id                 BINARY(16)     NULL                       COMMENT '文件ID（resource_type=file 时必填）',
    FOREIGN KEY (file_id) REFERENCES pcd_file_info_table(file_id) ON DELETE CASCADE,
    node_id                 BINARY(16)     NULL                       COMMENT '文件夹节点ID（resource_type=folder 时必填）',
    FOREIGN KEY (node_id) REFERENCES pcd_directory_tree_table(node_id) ON DELETE CASCADE,
    created_at              DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_resource_share (share_id),
    INDEX idx_resource_file (file_id),
    INDEX idx_resource_node (node_id),
    CONSTRAINT chk_resource_target CHECK (
        (resource_type = 'file' AND file_id IS NOT NULL AND node_id IS NULL) OR
        (resource_type = 'folder' AND node_id IS NOT NULL AND file_id IS NULL)
    )
) COMMENT='分享资源关系表（一个分享链接可包含多个文件/文件夹）';

-- =====================================================================
-- 第二步：修改分享链接主表 — 移除单目标字段
-- =====================================================================
-- 注意：MySQL 不支持直接修改 ENUM 列，需重建表。
-- 此处采用 ALTER TABLE 方式逐步修改。

-- 2.1 删除旧的外键约束
ALTER TABLE pcd_share_link_table
    DROP FOREIGN KEY pcd_share_link_table_ibfk_2,
    DROP FOREIGN KEY pcd_share_link_table_ibfk_3;

-- 2.2 删除旧的 CHECK 约束（MySQL 8.0.16+ 支持 DROP CHECK）
ALTER TABLE pcd_share_link_table
    DROP CHECK chk_share_target;

-- 2.3 删除旧的索引
ALTER TABLE pcd_share_link_table
    DROP INDEX share_file_id,
    DROP INDEX share_node_id;

-- 2.4 删除旧的列
ALTER TABLE pcd_share_link_table
    DROP COLUMN share_target_type,
    DROP COLUMN share_file_id,
    DROP COLUMN share_node_id;

-- =====================================================================
-- 完成
-- =====================================================================