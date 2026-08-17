-- =====================================================================
-- 上传会话状态简化
-- 需求：REQ-UPLOAD-SESSION-STATE-2026-07
--
-- 原状态：uploading -> merging -> completed/failed，取消后还可能进入 deleted。
-- 新状态：uploading / completed / canceled。
-- 文件合并、扫描、索引和激活属于文件后处理状态，不再写入上传会话表。
--
-- 历史残留处理：无法从数据库判断旧 merging/failed/deleted 记录对应的物理文件
-- 是否已经清理，因此先归一为 canceled，交由现有取消/过期清理链路删除物理分块、
-- 回滚预占配额并删除会话记录，避免直接 ALTER 后留下不可解释的脏记录。
-- =====================================================================

UPDATE pcd_uploads_session_table
SET uploads_status = 'canceled'
WHERE uploads_status IN (
    'merging', 'failed', 'deleted',
    'merge_failed', 'scan_failed', 'process_failed', 'scaning', 'processing'
);

ALTER TABLE pcd_uploads_session_table
    MODIFY COLUMN uploads_status
    ENUM('uploading', 'completed', 'canceled')
    DEFAULT 'uploading'
    COMMENT '上传会话状态：uploading=分块接收中，completed=分块已保存且合并任务已触发，canceled=用户取消或过期清理';
