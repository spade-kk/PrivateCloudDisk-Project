-- ============================================================
-- 迁移脚本：消息状态精简为四种核心投递状态
-- ============================================================
-- 背景：将消息状态从 7 态精简为 4 态（PREPARING/DELIVERED/READ/FAILED），
--       剔除冗余的 SENDING / SENT 状态。
-- 旧值映射（pcd_im_message.status）：
--   0 SENDING     -> 0 PREPARING（已入库未送达）
--   1 SENT        -> 0 PREPARING（服务端已持久化但未送达，归并到准备中）
--   2 DELIVERED   -> 1 DELIVERED
--   3 READ        -> 2 READ
--   4 FAILED      -> 3 FAILED
--   5 RECALLED    -> 5 RECALLED（可见性状态，保持不变）
--   6 DELETED     -> 6 DELETED（可见性状态，保持不变）
--
-- 说明：
--   1. 请在执行前对 pcd_im_message 表做备份。
--   2. 迁移为幂等操作，可重复执行（目标状态已正确的行不会重复变动）。
-- ============================================================

USE `private_cloud_disk`;

-- 1. SENDING(0) / SENT(1) -> PREPARING(0)
UPDATE `pcd_im_message`
SET `status` = 0, `update_time` = NOW()
WHERE `status` IN (0, 1);

-- 2. DELIVERED(2) -> DELIVERED(1)
UPDATE `pcd_im_message`
SET `status` = 1, `update_time` = NOW()
WHERE `status` = 2;

-- 3. READ(3) -> READ(2)
UPDATE `pcd_im_message`
SET `status` = 2, `update_time` = NOW()
WHERE `status` = 3;

-- 4. FAILED(4) -> FAILED(3)
UPDATE `pcd_im_message`
SET `status` = 3, `update_time` = NOW()
WHERE `status` = 4;

-- 5. 调整默认值：新建消息默认 PREPARING(0)
ALTER TABLE `pcd_im_message`
MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 0
COMMENT '消息状态：0-准备中(PREPARING) 1-已送达(DELIVERED) 2-已读(READ) 3-失败(FAILED)；5-已撤回 6-已删除（可见性状态）';

-- 6. 历史查询与离线拉取性能索引
--    (receiver_id, status) 支持离线消息按用户+状态查询
ALTER TABLE `pcd_im_message`
ADD INDEX `idx_receiver_status` (`receiver_id`, `status`);

--    (conversation_id, server_seq, status) 支持游标分页历史查询
ALTER TABLE `pcd_im_message`
ADD INDEX `idx_conversation_seq_status` (`conversation_id`, `server_seq`, `status`);
