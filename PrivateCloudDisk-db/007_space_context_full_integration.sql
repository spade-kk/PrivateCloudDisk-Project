-- =====================================================================
-- PrivateCloudDisk 空间管理能力全量集成迁移
-- 需求关联：空间管理能力全量集成（二、三、五、六、七）
--
-- 变更原则：
--   1. 保留原 file_id/node_id 以及所有接口契约，只增加 space_id 定位维度。
--   2. 历史数据统一回填到资源所有者的 personal 空间。
--   3. 关联表记录空间快照，查询时不再依赖跨表猜测资源空间。
--   4. 不改变全局 UUID 主键；当前 file_id/node_id 本身全局唯一，
--      (space_id, resource_id) 是授权定位键，不需要允许跨空间复用同一 UUID。
--
-- 执行前置：
--   先确认 003_share_resource_migration.sql、005_preview_resource_persistence.sql、
--   006_tag_color_and_preview_resource_cleanup.sql 已执行，并完成数据库备份。
--   本脚本不创建上述业务表，只对既有表做幂等扩展与历史数据回填。
-- =====================================================================

USE private_cloud_disk;

DELIMITER $$

DROP PROCEDURE IF EXISTS pcd_add_column_if_missing$$
CREATE PROCEDURE pcd_add_column_if_missing(
    IN p_table_name VARCHAR(128),
    IN p_column_name VARCHAR(128),
    IN p_column_ddl TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND COLUMN_NAME = p_column_name
    ) THEN
        SET @pcd_ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN ', p_column_ddl);
        PREPARE pcd_stmt FROM @pcd_ddl;
        EXECUTE pcd_stmt;
        DEALLOCATE PREPARE pcd_stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS pcd_add_index_if_missing$$
CREATE PROCEDURE pcd_add_index_if_missing(
    IN p_table_name VARCHAR(128),
    IN p_index_name VARCHAR(128),
    IN p_index_ddl TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND INDEX_NAME = p_index_name
    ) THEN
        SET @pcd_ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD ', p_index_ddl);
        PREPARE pcd_stmt FROM @pcd_ddl;
        EXECUTE pcd_stmt;
        DEALLOCATE PREPARE pcd_stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS pcd_drop_index_if_exists$$
CREATE PROCEDURE pcd_drop_index_if_exists(
    IN p_table_name VARCHAR(128),
    IN p_index_name VARCHAR(128)
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND INDEX_NAME = p_index_name
    ) THEN
        SET @pcd_ddl = CONCAT(
            'ALTER TABLE `', p_table_name, '` DROP INDEX `', p_index_name, '`'
        );
        PREPARE pcd_stmt FROM @pcd_ddl;
        EXECUTE pcd_stmt;
        DEALLOCATE PREPARE pcd_stmt;
    END IF;
END$$

DELIMITER ;

-- 需求六：核心资源与目录闭包表空间字段。
CALL pcd_add_column_if_missing('pcd_file_info_table', 'file_space_id',
    'file_space_id BINARY(16) NULL COMMENT ''所属空间ID；历史个人空间数据迁移前可为空''');
CALL pcd_add_column_if_missing('pcd_directory_tree_table', 'node_space_id',
    'node_space_id BINARY(16) NULL COMMENT ''所属空间ID；历史个人空间数据迁移前可为空''');
CALL pcd_add_column_if_missing('pcd_directory_closure_table', 'closure_space_id',
    'closure_space_id BINARY(16) NULL COMMENT ''目录闭包所属空间ID''');
CALL pcd_add_column_if_missing('pcd_uploads_session_table', 'uploads_space_id',
    'uploads_space_id BINARY(16) NULL COMMENT ''上传目标空间ID''');

-- 需求五：关联业务表空间快照字段。
CALL pcd_add_column_if_missing('pcd_file_star_table', 'star_space_id',
    'star_space_id BINARY(16) NULL COMMENT ''收藏所属空间ID''');
CALL pcd_add_column_if_missing('pcd_tag_table', 'tag_space_id',
    'tag_space_id BINARY(16) NULL COMMENT ''标签所属空间ID''');
CALL pcd_add_column_if_missing('pcd_file_tag_table', 'ft_space_id',
    'ft_space_id BINARY(16) NULL COMMENT ''标签关联所属空间ID''');
CALL pcd_add_column_if_missing('pcd_trash_target_table', 'trash_space_id',
    'trash_space_id BINARY(16) NULL COMMENT ''回收站记录所属空间ID''');
CALL pcd_add_column_if_missing('pcd_share_link_table', 'share_space_id',
    'share_space_id BINARY(16) NULL COMMENT ''分享来源空间ID''');
