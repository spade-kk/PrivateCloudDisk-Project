-- AUDIT FIX [7.4]: 将预览资源、播放进度和死信处置从临时缓存提升为可审计的持久化业务数据。
-- 本迁移采用 UUID_TO_BIN/BIN_TO_UUID 与现有 BINARY(16) 主键规范保持一致。

CREATE TABLE IF NOT EXISTS pcd_preview_resource_table (
    resource_id       BINARY(16)   NOT NULL PRIMARY KEY COMMENT '预览资源ID',
    file_id           BINARY(16)   NOT NULL COMMENT '源文件ID',
    user_id           BINARY(16)   NOT NULL COMMENT '资源所属用户ID',
    space_id          BINARY(16)   DEFAULT NULL COMMENT '预览资源所属空间ID',
    resource_type     VARCHAR(32)  NOT NULL COMMENT '派生资源类型：hls/video_preview/thumbnail/office_pdf/office_thumbnail/archive等',
    resource_variant  VARCHAR(32)  NOT NULL DEFAULT 'default' COMMENT '资源变体：poster/small/medium/large/master等',
    storage_backend   VARCHAR(24)  NOT NULL DEFAULT 'localstorage' COMMENT '存储后端',
    storage_path      VARCHAR(1024) NOT NULL COMMENT '资源文件或目录路径',
    mime_type         VARCHAR(128) DEFAULT NULL COMMENT '资源MIME类型',
    resource_status   ENUM('pending','processing','ready','failed','deleting','deleted')
                                     NOT NULL DEFAULT 'pending' COMMENT '资源生命周期状态',
    size_bytes        BIGINT        NOT NULL DEFAULT 0 COMMENT '资源大小',
    checksum          VARCHAR(128)  DEFAULT NULL COMMENT '资源校验值',
    width             INT          DEFAULT NULL COMMENT '图像或视频宽度',
    height            INT          DEFAULT NULL COMMENT '图像或视频高度',
    duration_seconds  DECIMAL(12,3) DEFAULT NULL COMMENT '媒体时长',
    page_count        INT          DEFAULT NULL COMMENT '文档页数',
    metadata_json     JSON         DEFAULT NULL COMMENT '类型专属扩展属性',
    error_message     VARCHAR(1000) DEFAULT NULL COMMENT '失败原因摘要',
    source_version    BIGINT       NOT NULL DEFAULT 1 COMMENT '源文件版本，用于缓存失效',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                     ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    ready_at          DATETIME(3)  DEFAULT NULL COMMENT '就绪时间',
    deleted_at        DATETIME(3)  DEFAULT NULL COMMENT '删除时间',
    UNIQUE KEY uk_preview_file_type_variant (file_id, resource_type, resource_variant),
    INDEX idx_preview_user_status (user_id, resource_status, updated_at),
    INDEX idx_preview_file_status (file_id, resource_status),
    INDEX idx_preview_space_file (space_id, file_id, resource_status),
    INDEX idx_preview_type_status (resource_type, resource_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件预览媒体资源元数据表';

CREATE TABLE IF NOT EXISTS pcd_video_watch_progress_table (
    user_id              BINARY(16)    NOT NULL COMMENT '用户ID',
    file_id              BINARY(16)    NOT NULL COMMENT '视频文件ID',
    space_id             BINARY(16)    DEFAULT NULL COMMENT '观看记录所属空间ID',
    file_name            VARCHAR(512)  NOT NULL DEFAULT '' COMMENT '视频文件名快照',
    current_time_seconds DECIMAL(12,3) NOT NULL DEFAULT 0 COMMENT '最后播放位置',
    duration_seconds     DECIMAL(12,3) NOT NULL DEFAULT 0 COMMENT '视频总时长',
    resolution           VARCHAR(24)   NOT NULL DEFAULT 'auto' COMMENT '最后选择的清晰度',
    playback_rate        DECIMAL(4,2)  NOT NULL DEFAULT 1 COMMENT '播放速度',
    completed            TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否已播放完成',
    created_at           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '首次观看时间',
    updated_at           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                       ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '进度更新时间',
    last_watched_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近观看时间',
    PRIMARY KEY (user_id, file_id),
    INDEX idx_video_history (user_id, last_watched_at DESC),
    INDEX idx_video_progress_space (space_id, user_id, last_watched_at),
    INDEX idx_video_file (file_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频播放进度与观看历史表';

CREATE TABLE IF NOT EXISTS pcd_mq_dead_letter_record_table (
    record_id        BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '死信处置记录ID',
    event_id         VARCHAR(64)   DEFAULT NULL COMMENT '事件ID',
    task_id          VARCHAR(64)   DEFAULT NULL COMMENT '任务ID',
    file_id          BINARY(16)    DEFAULT NULL COMMENT '关联文件ID',
    user_id          BINARY(16)    DEFAULT NULL COMMENT '关联用户ID',
    source_queue     VARCHAR(128)  NOT NULL COMMENT '死信来源队列或流水线',
    process_stage    VARCHAR(64)   NOT NULL COMMENT '失败阶段',
    failure_reason   VARCHAR(128)  NOT NULL COMMENT '标准失败原因',
    process_status   ENUM('open','retrying','resolved','discarded')
                                  NOT NULL DEFAULT 'open' COMMENT '处置状态',
    retry_count      INT           NOT NULL DEFAULT 0 COMMENT '累计重试次数',
    payload_json     JSON          NOT NULL COMMENT '原始消息快照',
    resolution_note VARCHAR(1000)  DEFAULT NULL COMMENT '人工或自动处置说明',
    last_error       VARCHAR(2000) DEFAULT NULL COMMENT '最近错误摘要',
    first_seen_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '首次进入死信时间',
    last_seen_at     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                  ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近出现时间',
    resolved_at      DATETIME(3)   DEFAULT NULL COMMENT '处置完成时间',
    UNIQUE KEY uk_dlq_source_task_stage (source_queue, task_id, process_stage),
    INDEX idx_dlq_status_seen (process_status, last_seen_at),
    INDEX idx_dlq_file (file_id, last_seen_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ死信持久化处置记录表';
