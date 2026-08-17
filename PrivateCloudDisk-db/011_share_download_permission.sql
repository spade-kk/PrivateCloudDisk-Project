-- =====================================================================
-- 011_share_download_permission.sql
-- 需求 2.3：分享链接增加“仅浏览/允许下载”权限开关。
-- 兼容策略：历史分享全部回填为允许下载；新分享可显式设置为 false。
-- =====================================================================
USE private_cloud_disk;

SET @sql := IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'pcd_share_link_table'
        AND COLUMN_NAME = 'share_allow_download') = 0,
    'ALTER TABLE pcd_share_link_table ADD COLUMN share_allow_download TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''是否允许通过分享授权获取实际文件内容'' AFTER share_has_password',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE pcd_share_link_table
SET share_allow_download = 1
WHERE share_allow_download IS NULL;