CALL pcd_add_column_if_missing('pcd_share_resource_table', 'space_id',
    'space_id BINARY(16) NULL COMMENT ''分享资源所属空间ID''');
CALL pcd_add_column_if_missing('pcd_recent_access_table', 'ra_space_id',
    'ra_space_id BINARY(16) NULL COMMENT ''访问发生的空间ID''');
CALL pcd_add_column_if_missing('pcd_preview_resource_table', 'space_id',
    'space_id BINARY(16) NULL COMMENT ''预览资源所属空间ID''');
CALL pcd_add_column_if_missing('pcd_video_watch_progress_table', 'space_id',
    'space_id BINARY(16) NULL COMMENT ''观看记录所属空间ID''');

-- 需求五-10：空间上传预占额度，避免只按用户配额造成共享空间超卖。
CALL pcd_add_column_if_missing('pcd_space_table', 'space_reserved',
    'space_reserved BIGINT NOT NULL DEFAULT 0 COMMENT ''上传中预占容量（字节）''');

-- 为历史用户补建唯一 personal 空间。默认空间名称统一使用“我的网盘”，
-- 如同一所有者已有同名其他空间则使用“我的网盘（个人）”避免唯一键冲突。
INSERT INTO pcd_space_table (
    space_id, space_name, space_type, space_owner_id,
    space_quota, space_used, space_file_count,
    space_visibility, space_status
)
SELECT
    UUID_TO_BIN(UUID()),
    CASE
        WHEN EXISTS (
            SELECT 1 FROM pcd_space_table name_check
            WHERE name_check.space_owner_id = u.user_id
              AND name_check.space_name = '我的网盘'
        ) THEN '我的网盘（个人）'
        ELSE '我的网盘'
    END,
    'personal',
    u.user_id,
    COALESCE(q.quota_total_capacity, 10737418240),
    COALESCE(q.quota_used_capacity, 0),
    COALESCE(q.quota_file_count, 0),
    'private',
    'active'
FROM pcd_user_info_table u
LEFT JOIN pcd_user_quota_table q ON q.quota_user_id = u.user_id
WHERE NOT EXISTS (
    SELECT 1
    FROM pcd_space_table s
    WHERE s.space_owner_id = u.user_id
      AND s.space_type = 'personal'
      AND s.space_status != 'deleted'
);

-- personal 空间所有者成员与权限补全。
INSERT IGNORE INTO pcd_space_member_table (space_id, user_id, role)
SELECT s.space_id, s.space_owner_id, 'owner'
FROM pcd_space_table s
WHERE s.space_type = 'personal'
  AND s.space_status = 'active';

INSERT INTO pcd_space_permission_table (
    space_id, user_id, target_node_id,
    can_read, can_write, can_delete, can_share, can_invite, can_manage, granted_by
)
SELECT
    s.space_id, s.space_owner_id, NULL,
    1, 1, 1, 1, 1, 1, s.space_owner_id
FROM pcd_space_table s
WHERE s.space_type = 'personal'
  AND s.space_status = 'active'
  AND NOT EXISTS (
      SELECT 1
      FROM pcd_space_permission_table p
      WHERE p.space_id = s.space_id
        AND p.user_id = s.space_owner_id
        AND p.target_node_id IS NULL
  );

-- 核心资源按原所属用户回填 personal 空间。
UPDATE pcd_directory_tree_table d
JOIN pcd_space_table s
  ON s.space_owner_id = d.node_user_id
 AND s.space_type = 'personal'
 AND s.space_status = 'active'
SET d.node_space_id = s.space_id
WHERE d.node_space_id IS NULL;

UPDATE pcd_file_info_table f
JOIN pcd_space_table s
  ON s.space_owner_id = f.file_author_id
 AND s.space_type = 'personal'
 AND s.space_status = 'active'
SET f.file_space_id = s.space_id
WHERE f.file_space_id IS NULL;

UPDATE pcd_uploads_session_table u
JOIN pcd_space_table s
  ON s.space_owner_id = u.uploads_user_id
 AND s.space_type = 'personal'
 AND s.space_status = 'active'
SET u.uploads_space_id = s.space_id
WHERE u.uploads_space_id IS NULL;

-- 历史空间基础 CRUD 曾只创建空间/成员，未创建根目录；为每个缺失根节点的有效空间补齐。
INSERT INTO pcd_directory_tree_table (
    node_id, node_user_id, node_parent_id, node_name,
    node_create_time, node_status, node_space_id
)
SELECT
    UUID_TO_BIN(UUID()), s.space_owner_id, NULL, s.space_name,
    CURRENT_TIMESTAMP, 'active', s.space_id
