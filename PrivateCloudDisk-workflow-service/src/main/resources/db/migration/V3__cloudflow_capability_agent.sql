-- [CLOUDFLOW-RUNTIME-AGENT-001] gRPC Agent 能力调用的持久化幂等台账。
-- 同一 execution_id + step_id + attempt 无论网络重放多少次都只产生一次实际副作用。
CREATE TABLE pcd_capability_invocation (
    idempotency_key VARCHAR(300) NOT NULL,
    execution_id BINARY(16) NOT NULL,
    step_id VARCHAR(128) NOT NULL,
    attempt INT UNSIGNED NOT NULL,
    capability_key VARCHAR(255) NOT NULL,
    user_id BINARY(16) NOT NULL,
    space_id BINARY(16) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'RUNNING',
    result_json JSON NULL,
    trace_id VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at DATETIME(3) NULL,
    PRIMARY KEY (idempotency_key),
    UNIQUE KEY uk_capability_step_attempt (execution_id, step_id, attempt),
    KEY idx_capability_invocation_recovery (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Outbox 多实例领取租约；Publisher 崩溃后可恢复为 PENDING。
ALTER TABLE pcd_workflow_outbox
    ADD COLUMN claimed_at DATETIME(3) NULL AFTER next_retry_at;
