-- [PLUGIN-TEST-001] 开发阶段异步测试任务；与正常执行记录分离，短期结果可清理但保留审计摘要。
CREATE TABLE pcd_plugin_execution_task (
    task_id BINARY(16) NOT NULL,
    plugin_id BINARY(16) NOT NULL,
    version_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    space_id BINARY(16) NULL,
    execution_type VARCHAR(16) NOT NULL DEFAULT 'TEST',
    test_entrypoint VARCHAR(128) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    sandbox_id VARCHAR(128) NULL,
    result_json JSON NULL,
    error_code VARCHAR(64) NULL,
    error_summary VARCHAR(2000) NULL,
    started_at DATETIME(3) NULL,
    ended_at DATETIME(3) NULL,
    expires_at DATETIME(3) NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (task_id),
    KEY idx_plugin_test_history (plugin_id, created_at),
    KEY idx_plugin_test_status (status, created_at),
    CONSTRAINT chk_plugin_test_type CHECK (execution_type IN ('TEST', 'NORMAL')),
    CONSTRAINT chk_plugin_test_status CHECK (
        status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'TIMEOUT', 'CANCELLED')
    ),
    CONSTRAINT fk_plugin_test_plugin FOREIGN KEY (plugin_id)
        REFERENCES pcd_plugin(plugin_id) ON DELETE RESTRICT,
    CONSTRAINT fk_plugin_test_version FOREIGN KEY (version_id)
        REFERENCES pcd_plugin_version(version_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pcd_plugin_test_entrypoint (
    test_entrypoint_id BINARY(16) NOT NULL,
    version_id BINARY(16) NOT NULL,
    function_name VARCHAR(128) NOT NULL,
    metadata_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (test_entrypoint_id),
    UNIQUE KEY uk_plugin_test_entrypoint (version_id, function_name),
    CONSTRAINT fk_plugin_test_entrypoint_version FOREIGN KEY (version_id)
        REFERENCES pcd_plugin_version(version_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
