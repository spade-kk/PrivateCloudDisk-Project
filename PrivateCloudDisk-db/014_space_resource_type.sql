-- =====================================================================
-- [REQ-GIT-SPACE-2.2] 空间资源抽象迁移
-- 原行为：公开空间隐式等同文件仓库；新行为：space_type 表示协作语义，
-- resource_type 表示底层资源实现。历史数据全部回填为 file，保持向后兼容。
-- 影响范围：公开空间创建/展示及资源 Provider 分流；不改变现有文件表数据。
-- =====================================================================
USE private_cloud_disk;

SET @has_resource_type := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pcd_space_table'
      AND COLUMN_NAME = 'resource_type'
);
SET @sql := IF(@has_resource_type = 0,
    'ALTER TABLE pcd_space_table ADD COLUMN resource_type VARCHAR(32) NOT NULL DEFAULT ''file'' COMMENT ''资源实现类型：file/git，预留 dataset/docker/model'' AFTER space_type',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE pcd_space_table SET resource_type = 'file'
WHERE resource_type IS NULL OR resource_type = '';

SET @has_resource_index := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pcd_space_table'
      AND INDEX_NAME = 'idx_space_resource_type'
);
SET @sql := IF(@has_resource_index = 0,
    'ALTER TABLE pcd_space_table ADD INDEX idx_space_resource_type (resource_type, space_status, space_updated_at)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
