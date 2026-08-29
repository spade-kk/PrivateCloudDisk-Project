-- ============================================================================
-- IM 测试初始化数据脚本
-- 需求编号：IM-TEST-SEED-20260810
-- 适用版本：MySQL 8.0+
--
-- 目的：
--   1. 从 pcd_user_info_table 读取现有账户，不写入虚构用户；
--   2. 按账户排序后相邻两两配对，创建对称的一对一好友关系；
--   3. 为每组好友创建双方会话记录；
--   4. 创建一个固定 UUID 的大型测试群，加入前 N 个现有用户；
--   5. 为测试群成员创建群聊会话记录。
--
-- 重要说明：
--   - 用户表使用 BINARY(16) 保存 UUID，IM 表使用 VARCHAR(64) 保存 UUID 字符串，
--     因此本脚本统一通过 BIN_TO_UUID(user_id) 转换，不能直接把二进制值写入 IM 表。
--   - “每个账户一对一”按相邻配对实现：排序后第 1/2 个账户配一组、第 3/4 个账户配一组，
--     这样每个账户最多拥有一个测试好友；账户总数为奇数时，最后一个账户不创建测试好友。
--   - 好友关系和会话使用 INSERT IGNORE，并依赖现有唯一约束实现幂等。重复执行不会重复插入，
--     也不会把已存在但已解除的好友关系强制恢复为有效状态。
--   - MySQL 临时表不能在同一条语句中被重复打开；本脚本使用 LEAD() 生成配对，并将两个
--     方向的插入拆成独立语句，避免 ERROR 1137（Can't reopen table）。
--   - 临时表和测试群变量显式使用 utf8mb4_unicode_ci，与 IM 表保持一致，避免 ERROR 1267
--     （Illegal mix of collations）。
--   - 测试群 ID 使用合法 UUID，便于通过当前群组接口（其参数校验要求 UUID）访问。
--   - 本脚本只负责数据库初始化，不负责刷新 Redis 会话摘要缓存；首次查询会话时由服务按
--     当前实现从消息表回查并重建缓存。
--
-- 执行前提：
--   1. 当前连接已经 USE 业务数据库；默认数据库名通常为 private_cloud_disk。
--   2. 已执行 PrivateCloudDisk-im/sql/init.sql 及会话简化迁移，使下列字段和唯一键存在：
--      pcd_im_friendship.uk_user_friend、pcd_im_conversation.uk_user_peer、
--      pcd_im_group.uk_group_id、pcd_im_group_member.uk_group_user。
--   3. 如果当前连接没有选择数据库，请先执行：USE private_cloud_disk;
--
-- 原有业务逻辑不变：本文件是新增测试数据脚本，不修改现有注释、表结构或生产业务代码。
-- ============================================================================

START TRANSACTION;

-- 可调参数：测试群最多加入多少个现有账户；少于该数量时自动使用全部账户。
SET @seed_group_member_limit := 20;

-- 固定 ID 使脚本可以安全重复执行；如需另一套独立测试数据，只需修改该 UUID。
-- 显式指定排序规则，避免连接默认 utf8mb4_0900_ai_ci 与 IM 表排序规则不一致。
SET @seed_group_id := CONVERT('9c7b4c32-0e51-4e43-9d7c-202608100001' USING utf8mb4)
                       COLLATE utf8mb4_unicode_ci;

-- 临时用户快照：把主业务用户表的 BINARY(16) UUID 转换为 IM 使用的字符串 UUID。
DROP TEMPORARY TABLE IF EXISTS tmp_im_seed_users;
CREATE TEMPORARY TABLE tmp_im_seed_users (
    row_no  INT NOT NULL PRIMARY KEY,
    user_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL UNIQUE
) ENGINE=InnoDB;

INSERT INTO tmp_im_seed_users (row_no, user_id)
SELECT
    ROW_NUMBER() OVER (ORDER BY u.user_id),
    LOWER(BIN_TO_UUID(u.user_id))
FROM pcd_user_info_table u
WHERE u.user_id IS NOT NULL;

-- 临时好友配对表：每个账户最多进入一组一对一测试关系。
DROP TEMPORARY TABLE IF EXISTS tmp_im_seed_pairs;
CREATE TEMPORARY TABLE tmp_im_seed_pairs (
    pair_no INT NOT NULL PRIMARY KEY,
    user_a  VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    user_b  VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    UNIQUE KEY uk_seed_pair (user_a, user_b)
) ENGINE=InnoDB;

