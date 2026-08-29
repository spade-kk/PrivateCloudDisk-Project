-- ============================================================================
-- IM 会话同步简化迁移
-- 需求：IM-EMOJI-SESSION-20260810（5.1-5.8）
-- 原行为：pcd_im_conversation 以全局 conversation_id 唯一，持久化最后消息与未读数，
--         导致双方会话元数据相互覆盖。
-- 新行为：按 user_id + peer_id 保存每位参与者的会话元数据；session_id 仅作为共享消息流
--         标识。最后消息/未读数移至 Redis Hash（im:conversation:summary:{user}:{session}）。
-- 影响：部署时先备份，再在同一发布窗口升级 IM Platform 与本迁移；旧下划线会话 ID 会被
--       规范为 * 分隔或 group* 前缀，消息表同步更新，避免历史消息断链。
-- ============================================================================

-- 注意：MySQL DDL 会隐式提交；本脚本不宣称可回滚。执行前必须完成数据库备份。

ALTER TABLE pcd_im_conversation DROP INDEX uk_conversation_id;
ALTER TABLE pcd_im_conversation
    CHANGE COLUMN conversation_id session_id VARCHAR(160) NOT NULL COMMENT '共享会话 ID',
    CHANGE COLUMN conversation_type session_type TINYINT NOT NULL DEFAULT 1 COMMENT '1-SINGLE 2-GROUP',
    CHANGE COLUMN target_id peer_id VARCHAR(64) NOT NULL COMMENT '对端 ID',
    CHANGE COLUMN is_top is_pinned TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否置顶',
    CHANGE COLUMN create_time created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    CHANGE COLUMN update_time updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    DROP COLUMN last_message,
    DROP COLUMN last_message_type,
    DROP COLUMN last_message_time,
    DROP COLUMN unread_count,
    DROP COLUMN status,
    ADD UNIQUE KEY uk_user_peer (user_id, peer_id),
    ADD KEY idx_session_user (session_id, user_id);

-- 历史单聊的 session_id 原格式为 A_B；UUID 自身不含下划线，可安全转换。
UPDATE pcd_im_conversation
SET session_id = REPLACE(session_id, '_', '*')
WHERE session_type = 1 AND session_id LIKE '%\\_%';

-- 历史群聊曾直接使用 groupId；统一改为 group*groupId。
UPDATE pcd_im_conversation
SET session_id = CONCAT('group*', peer_id)
WHERE session_type = 2 AND session_id NOT LIKE 'group*%';

-- 原表把共享会话 ID 设为全局唯一，因此历史数据通常只保留一侧元数据。补齐单聊对端
-- 元数据行；置顶/免打扰使用安全默认值，最后消息和未读数会由 Redis 回查消息表重建。
INSERT IGNORE INTO pcd_im_conversation
    (session_id, session_type, user_id, peer_id, is_pinned, is_muted, created_at, updated_at)
SELECT session_id, session_type, peer_id, user_id, 0, 0, created_at, updated_at
FROM pcd_im_conversation
WHERE session_type = 1;

UPDATE pcd_im_message
SET conversation_id = REPLACE(conversation_id, '_', '*')
WHERE conversation_type = 1 AND conversation_id LIKE '%\\_%';

UPDATE pcd_im_message
SET conversation_id = CONCAT('group*', receiver_id)
WHERE conversation_type = 2 AND conversation_id NOT LIKE 'group*%';

CREATE TABLE IF NOT EXISTS pcd_im_friend_request (
    id BIGINT NOT NULL AUTO_INCREMENT, request_id VARCHAR(64) NOT NULL, requester_id VARCHAR(64) NOT NULL,
    recipient_id VARCHAR(64) NOT NULL, verification_message VARCHAR(255) DEFAULT NULL, status TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_request_id (request_id), KEY idx_recipient_status (recipient_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM 好友申请表';

CREATE TABLE IF NOT EXISTS pcd_im_friendship (
    id BIGINT NOT NULL AUTO_INCREMENT, user_id VARCHAR(64) NOT NULL, friend_id VARCHAR(64) NOT NULL,
    status TINYINT NOT NULL DEFAULT 0, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_user_friend (user_id, friend_id), KEY idx_friend_user (friend_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM 对称好友关系表';
