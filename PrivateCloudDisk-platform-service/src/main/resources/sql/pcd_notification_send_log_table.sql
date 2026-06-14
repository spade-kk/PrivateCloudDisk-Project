-- =====================================================================
-- 通知发送日志表 pcd_notification_send_log_table
-- 用途：记录邮件/短信等通知的发送状态，实现消费者幂等性，便于重试和排查
-- =====================================================================

DROP TABLE IF EXISTS `pcd_notification_send_log_table`;
CREATE TABLE `pcd_notification_send_log_table` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键自增ID',
    `event_id`    VARCHAR(255) NOT NULL                COMMENT '事件唯一ID（由发布方生成）',
    `channel`     VARCHAR(20)  NOT NULL                COMMENT '通道：EMAIL（邮件）、SMS（短信）',
    `receiver`    VARCHAR(255) NOT NULL                COMMENT '接收者：邮箱地址或手机号',
    `user_id`     VARCHAR(255) DEFAULT NULL            COMMENT '关联用户ID（可为空）',
    `status`      VARCHAR(20)  NOT NULL                COMMENT '状态：PENDING、SUCCESS、FAILED',
    `retry_count` INT          NOT NULL DEFAULT 0      COMMENT '重试次数',
    `error_message` VARCHAR(1000) DEFAULT NULL         COMMENT '错误信息（失败时记录，截断至1000字符）',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 唯一索引：同一事件+通道+接收者 只能有一条记录，保证数据库级的幂等性
    UNIQUE KEY `uk_event_channel_receiver` (`event_id`, `channel`, `receiver`),
    -- 状态索引：便于按状态查询（如查询所有FAILED记录进行人工重试）
    KEY `idx_status` (`status`),
    -- 时间索引：便于历史数据清理
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知发送日志表（邮件/短信，幂等性支持）';

-- =====================================================================
-- 说明：
--   1. (event_id, channel, receiver) 唯一索引是实现"消息不重复发送"的核心
--      当MQ消息因网络原因重复投递时，第一个消费者插入成功，后续会冲突
--   2. status 状态流转：
--      PENDING → SUCCESS（发送成功）
--      PENDING → FAILED（发送失败）
--      FAILED  → PENDING（人工触发重试，通过 update 语句重置）
--   3. 建议每日清理超过30天的 SUCCESS 状态记录，避免表无限膨胀
-- =====================================================================

INSERT INTO pcd_directory_closure_table (ancestor_id, descendant_id, depth, user_id) VALUES
                                                                                         (UNHEX('AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA1'), UNHEX('AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA1'), 0, UUID_TO_BIN('11111111-1111-1111-1111-111111111111')),
                                                                                         (UNHEX('AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA2'), UNHEX('AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA2'), 0, UUID_TO_BIN('22222222-2222-2222-2222-222222222222')),
                                                                                         (UNHEX('AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA3'), UNHEX('AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA3'), 0, UUID_TO_BIN('33333333-3333-3333-3333-333333333333')),
                                                                                         (UNHEX('AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA4'), UNHEX('AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA4'), 0, UUID_TO_BIN('44444444-4444-4444-4444-444444444444')),
                                                                                         (UNHEX('AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA5'), UNHEX('AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA5'), 0, UUID_TO_BIN('55555555-5555-5555-5555-555555555555'));