INSERT INTO tmp_im_seed_pairs (pair_no, user_a, user_b)
SELECT
    candidate.pair_no,
    candidate.user_a,
    candidate.user_b
FROM (
    SELECT
        CEILING(row_no / 2) AS pair_no,
        user_id AS user_a,
        LEAD(user_id) OVER (ORDER BY row_no) AS user_b,
        row_no
    FROM tmp_im_seed_users
) candidate
WHERE MOD(candidate.row_no, 2) = 1
  AND candidate.user_b IS NOT NULL;

-- ============================================================================
-- 一、创建对称好友关系
-- 原有行为：好友关系要求 user_id -> friend_id 与 friend_id -> user_id 各有一条记录。
-- 新增行为：测试脚本对每个配对一次性补齐两个方向，并通过唯一键跳过已有关系。
-- ============================================================================
INSERT IGNORE INTO pcd_im_friendship
    (user_id, friend_id, status, created_at, updated_at)
SELECT user_a, user_b, 0, NOW(), NOW()
FROM tmp_im_seed_pairs
;

INSERT IGNORE INTO pcd_im_friendship
    (user_id, friend_id, status, created_at, updated_at)
SELECT user_b, user_a, 0, NOW(), NOW()
FROM tmp_im_seed_pairs;

-- ============================================================================
-- 二、创建单聊会话
-- 原有行为：双方分别拥有自己的会话元数据，session_id 是共享消息流 ID。
-- 新行为：每组配对写入两个方向的会话行，使用当前唯一键确保重复执行安全。
-- ============================================================================
INSERT IGNORE INTO pcd_im_conversation
    (session_id, session_type, user_id, peer_id, is_pinned, is_muted, created_at, updated_at)
SELECT
    CASE WHEN user_a <= user_b
         THEN CONCAT(user_a, '*', user_b)
         ELSE CONCAT(user_b, '*', user_a)
    END,
    1,
    user_a,
    user_b,
    0,
    0,
    NOW(),
    NOW()
FROM tmp_im_seed_pairs
;

INSERT IGNORE INTO pcd_im_conversation
    (session_id, session_type, user_id, peer_id, is_pinned, is_muted, created_at, updated_at)
SELECT
    CASE WHEN user_a <= user_b
         THEN CONCAT(user_a, '*', user_b)
         ELSE CONCAT(user_b, '*', user_a)
    END,
    1,
    user_b,
    user_a,
    0,
    0,
    NOW(),
    NOW()
FROM tmp_im_seed_pairs;

-- ============================================================================
-- 三、创建大型测试群
-- 群主取排序后的第一个现有账户；群成员取前 N 个现有账户，包含群主。
-- 若固定 group_id 已存在，则跳过群元数据插入，但仍会幂等补齐缺失成员和会话。
-- ============================================================================
INSERT IGNORE INTO pcd_im_group
    (group_id, group_name, avatar, owner_id, announcement, description,
     member_count, max_members, join_mode, is_all_muted, status, create_time, update_time)
SELECT
    @seed_group_id,
    'IM 大型测试群',
    NULL,
    u.user_id,
    '用于测试群聊消息、未读数、在线状态和历史消息加载。',
    '由 IM-TEST-SEED-20260810 初始化的测试群。',
    0,
    500,
    0,
    0,
    0,
    NOW(),
    NOW()
FROM tmp_im_seed_users u
WHERE u.row_no = 1;

-- 群主角色为 1；若有第二个成员则设置为管理员，其余为普通成员。
-- 已存在的成员由 uk_group_user 跳过，不修改已有角色或禁言状态。
INSERT IGNORE INTO pcd_im_group_member
    (group_id, user_id, role, alias, mute_until, last_read_seq, join_time, create_time)
SELECT
    @seed_group_id,
    u.user_id,
    CASE
        WHEN u.user_id = g.owner_id THEN 1
        WHEN u.row_no = 2 THEN 2
        ELSE 3
    END,
    NULL,
    NULL,
    0,
    NOW(),
    NOW()
FROM tmp_im_seed_users u
JOIN pcd_im_group g
  ON g.group_id = (CONVERT(@seed_group_id USING utf8mb4) COLLATE utf8mb4_unicode_ci)
