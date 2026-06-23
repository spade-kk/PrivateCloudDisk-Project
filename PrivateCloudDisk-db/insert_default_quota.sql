-- ============================================================
-- 插入默认配额记录
-- 注意：需要先确保 pcd_user_info_table 中存在对应的 user_id
--       如果缺失 quota_released_capacity 列，请先执行：
--       ALTER TABLE pcd_user_quota_table
--         ADD COLUMN quota_released_capacity BIGINT NOT NULL DEFAULT 0
--         COMMENT '预占容量（字节）' AFTER quota_used_capacity;
-- ============================================================

INSERT IGNORE INTO pcd_user_quota_table
    (quota_user_id, quota_total_capacity, quota_used_capacity, quota_file_count, quota_version)
VALUES
    -- 1. 0x11111111111111111111111111111111
    (UUID_TO_BIN('11111111-1111-1111-1111-111111111111'), 10737418240, 0, 0, 0),

    -- 2. """""""""""""""" (16 个 0x22 = ASCII 双引号)
    (UUID_TO_BIN('22222222-2222-2222-2222-222222222222'), 10737418240, 0, 0, 0),

    -- 3. 3333333333333333 (16 个 0x33 = ASCII '3')
    (UUID_TO_BIN('33333333-3333-3333-3333-333333333333'), 10737418240, 0, 0, 0),

    -- 4. 0x37E06D5A689F4395BB114010E1016CD6
    (UUID_TO_BIN('37E06D5A-689F-4395-BB11-4010E1016CD6'), 10737418240, 0, 0, 0),

    -- 5. 0x415D3064A46548138F42D6F1AA9B87C0
    (UUID_TO_BIN('415D3064-A465-4813-8F42-D6F1AA9B87C0'), 10737418240, 0, 0, 0),

    -- 6. DDDDDDDDDDDDDDDD (16 个 0x44 = ASCII 'D')
    (UUID_TO_BIN('44444444-4444-4444-4444-444444444444'), 10737418240, 0, 0, 0),

    -- 7. UUUUUUUUUUUUUUUU (16 个 0x55 = ASCII 'U')
    (UUID_TO_BIN('55555555-5555-5555-5555-555555555555'), 10737418240, 0, 0, 0);