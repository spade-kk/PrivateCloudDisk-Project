-- 工作流服务独立 schema。工作流发布版本不可变，执行与步骤均以数据库为事实源。
CREATE TABLE pcd_workflow (
    workflow_id BINARY(16) NOT NULL,
    owner_user_id BINARY(16) NOT NULL,
    owner_scope_type VARCHAR(16) NOT NULL,
    owner_scope_id BINARY(16) NOT NULL,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(120) NOT NULL,
    description VARCHAR(2000) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    latest_version_id BINARY(16) NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (workflow_id),
    UNIQUE KEY uk_workflow_scope_slug (owner_scope_type, owner_scope_id, slug),
    KEY idx_workflow_scope_status (owner_scope_type, owner_scope_id, status, updated_at),
    CONSTRAINT chk_workflow_scope CHECK (owner_scope_type IN ('USER', 'SPACE')),
    CONSTRAINT chk_workflow_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'PAUSED', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pcd_workflow_version (
    version_id BINARY(16) NOT NULL,
    workflow_id BINARY(16) NOT NULL,
    version INT UNSIGNED NOT NULL,
    dsl_text MEDIUMTEXT NOT NULL,
    dsl_sha256 BINARY(32) NOT NULL,
    graph_json JSON NOT NULL,
    schema_version VARCHAR(64) NOT NULL DEFAULT 'workflow.cloudflow.io/v1',
    validation_report_json JSON NOT NULL,
    immutable TINYINT(1) NOT NULL DEFAULT 0,
    published_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (version_id),
    UNIQUE KEY uk_workflow_version (workflow_id, version),
    KEY idx_workflow_version_publish (workflow_id, published_at),
    CONSTRAINT fk_workflow_version_workflow FOREIGN KEY (workflow_id)
        REFERENCES pcd_workflow(workflow_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pcd_workflow_trigger (
    trigger_id BINARY(16) NOT NULL,
    workflow_id BINARY(16) NOT NULL,
    version_id BINARY(16) NOT NULL,
    trigger_type VARCHAR(24) NOT NULL,
    event_type VARCHAR(128) NULL,
    filter_json JSON NOT NULL,
    schedule_id BINARY(16) NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (trigger_id),
    KEY idx_workflow_trigger_match (trigger_type, event_type, enabled),
    KEY idx_workflow_trigger_schedule (schedule_id, enabled),
    CONSTRAINT chk_workflow_trigger_type CHECK (trigger_type IN ('MANUAL', 'EVENT', 'SCHEDULE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pcd_capability_registry (
    capability_key VARCHAR(255) NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    source_id VARCHAR(128) NULL,
    source_version VARCHAR(32) NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NULL,
    input_schema_json JSON NOT NULL,
    output_schema_json JSON NOT NULL,
    required_permissions_json JSON NOT NULL,
    availability_policy_json JSON NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    revision BIGINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (capability_key),
    KEY idx_capability_source_status (source_type, status, display_name),
    CONSTRAINT chk_capability_source CHECK (
        source_type IN ('BUILTIN', 'API', 'PLUGIN', 'LOCAL_PLUGIN')
    ),
    CONSTRAINT chk_capability_status CHECK (
        status IN ('ACTIVE', 'DEPRECATED', 'DISABLED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pcd_workflow_execution (
    execution_id BINARY(16) NOT NULL,
    workflow_id BINARY(16) NOT NULL,
    version_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    space_id BINARY(16) NULL,
    trigger_type VARCHAR(24) NOT NULL,
    trigger_ref VARCHAR(255) NULL,
    status VARCHAR(24) NOT NULL,
    started_at DATETIME(3) NULL,
    ended_at DATETIME(3) NULL,
    heartbeat_at DATETIME(3) NULL,
    current_step VARCHAR(128) NULL,
    input_summary_json JSON NOT NULL,
    output_summary_json JSON NULL,
    error_code VARCHAR(64) NULL,
    error_summary VARCHAR(2000) NULL,
    trace_id VARCHAR(64) NOT NULL,
    correlation_id VARCHAR(64) NULL,
    causation_id VARCHAR(64) NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    retry_of_execution_id BINARY(16) NULL,
    cancel_requested TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (execution_id),
    UNIQUE KEY uk_workflow_execution_idempotency (idempotency_key),
    KEY idx_workflow_execution_history (workflow_id, created_at),
    KEY idx_workflow_space_history (space_id, created_at),
    KEY idx_workflow_execution_claim (status, heartbeat_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pcd_workflow_step_execution (
    step_execution_id BINARY(16) NOT NULL,
    workflow_execution_id BINARY(16) NOT NULL,
    step_id VARCHAR(128) NOT NULL,
    step_name VARCHAR(160) NULL,
    capability_key VARCHAR(255) NOT NULL,
    attempt INT UNSIGNED NOT NULL DEFAULT 1,
    status VARCHAR(24) NOT NULL,
    input_summary_json JSON NOT NULL,
    output_summary_json JSON NULL,
    plugin_execution_id BINARY(16) NULL,
    started_at DATETIME(3) NOT NULL,
    ended_at DATETIME(3) NULL,
    duration_ms BIGINT UNSIGNED NULL,
    error_code VARCHAR(64) NULL,
    error_summary VARCHAR(2000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (step_execution_id),
    UNIQUE KEY uk_workflow_step_attempt (workflow_execution_id, step_id, attempt),
    KEY idx_workflow_step_history (workflow_execution_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pcd_workflow_inbox (
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload_json JSON NOT NULL,
    payload_sha256 BINARY(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    attempt INT UNSIGNED NOT NULL DEFAULT 0,
    next_retry_at DATETIME(3) NULL,
    received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    processed_at DATETIME(3) NULL,
    PRIMARY KEY (event_id),
    KEY idx_workflow_inbox_recovery (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pcd_workflow_outbox (
    event_id BINARY(16) NOT NULL,
    aggregate_id BINARY(16) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    routing_key VARCHAR(128) NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    attempt INT UNSIGNED NOT NULL DEFAULT 0,
    next_retry_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    published_at DATETIME(3) NULL,
    PRIMARY KEY (event_id),
    KEY idx_workflow_outbox_publish (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pcd_workflow_marketplace_listing (
    workflow_id BINARY(16) NOT NULL,
    review_status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    category_code VARCHAR(64) NULL,
    tags_json JSON NOT NULL,
    install_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    rating_average DECIMAL(3,2) NOT NULL DEFAULT 0,
    rating_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    published_by BINARY(16) NULL,
    published_at DATETIME(3) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (workflow_id),
    KEY idx_workflow_market (review_status, category_code, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pcd_workflow_review (
    workflow_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    rating TINYINT UNSIGNED NOT NULL,
    comment_text VARCHAR(2000) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'VISIBLE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (workflow_id, user_id),
    KEY idx_workflow_review (workflow_id, status, created_at),
    CONSTRAINT chk_workflow_review_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 内置能力随迁移建立，避免应用多实例并发启动时重复注册。
INSERT INTO pcd_capability_registry (
    capability_key, source_type, display_name, description,
    input_schema_json, output_schema_json, required_permissions_json,
    availability_policy_json, status
) VALUES
('builtin:date.now', 'BUILTIN', '当前时间', '返回指定时区的当前时间',
 '{"type":"object","properties":{"timezone":{"type":"string"}}}',
 '{"type":"object","required":["iso"],"properties":{"iso":{"type":"string"}}}',
 '[]', '{"timeout_seconds":1,"max_concurrency":100}', 'ACTIVE'),
('builtin:text.transform', 'BUILTIN', '文本转换', '执行长度受限的大小写或去空白转换',
 '{"type":"object","required":["text","operation"],"properties":{"text":{"type":"string","maxLength":65536},"operation":{"enum":["upper","lower","trim"]}}}',
 '{"type":"object","required":["text"],"properties":{"text":{"type":"string"}}}',
 '[]', '{"timeout_seconds":2,"max_concurrency":50}', 'ACTIVE'),
('api:user.notify', 'API', '发送通知', '通过平台通知能力发送站内消息',
 '{"type":"object","required":["title","body"],"properties":{"title":{"type":"string","maxLength":120},"body":{"type":"string","maxLength":2000}}}',
 '{"type":"object","properties":{"accepted":{"type":"boolean"}}}',
 '["notification.send"]', '{"timeout_seconds":5,"circuit_breaker":"platform"}', 'ACTIVE');
