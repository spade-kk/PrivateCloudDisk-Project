// Package repository 提供数据访问层，封装所有数据库操作。
package repository

import (
	"fmt"
	"log"
	"time"

	"github.com/jmoiron/sqlx"

	"github.com/privateclouddisk/notification-service/internal/domain"
)

// =============================================================================
// TemplateRepo 模板仓库
// =============================================================================
type TemplateRepo struct {
	db *sqlx.DB
}

func NewTemplateRepo(db *sqlx.DB) *TemplateRepo {
	return &TemplateRepo{db: db}
}

// GetByCode 根据模板 CODE + 渠道 + 语言获取模板
func (r *TemplateRepo) GetByCode(code, channel, lang string) (*domain.Template, error) {
	var t domain.Template
	err := r.db.Get(&t, `
		SELECT id, code, name, channel, lang, title, body, html_body,
		       COALESCE(variables_json, '[]') as variables_json, is_active, created_at, updated_at
		FROM pcd_notification_templates
		WHERE code = ? AND channel = ? AND lang = ? AND is_active = 1
		LIMIT 1
	`, code, channel, lang)
	if err != nil {
		return nil, fmt.Errorf("查询模板失败: %w", err)
	}
	return &t, nil
}

// GetByCodeFallback 获取模板，支持语言降级
func (r *TemplateRepo) GetByCodeFallback(code, channel, lang string) (*domain.Template, error) {
	// 优先精确匹配
	t, err := r.GetByCode(code, channel, lang)
	if err == nil {
		return t, nil
	}

	// 降级：去掉区域后缀 (zh-CN → zh)
	if len(lang) > 2 {
		fallback := lang[:2]
		t, err = r.GetByCode(code, channel, fallback)
		if err == nil {
			return t, nil
		}
	}

	// 最终降级：使用默认语言
	return r.GetByCode(code, channel, "zh-CN")
}

// List 获取模板列表
func (r *TemplateRepo) List(channel string, offset, limit int) ([]domain.Template, int, error) {
	var total int
	countSQL := "SELECT COUNT(*) FROM pcd_notification_templates WHERE is_active = 1"
	args := []interface{}{}
	if channel != "" {
		countSQL += " AND channel = ?"
		args = append(args, channel)
	}
	if err := r.db.Get(&total, countSQL, args...); err != nil {
		return nil, 0, err
	}

	querySQL := `
		SELECT id, code, name, channel, lang, title, body, html_body,
		       COALESCE(variables_json, '[]') as variables_json, is_active, created_at, updated_at
		FROM pcd_notification_templates
		WHERE is_active = 1
	`
	queryArgs := []interface{}{}
	if channel != "" {
		querySQL += " AND channel = ?"
		queryArgs = append(queryArgs, channel)
	}
	querySQL += " ORDER BY id DESC LIMIT ? OFFSET ?"
	queryArgs = append(queryArgs, limit, offset)

	var templates []domain.Template
	if err := r.db.Select(&templates, querySQL, queryArgs...); err != nil {
		return nil, 0, err
	}
	return templates, total, nil
}

// Create 创建模板
func (r *TemplateRepo) Create(t *domain.Template) (int64, error) {
	result, err := r.db.Exec(`
		INSERT INTO pcd_notification_templates (code, name, channel, lang, title, body, html_body, variables_json, is_active)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
		ON DUPLICATE KEY UPDATE name=VALUES(name), title=VALUES(title), body=VALUES(body),
			html_body=VALUES(html_body), variables_json=VALUES(variables_json), is_active=VALUES(is_active)
	`, t.Code, t.Name, t.Channel, t.Lang, t.Title, t.Body, t.HTMLBody, t.VariablesJSON, t.IsActive)
	if err != nil {
		return 0, fmt.Errorf("创建模板失败: %w", err)
	}
	return result.LastInsertId()
}

// =============================================================================
// NotificationRepo 通知记录仓库
// =============================================================================
type NotificationRepo struct {
	db *sqlx.DB
}

func NewNotificationRepo(db *sqlx.DB) *NotificationRepo {
	return &NotificationRepo{db: db}
}

// Insert 插入通知记录
func (r *NotificationRepo) Insert(record *domain.NotificationRecord) (int64, error) {
	result, err := r.db.Exec(`
		INSERT INTO pcd_notification_records
			(event_id, user_id, channel, type, title, body, recipient,
			 template_code, status, priority, retry_count, max_retries, aggregation_id)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
	`, record.EventID, record.UserID, record.Channel, record.Type,
		record.Title, record.Body, record.Recipient, record.TemplateCode,
		record.Status, record.Priority, record.RetryCount, record.MaxRetries, record.AggregationID)
	if err != nil {
		return 0, fmt.Errorf("插入通知记录失败: %w", err)
	}
	id, err := result.LastInsertId()
	if err != nil {
		return 0, err
	}
	record.ID = id
	return id, nil
}

