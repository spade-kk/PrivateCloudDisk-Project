// Package database 提供 MySQL 数据库连接管理。
package database

import (
	"fmt"
	"log"
	"time"

	"github.com/jmoiron/sqlx"
	_ "github.com/go-sql-driver/mysql"

	"github.com/privateclouddisk/notification-service/internal/config"
)

// DB 全局数据库连接池
var DB *sqlx.DB

// Connect 建立 MySQL 连接池
func Connect(cfg config.MySQLConfig) error {
	var err error
	DB, err = sqlx.Connect("mysql", cfg.DSN())
	if err != nil {
		return fmt.Errorf("MySQL 连接失败: %w", err)
	}

	DB.SetMaxOpenConns(cfg.MaxOpenConns)
	DB.SetMaxIdleConns(cfg.MaxIdleConns)
	DB.SetConnMaxLifetime(cfg.ConnMaxLifetime)

	if err := DB.Ping(); err != nil {
		return fmt.Errorf("MySQL Ping 失败: %w", err)
	}

	log.Printf("[DB] MySQL 连接成功: %s:%d/%s (max_open=%d, max_idle=%d)",
		cfg.Host, cfg.Port, cfg.Database, cfg.MaxOpenConns, cfg.MaxIdleConns)
	return nil
}

// Close 关闭数据库连接
func Close() {
	if DB != nil {
		if err := DB.Close(); err != nil {
			log.Printf("[DB] 关闭 MySQL 连接异常: %v", err)
		} else {
			log.Println("[DB] MySQL 连接已关闭")
		}
	}
}

// IsHealthy 检查数据库连接健康状态
func IsHealthy() bool {
	if DB == nil {
		return false
	}
	return DB.Ping() == nil
}

// RunMigrations 执行数据库迁移（检查核心表是否存在）
func RunMigrations() error {
	tables := []string{
		"pcd_notification_templates",
		"pcd_notification_records",
		"pcd_notification_delivery_logs",
		"pcd_notification_preferences",
		"pcd_notification_device_subscriptions",
		"pcd_notification_aggregation_windows",
	}

	missing := false
	for _, table := range tables {
		var count int
		err := DB.Get(&count, `
			SELECT COUNT(*) FROM information_schema.tables
			WHERE table_schema = DATABASE() AND table_name = ?
		`, table)
		if err != nil {
			return fmt.Errorf("检查表 %s 失败: %w", table, err)
		}
		if count == 0 {
			log.Printf("[DB] 表 %s 不存在，请先执行 migrations/001_init.sql", table)
			missing = true
		}
	}

	if missing {
		log.Println("[DB] 部分表缺失，服务将以降级模式运行")
	} else {
		log.Println("[DB] 所有表已就绪")
	}
	return nil
}

// RetryConnect 带重试的数据库连接
func RetryConnect(cfg config.MySQLConfig, maxRetries int, backoff time.Duration) error {
	var lastErr error
	for i := 0; i < maxRetries; i++ {
		lastErr = Connect(cfg)
		if lastErr == nil {
			return nil
		}
		log.Printf("[DB] 连接失败 (第 %d/%d 次重试): %v", i+1, maxRetries, lastErr)
		time.Sleep(backoff)
		backoff *= 2
	}
	return fmt.Errorf("MySQL 连接失败 (已重试 %d 次): %w", maxRetries, lastErr)
}