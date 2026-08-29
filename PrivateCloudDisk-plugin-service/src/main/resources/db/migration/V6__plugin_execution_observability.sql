-- [PLUGIN-EXEC-OBS-001] 执行详情可观测性：摘要仍保存在 pcd_plugin_execution_log，
-- 完整日志行与能力调用审计独立持久化，避免列表查询误扫大字段并支持按执行 ID 受权分页。
-- 所有记录均为同库逻辑引用；运行时/自动化服务只能通过受信内部接口写入。

CREATE TABLE pcd_plugin_execution_log_line (
    execution_id BINARY(16) NOT NULL,
    sequence_no BIGINT UNSIGNED NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    log_level VARCHAR(12) NOT NULL,
    log_source VARCHAR(24) NOT NULL,
    message_text MEDIUMTEXT NOT NULL,
    byte_offset BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (execution_id, sequence_no),
    KEY idx_plugin_execution_log_time (execution_id, occurred_at, sequence_no),
    KEY idx_plugin_execution_log_filter (execution_id, log_level, log_source, sequence_no),
    CONSTRAINT chk_plugin_execution_log_level CHECK (
        log_level IN ('DEBUG', 'INFO', 'WARN', 'ERROR')
    ),
    CONSTRAINT chk_plugin_execution_log_source CHECK (
        log_source IN ('STDOUT', 'STDERR', 'PYCLOUDSDK', 'SYSTEM', 'RUNNER')
    ),
    CONSTRAINT fk_plugin_execution_log_line_execution FOREIGN KEY (execution_id)
        REFERENCES pcd_plugin_execution_log(execution_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 每个 execution 独占的序号游标。服务在事务中 SELECT ... FOR UPDATE，
-- 使重试/多实例并发上报仍得到连续、稳定的日志与审计游标。
CREATE TABLE pcd_plugin_execution_observability_cursor (
    execution_id BINARY(16) NOT NULL,
    next_log_sequence BIGINT UNSIGNED NOT NULL DEFAULT 1,
    next_audit_sequence BIGINT UNSIGNED NOT NULL DEFAULT 1,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (execution_id),
    CONSTRAINT fk_plugin_execution_observability_cursor_execution FOREIGN KEY (execution_id)
        REFERENCES pcd_plugin_execution_log(execution_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 内部调用的 at-least-once 投递去重键；同一 observation_id 重放不会复制日志或审计。
CREATE TABLE pcd_plugin_execution_observation_ingest (
    execution_id BINARY(16) NOT NULL,
    observation_id VARCHAR(128) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (execution_id, observation_id),
    CONSTRAINT fk_plugin_execution_observation_ingest_execution FOREIGN KEY (execution_id)
        REFERENCES pcd_plugin_execution_log(execution_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pcd_plugin_execution_audit_trail (
    audit_id BINARY(16) NOT NULL,
    execution_id BINARY(16) NOT NULL,
    parent_audit_id BINARY(16) NULL,
    sequence_no BIGINT UNSIGNED NOT NULL,
    capability_key VARCHAR(160) NOT NULL,
    capability_type VARCHAR(24) NOT NULL,
    summary_template VARCHAR(96) NULL,
    summary_text VARCHAR(2000) NOT NULL,
    target_context JSON NULL,
    input_params JSON NULL,
    input_summary VARCHAR(2000) NULL,
    output_result JSON NULL,
    output_summary VARCHAR(2000) NULL,
    audit_status VARCHAR(16) NOT NULL,
    duration_ms BIGINT UNSIGNED NULL,
    retry_count INT UNSIGNED NOT NULL DEFAULT 0,
    error_code VARCHAR(96) NULL,
    error_summary VARCHAR(2000) NULL,
    occurred_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (audit_id),
    UNIQUE KEY uk_plugin_execution_audit_sequence (execution_id, sequence_no),
    KEY idx_plugin_execution_audit_time (execution_id, occurred_at, sequence_no),
    KEY idx_plugin_execution_audit_parent (execution_id, parent_audit_id),
    KEY idx_plugin_execution_audit_filter (execution_id, capability_type, audit_status, sequence_no),
    CONSTRAINT chk_plugin_execution_audit_type CHECK (
        capability_type IN ('BUILTIN', 'PLATFORM_API', 'PLUGIN')
    ),
    CONSTRAINT chk_plugin_execution_audit_status CHECK (
        audit_status IN ('SUCCESS', 'FAILED', 'TIMEOUT', 'RUNNING', 'SKIPPED')
    ),
    CONSTRAINT fk_plugin_execution_audit_execution FOREIGN KEY (execution_id)
        REFERENCES pcd_plugin_execution_log(execution_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
