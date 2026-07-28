-- =====================================================================
-- 004_share_description.sql — 分享公开说明字段
-- =====================================================================
-- AUDIT FIX [UX 2.1 / 6.3]: 为公开分享页提供可选的富文本说明区域。
-- 展示端必须使用严格 HTML 白名单净化；数据库只负责长度可控的持久化。

ALTER TABLE pcd_share_link_table
    ADD COLUMN share_description TEXT NULL
        COMMENT '分享说明（支持受限富文本）'
        AFTER share_name;
