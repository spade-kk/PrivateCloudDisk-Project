-- Scheduler 独立 schema：只维护时间计划、租约和 fire Outbox，不执行业务步骤。
CREATE TABLE pcd_workflow_schedule (
    schedule_id BINARY(16) NOT NULL,
    workflow_id BINARY(16) NOT NULL,
    version_id BINARY(16) NOT NULL,
    owner_user_id BINARY(16) NOT NULL,
    space_id BINARY(16) NULL,
    cron_expression VARCHAR(128) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    misfire_policy VARCHAR(32) NOT NULL DEFAULT 'FIRE_ONCE',
    inputs_json JSON NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    next_fire_at DATETIME(3) NOT NULL,
    last_scheduled_at DATETIME(3) NULL,
    lease_owner VARCHAR(128) NULL,
    lease_expires_at DATETIME(3) NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (schedule_id),
    KEY idx_schedule_due (status, next_fire_at, lease_expires_at),
    KEY idx_schedule_workflow (workflow_id, status),
    CONSTRAINT chk_schedule_status CHECK (status IN ('ACTIVE', 'PAUSED', 'DELETED')),
    CONSTRAINT chk_schedule_misfire CHECK (
        misfire_policy IN ('SKIP', 'FIRE_ONCE', 'CATCH_UP_LIMITED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pcd_schedule_fire_outbox (
    event_id BINARY(16) NOT NULL,
    schedule_id BINARY(16) NOT NULL,
    scheduled_at DATETIME(3) NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    attempt INT UNSIGNED NOT NULL DEFAULT 0,
    next_retry_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    published_at DATETIME(3) NULL,
    PRIMARY KEY (event_id),
    UNIQUE KEY uk_schedule_fire (schedule_id, scheduled_at),
    KEY idx_schedule_outbox_publish (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pcd_scheduler_idempotency (
    idempotency_key VARCHAR(160) NOT NULL,
    actor_user_id BINARY(16) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    resource_id BINARY(16) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at DATETIME(3) NOT NULL,
    PRIMARY KEY (idempotency_key),
    KEY idx_scheduler_idempotency_expire (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
