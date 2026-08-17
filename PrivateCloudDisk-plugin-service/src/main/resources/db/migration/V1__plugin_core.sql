-- Plugin Service 独立 schema。发布版本不可变；包内容不存数据库。
CREATE TABLE pcd_plugin (
    plugin_id BINARY(16) NOT NULL,
    owner_user_id BINARY(16) NOT NULL,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(120) NOT NULL,
    description TEXT NULL,
    plugin_type VARCHAR(32) NOT NULL,
    visibility VARCHAR(24) NOT NULL DEFAULT 'PRIVATE',
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    latest_version_id BINARY(16) NULL,
    author_display_name VARCHAR(120) NULL,
    category_code VARCHAR(64) NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (plugin_id),
    UNIQUE KEY uk_plugin_owner_slug (owner_user_id, slug),
    KEY idx_plugin_owner_status (owner_user_id, status, updated_at),
    KEY idx_plugin_market (visibility, status, category_code, updated_at),
    CONSTRAINT chk_plugin_type CHECK (
        plugin_type IN ('CLOUD_PLUGIN', 'LOCAL_PLUGIN', 'WORKFLOW_PLUGIN')
    ),
    CONSTRAINT chk_plugin_status CHECK (
        status IN ('DRAFT', 'VALIDATING', 'READY', 'PUBLISHED', 'SUSPENDED', 'DELETED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pcd_plugin_version (
    version_id BINARY(16) NOT NULL,
    plugin_id BINARY(16) NOT NULL,
    version VARCHAR(32) NOT NULL,
    runtime VARCHAR(32) NOT NULL,
    entrypoint VARCHAR(255) NOT NULL,
    manifest_json JSON NOT NULL,
    permission_config JSON NOT NULL,
    supported_platforms JSON NOT NULL,
    client_types JSON NOT NULL,
    package_object_key VARCHAR(512) NULL,
    package_sha256 BINARY(32) NULL,
    package_size BIGINT UNSIGNED NOT NULL DEFAULT 0,
    signature VARBINARY(1024) NULL,
    signing_key_id VARCHAR(128) NULL,
    validation_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    validation_report_json JSON NULL,
    immutable TINYINT(1) NOT NULL DEFAULT 0,
    published_at DATETIME(3) NULL,
    revoked_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (version_id),
    UNIQUE KEY uk_plugin_version (plugin_id, version),
    UNIQUE KEY uk_plugin_package_sha (package_sha256),
    KEY idx_plugin_version_publish (plugin_id, published_at),
    CONSTRAINT chk_plugin_runtime CHECK (
        runtime IN ('PYTHON_3_11', 'JAVASCRIPT_ES2022', 'PCD_WORKFLOW_V1')
    ),
    CONSTRAINT chk_plugin_validation CHECK (
        validation_status IN ('PENDING', 'PASSED', 'FAILED', 'EXPIRED')
    ),
    CONSTRAINT fk_plugin_version_plugin FOREIGN KEY (plugin_id)
        REFERENCES pcd_plugin(plugin_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pcd_plugin_entrypoint (
    entrypoint_id BINARY(16) NOT NULL,
    version_id BINARY(16) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    function_name VARCHAR(128) NOT NULL,
    priority INT NOT NULL DEFAULT 100,
    condition_json JSON NOT NULL,
    permission_json JSON NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (entrypoint_id),
    UNIQUE KEY uk_plugin_entrypoint (version_id, event_type, function_name),
    KEY idx_plugin_entrypoint_match (event_type, enabled, priority),
    CONSTRAINT fk_plugin_entrypoint_version FOREIGN KEY (version_id)
        REFERENCES pcd_plugin_version(version_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pcd_plugin_capability (
    capability_id BINARY(16) NOT NULL,
    version_id BINARY(16) NOT NULL,
    capability_name VARCHAR(128) NOT NULL,
    description VARCHAR(1000) NULL,
    input_schema_json JSON NOT NULL,
    output_schema_json JSON NOT NULL,
    permission_json JSON NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (capability_id),
    UNIQUE KEY uk_plugin_capability (version_id, capability_name),
    KEY idx_plugin_capability_status (status, capability_name),
    CONSTRAINT fk_plugin_capability_version FOREIGN KEY (version_id)
        REFERENCES pcd_plugin_version(version_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pcd_user_plugin (
    installation_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    plugin_id BINARY(16) NOT NULL,
    version_id BINARY(16) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    config_json JSON NOT NULL,
    granted_permissions_json JSON NOT NULL,
    installed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (installation_id),
    UNIQUE KEY uk_user_plugin (user_id, plugin_id),
    KEY idx_user_plugin_enabled (user_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pcd_space_plugin (
    installation_id BINARY(16) NOT NULL,
    space_id BINARY(16) NOT NULL,
    plugin_id BINARY(16) NOT NULL,
    version_id BINARY(16) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    config_json JSON NOT NULL,
    granted_permissions_json JSON NOT NULL,
    installed_by BINARY(16) NOT NULL,
    installed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (installation_id),
    UNIQUE KEY uk_space_plugin (space_id, plugin_id),
    KEY idx_space_plugin_enabled (space_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pcd_plugin_execution_log (
    execution_id BINARY(16) NOT NULL,
    plugin_id BINARY(16) NOT NULL,
    version_id BINARY(16) NOT NULL,
    installation_id BINARY(16) NOT NULL,
    user_id BINARY(16) NULL,
    space_id BINARY(16) NULL,
    client_id VARCHAR(128) NULL,
    trigger_event VARCHAR(128) NOT NULL,
    trigger_source VARCHAR(32) NOT NULL,
    execution_status VARCHAR(24) NOT NULL,
    started_at DATETIME(3) NOT NULL,
    ended_at DATETIME(3) NULL,
    duration_ms BIGINT UNSIGNED NULL,
    output_summary VARCHAR(4000) NULL,
    full_log_object_key VARCHAR(512) NULL,
    resource_usage_json JSON NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (execution_id),
    KEY idx_plugin_execution_history (plugin_id, created_at),
    KEY idx_space_execution_history (space_id, created_at),
    KEY idx_execution_status (execution_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pcd_plugin_market_review (
    review_id BINARY(16) NOT NULL,
    plugin_id BINARY(16) NOT NULL,
    reviewer_user_id BINARY(16) NOT NULL,
    review_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    security_report_json JSON NULL,
    reviewed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (review_id),
    KEY idx_market_review (plugin_id, review_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pcd_plugin_rating (
    plugin_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    rating TINYINT UNSIGNED NOT NULL,
    comment_text VARCHAR(2000) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'VISIBLE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (plugin_id, user_id),
    KEY idx_plugin_rating (plugin_id, status, created_at),
    CONSTRAINT chk_plugin_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
