-- ============================================================
-- IM 系统数据库初始化脚本
-- 适用于 MySQL 8.0+
-- ============================================================

-- ==================== 消息表 ====================
CREATE TABLE IF NOT EXISTS `pcd_im_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `message_id` VARCHAR(64) NOT NULL COMMENT '消息唯一 ID（雪花算法）',
    `conversation_id` VARCHAR(128) NOT NULL COMMENT '会话 ID',
    `conversation_type` TINYINT NOT NULL DEFAULT 1 COMMENT '会话类型：1-单聊 2-群聊',
    `message_type` TINYINT NOT NULL DEFAULT 1 COMMENT '消息类型：1-文本 2-图片 3-文件 4-语音 5-视频 6-位置 7-系统通知 8-自定义',
    `sender_id` VARCHAR(64) NOT NULL COMMENT '发送者用户 ID',
    `receiver_id` VARCHAR(64) NOT NULL COMMENT '接收者 ID（单聊为对方 userId，群聊为 groupId）',
    `content` TEXT COMMENT '消息内容',
    `extra` TEXT COMMENT '扩展内容（JSON 格式）',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '消息状态：0-准备中(PREPARING) 1-已送达(DELIVERED) 2-已读(READ) 3-失败(FAILED)；5-已撤回 6-已删除（可见性状态）',
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
CREATE TABLE IF NOT EXISTS `pcd_im_conversation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `session_id` VARCHAR(160) NOT NULL COMMENT '共享会话 ID：单聊 minUserId*maxUserId，群聊 group*groupId',
    `session_type` TINYINT NOT NULL DEFAULT 1 COMMENT '会话类型：1-SINGLE 2-GROUP',
    `user_id` VARCHAR(64) NOT NULL COMMENT '当前用户 ID',
    `peer_id` VARCHAR(64) NOT NULL COMMENT '对端 ID（单聊为对方 userId，群聊为 groupId）',
    `is_pinned` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否置顶：0-否 1-是',
    `is_muted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否免打扰：0-否 1-是',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_peer` (`user_id`, `peer_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_session_user` (`session_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM 会话元数据表；最后消息和未读数由 Redis Hash 管理';

-- ==================== 好友申请与好友关系表 ====================
CREATE TABLE IF NOT EXISTS `pcd_im_friend_request` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `request_id` VARCHAR(64) NOT NULL COMMENT '申请唯一 ID',
    `requester_id` VARCHAR(64) NOT NULL COMMENT '申请人 ID',
    `recipient_id` VARCHAR(64) NOT NULL COMMENT '接收人 ID',
    `verification_message` VARCHAR(255) DEFAULT NULL COMMENT '验证信息',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-待处理 1-已接受 2-已拒绝 3-已撤销',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_request_id` (`request_id`),
    KEY `idx_recipient_status` (`recipient_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM 好友申请表';

CREATE TABLE IF NOT EXISTS `pcd_im_friendship` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户 ID',
    `friend_id` VARCHAR(64) NOT NULL COMMENT '好友 ID',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-有效 1-已解除',
    `remark` VARCHAR(64) DEFAULT NULL COMMENT '当前用户对好友的私有备注',
    `is_starred` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否星标联系人：0-否 1-是',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_friend` (`user_id`, `friend_id`),
    KEY `idx_friend_user` (`friend_id`, `user_id`),
    KEY `idx_user_starred` (`user_id`, `status`, `is_starred`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM 对称好友关系表';

-- 好友管理扩展：黑名单决定 IM 消息拒收，拒收申请规则只影响未来好友申请。
CREATE TABLE IF NOT EXISTS `pcd_im_blacklist` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `user_id` VARCHAR(64) NOT NULL COMMENT '拉黑操作人 ID',
    `blocked_user_id` VARCHAR(64) NOT NULL COMMENT '被拉黑用户 ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_blocked` (`user_id`,`blocked_user_id`),
    KEY `idx_blocked_user` (`blocked_user_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM 用户黑名单表';

CREATE TABLE IF NOT EXISTS `pcd_im_friend_request_block` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `user_id` VARCHAR(64) NOT NULL COMMENT '不再接收申请的用户 ID',
    `blocked_user_id` VARCHAR(64) NOT NULL COMMENT '被拒收申请的用户 ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_request_blocked` (`user_id`,`blocked_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM 好友申请拒收规则表';

-- ==================== 群组表 ====================
CREATE TABLE IF NOT EXISTS `pcd_im_group` (
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
CREATE TABLE IF NOT EXISTS `pcd_im_group_member` (
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

-- ==================== 通话记录表 ====================
CREATE TABLE IF NOT EXISTS `pcd_im_call_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `call_id` VARCHAR(64) NOT NULL COMMENT '通话唯一 ID',
    `room_id` VARCHAR(64) DEFAULT NULL COMMENT '通话房间 ID（群组通话时使用）',
    `call_type` TINYINT NOT NULL DEFAULT 2 COMMENT '通话类型：1-语音通话 2-视频通话',
    `call_mode` TINYINT NOT NULL DEFAULT 1 COMMENT '通话模式：1-P2P 2-群组',
    `caller_id` VARCHAR(64) NOT NULL COMMENT '发起者用户 ID',
    `callee_id` VARCHAR(64) DEFAULT NULL COMMENT '被叫者用户 ID（P2P 模式）',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '通话状态：0-等待接听 1-通话中 2-已拒绝 3-已取消 4-已挂断 5-超时 6-忙线',
    `start_time` DATETIME DEFAULT NULL COMMENT '通话开始时间',
    `end_time` DATETIME DEFAULT NULL COMMENT '通话结束时间',
    `duration` BIGINT DEFAULT 0 COMMENT '通话持续时间（秒）',
    `reject_reason` VARCHAR(255) DEFAULT NULL COMMENT '拒绝原因',
    `participants` JSON DEFAULT NULL COMMENT '参与者列表（JSON 数组）',
    `video_enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用视频',
    `screen_share_enabled` TINYINT(1) DEFAULT 0 COMMENT '是否启用屏幕共享',
    `hangup_by` VARCHAR(64) DEFAULT NULL COMMENT '挂断方用户 ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_call_id` (`call_id`),
    KEY `idx_caller_id` (`caller_id`),
    KEY `idx_callee_id` (`callee_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM 通话记录表';
