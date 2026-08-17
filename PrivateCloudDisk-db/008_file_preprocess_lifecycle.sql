-- ============================================================================
-- PrivateCloudDisk 文件内容预处理生命周期持久化
-- 需求来源：插件生态与自动化工作流平台 / 文件生命周期事件扩展
-- 变更原因：在 merge 与最终 hash 之间增加可恢复的内容预处理闸门，禁止使用
-- Redis-only 状态决定核心文件是否继续激活。
-- 影响范围：PrivateCloudDisk-storage-service Worker；不修改既有上传接口与
-- file.available 消息字段，旧客户端及旧消费者保持兼容。
-- ============================================================================

USE private_cloud_disk;

CREATE TABLE IF NOT EXISTS pcd_file_preprocess_gate (
    gate_id BINARY(16) NOT NULL COMMENT '预处理闸门 ID',
    ready_event_id BINARY(16) NOT NULL COMMENT 'file.content.ready 事件幂等 ID',
    processed_event_id BINARY(16) NULL COMMENT '最终接受的 file.content.processed 事件 ID',
    backend_task_id CHAR(32) NOT NULL COMMENT '既有后台流水线任务 ID（UUID hex）',
    pipeline_id VARCHAR(64) NOT NULL COMMENT '既有流水线追踪 ID',
    file_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    space_id BINARY(16) NULL COMMENT '为空表示兼容历史个人空间事件',
    uploads_id VARCHAR(64) NULL,
    file_name VARCHAR(512) NOT NULL,
    file_type VARCHAR(255) NOT NULL DEFAULT 'application/octet-stream',
    continuation_json JSON NOT NULL COMMENT '构建 hash 事件所需的既有后台事件快照',
    content_lease_hash CHAR(64) NOT NULL COMMENT '一次性候选内容 Lease 的 SHA-256，不保存明文',
    runtime_lease_hash CHAR(64) NULL COMMENT 'Runtime 换取的执行级 Lease 摘要',
    runtime_lease_execution_id VARCHAR(128) NULL COMMENT '执行级 Lease 绑定的 execution_id',
    runtime_lease_expires_at DATETIME(3) NULL COMMENT '执行级 Lease 到期时间，不得晚于 Gate deadline',
    original_locator VARCHAR(1024) NOT NULL COMMENT '存储服务内部原始对象定位符，绝不下发给插件',
    candidate_id VARCHAR(128) NULL COMMENT 'Runtime/Broker 返回给 Automation 的不透明候选 ID',
    candidate_locator VARCHAR(1024) NULL COMMENT '受信 Broker 登记的候选对象定位符',
    selected_locator VARCHAR(1024) NULL COMMENT '最终送入 hash 的存储定位符',
    upload_checksum CHAR(64) NOT NULL COMMENT '分片合并完成时校验的原始 SHA-256',
    candidate_checksum CHAR(64) NULL COMMENT '受信 Broker 计算的候选 SHA-256',
    final_checksum CHAR(64) NULL COMMENT 'Storage Hash Worker 独立计算的最终 SHA-256',
    original_size BIGINT UNSIGNED NOT NULL DEFAULT 0,
    candidate_size BIGINT UNSIGNED NULL,
    final_size BIGINT UNSIGNED NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN'
        COMMENT 'OPEN/SELECTED/FALLBACK/CLEANED',
    result_status VARCHAR(32) NULL
        COMMENT 'success/skipped/failed/timeout/fallback_unavailable',
    content_modified TINYINT(1) NOT NULL DEFAULT 0,
    content_revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    failure_code VARCHAR(128) NULL,
    failure_summary VARCHAR(1000) NULL COMMENT '只允许保存脱敏后的摘要',
    deadline_at DATETIME(3) NOT NULL,
    selected_at DATETIME(3) NULL,
    activation_committed_at DATETIME(3) NULL
        COMMENT '业务服务完成最终内容快照激活且 file.available 已发布的时间',
    cleaned_at DATETIME(3) NULL COMMENT '未选中内容副本完成物理清理的时间',
    cleanup_attempts INT UNSIGNED NOT NULL DEFAULT 0,
    last_cleanup_error VARCHAR(1000) NULL COMMENT '清理失败摘要，供后台补偿和运维审计',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (gate_id),
    UNIQUE KEY uk_preprocess_gate_ready_event (ready_event_id),
    UNIQUE KEY uk_preprocess_gate_processed_event (processed_event_id),
    UNIQUE KEY uk_preprocess_gate_backend_task (backend_task_id),
    KEY idx_preprocess_gate_deadline (status, deadline_at),
    KEY idx_preprocess_gate_cleanup (activation_committed_at, status, cleaned_at),
    KEY idx_preprocess_gate_space_file (space_id, file_id),
    CONSTRAINT chk_preprocess_gate_status CHECK (
        status IN ('OPEN', 'SELECTED', 'FALLBACK', 'CLEANED')
    ),
    CONSTRAINT chk_preprocess_gate_result CHECK (
        result_status IS NULL OR result_status IN (
            'success', 'skipped', 'failed', 'timeout', 'fallback_unavailable'
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='文件激活前内容预处理持久化闸门';

CREATE TABLE IF NOT EXISTS pcd_file_preprocess_inbox (
    event_id BINARY(16) NOT NULL COMMENT '消费事件 ID，承担数据库级幂等',
    gate_id BINARY(16) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload_sha256 CHAR(64) NOT NULL COMMENT '同事件 ID 不同内容时用于识别协议违规',
    received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    processed_at DATETIME(3) NULL,
    process_status VARCHAR(24) NOT NULL DEFAULT 'RECEIVED'
        COMMENT 'RECEIVED/PROCESSED/IGNORED/FAILED',
    failure_code VARCHAR(128) NULL,
    PRIMARY KEY (event_id),
    KEY idx_preprocess_inbox_gate (gate_id, received_at),
    KEY idx_preprocess_inbox_status (process_status, received_at),
    CONSTRAINT chk_preprocess_inbox_status CHECK (
        process_status IN ('RECEIVED', 'PROCESSED', 'IGNORED', 'FAILED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Storage 预处理事件 Inbox';

CREATE TABLE IF NOT EXISTS pcd_storage_outbox (
    outbox_id BINARY(16) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id BINARY(16) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    exchange_name VARCHAR(255) NOT NULL,
    routing_key VARCHAR(255) NOT NULL,
    payload_json JSON NOT NULL,
    message_status VARCHAR(24) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING/PUBLISHING/SENT/FAILED',
    available_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    published_at DATETIME(3) NULL,
    retry_count INT UNSIGNED NOT NULL DEFAULT 0,
    last_error VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (outbox_id),
    KEY idx_storage_outbox_publish (message_status, available_at, created_at),
    KEY idx_storage_outbox_aggregate (aggregate_type, aggregate_id, created_at),
    CONSTRAINT chk_storage_outbox_status CHECK (
        message_status IN ('PENDING', 'PUBLISHING', 'SENT', 'FAILED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Storage 可靠事件 Outbox';
