-- FRIEND-MANAGEMENT-20260810
-- 变更原因：好友关系由“仅可列举的 ID”扩展为企业消息中心所需的备注、星标、黑名单和申请拒收。
-- 原有单聊会话创建逻辑不变：接受申请仍在 IM Business 的同一个本地事务内创建双方会话。
-- 影响范围：pcd_im_friendship、好友申请与 IM 消息黑名单；可重复执行，适用于既有库升级。

SET @has_remark := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pcd_im_friendship' AND COLUMN_NAME = 'remark');
SET @sql := IF(@has_remark = 0,
  'ALTER TABLE pcd_im_friendship ADD COLUMN remark VARCHAR(64) NULL COMMENT ''当前用户对好友的私有备注'' AFTER status',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_starred := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pcd_im_friendship' AND COLUMN_NAME = 'is_starred');
SET @sql := IF(@has_starred = 0,
  'ALTER TABLE pcd_im_friendship ADD COLUMN is_starred TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否星标联系人'' AFTER remark',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS pcd_im_blacklist (
  id BIGINT NOT NULL AUTO_INCREMENT, user_id VARCHAR(64) NOT NULL, blocked_user_id VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_user_blocked (user_id,blocked_user_id), KEY idx_blocked_user (blocked_user_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM 用户黑名单表';

CREATE TABLE IF NOT EXISTS pcd_im_friend_request_block (
  id BIGINT NOT NULL AUTO_INCREMENT, user_id VARCHAR(64) NOT NULL, blocked_user_id VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_user_request_blocked (user_id,blocked_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM 好友申请拒收规则表';