// UpdateStatus 更新通知状态
func (r *NotificationRepo) UpdateStatus(id int64, status domain.DeliveryStatus, errMsg string) error {
	_, err := r.db.Exec(`
		UPDATE pcd_notification_records SET status = ?, error_msg = ?, retry_count = retry_count + 1, updated_at = NOW()
		WHERE id = ?
	`, status, errMsg, id)
	return err
}

// MarkSent 标记为已发送
func (r *NotificationRepo) MarkSent(id int64) error {
	_, err := r.db.Exec(`
		UPDATE pcd_notification_records SET status = 'sent', updated_at = NOW() WHERE id = ?
	`, id)
	return err
}

// MarkFailed 标记为失败
func (r *NotificationRepo) MarkFailed(id int64, errMsg string) error {
	_, err := r.db.Exec(`
		UPDATE pcd_notification_records SET status = 'failed', error_msg = ?, updated_at = NOW() WHERE id = ?
	`, errMsg, id)
	return err
}

// ExistsByEventID 检查事件是否已处理（幂等性）
func (r *NotificationRepo) ExistsByEventID(eventID, channel string) (bool, error) {
	var count int
	err := r.db.Get(&count, `
		SELECT COUNT(*) FROM pcd_notification_records WHERE event_id = ? AND channel = ?
	`, eventID, channel)
	return count > 0, err
}

// GetByID 根据 ID 查询通知记录
func (r *NotificationRepo) GetByID(id int64) (*domain.NotificationRecord, error) {
	var record domain.NotificationRecord
	err := r.db.Get(&record, `
		SELECT id, event_id, user_id, channel, type, title, body, recipient,
		       template_code, status, priority, retry_count, max_retries,
		       error_msg, aggregation_id, created_at, updated_at
		FROM pcd_notification_records WHERE id = ?
	`, id)
	if err != nil {
		return nil, fmt.Errorf("查询通知记录失败: %w", err)
	}
	return &record, nil
}

// GetHistory 查询用户通知历史
func (r *NotificationRepo) GetHistory(userID string, offset, limit int) ([]domain.NotificationRecord, int, error) {
	var total int
	if err := r.db.Get(&total, "SELECT COUNT(*) FROM pcd_notification_records WHERE user_id = ?", userID); err != nil {
		return nil, 0, err
	}

	var records []domain.NotificationRecord
	err := r.db.Select(&records, `
		SELECT id, event_id, user_id, channel, type, title, body, recipient,
		       template_code, status, priority, retry_count, max_retries,
		       error_msg, aggregation_id, created_at, updated_at
		FROM pcd_notification_records
		WHERE user_id = ?
		ORDER BY created_at DESC
		LIMIT ? OFFSET ?
	`, userID, limit, offset)
	if err != nil {
		return nil, 0, err
	}
	return records, total, nil
}

// GetPendingAggregated 查询待发送的聚合通知
func (r *NotificationRepo) GetPendingAggregated(aggregationID string) ([]domain.NotificationRecord, error) {
	var records []domain.NotificationRecord
	err := r.db.Select(&records, `
		SELECT id, event_id, user_id, channel, type, title, body, recipient,
		       template_code, status, priority, retry_count, max_retries,
		       error_msg, aggregation_id, created_at, updated_at
		FROM pcd_notification_records
		WHERE aggregation_id = ? AND status = 'aggregated'
		ORDER BY created_at ASC
	`, aggregationID)
	if err != nil {
		return nil, err
	}
	return records, nil
}

// =============================================================================
// DeliveryLogRepo 送达日志仓库
// =============================================================================
type DeliveryLogRepo struct {
	db *sqlx.DB
}

func NewDeliveryLogRepo(db *sqlx.DB) *DeliveryLogRepo {
	return &DeliveryLogRepo{db: db}
}

// Insert 插入送达日志
func (r *DeliveryLogRepo) Insert(log *domain.DeliveryLog) (int64, error) {
	result, err := r.db.Exec(`
		INSERT INTO pcd_notification_delivery_logs
			(notification_id, event_id, channel, status, provider_response, error_msg, duration_ms)
		VALUES (?, ?, ?, ?, ?, ?, ?)
	`, log.NotificationID, log.EventID, log.Channel, log.Status,
		log.ProviderResponse, log.ErrorMsg, log.DurationMs)
	if err != nil {
		return 0, fmt.Errorf("插入送达日志失败: %w", err)
	}
	return result.LastInsertId()
}

