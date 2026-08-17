package repository

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"time"

	"github.com/jmoiron/sqlx"
	"github.com/privateclouddisk/client-registration-service/internal/domain"
)

// ClientRepository 客户端身份数据访问层
type ClientRepository struct {
	db *sqlx.DB
}

// BindUser 将已签名客户端绑定到当前登录用户；同一 client_id 只能有一个有效归属。
func (r *ClientRepository) BindUser(
	clientID, userID, clientType, platform, appVersion string,
	capabilities []string,
) error {
	capabilitiesJSON, err := json.Marshal(capabilities)
	if err != nil {
		return fmt.Errorf("序列化客户端能力失败: %w", err)
	}
	query := `
		INSERT INTO pcd_client_user_bindings(
			client_id, user_id, client_type, platform, app_version, capabilities_json
		) VALUES (?, ?, ?, ?, ?, CAST(? AS JSON))
		ON DUPLICATE KEY UPDATE
			client_type=IF(user_id=VALUES(user_id), VALUES(client_type), client_type),
			platform=IF(user_id=VALUES(user_id), VALUES(platform), platform),
			app_version=IF(user_id=VALUES(user_id), VALUES(app_version), app_version),
			capabilities_json=IF(
				user_id=VALUES(user_id), VALUES(capabilities_json), capabilities_json
			),
			status=IF(user_id=VALUES(user_id), 'active', status)
	`
	_, err = r.db.Exec(query, clientID, userID, clientType, platform, appVersion, capabilitiesJSON)
	if err != nil {
		return fmt.Errorf("绑定客户端用户失败: %w", err)
	}
	return nil
}

// GetUserBinding 仅返回 active 身份与 active 绑定，吊销设备后不能继续分发插件。
func (r *ClientRepository) GetUserBinding(
	clientID, userID string,
) (*domain.ClientUserBinding, error) {
	query := `
		SELECT b.client_id, b.user_id, b.client_type, b.platform,
		       b.app_version, CAST(b.capabilities_json AS CHAR) capabilities_json,
		       b.status, b.bound_at
		  FROM pcd_client_user_bindings b
		  JOIN pcd_client_identities i ON i.client_id=b.client_id
		 WHERE b.client_id=? AND b.user_id=? AND b.status='active' AND i.status='active'
	`
	var binding domain.ClientUserBinding
	if err := r.db.Get(&binding, query, clientID, userID); err != nil {
		if err == sql.ErrNoRows {
			return nil, nil
		}
		return nil, fmt.Errorf("查询客户端用户绑定失败: %w", err)
	}
	if err := json.Unmarshal([]byte(binding.CapabilitiesJSON), &binding.Capabilities); err != nil {
		binding.Capabilities = []string{}
	}
	return &binding, nil
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
