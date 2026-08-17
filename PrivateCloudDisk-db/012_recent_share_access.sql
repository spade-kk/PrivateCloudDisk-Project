-- =====================================================================
-- 012_recent_share_access.sql
-- 需求 2.4：区分普通空间下载与分享资源下载，避免最近访问列表把分享
-- 的虚拟资源标识误当作真实 file_id。
-- =====================================================================
USE private_cloud_disk;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pcd_recent_access_table'
      AND COLUMN_NAME = 'ra_access_source') = 0,
    'ALTER TABLE pcd_recent_access_table ADD COLUMN ra_access_source VARCHAR(32) NOT NULL DEFAULT ''space'' COMMENT ''访问来源：space/share'' AFTER ra_space_id',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pcd_recent_access_table'
      AND COLUMN_NAME = 'ra_share_resource_id') = 0,
    'ALTER TABLE pcd_recent_access_table ADD COLUMN ra_share_resource_id VARCHAR(512) NULL COMMENT ''分享资源虚拟ID'' AFTER ra_access_source',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