// =============================================================================
// PreferenceRepo 用户偏好仓库
// =============================================================================
type PreferenceRepo struct {
	db *sqlx.DB
}

func NewPreferenceRepo(db *sqlx.DB) *PreferenceRepo {
	return &PreferenceRepo{db: db}
}

// GetByUserID 获取用户所有渠道偏好
func (r *PreferenceRepo) GetByUserID(userID string) ([]domain.NotificationPreference, error) {
	var prefs []domain.NotificationPreference
	err := r.db.Select(&prefs, `
		SELECT id, user_id, channel, enabled, dnd_start, dnd_end, dnd_enabled,
		       max_per_day, COALESCE(quiet_hours_json, '[]') as quiet_hours_json, created_at, updated_at
		FROM pcd_notification_preferences
		WHERE user_id = ?
	`, userID)
	if err != nil {
		return nil, fmt.Errorf("查询用户偏好失败: %w", err)
	}
	return prefs, nil
}

// GetByChannel 获取用户特定渠道偏好
func (r *PreferenceRepo) GetByChannel(userID, channel string) (*domain.NotificationPreference, error) {
	var pref domain.NotificationPreference
	err := r.db.Get(&pref, `
		SELECT id, user_id, channel, enabled, dnd_start, dnd_end, dnd_enabled,
		       max_per_day, COALESCE(quiet_hours_json, '[]') as quiet_hours_json, created_at, updated_at
		FROM pcd_notification_preferences
		WHERE user_id = ? AND channel = ?
	`, userID, channel)
	if err != nil {
		return nil, fmt.Errorf("查询渠道偏好失败: %w", err)
	}
	return &pref, nil
}

// Upsert 创建或更新用户偏好
func (r *PreferenceRepo) Upsert(pref *domain.NotificationPreference) error {
	_, err := r.db.Exec(`
		INSERT INTO pcd_notification_preferences (user_id, channel, enabled, dnd_start, dnd_end, dnd_enabled, max_per_day, quiet_hours_json)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?)
		ON DUPLICATE KEY UPDATE
			enabled = VALUES(enabled), dnd_start = VALUES(dnd_start), dnd_end = VALUES(dnd_end),
			dnd_enabled = VALUES(dnd_enabled), max_per_day = VALUES(max_per_day), quiet_hours_json = VALUES(quiet_hours_json)
	`, pref.UserID, pref.Channel, pref.Enabled, pref.DNDStart, pref.DNDEnd,
		pref.DNDEnabled, pref.MaxPerDay, pref.QuietHoursJSON)
	return err
}

// =============================================================================
// DeviceRepo 设备订阅仓库
// =============================================================================
type DeviceRepo struct {
	db *sqlx.DB
}

func NewDeviceRepo(db *sqlx.DB) *DeviceRepo {
	return &DeviceRepo{db: db}
}

// GetByUserID 获取用户所有活跃设备
func (r *DeviceRepo) GetByUserID(userID string) ([]domain.DeviceSubscription, error) {
	var devices []domain.DeviceSubscription
	err := r.db.Select(&devices, `
		SELECT id, user_id, device_token, platform, app_version, is_active, created_at, updated_at
		FROM pcd_notification_device_subscriptions
		WHERE user_id = ? AND is_active = 1
	`, userID)
	if err != nil {
		return nil, fmt.Errorf("查询设备订阅失败: %w", err)
	}
	return devices, nil
}

// GetByPlatform 获取用户特定平台设备
func (r *DeviceRepo) GetByPlatform(userID, platform string) ([]domain.DeviceSubscription, error) {
	var devices []domain.DeviceSubscription
	err := r.db.Select(&devices, `
		SELECT id, user_id, device_token, platform, app_version, is_active, created_at, updated_at
		FROM pcd_notification_device_subscriptions
		WHERE user_id = ? AND platform = ? AND is_active = 1
	`, userID, platform)
	if err != nil {
		return nil, err
	}
	return devices, nil
}

// Upsert 创建或更新设备订阅
func (r *DeviceRepo) Upsert(device *domain.DeviceSubscription) error {
	_, err := r.db.Exec(`
		INSERT INTO pcd_notification_device_subscriptions (user_id, device_token, platform, app_version, is_active)
		VALUES (?, ?, ?, ?, ?)
		ON DUPLICATE KEY UPDATE
			platform = VALUES(platform), app_version = VALUES(app_version),
			is_active = VALUES(is_active), updated_at = NOW()
	`, device.UserID, device.DeviceToken, device.Platform, device.AppVersion, device.IsActive)
	return err
}

