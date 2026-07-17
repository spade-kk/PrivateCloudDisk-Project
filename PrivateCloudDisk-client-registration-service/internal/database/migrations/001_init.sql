-- ============================================
-- PrivateCloudDisk 客户端注册微服务 - 数据库迁移
-- ============================================
-- 在已有数据库中执行此迁移脚本。
-- 如果 database.RunMigrations() 已执行，不需要手动运行。
--
-- 注意：挑战值（Challenge）是临时数据，仅存储在 Redis 中，
-- 利用 Redis TTL 自动过期，不持久化到数据库。

-- 客户端身份表
CREATE TABLE IF NOT EXISTS pcd_client_identities (
    client_id       VARCHAR(64)     PRIMARY KEY COMMENT '客户端唯一标识（UUID v4）',
    device_id       VARCHAR(128)    NOT NULL COMMENT '设备硬件指纹（SHA-256 哈希）',
    platform        VARCHAR(32)     NOT NULL DEFAULT 'macOS' COMMENT '平台标识',
    app_id          VARCHAR(256)    NOT NULL COMMENT '应用 Bundle ID',
    public_key      TEXT            NOT NULL COMMENT 'ECDSA P-256 公钥（Base64 DER）',
    key_algorithm   VARCHAR(32)     NOT NULL DEFAULT 'ECDSA-P256' COMMENT '密钥算法',
    token_id        VARCHAR(64)     NOT NULL COMMENT '密钥存储位置（SecureEnclave/Keychain）',
    integrity_level VARCHAR(16)     NOT NULL DEFAULT 'medium' COMMENT '完整性等级（high/medium/low）',
    os_version      VARCHAR(128)    NOT NULL DEFAULT '' COMMENT '操作系统版本',
    hostname        VARCHAR(256)    NOT NULL DEFAULT '' COMMENT '设备主机名',
    status          VARCHAR(16)     NOT NULL DEFAULT 'active' COMMENT '状态: active/revoked/pending',
    registered_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    last_verified_at DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后验证时间',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_device_id (device_id),
    INDEX idx_app_id (app_id),
    INDEX idx_status (status),
    INDEX idx_registered_at (registered_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='客户端设备身份注册表';