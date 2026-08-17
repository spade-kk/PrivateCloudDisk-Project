-- =====================================================================
-- 公开空间（仓库）权限扩展
-- 需求：公开仓库与分享链接分离；仓库为持久资源，权限由所有者统一控制。
-- 兼容策略：仅新增可空检查后的默认字段，不改变现有文件、成员、分享数据。
-- =====================================================================
USE private_cloud_disk;

SET @has_browse := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pcd_space_table'
      AND COLUMN_NAME = 'allow_public_browse'
);
SET @sql := IF(@has_browse = 0,
    'ALTER TABLE pcd_space_table ADD COLUMN allow_public_browse TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''公开仓库是否允许浏览'' AFTER space_visibility',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_download := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pcd_space_table'
      AND COLUMN_NAME = 'allow_public_download'
);
SET @sql := IF(@has_download = 0,
    'ALTER TABLE pcd_space_table ADD COLUMN allow_public_download TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''公开仓库是否允许下载'' AFTER allow_public_browse',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_upload := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pcd_space_table'
      AND COLUMN_NAME = 'allow_public_upload'
);
SET @sql := IF(@has_upload = 0,
    'ALTER TABLE pcd_space_table ADD COLUMN allow_public_upload TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''公开仓库是否允许登录用户上传'' AFTER allow_public_download',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_index := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pcd_space_table'
      AND INDEX_NAME = 'idx_public_repository'
);
SET @sql := IF(@has_index = 0,
    'ALTER TABLE pcd_space_table ADD INDEX idx_public_repository (space_type, space_visibility, space_status, space_updated_at)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 历史公共空间采用安全默认值：可浏览、可下载、禁止公开上传。
UPDATE pcd_space_table
SET allow_public_browse = 1,
    allow_public_download = 1,
    allow_public_upload = 0
WHERE space_type = 'public';