// Deactivate 停用设备（用户登出时）
func (r *DeviceRepo) Deactivate(userID, deviceToken string) error {
	_, err := r.db.Exec(`
		UPDATE pcd_notification_device_subscriptions SET is_active = 0, updated_at = NOW()
		WHERE user_id = ? AND device_token = ?
	`, userID, deviceToken)
	return err
}

// =============================================================================
// AggregationRepo 聚合窗口仓库
// =============================================================================
type AggregationRepo struct {
	db *sqlx.DB
}

func NewAggregationRepo(db *sqlx.DB) *AggregationRepo {
	return &AggregationRepo{db: db}
}

// GetOpenWindow 获取当前打开的聚合窗口
func (r *AggregationRepo) GetOpenWindow(userID, channel, notifType string) (*domain.AggregationWindow, error) {
	var win domain.AggregationWindow
	err := r.db.Get(&win, `
		SELECT id, user_id, channel, type, count, status, window_start, window_end, sent_at, created_at
		FROM pcd_notification_aggregation_windows
		WHERE user_id = ? AND channel = ? AND type = ? AND status = 'open'
		ORDER BY window_start DESC
		LIMIT 1
	`, userID, channel, notifType)
	if err != nil {
		return nil, err
	}
	return &win, nil
}

// Create 创建聚合窗口
func (r *AggregationRepo) Create(win *domain.AggregationWindow) error {
	_, err := r.db.Exec(`
		INSERT INTO pcd_notification_aggregation_windows (id, user_id, channel, type, count, status, window_start, window_end)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?)
	`, win.ID, win.UserID, win.Channel, win.Type, win.Count, win.Status, win.WindowStart, win.WindowEnd)
	return err
}

// UpdateCount 更新聚合计数
func (r *AggregationRepo) UpdateCount(id string, count int) error {
	_, err := r.db.Exec(`
		UPDATE pcd_notification_aggregation_windows SET count = ? WHERE id = ?
	`, count, id)
	return err
}

// Close 关闭聚合窗口
func (r *AggregationRepo) Close(id string, recordIDsJSON string) error {
	_, err := r.db.Exec(`
		UPDATE pcd_notification_aggregation_windows
		SET status = 'closed', record_ids = ?, window_end = NOW()
		WHERE id = ?
	`, recordIDsJSON, id)
	return err
}

// MarkSent 标记聚合窗口已发送
func (r *AggregationRepo) MarkSent(id string) error {
	_, err := r.db.Exec(`
		UPDATE pcd_notification_aggregation_windows
		SET status = 'sent', sent_at = NOW()
		WHERE id = ?
	`, id)
	return err
}

// GetExpiredWindows 获取已过期的聚合窗口
func (r *AggregationRepo) GetExpiredWindows() ([]domain.AggregationWindow, error) {
	var windows []domain.AggregationWindow
	err := r.db.Select(&windows, `
		SELECT id, user_id, channel, type, count, status, window_start, window_end, sent_at, created_at
		FROM pcd_notification_aggregation_windows
		WHERE status = 'open' AND window_end <= NOW()
		ORDER BY window_end ASC
		LIMIT 100
	`)
	if err != nil {
		return nil, err
	}
	return windows, nil
}

// =============================================================================
// 定时任务辅助
// =============================================================================

// GetDailyCount 获取用户今日在某渠道的通知数量
func (r *NotificationRepo) GetDailyCount(userID, channel string) (int, error) {
	var count int
	today := time.Now().Format("2006-01-02")
	err := r.db.Get(&count, `
		SELECT COUNT(*) FROM pcd_notification_records
		WHERE user_id = ? AND channel = ? AND DATE(created_at) = ?
	`, userID, channel, today)
	return count, err
}

// CleanupOldLogs 清理过期送达日志
func (r *DeliveryLogRepo) CleanupOldLogs(retentionDays int) (int64, error) {
	cutoff := time.Now().AddDate(0, 0, -retentionDays)
	result, err := r.db.Exec(`
		DELETE FROM pcd_notification_delivery_logs WHERE created_at < ?
	`, cutoff)
	if err != nil {
		return 0, err
	}
	rows, _ := result.RowsAffected()
	log.Printf("[Repository] 清理了 %d 条过期送达日志", rows)
	return rows, nil
}