FROM pcd_space_table s
WHERE s.space_status = 'active'
  AND NOT EXISTS (
      SELECT 1
      FROM pcd_directory_tree_table root_node
      WHERE root_node.node_space_id = s.space_id
        AND root_node.node_parent_id IS NULL
        AND root_node.node_status NOT IN ('deleted')
  );

UPDATE pcd_directory_closure_table c
JOIN pcd_directory_tree_table d ON d.node_id = c.descendant_id
SET c.closure_space_id = d.node_space_id
WHERE c.closure_space_id IS NULL;

INSERT IGNORE INTO pcd_directory_closure_table (
    user_id, ancestor_id, descendant_id, depth, closure_space_id
)
SELECT d.node_user_id, d.node_id, d.node_id, 0, d.node_space_id
FROM pcd_directory_tree_table d
WHERE d.node_parent_id IS NULL
  AND d.node_space_id IS NOT NULL;

-- 关联表优先从目标资源回填，无法解析时回退到记录用户的 personal 空间。
UPDATE pcd_file_star_table st
LEFT JOIN pcd_file_info_table f ON f.file_id = st.star_file_id
LEFT JOIN pcd_directory_tree_table d ON d.node_id = st.star_node_id
LEFT JOIN pcd_space_table s
  ON s.space_owner_id = st.star_user_id
 AND s.space_type = 'personal'
 AND s.space_status = 'active'
SET st.star_space_id = COALESCE(f.file_space_id, d.node_space_id, s.space_id)
WHERE st.star_space_id IS NULL;

UPDATE pcd_tag_table t
JOIN pcd_space_table s
  ON s.space_owner_id = t.tag_user_id
 AND s.space_type = 'personal'
 AND s.space_status = 'active'
SET t.tag_space_id = s.space_id
WHERE t.tag_space_id IS NULL;

UPDATE pcd_file_tag_table ft
LEFT JOIN pcd_file_info_table f ON f.file_id = ft.ft_file_id
LEFT JOIN pcd_directory_tree_table d ON d.node_id = ft.ft_node_id
LEFT JOIN pcd_tag_table t ON t.tag_id = ft.ft_tag_id
SET ft.ft_space_id = COALESCE(f.file_space_id, d.node_space_id, t.tag_space_id)
WHERE ft.ft_space_id IS NULL;

UPDATE pcd_trash_target_table tr
LEFT JOIN pcd_file_info_table f
  ON tr.trash_target_type = 'file' AND f.file_id = tr.trash_target_id
LEFT JOIN pcd_directory_tree_table d
  ON tr.trash_target_type = 'folder' AND d.node_id = tr.trash_target_id
LEFT JOIN pcd_space_table s
  ON s.space_owner_id = tr.trash_user_id
 AND s.space_type = 'personal'
 AND s.space_status = 'active'
SET tr.trash_space_id = COALESCE(f.file_space_id, d.node_space_id, s.space_id)
WHERE tr.trash_space_id IS NULL;

UPDATE pcd_share_resource_table sr
LEFT JOIN pcd_file_info_table f ON f.file_id = sr.file_id
LEFT JOIN pcd_directory_tree_table d ON d.node_id = sr.node_id
SET sr.space_id = COALESCE(f.file_space_id, d.node_space_id)
WHERE sr.space_id IS NULL;

UPDATE pcd_share_link_table sl
LEFT JOIN (
    SELECT share_id, MIN(space_id) AS space_id
    FROM pcd_share_resource_table
    GROUP BY share_id
) sr ON sr.share_id = sl.share_id
LEFT JOIN pcd_space_table s
  ON s.space_owner_id = sl.share_owner_id
 AND s.space_type = 'personal'
 AND s.space_status = 'active'
SET sl.share_space_id = COALESCE(sr.space_id, s.space_id)
WHERE sl.share_space_id IS NULL;

UPDATE pcd_recent_access_table ra
LEFT JOIN pcd_file_info_table f ON f.file_id = ra.ra_file_id
LEFT JOIN pcd_directory_tree_table d ON d.node_id = ra.ra_node_id
LEFT JOIN pcd_space_table s
  ON s.space_owner_id = ra.ra_user_id
 AND s.space_type = 'personal'
 AND s.space_status = 'active'
SET ra.ra_space_id = COALESCE(f.file_space_id, d.node_space_id, s.space_id)
WHERE ra.ra_space_id IS NULL;

