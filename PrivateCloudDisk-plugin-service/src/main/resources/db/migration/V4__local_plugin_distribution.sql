-- 第二阶段本地插件：短时下载授权与设备绑定分发。
-- 原始插件包仍由不可变存储管理；数据库仅保存摘要签名和不可逆授权摘要。
CREATE TABLE pcd_plugin_download_grant (
    grant_id BINARY(16) NOT NULL,
    token_sha256 BINARY(32) NOT NULL,
    version_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    space_id BINARY(16) NULL,
    client_id VARCHAR(64) NOT NULL,
    max_downloads TINYINT UNSIGNED NOT NULL DEFAULT 1,
    download_count TINYINT UNSIGNED NOT NULL DEFAULT 0,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    consumed_at DATETIME(3) NULL,
    PRIMARY KEY (grant_id),
    UNIQUE KEY uk_plugin_download_token (token_sha256),
    KEY idx_plugin_download_expire (expires_at),
    KEY idx_plugin_download_client (client_id, user_id, created_at),
    CONSTRAINT fk_plugin_download_version FOREIGN KEY (version_id)
        REFERENCES pcd_plugin_version(version_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='本地插件一次性短时下载授权';
