-- ============================================================
-- IM 系统数据库初始化脚本
-- 适用于 MySQL 8.0+
-- ============================================================

-- ==================== 消息表 ====================
CREATE TABLE IF NOT EXISTS `im_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `message_id` VARCHAR(64) NOT NULL COMMENT '消息唯一 ID（雪花算法）',
    `conversation_id` VARCHAR(128) NOT NULL COMMENT '会话 ID',
    `conversation_type` TINYINT NOT NULL DEFAULT 1 COMMENT '会话类型：1-单聊 2-群聊',
    `message_type` TINYINT NOT NULL DEFAULT 1 COMMENT '消息类型：1-文本 2-图片 3-文件 4-语音 5-视频 6-位置 7-系统通知 8-自定义',
    `sender_id` VARCHAR(64) NOT NULL COMMENT '发送者用户 ID',
    `receiver_id` VARCHAR(64) NOT NULL COMMENT '接收者 ID（单聊为对方 userId，群聊为 groupId）',
    `content` TEXT COMMENT '消息内容',
    `extra` TEXT COMMENT '扩展内容（JSON 格式）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '消息状态：0-发送中 1-已发送 2-已送达 3-已读 4-失败 5-已撤回 6-已删除',
    `server_seq` BIGINT NOT NULL DEFAULT 0 COMMENT '服务端消息序列号',
    `reply_to` VARCHAR(64) DEFAULT NULL COMMENT '引用消息 ID',
    `send_time` DATETIME DEFAULT NULL COMMENT '发送时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_message_id` (`message_id`),
    KEY `idx_conversation_seq` (`conversation_id`, `server_seq`),
    KEY `idx_sender_id` (`sender_id`),
    KEY `idx_send_time` (`send_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM 消息表';

-- ==================== 会话表 ====================
CREATE TABLE IF NOT EXISTS `im_conversation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `conversation_id` VARCHAR(128) NOT NULL COMMENT '会话唯一 ID',
    `conversation_type` TINYINT NOT NULL DEFAULT 1 COMMENT '会话类型：1-单聊 2-群聊',
    `user_id` VARCHAR(64) NOT NULL COMMENT '当前用户 ID',
    `target_id` VARCHAR(64) NOT NULL COMMENT '对方 ID（单聊为对方 userId，群聊为 groupId）',
    `last_message` VARCHAR(500) DEFAULT NULL COMMENT '最后一条消息内容',
    `last_message_type` TINYINT DEFAULT NULL COMMENT '最后一条消息类型',
    `last_message_time` DATETIME DEFAULT NULL COMMENT '最后一条消息时间',
    `unread_count` INT NOT NULL DEFAULT 0 COMMENT '未读消息数',
    `is_top` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否置顶：0-否 1-是',
    `is_muted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否免打扰：0-否 1-是',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-正常 1-已删除',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_conversation_id` (`conversation_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_user_target` (`user_id`, `target_id`, `conversation_type`),
    KEY `idx_last_message_time` (`last_message_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM 会话表';

-- ==================== 群组表 ====================
CREATE TABLE IF NOT EXISTS `im_group` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `group_id` VARCHAR(64) NOT NULL COMMENT '群组唯一 ID（雪花算法）',
    `group_name` VARCHAR(128) NOT NULL COMMENT '群组名称',
    `avatar` VARCHAR(512) DEFAULT NULL COMMENT '群组头像 URL',
    `owner_id` VARCHAR(64) NOT NULL COMMENT '群主用户 ID',
    `announcement` TEXT COMMENT '群公告',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '群简介',
    `member_count` INT NOT NULL DEFAULT 0 COMMENT '当前成员数',
    `max_members` INT NOT NULL DEFAULT 500 COMMENT '最大成员数',
    `join_mode` TINYINT NOT NULL DEFAULT 0 COMMENT '加群方式：0-自由加入 1-需要审核 2-禁止加入',
    `is_all_muted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否全员禁言：0-否 1-是',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-正常 1-已解散',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_id` (`group_id`),
    KEY `idx_owner_id` (`owner_id`),
    KEY `idx_group_name` (`group_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM 群组表';

-- ==================== 群组成员表 ====================
CREATE TABLE IF NOT EXISTS `im_group_member` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `group_id` VARCHAR(64) NOT NULL COMMENT '群组 ID',
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户 ID',
    `role` TINYINT NOT NULL DEFAULT 3 COMMENT '群内角色：1-群主 2-管理员 3-成员',
    `alias` VARCHAR(64) DEFAULT NULL COMMENT '群内别名',
    `mute_until` DATETIME DEFAULT NULL COMMENT '禁言截止时间',
    `last_read_seq` BIGINT NOT NULL DEFAULT 0 COMMENT '最后阅读的消息序号',
    `join_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_user` (`group_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM 群组成员表';