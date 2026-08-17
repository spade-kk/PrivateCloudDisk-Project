-- =====================================================================
-- 空间多人协作扩展（SPACE-COLLAB-DB）
-- 兼容策略：保留旧 visibility/permission 字段和旧接口；通过新增字段与回填
-- 增量启用加入策略、细粒度权限、可重复申请和邀请链接。
-- =====================================================================
USE private_cloud_disk;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pcd_space_table' AND COLUMN_NAME = 'join_policy') = 0,
  'ALTER TABLE pcd_space_table ADD COLUMN join_policy ENUM(''open'',''approval_required'',''invite_only'') NOT NULL DEFAULT ''invite_only'' COMMENT ''空间加入策略'' AFTER space_visibility',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 旧空间类型增加 private；公开仓库仍由 public 类型独立处理。
ALTER TABLE pcd_space_table MODIFY COLUMN space_type ENUM('personal','private','enterprise','public','team') NOT NULL;
ALTER TABLE pcd_space_member_table MODIFY COLUMN role ENUM('owner','admin','editor','viewer','custom') NOT NULL DEFAULT 'viewer';
-- visibility 保留旧值，新增 visible/hidden 供协作发现使用，避免历史数据立即失效。
ALTER TABLE pcd_space_table MODIFY COLUMN space_visibility ENUM('private','public','visible','hidden','whitelist','blacklist') NOT NULL DEFAULT 'hidden';

UPDATE pcd_space_table SET space_visibility = 'hidden', join_policy = 'invite_only'
 WHERE space_type = 'personal' AND space_status <> 'deleted';
UPDATE pcd_space_table SET space_visibility = 'visible', join_policy = 'approval_required'
 WHERE space_type IN ('enterprise','team') AND space_status <> 'deleted';
UPDATE pcd_space_table SET space_visibility = 'hidden', join_policy = 'invite_only'
 WHERE space_type = 'private' AND space_status <> 'deleted';
UPDATE pcd_space_table SET space_visibility = 'public', join_policy = 'invite_only'
 WHERE space_type = 'public' AND space_status <> 'deleted';

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pcd_space_permission_table' AND COLUMN_NAME = 'can_view') = 0,
  'ALTER TABLE pcd_space_permission_table ADD COLUMN can_view TINYINT(1) NOT NULL DEFAULT 0, ADD COLUMN can_download TINYINT(1) NOT NULL DEFAULT 0, ADD COLUMN can_upload TINYINT(1) NOT NULL DEFAULT 0, ADD COLUMN can_edit TINYINT(1) NOT NULL DEFAULT 0, ADD COLUMN can_manage_members TINYINT(1) NOT NULL DEFAULT 0, ADD COLUMN can_manage_plugins TINYINT(1) NOT NULL DEFAULT 0, ADD COLUMN can_manage_settings TINYINT(1) NOT NULL DEFAULT 0',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE pcd_space_permission_table
SET can_view = can_read,
    can_download = can_read,
    can_upload = can_write,
    can_edit = can_write,
    can_manage_members = can_invite,
    can_manage_plugins = can_manage,
    can_manage_settings = can_manage;

-- 允许同一用户在被拒绝后重新申请，业务层仍保证 pending 只能有一条。
SET @idx := (SELECT INDEX_NAME FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pcd_space_join_request_table' AND INDEX_NAME = 'uk_space_user_pending');
SET @sql := IF(@idx IS NOT NULL, 'ALTER TABLE pcd_space_join_request_table DROP INDEX uk_space_user_pending', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pcd_space_join_request_table' AND INDEX_NAME = 'idx_join_user_status') = 0,
  'ALTER TABLE pcd_space_join_request_table ADD INDEX idx_join_user_status (space_id,user_id,status,created_at)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS pcd_space_invitation_table (
    invitation_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    space_id BINARY(16) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    created_by BINARY(16) NOT NULL,
    expires_at DATETIME NOT NULL,
    max_uses INT NOT NULL DEFAULT 1,
    used_count INT NOT NULL DEFAULT 0,
    status ENUM('active','revoked','expired') NOT NULL DEFAULT 'active',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_invitation_token (token_hash),
    KEY idx_invitation_space_status (space_id,status,expires_at),
    CONSTRAINT fk_invitation_space FOREIGN KEY (space_id) REFERENCES pcd_space_table(space_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='空间成员邀请链接';
