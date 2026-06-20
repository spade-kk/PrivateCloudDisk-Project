-- ============================================================
-- PrivateCloudDisk AI Service - 数据库表结构
-- 在 private_cloud_disk 数据库中创建
-- 与 platform-service 共用数据库
-- ============================================================

USE private_cloud_disk;

-- ============================================================
-- AI 标签表 (pcd_ai_tags)
-- 存储所有 AI 分析产生的标签结果
-- ============================================================
CREATE TABLE IF NOT EXISTS pcd_ai_tags (
    id                  BIGINT          NOT NULL PRIMARY KEY AUTO_INCREMENT,
    file_id             BINARY(16)      NOT NULL                    COMMENT '文件 ID',
    user_id             BINARY(16)      NOT NULL                    COMMENT '用户 ID',
    tenant_id           VARCHAR(64)     DEFAULT ''                  COMMENT '租户 ID',
    tag_type            VARCHAR(50)     NOT NULL                    COMMENT '标签类型: image_classification|object_detection|face|nsfw|ocr',
    tag_name            VARCHAR(255)    NOT NULL                    COMMENT '标签名称',
    tag_label_zh        VARCHAR(255)    DEFAULT ''                  COMMENT '标签中文名称',
    confidence          FLOAT           DEFAULT 0.0                 COMMENT '置信度 (0.0 ~ 1.0)',
    bounding_box        JSON            DEFAULT NULL                COMMENT '边界框 {x, y, w, h} (物体检测/人脸)',
    metadata            JSON            DEFAULT NULL                COMMENT '扩展元数据',
    model_name          VARCHAR(100)    DEFAULT ''                  COMMENT '使用的模型名称',
    model_version       VARCHAR(50)     DEFAULT ''                  COMMENT '模型版本',
    processing_time_ms  INT             DEFAULT 0                   COMMENT '处理耗时 (毫秒)',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_file_id (file_id),
    INDEX idx_user_id (user_id),
    INDEX idx_tag_type (tag_type),
    INDEX idx_tag_name (tag_name),
    INDEX idx_file_tag (file_id, tag_type),
    INDEX idx_user_tag (user_id, tag_type)
) COMMENT='AI 标签表';

-- ============================================================
-- 人脸聚类表 (pcd_ai_face_clusters)
-- 存储人脸聚类结果
-- ============================================================
CREATE TABLE IF NOT EXISTS pcd_ai_face_clusters (
    id                      BIGINT          NOT NULL PRIMARY KEY AUTO_INCREMENT,
    cluster_id              VARCHAR(64)     NOT NULL                COMMENT '聚类 ID (UUID)',
    user_id                 BINARY(16)      NOT NULL                COMMENT '用户 ID',
    tenant_id               VARCHAR(64)     DEFAULT ''              COMMENT '租户 ID',
    cluster_label           VARCHAR(255)    DEFAULT ''              COMMENT '聚类标签 (用户可自定义)',
    representative_file_id  BINARY(16)      DEFAULT NULL            COMMENT '代表图片文件 ID',
    representative_encoding BLOB            DEFAULT NULL            COMMENT '代表人脸编码 (128维 float32)',
    face_count              INT             DEFAULT 0               COMMENT '聚类中的人脸数量',
    file_count              INT             DEFAULT 0               COMMENT '聚类中的文件数量',
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_cluster_id (cluster_id),
    INDEX idx_user_id (user_id),
    INDEX idx_user_label (user_id, cluster_label)
) COMMENT='人脸聚类表';

-- ============================================================
-- 人脸文件关联表 (pcd_ai_face_files)
-- 记录每张图片中检测到的人脸与聚类的关联
-- ============================================================
CREATE TABLE IF NOT EXISTS pcd_ai_face_files (
    id              BIGINT          NOT NULL PRIMARY KEY AUTO_INCREMENT,
    file_id         BINARY(16)      NOT NULL                    COMMENT '文件 ID',
    user_id         BINARY(16)      NOT NULL                    COMMENT '用户 ID',
    cluster_id      VARCHAR(64)     DEFAULT NULL                COMMENT '所属聚类 ID',
    face_index      INT             DEFAULT 0                   COMMENT '图片中第几个人脸 (0-based)',
    face_encoding   BLOB            DEFAULT NULL                COMMENT '人脸编码 (128维 float32)',
    face_bbox       JSON            DEFAULT NULL                COMMENT '人脸边界框 {x, y, w, h}',
    face_landmarks  JSON            DEFAULT NULL                COMMENT '人脸关键点',
    confidence      FLOAT           DEFAULT 0.0                 COMMENT '检测置信度',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_file_id (file_id),
    INDEX idx_cluster_id (cluster_id),
    INDEX idx_user_id (user_id)
) COMMENT='人脸文件关联表';

