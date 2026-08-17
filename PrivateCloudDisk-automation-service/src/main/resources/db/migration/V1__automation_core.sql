-- 插件生态与自动化工作流平台：Automation Service 独立事实表。
-- 不引用 Platform/Plugin 数据库外键，跨服务只保存 UUID 快照。
CREATE TABLE pcd_automation_inbox (
    event_id BINARY(16) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    payload_json JSON NOT NULL COMMENT 'Inbox Lease 过期后可脱离 RabbitMQ 原消息恢复',
    status VARCHAR(24) NOT NULL DEFAULT 'PROCESSING',
    lease_until DATETIME(3) NOT NULL,
    attempt INT UNSIGNED NOT NULL DEFAULT 1,
    first_received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    completed_at DATETIME(3) NULL,
    failure_code VARCHAR(128) NULL,
    PRIMARY KEY (event_id),
    KEY idx_automation_inbox_recovery (status, lease_until),
    CONSTRAINT chk_automation_inbox_status
        CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pcd_automation_dispatch (
    dispatch_id BINARY(16) NOT NULL,
    source_event_id BINARY(16) NOT NULL,
    gate_id BINARY(16) NULL,
    backend_task_id CHAR(32) NULL,
    user_id BINARY(16) NULL,
    space_id BINARY(16) NULL,
    trigger_type VARCHAR(128) NOT NULL,
    matched_count INT UNSIGNED NOT NULL DEFAULT 0,
    completed_count INT UNSIGNED NOT NULL DEFAULT 0,
    dispatch_status VARCHAR(24) NOT NULL,
    result_summary VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at DATETIME(3) NULL,
    PRIMARY KEY (dispatch_id),
    UNIQUE KEY uk_automation_dispatch_event (source_event_id),
    KEY idx_automation_dispatch_space (space_id, created_at),
    KEY idx_automation_dispatch_gate (gate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pcd_automation_outbox (
    outbox_id BINARY(16) NOT NULL,
    aggregate_id BINARY(16) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    exchange_name VARCHAR(255) NOT NULL,
    routing_key VARCHAR(255) NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    available_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    retry_count INT UNSIGNED NOT NULL DEFAULT 0,
    published_at DATETIME(3) NULL,
    last_error VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (outbox_id),
    UNIQUE KEY uk_automation_outbox_event (aggregate_id, event_type),
    KEY idx_automation_outbox_publish (status, available_at, created_at),
    CONSTRAINT chk_automation_outbox_status
        CHECK (status IN ('PENDING', 'PUBLISHING', 'SENT', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
