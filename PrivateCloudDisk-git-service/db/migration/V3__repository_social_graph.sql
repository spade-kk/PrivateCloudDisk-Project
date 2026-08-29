-- AUDIT FIX [3.1-3.5] Star/Fork are repository-resource facts, not Space fields.
-- The tables remain inside Git Service and only retain cross-service user/space IDs.
CREATE TABLE IF NOT EXISTS git_repo_star (
    id BIGINT NOT NULL AUTO_INCREMENT,
    repo_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_git_repo_star_user (repo_id, user_id),
    KEY idx_git_repo_star_repo (repo_id, created_at DESC),
    KEY idx_git_repo_star_user (user_id, created_at DESC),
    CONSTRAINT fk_git_repo_star_repo FOREIGN KEY (repo_id) REFERENCES pcd_git_repository(repo_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS git_repo_fork (
    id BIGINT NOT NULL AUTO_INCREMENT,
    repo_id CHAR(36) NOT NULL,
    forked_repo_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_git_repo_fork_child (forked_repo_id),
    KEY idx_git_repo_fork_source (repo_id, created_at DESC),
    KEY idx_git_repo_fork_user (user_id, created_at DESC),
    CONSTRAINT fk_git_repo_fork_source FOREIGN KEY (repo_id) REFERENCES pcd_git_repository(repo_id) ON DELETE CASCADE,
    CONSTRAINT fk_git_repo_fork_child FOREIGN KEY (forked_repo_id) REFERENCES pcd_git_repository(repo_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