UPDATE pcd_preview_resource_table pr
LEFT JOIN pcd_file_info_table f ON f.file_id = pr.file_id
SET pr.space_id = f.file_space_id
WHERE pr.space_id IS NULL;

UPDATE pcd_video_watch_progress_table vp
LEFT JOIN pcd_file_info_table f ON f.file_id = vp.file_id
SET vp.space_id = f.file_space_id
WHERE vp.space_id IS NULL;

-- 原唯一键只含用户与资源，会阻止同一用户在不同空间使用同名标签/独立收藏语义。
-- 新唯一键增加空间维度；资源 UUID 仍保持全局唯一，不改变既有主键。
CALL pcd_drop_index_if_exists('pcd_file_star_table', 'uk_user_file_star');
CALL pcd_drop_index_if_exists('pcd_file_star_table', 'uk_user_folder_star');
CALL pcd_drop_index_if_exists('pcd_tag_table', 'uk_user_tag');
CALL pcd_add_index_if_missing('pcd_file_star_table', 'uk_user_file_star',
    'UNIQUE INDEX uk_user_file_star (star_user_id, star_space_id, star_file_id)');
CALL pcd_add_index_if_missing('pcd_file_star_table', 'uk_user_folder_star',
    'UNIQUE INDEX uk_user_folder_star (star_user_id, star_space_id, star_node_id)');
CALL pcd_add_index_if_missing('pcd_tag_table', 'uk_user_tag',
    'UNIQUE INDEX uk_user_tag (tag_user_id, tag_space_id, tag_name)');

-- 查询与关联操作索引。索引名称固定，便于运维核查和幂等执行。
CALL pcd_add_index_if_missing('pcd_file_info_table', 'idx_file_space_node_status',
    'INDEX idx_file_space_node_status (file_space_id, file_node_id, file_status)');
CALL pcd_add_index_if_missing('pcd_directory_tree_table', 'idx_node_space_parent_status',
    'INDEX idx_node_space_parent_status (node_space_id, node_parent_id, node_status)');
CALL pcd_add_index_if_missing('pcd_directory_closure_table', 'idx_closure_space_ancestor',
    'INDEX idx_closure_space_ancestor (closure_space_id, ancestor_id, descendant_id)');
CALL pcd_add_index_if_missing('pcd_uploads_session_table', 'idx_upload_space_status',
    'INDEX idx_upload_space_status (uploads_space_id, uploads_status)');
CALL pcd_add_index_if_missing('pcd_file_star_table', 'idx_star_space_user',
    'INDEX idx_star_space_user (star_space_id, star_user_id, star_starred_at)');
CALL pcd_add_index_if_missing('pcd_tag_table', 'idx_tag_space_user',
    'INDEX idx_tag_space_user (tag_space_id, tag_user_id)');
CALL pcd_add_index_if_missing('pcd_file_tag_table', 'idx_file_tag_space',
    'INDEX idx_file_tag_space (ft_space_id, ft_user_id, ft_tag_id)');
CALL pcd_add_index_if_missing('pcd_trash_target_table', 'idx_trash_space_user',
    'INDEX idx_trash_space_user (trash_space_id, trash_user_id, trash_deleted_at)');
CALL pcd_add_index_if_missing('pcd_share_link_table', 'idx_share_space_owner',
    'INDEX idx_share_space_owner (share_space_id, share_owner_id, share_status)');
CALL pcd_add_index_if_missing('pcd_share_resource_table', 'idx_share_resource_space',
    'INDEX idx_share_resource_space (space_id, share_id)');
CALL pcd_add_index_if_missing('pcd_recent_access_table', 'idx_recent_space_user',
    'INDEX idx_recent_space_user (ra_space_id, ra_user_id, ra_accessed_at)');
CALL pcd_add_index_if_missing('pcd_preview_resource_table', 'idx_preview_space_file',
    'INDEX idx_preview_space_file (space_id, file_id, resource_status)');
CALL pcd_add_index_if_missing('pcd_video_watch_progress_table', 'idx_video_progress_space',
    'INDEX idx_video_progress_space (space_id, user_id, last_watched_at)');

DROP PROCEDURE IF EXISTS pcd_add_column_if_missing;
DROP PROCEDURE IF EXISTS pcd_add_index_if_missing;
DROP PROCEDURE IF EXISTS pcd_drop_index_if_exists;

-- 迁移后核查建议：
-- SELECT COUNT(*) FROM pcd_file_info_table WHERE file_space_id IS NULL;
-- SELECT COUNT(*) FROM pcd_directory_tree_table WHERE node_space_id IS NULL;
-- SELECT space_id, space_name, space_used, space_file_count FROM pcd_space_table;
