-- =====================================================================
-- [REQ-GIT-AUDIT-4.1~4.25/6.19~6.20] Git 资源可见性与协议安全审计。
-- 原模型只能通过公开空间的通用下载开关推断 Git 访问，无法表达隐藏/私密仓库；
-- 新字段属于 pcd_git_repository，不修改 Space 的 public/resource_type=git 抽象，
-- 以便 Docker、AI 模型等未来资源实现继续复用同一个空间上层边界。
-- =====================================================================
ALTER TABLE pcd_git_repository
    ADD COLUMN repository_visibility ENUM('PUBLIC','HIDDEN','PRIVATE')
        NOT NULL DEFAULT 'PUBLIC' AFTER description;

-- 认证失败、协议拒绝和 SSH 非法命令不能写入仓库业务审计表（仓库可能不存在），
-- 因此单独保留不可变安全审计记录；不设置仓库外键以便软删后仍可追溯。
CREATE TABLE IF NOT EXISTS pcd_git_security_audit_log (
    security_audit_id BIGINT NOT NULL AUTO_INCREMENT,
    repo_id CHAR(36) NULL,
    actor_id CHAR(36) NULL,
    operation VARCHAR(128) NOT NULL,
    client_ip VARCHAR(64) NOT NULL,
    detail_json JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (security_audit_id),
    KEY idx_git_security_audit_time (created_at DESC),
    KEY idx_git_security_audit_repo_time (repo_id, created_at DESC),
    KEY idx_git_security_audit_ip_time (client_ip, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
