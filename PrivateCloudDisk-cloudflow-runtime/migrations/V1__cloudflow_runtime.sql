-- [CLOUDFLOW-DSL-001] Runtime 执行面独立状态表；控制面 Workflow Service 仍是版本与权限事实源。
CREATE TABLE cloudflow_execution (
    execution_id CHAR(36) PRIMARY KEY,
    workflow_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    space_id CHAR(36) NULL,
    plan_hash CHAR(71) NOT NULL,
    status VARCHAR(24) NOT NULL,
    current_step VARCHAR(128) NULL,
    context_json JSON NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    heartbeat_at TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT chk_cloudflow_execution_status CHECK (
        status IN ('PENDING','RUNNING','RETRY_WAITING','SUCCESS','FAILED','TIMED_OUT','CANCELLED')
    ),
    INDEX idx_cloudflow_execution_recovery (status, heartbeat_at),
    INDEX idx_cloudflow_execution_scope (space_id, created_at)
);

CREATE TABLE cloudflow_step_execution (
    execution_id CHAR(36) NOT NULL,
    step_id VARCHAR(128) NOT NULL,
    attempt INT NOT NULL,
    status VARCHAR(24) NOT NULL,
    input_summary_json JSON NOT NULL,
    output_summary_json JSON NULL,
    error_code VARCHAR(64) NULL,
    duration_ms BIGINT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (execution_id, step_id, attempt),
    INDEX idx_cloudflow_step_status (execution_id, status)
);

CREATE TABLE cloudflow_idempotency (
    idempotency_key VARCHAR(160) PRIMARY KEY,
    execution_id CHAR(36) NOT NULL,
    expires_at TIMESTAMP(3) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);

CREATE TABLE cloudflow_outbox (
    event_id CHAR(36) PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    payload_json JSON NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    published_at TIMESTAMP(3) NULL,
    INDEX idx_cloudflow_outbox_publish (published_at, next_retry_at)
);
