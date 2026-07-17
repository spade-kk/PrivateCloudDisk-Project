package repository

import (
	"database/sql"
	"fmt"
	"time"

	"github.com/jmoiron/sqlx"
	"github.com/privateclouddisk/client-registration-service/internal/domain"
)

// ClientRepository 客户端身份数据访问层
type ClientRepository struct {
	db *sqlx.DB
}

// NewClientRepository 创建新的 ClientRepository
func NewClientRepository(db *sqlx.DB) *ClientRepository {
	return &ClientRepository{db: db}
}

// ─── 客户端身份 CRUD ────────────────────────────────────────────────────────────

// InsertClient 插入新的客户端身份记录
func (r *ClientRepository) InsertClient(identity *domain.ClientIdentity) error {
	query := `
		INSERT INTO pcd_client_identities (
			client_id, device_id, platform, app_id, public_key,
			key_algorithm, token_id, integrity_level, os_version,
			hostname, status, registered_at, last_verified_at
		) VALUES (
			:client_id, :device_id, :platform, :app_id, :public_key,
			:key_algorithm, :token_id, :integrity_level, :os_version,
			:hostname, :status, :registered_at, :last_verified_at
		)
	`

	_, err := r.db.NamedExec(query, identity)
	if err != nil {
		return fmt.Errorf("插入客户端身份失败: %w", err)
	}

	return nil
}

// GetByClientID 根据客户端 ID 查询身份
func (r *ClientRepository) GetByClientID(clientID string) (*domain.ClientIdentity, error) {
	query := `
		SELECT client_id, device_id, platform, app_id, public_key,
		       key_algorithm, token_id, integrity_level, os_version,
		       hostname, status, registered_at, last_verified_at, created_at
		FROM pcd_client_identities
		WHERE client_id = ?
	`

	var identity domain.ClientIdentity
	err := r.db.Get(&identity, query, clientID)
	if err != nil {
		if err == sql.ErrNoRows {
			return nil, nil
		}
		return nil, fmt.Errorf("查询客户端身份失败: %w", err)
	}

	return &identity, nil
}

// GetPublicKeyByClientID 根据客户端 ID 查询公钥（轻量查询）
func (r *ClientRepository) GetPublicKeyByClientID(clientID string) (*domain.PublicKeyResponse, error) {
	query := `
		SELECT client_id, public_key, key_algorithm, integrity_level, status
		FROM pcd_client_identities
		WHERE client_id = ? AND status = 'active'
	`

	var resp domain.PublicKeyResponse
	err := r.db.Get(&resp, query, clientID)
	if err != nil {
		if err == sql.ErrNoRows {
			return nil, nil
		}
		return nil, fmt.Errorf("查询客户端公钥失败: %w", err)
	}

	return &resp, nil
}

// GetByDeviceID 根据设备指纹查询身份
func (r *ClientRepository) GetByDeviceID(deviceID string) (*domain.ClientIdentity, error) {
	query := `
		SELECT client_id, device_id, platform, app_id, public_key,
		       key_algorithm, token_id, integrity_level, os_version,
		       hostname, status, registered_at, last_verified_at, created_at
		FROM pcd_client_identities
		WHERE device_id = ? AND status = 'active'
	`

	var identity domain.ClientIdentity
	err := r.db.Get(&identity, query, deviceID)
	if err != nil {
		if err == sql.ErrNoRows {
			return nil, nil
		}
		return nil, fmt.Errorf("查询设备身份失败: %w", err)
	}

	return &identity, nil
}

// UpdateStatus 更新客户端状态
func (r *ClientRepository) UpdateStatus(clientID string, status string) error {
	query := `UPDATE pcd_client_identities SET status = ? WHERE client_id = ?`
	_, err := r.db.Exec(query, status, clientID)
	if err != nil {
		return fmt.Errorf("更新客户端状态失败: %w", err)
	}
	return nil
}

// UpdateLastVerified 更新最后验证时间
func (r *ClientRepository) UpdateLastVerified(clientID string) error {
	query := `UPDATE pcd_client_identities SET last_verified_at = ? WHERE client_id = ?`
	_, err := r.db.Exec(query, time.Now(), clientID)
	return err
}