-- ============================================================
-- OCR 结果表 (pcd_ai_ocr_results)
-- 存储增强 OCR 的文字识别结果
-- ============================================================
CREATE TABLE IF NOT EXISTS pcd_ai_ocr_results (
    id              BIGINT          NOT NULL PRIMARY KEY AUTO_INCREMENT,
    file_id         BINARY(16)      NOT NULL                    COMMENT '文件 ID',
    user_id         BINARY(16)      NOT NULL                    COMMENT '用户 ID',
    tenant_id       VARCHAR(64)     DEFAULT ''                  COMMENT '租户 ID',
    ocr_text        MEDIUMTEXT      DEFAULT NULL                COMMENT 'OCR 识别全文',
    language        VARCHAR(20)     DEFAULT 'unknown'           COMMENT '识别语言',
    confidence      FLOAT           DEFAULT 0.0                 COMMENT '平均置信度',
    pages           INT             DEFAULT 1                   COMMENT '页数',
    engine          VARCHAR(50)     DEFAULT 'paddleocr'         COMMENT '引擎: paddleocr|tesseract',
    model_version   VARCHAR(50)     DEFAULT ''                  COMMENT '模型版本',
    processing_time_ms INT          DEFAULT 0                   COMMENT '处理耗时',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_file_id (file_id),
    INDEX idx_user_id (user_id)
) COMMENT='OCR 识别结果表';

-- ============================================================
-- AI 文件摘要表 (pcd_ai_summaries)
-- 存储 AI 生成的文件摘要
-- ============================================================
CREATE TABLE IF NOT EXISTS pcd_ai_summaries (
    id              BIGINT          NOT NULL PRIMARY KEY AUTO_INCREMENT,
    file_id         BINARY(16)      NOT NULL                    COMMENT '文件 ID',
    user_id         BINARY(16)      NOT NULL                    COMMENT '用户 ID',
    tenant_id       VARCHAR(64)     DEFAULT ''                  COMMENT '租户 ID',
    summary         TEXT            DEFAULT NULL                COMMENT 'AI 摘要 (中文)',
    summary_en      TEXT            DEFAULT NULL                COMMENT 'AI 摘要 (英文)',
    keywords        JSON            DEFAULT NULL                COMMENT '关键词列表',
    category        VARCHAR(100)    DEFAULT ''                  COMMENT '文档分类',
    reading_time_min INT            DEFAULT 0                   COMMENT '预估阅读时间 (分钟)',
    model_name      VARCHAR(100)    DEFAULT ''                  COMMENT '模型名称',
    processing_time_ms INT          DEFAULT 0                   COMMENT '处理耗时',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_file_id (file_id),
    INDEX idx_user_id (user_id),
    INDEX idx_category (category)
) COMMENT='AI 文件摘要表';

-- ============================================================
-- 文件推荐表 (pcd_ai_recommendations)
-- 存储个性化文件推荐结果
-- ============================================================
CREATE TABLE IF NOT EXISTS pcd_ai_recommendations (
    id              BIGINT          NOT NULL PRIMARY KEY AUTO_INCREMENT,
    user_id         BINARY(16)      NOT NULL                    COMMENT '用户 ID',
    file_id         BINARY(16)      NOT NULL                    COMMENT '推荐文件 ID',
    score           FLOAT           DEFAULT 0.0                 COMMENT '推荐分数',
    reason          VARCHAR(255)    DEFAULT ''                  COMMENT '推荐理由',
    reason_type     VARCHAR(50)     DEFAULT ''                  COMMENT '理由类型: similar_content|collaborative|recent_trend|tag_match',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_user_score (user_id, score DESC),
    INDEX idx_user_file (user_id, file_id),
    INDEX idx_reason_type (reason_type)
) COMMENT='文件推荐表';

-- ============================================================
-- 用户行为日志表 (pcd_ai_user_behaviors)
-- 用于推荐系统的用户行为记录
-- 由 platform-service 写入，AI Service 只读
-- ============================================================
CREATE TABLE IF NOT EXISTS pcd_ai_user_behaviors (
    id              BIGINT          NOT NULL PRIMARY KEY AUTO_INCREMENT,
    user_id         BINARY(16)      NOT NULL                    COMMENT '用户 ID',
    file_id         BINARY(16)      NOT NULL                    COMMENT '文件 ID',
    behavior_type   VARCHAR(50)     NOT NULL                    COMMENT '行为类型: view|download|share|edit|delete|favorite|upload',
    behavior_weight FLOAT           DEFAULT 1.0                 COMMENT '行为权重',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_user_time (user_id, created_at DESC),
    INDEX idx_file_time (file_id, created_at DESC),
    INDEX idx_user_behavior (user_id, behavior_type, created_at DESC)
) COMMENT='用户行为日志表';

-- ============================================================
-- AI 任务执行日志表 (pcd_ai_task_logs)
-- 记录每次 AI 任务的执行情况
-- ============================================================
CREATE TABLE IF NOT EXISTS pcd_ai_task_logs (
    id              BIGINT          NOT NULL PRIMARY KEY AUTO_INCREMENT,
    task_id         VARCHAR(64)     NOT NULL                    COMMENT '任务 ID (message_id)',
    file_id         BINARY(16)      NOT NULL                    COMMENT '文件 ID',
    user_id         BINARY(16)      NOT NULL                    COMMENT '用户 ID',
    task_type       VARCHAR(50)     NOT NULL                    COMMENT '任务类型',
    status          VARCHAR(30)     NOT NULL DEFAULT 'pending'  COMMENT '状态: pending|processing|completed|failed|skipped',
    error_message   TEXT            DEFAULT NULL                COMMENT '错误信息',
    processing_time_ms INT          DEFAULT 0                   COMMENT '处理耗时',
    retry_count     INT             DEFAULT 0                   COMMENT '重试次数',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_task_id (task_id),
    INDEX idx_file_id (file_id),
    INDEX idx_status (status),
    INDEX idx_created (created_at DESC)
) COMMENT='AI 任务执行日志表';