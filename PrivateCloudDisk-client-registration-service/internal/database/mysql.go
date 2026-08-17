package database

import (
	"fmt"
	"time"

	_ "github.com/go-sql-driver/mysql"
	"github.com/jmoiron/sqlx"
	"github.com/privateclouddisk/client-registration-service/internal/config"
)

// NewMySQL 创建 MySQL 连接
func NewMySQL(cfg *config.DatabaseConfig) (*sqlx.DB, error) {
	db, err := sqlx.Connect("mysql", cfg.DSN())
	if err != nil {
		return nil, fmt.Errorf("连接 MySQL 失败: %w", err)
	}

	db.SetMaxOpenConns(cfg.MaxOpenConns)
	db.SetMaxIdleConns(cfg.MaxIdleConns)
	db.SetConnMaxLifetime(cfg.ConnMaxLifetime)

	if err := db.Ping(); err != nil {
		return nil, fmt.Errorf("MySQL Ping 失败: %w", err)
	}

	return db, nil
}

// RunMigrations 执行数据库迁移
func RunMigrations(db *sqlx.DB) error {
	migrations := []string{
		migrationCreateClientIdentities,
		migrationCreateClientUserBindings,
	}

	for _, m := range migrations {
		if _, err := db.Exec(m); err != nil {
			return fmt.Errorf("执行迁移失败: %w", err)
		}
	}

	return nil
}

const migrationCreateClientIdentities = `
CREATE TABLE IF NOT EXISTS pcd_client_identities (
    client_id       VARCHAR(64)     PRIMARY KEY COMMENT '客户端唯一标识（UUID v4）',
    device_id       VARCHAR(128)    NOT NULL COMMENT '设备硬件指纹（SHA-256 哈希）',
    platform        VARCHAR(32)     NOT NULL DEFAULT 'macOS' COMMENT '平台标识',
    app_id          VARCHAR(256)    NOT NULL COMMENT '应用 Bundle ID',
    public_key      TEXT            NOT NULL COMMENT 'ECDSA P-256 公钥（Base64 DER）',
    key_algorithm   VARCHAR(32)     NOT NULL DEFAULT 'ECDSA-P256' COMMENT '密钥算法',
    token_id        VARCHAR(64)     NOT NULL COMMENT '密钥存储位置（SecureEnclave/Keychain）',
    integrity_level VARCHAR(16)     NOT NULL DEFAULT 'medium' COMMENT '完整性等级',
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
`

// 第二阶段本地插件：客户端身份与登录用户绑定独立存表，避免破坏既有身份主表。
const migrationCreateClientUserBindings = `
CREATE TABLE IF NOT EXISTS pcd_client_user_bindings (
    client_id        VARCHAR(64)  NOT NULL,
    user_id          CHAR(36)     NOT NULL,
    client_type      VARCHAR(16)  NOT NULL,
    platform         VARCHAR(16)  NOT NULL,
    app_version      VARCHAR(32)  NOT NULL,
    capabilities_json JSON       NOT NULL,
    status           VARCHAR(16)  NOT NULL DEFAULT 'active',
    bound_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (client_id),
    INDEX idx_client_binding_user (user_id, status),
    CONSTRAINT fk_client_binding_identity FOREIGN KEY (client_id)
        REFERENCES pcd_client_identities(client_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='客户端身份与登录用户绑定，用于本地插件可信分发';
`

// init 确保 time 包被引用
var _ = time.Now
