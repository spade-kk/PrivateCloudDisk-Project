-- [CLOUDFLOW-RUNTIME-STATE-001] Rust Runtime 独立状态库；Workflow Service 仍是工作流版本与权限事实源。
-- Runtime 仅保存不可变 IR 快照、执行检查点和可靠消息，不直接查询平台核心业务表。
CREATE TABLE IF NOT EXISTS cloudflow_execution (
    execution_id VARCHAR(128) NOT NULL,
    workflow_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    space_id VARCHAR(128) NULL,
    plan_hash CHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    current_step VARCHAR(128) NULL,
    ir_json JSON NOT NULL,
    variables_json JSON NOT NULL,
    outputs_json JSON NOT NULL,
    declared_permissions_json JSON NOT NULL,
    granted_permissions_json JSON NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    error_code VARCHAR(64) NULL,
    error_summary VARCHAR(2000) NULL,
    cancel_requested TINYINT(1) NOT NULL DEFAULT 0,
    pause_requested TINYINT(1) NOT NULL DEFAULT 0,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    heartbeat_at DATETIME(3) NULL,
    started_at DATETIME(3) NULL,
    ended_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (execution_id),
    KEY idx_cloudflow_execution_recovery (status, heartbeat_at, created_at),
    KEY idx_cloudflow_execution_scope (space_id, created_at),
    CONSTRAINT chk_cloudflow_execution_status CHECK (
        status IN ('CREATED','READY','RUNNING','WAITING','SUCCESS','FAILED','CANCELLED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS cloudflow_step_execution (
    execution_id VARCHAR(128) NOT NULL,
    step_id VARCHAR(128) NOT NULL,
    attempt INT UNSIGNED NOT NULL,
    status VARCHAR(24) NOT NULL,
    input_summary_json JSON NOT NULL,
    output_summary_json JSON NULL,
    error_code VARCHAR(64) NULL,
    error_summary VARCHAR(2000) NULL,
    retryable TINYINT(1) NOT NULL DEFAULT 0,
    started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ended_at DATETIME(3) NULL,
    duration_ms BIGINT UNSIGNED NULL,
    PRIMARY KEY (execution_id, step_id, attempt),
    KEY idx_cloudflow_step_status (execution_id, status, step_id),
    CONSTRAINT chk_cloudflow_step_status CHECK (
        status IN ('PENDING','RUNNING','RETRYING','SUCCESS','FAILED','SKIPPED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Inbox 是 RabbitMQ 重复投递的持久化幂等事实源，Redis 不参与正确性判断。
CREATE TABLE IF NOT EXISTS cloudflow_inbox (
    event_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PROCESSING',
    retry_count INT UNSIGNED NOT NULL DEFAULT 0,
    error_summary VARCHAR(2000) NULL,
    received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    processed_at DATETIME(3) NULL,
    PRIMARY KEY (event_id),
    KEY idx_cloudflow_inbox_recovery (status, received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Outbox 与执行状态在同一 MySQL 事务提交，Publisher Confirm 后才标记 PUBLISHED。
-- DEAD 为超过发布重试上限的人工处置终态；不得通过定时扫描无限重发。
CREATE TABLE IF NOT EXISTS cloudflow_outbox (
    event_id CHAR(36) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    routing_key VARCHAR(128) NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    attempts INT UNSIGNED NOT NULL DEFAULT 0,
    next_retry_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_error VARCHAR(2000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    published_at DATETIME(3) NULL,
    PRIMARY KEY (event_id),
    KEY idx_cloudflow_outbox_publish (status, next_retry_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS cloudflow_execution_log (
    execution_id VARCHAR(128) NOT NULL,
    sequence_no BIGINT UNSIGNED NOT NULL,
    level VARCHAR(16) NOT NULL,
    message VARCHAR(4000) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (execution_id, sequence_no),
    KEY idx_cloudflow_execution_log_time (execution_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
