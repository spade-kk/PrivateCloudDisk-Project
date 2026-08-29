-- =====================================================================
-- Git Service 独立数据模型
-- 空间和用户 ID 只保存跨服务标识，不创建跨 Schema 外键，避免微服务数据库耦合。
-- Git Object 为全局内容寻址；git_repo_object 保存仓库引用，实现跨仓库去重与引用计数。
-- =====================================================================
CREATE TABLE IF NOT EXISTS pcd_git_repository (
    repo_id CHAR(36) NOT NULL,
    space_id CHAR(36) NOT NULL,
    owner_id CHAR(36) NOT NULL,
    repo_name VARCHAR(128) NOT NULL,
    repo_slug VARCHAR(190) NOT NULL,
    description VARCHAR(500) NOT NULL DEFAULT '',
    default_branch VARCHAR(255) NOT NULL DEFAULT 'main',
    hash_algorithm ENUM('sha1','sha256') NOT NULL DEFAULT 'sha1',
    repo_status ENUM('ACTIVE','SYNCING','DEGRADED','DELETED') NOT NULL DEFAULT 'ACTIVE',
    object_count BIGINT NOT NULL DEFAULT 0,
    object_bytes BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (repo_id),
    UNIQUE KEY uk_git_repo_space (space_id),
    UNIQUE KEY uk_git_repo_slug (repo_slug),
    KEY idx_git_repo_owner (owner_id, repo_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS pcd_git_ref (
    repo_id CHAR(36) NOT NULL,
    ref_name VARCHAR(512) NOT NULL,
    object_hash CHAR(64) NOT NULL,
    ref_type ENUM('BRANCH','TAG','OTHER') NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (repo_id, ref_name),
    KEY idx_git_ref_object (object_hash),
    CONSTRAINT fk_git_ref_repo FOREIGN KEY (repo_id) REFERENCES pcd_git_repository(repo_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS pcd_git_object (
    algorithm ENUM('sha1','sha256') NOT NULL,
    object_hash CHAR(64) NOT NULL,
    object_type ENUM('blob','tree','commit','tag') NOT NULL,
    object_size BIGINT NOT NULL,
    storage_path VARCHAR(768) NOT NULL,
    reference_count BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (algorithm, object_hash),
    KEY idx_git_object_gc (reference_count, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS pcd_git_repo_object (
    repo_id CHAR(36) NOT NULL,
    algorithm ENUM('sha1','sha256') NOT NULL,
    object_hash CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (repo_id, algorithm, object_hash),
    CONSTRAINT fk_git_repo_object_repo FOREIGN KEY (repo_id) REFERENCES pcd_git_repository(repo_id) ON DELETE CASCADE,
    CONSTRAINT fk_git_repo_object_object FOREIGN KEY (algorithm, object_hash) REFERENCES pcd_git_object(algorithm, object_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS pcd_git_commit_index (
    repo_id CHAR(36) NOT NULL,
    commit_hash CHAR(64) NOT NULL,
    tree_hash CHAR(64) NOT NULL,
    parent_hashes JSON NOT NULL,
    author_name VARCHAR(255) NOT NULL,
    author_email VARCHAR(320) NOT NULL,
    authored_at DATETIME(6) NOT NULL,
    committer_name VARCHAR(255) NOT NULL,
    committed_at DATETIME(6) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    message_text MEDIUMTEXT NOT NULL,
    PRIMARY KEY (repo_id, commit_hash),
    KEY idx_git_commit_time (repo_id, committed_at DESC),
    KEY idx_git_commit_author (repo_id, author_email, committed_at DESC),
    CONSTRAINT fk_git_commit_repo FOREIGN KEY (repo_id) REFERENCES pcd_git_repository(repo_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS pcd_git_permission (
    permission_id BIGINT NOT NULL AUTO_INCREMENT,
    repo_id CHAR(36) NOT NULL,
    subject_type ENUM('USER','TEAM') NOT NULL DEFAULT 'USER',
    subject_id CHAR(36) NOT NULL,
    permission_level ENUM('READ','WRITE','ADMIN') NOT NULL,
    granted_by CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (permission_id),
    UNIQUE KEY uk_git_permission_subject (repo_id, subject_type, subject_id),
    CONSTRAINT fk_git_permission_repo FOREIGN KEY (repo_id) REFERENCES pcd_git_repository(repo_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS pcd_git_branch_protection (
    protection_id BIGINT NOT NULL AUTO_INCREMENT,
    repo_id CHAR(36) NOT NULL,
    ref_pattern VARCHAR(255) NOT NULL,
    require_merge_request TINYINT(1) NOT NULL DEFAULT 1,
    required_approvals INT NOT NULL DEFAULT 1,
    allow_force_push TINYINT(1) NOT NULL DEFAULT 0,
    created_by CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (protection_id),
    UNIQUE KEY uk_git_branch_protection (repo_id, ref_pattern),
    CONSTRAINT fk_git_protection_repo FOREIGN KEY (repo_id) REFERENCES pcd_git_repository(repo_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS pcd_git_merge_request (
    merge_request_id CHAR(36) NOT NULL,
    repo_id CHAR(36) NOT NULL,
    repo_number BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    source_branch VARCHAR(255) NOT NULL,
    target_branch VARCHAR(255) NOT NULL,
    author_id CHAR(36) NOT NULL,
    mr_status ENUM('OPEN','MERGED','CLOSED') NOT NULL DEFAULT 'OPEN',
    approval_status ENUM('PENDING','APPROVED','CHANGES_REQUESTED') NOT NULL DEFAULT 'PENDING',
    merge_strategy ENUM('FAST_FORWARD','MERGE_COMMIT') NOT NULL DEFAULT 'MERGE_COMMIT',
    merged_by CHAR(36) NULL,
    merged_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (merge_request_id),
    UNIQUE KEY uk_git_mr_number (repo_id, repo_number),
    KEY idx_git_mr_status (repo_id, mr_status, updated_at DESC),
    CONSTRAINT fk_git_mr_repo FOREIGN KEY (repo_id) REFERENCES pcd_git_repository(repo_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS pcd_git_merge_request_comment (
    comment_id CHAR(36) NOT NULL,
    merge_request_id CHAR(36) NOT NULL,
    author_id CHAR(36) NOT NULL,
    body TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (comment_id),
    CONSTRAINT fk_git_mr_comment FOREIGN KEY (merge_request_id) REFERENCES pcd_git_merge_request(merge_request_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS pcd_git_merge_request_approval (
    merge_request_id CHAR(36) NOT NULL,
    reviewer_id CHAR(36) NOT NULL,
    decision ENUM('APPROVED','CHANGES_REQUESTED') NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (merge_request_id, reviewer_id),
    CONSTRAINT fk_git_mr_approval FOREIGN KEY (merge_request_id) REFERENCES pcd_git_merge_request(merge_request_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS pcd_git_personal_access_token (
    token_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    token_name VARCHAR(128) NOT NULL,
    token_prefix CHAR(12) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    scopes JSON NOT NULL,
    expires_at DATETIME(6) NULL,
    last_used_at DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (token_id),
    UNIQUE KEY uk_git_pat_hash (token_hash),
    KEY idx_git_pat_user (user_id, revoked_at, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS pcd_git_ssh_key (
    key_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    key_name VARCHAR(128) NOT NULL,
    public_key TEXT NOT NULL,
    fingerprint_sha256 VARCHAR(128) NOT NULL,
    last_used_at DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (key_id),
    UNIQUE KEY uk_git_ssh_fingerprint (fingerprint_sha256),
    KEY idx_git_ssh_user (user_id, revoked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS pcd_git_webhook (
    webhook_id CHAR(36) NOT NULL,
    repo_id CHAR(36) NOT NULL,
    webhook_url VARCHAR(2048) NOT NULL,
    secret_ciphertext VARBINARY(1024) NOT NULL,
    events JSON NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_by CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (webhook_id),
    KEY idx_git_webhook_repo (repo_id, active),
    CONSTRAINT fk_git_webhook_repo FOREIGN KEY (repo_id) REFERENCES pcd_git_repository(repo_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS pcd_git_workflow_binding (
    binding_id CHAR(36) NOT NULL,
    repo_id CHAR(36) NOT NULL,
    workflow_id CHAR(36) NOT NULL,
    ref_pattern VARCHAR(255) NOT NULL DEFAULT 'refs/heads/main',
    events JSON NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_by CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (binding_id),
    UNIQUE KEY uk_git_workflow_binding (repo_id, workflow_id, ref_pattern),
    CONSTRAINT fk_git_workflow_repo FOREIGN KEY (repo_id) REFERENCES pcd_git_repository(repo_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS pcd_git_outbox (
    event_id CHAR(36) NOT NULL,
    aggregate_id CHAR(36) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    exchange_name VARCHAR(255) NOT NULL,
    routing_key VARCHAR(255) NOT NULL,
    payload_json JSON NOT NULL,
    outbox_status ENUM('PENDING','PUBLISHING','SENT','FAILED') NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    available_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_error VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    sent_at DATETIME(6) NULL,
    PRIMARY KEY (event_id),
    KEY idx_git_outbox_publish (outbox_status, available_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS pcd_git_audit_log (
    audit_id BIGINT NOT NULL AUTO_INCREMENT,
    repo_id CHAR(36) NOT NULL,
    actor_id CHAR(36) NULL,
    operation VARCHAR(128) NOT NULL,
    client_ip VARCHAR(64) NOT NULL,
    trace_id VARCHAR(128) NULL,
    detail_json JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (audit_id),
    KEY idx_git_audit_repo_time (repo_id, created_at DESC),
    KEY idx_git_audit_actor_time (actor_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
