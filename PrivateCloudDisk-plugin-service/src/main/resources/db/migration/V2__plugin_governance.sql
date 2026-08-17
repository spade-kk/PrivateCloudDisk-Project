-- 插件治理、幂等和审计补充。所有跨服务标识仅做逻辑引用，不建立跨库外键。
ALTER TABLE pcd_user_plugin
    ADD COLUMN auto_update_policy VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN uninstalled_at DATETIME(3) NULL,
    ADD COLUMN row_version BIGINT UNSIGNED NOT NULL DEFAULT 0;

ALTER TABLE pcd_space_plugin
    ADD COLUMN auto_update_policy VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN uninstalled_at DATETIME(3) NULL,
    ADD COLUMN row_version BIGINT UNSIGNED NOT NULL DEFAULT 0;

ALTER TABLE pcd_plugin_execution_log
    ADD COLUMN source_id VARCHAR(128) NULL,
    ADD COLUMN attempt INT UNSIGNED NOT NULL DEFAULT 1,
    ADD COLUMN error_code VARCHAR(64) NULL,
    ADD COLUMN trace_id VARCHAR(64) NULL,
    ADD COLUMN correlation_id VARCHAR(64) NULL,
    ADD COLUMN causation_id VARCHAR(64) NULL,
    ADD COLUMN idempotency_key VARCHAR(160) NULL,
    ADD UNIQUE KEY uk_plugin_execution_idempotency (idempotency_key),
    ADD KEY idx_plugin_execution_user (user_id, created_at);

CREATE TABLE pcd_plugin_idempotency (
    subject_id BINARY(16) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    request_sha256 BINARY(32) NOT NULL,
    response_json JSON NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (subject_id, operation, idempotency_key),
    KEY idx_plugin_idempotency_expire (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pcd_plugin_audit_log (
    audit_id BINARY(16) NOT NULL,
    actor_user_id BINARY(16) NOT NULL,
    space_id BINARY(16) NULL,
    action VARCHAR(80) NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    resource_id BINARY(16) NOT NULL,
    before_hash BINARY(32) NULL,
    after_hash BINARY(32) NULL,
    request_id VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (audit_id),
    KEY idx_plugin_audit_resource (resource_type, resource_id, created_at),
    KEY idx_plugin_audit_actor (actor_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pcd_plugin_marketplace_listing (
    plugin_id BINARY(16) NOT NULL,
    review_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    pricing_model VARCHAR(24) NOT NULL DEFAULT 'FREE',
    billing_product_id VARCHAR(128) NULL,
    published_by BINARY(16) NULL,
    published_at DATETIME(3) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (plugin_id),
    KEY idx_plugin_listing_review (review_status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
