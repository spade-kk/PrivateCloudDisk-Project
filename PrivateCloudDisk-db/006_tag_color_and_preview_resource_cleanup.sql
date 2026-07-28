-- ============================================================
-- 006 标签自定义颜色兼容与已移除预览类型清理
-- 关联需求：十二（标签自定义颜色）、五（移除 Markdown 后端增强）
-- 影响范围：标签颜色列约束、旧 markdown/code 派生资源元数据。
-- 原有数据库已使用 VARCHAR(7)，本迁移用于兼容旧环境并清理不合法历史颜色。
-- ============================================================

UPDATE pcd_tag_table
SET tag_color = '#3B82F6'
WHERE tag_color IS NULL
   OR tag_color NOT REGEXP '^#[0-9A-Fa-f]{6}$';

ALTER TABLE pcd_tag_table
    MODIFY COLUMN tag_color VARCHAR(7) NOT NULL DEFAULT '#3B82F6'
        COMMENT '标签自定义颜色（六位HEX）';

-- Markdown 与代码文件现改为通过 Preview Token 临时读取原始内容，数据库不再保留其派生资源记录。
DELETE FROM pcd_preview_resource_table
WHERE resource_type IN ('markdown', 'markdown_html', 'code');