WHERE u.row_no <= @seed_group_member_limit
   OR u.user_id COLLATE utf8mb4_unicode_ci = g.owner_id COLLATE utf8mb4_unicode_ci;

-- 以实际成员记录重新计算 member_count，避免重复执行后计数漂移。
UPDATE pcd_im_group g
LEFT JOIN (
    SELECT group_id, COUNT(*) AS actual_member_count
    FROM pcd_im_group_member
    WHERE group_id = (CONVERT(@seed_group_id USING utf8mb4) COLLATE utf8mb4_unicode_ci)
    GROUP BY group_id
) m ON m.group_id = g.group_id
SET g.member_count = COALESCE(m.actual_member_count, 0),
    g.update_time = NOW()
WHERE g.group_id = (CONVERT(@seed_group_id USING utf8mb4) COLLATE utf8mb4_unicode_ci);

-- ============================================================================
-- 四、创建群聊会话
-- 每个群成员对应一条个人会话元数据，所有成员共享 group*groupId 消息流。
-- ============================================================================
INSERT IGNORE INTO pcd_im_conversation
    (session_id, session_type, user_id, peer_id, is_pinned, is_muted, created_at, updated_at)
SELECT
    CONCAT('group*', gm.group_id),
    2,
    gm.user_id,
    gm.group_id,
    0,
    0,
    NOW(),
    NOW()
FROM pcd_im_group_member gm
WHERE gm.group_id = (CONVERT(@seed_group_id USING utf8mb4) COLLATE utf8mb4_unicode_ci);

-- 提交前输出本次测试数据的可核对结果；每个统计语句只读取一次临时表，避免 MySQL 1137。
SELECT COUNT(*)
INTO @seed_existing_user_count
FROM tmp_im_seed_users;

SELECT COUNT(*)
INTO @seed_pair_count
FROM tmp_im_seed_pairs;

SELECT COUNT(*)
INTO @seed_friendship_row_count
FROM pcd_im_friendship f
JOIN tmp_im_seed_pairs p
  ON (f.user_id COLLATE utf8mb4_unicode_ci = p.user_a COLLATE utf8mb4_unicode_ci
      AND f.friend_id COLLATE utf8mb4_unicode_ci = p.user_b COLLATE utf8mb4_unicode_ci)
  OR (f.user_id COLLATE utf8mb4_unicode_ci = p.user_b COLLATE utf8mb4_unicode_ci
      AND f.friend_id COLLATE utf8mb4_unicode_ci = p.user_a COLLATE utf8mb4_unicode_ci);

SELECT COUNT(*)
INTO @seed_single_conversation_row_count
FROM pcd_im_conversation c
JOIN tmp_im_seed_pairs p
  ON c.session_type = 1
 AND ((c.user_id COLLATE utf8mb4_unicode_ci = p.user_a COLLATE utf8mb4_unicode_ci
       AND c.peer_id COLLATE utf8mb4_unicode_ci = p.user_b COLLATE utf8mb4_unicode_ci)
      OR (c.user_id COLLATE utf8mb4_unicode_ci = p.user_b COLLATE utf8mb4_unicode_ci
       AND c.peer_id COLLATE utf8mb4_unicode_ci = p.user_a COLLATE utf8mb4_unicode_ci));

SELECT COUNT(*)
INTO @seed_group_member_count
FROM pcd_im_group_member
WHERE group_id = (CONVERT(@seed_group_id USING utf8mb4) COLLATE utf8mb4_unicode_ci);

SELECT COUNT(*)
INTO @seed_group_conversation_row_count
FROM pcd_im_conversation
WHERE session_type = 2
  AND peer_id = (CONVERT(@seed_group_id USING utf8mb4) COLLATE utf8mb4_unicode_ci);

SELECT
    @seed_existing_user_count AS existing_user_count,
    @seed_pair_count AS one_to_one_pair_count,
    @seed_friendship_row_count AS friendship_row_count,
    @seed_single_conversation_row_count AS single_conversation_row_count,
    @seed_group_member_count AS test_group_member_count,
    @seed_group_conversation_row_count AS group_conversation_row_count,
    @seed_group_id AS test_group_id;

COMMIT;

DROP TEMPORARY TABLE IF EXISTS tmp_im_seed_pairs;
DROP TEMPORARY TABLE IF EXISTS tmp_im_seed_users;
