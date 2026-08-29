package store

import (
	"context"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path"
	"path/filepath"
	"sort"
	"strings"
	"time"

	_ "github.com/go-sql-driver/mysql"
	"github.com/google/uuid"

	"privateclouddisk/git-service/internal/domain"
)

var (
	ErrNotFound = errors.New("not found")
	ErrLimit    = errors.New("resource limit reached")
)

// outboxLease prevents a process crash after ClaimOutbox from permanently
// stranding an event in PUBLISHING. A reclaimed event may be published twice,
// which is intentional for the documented at-least-once delivery contract.
const outboxLease = 5 * time.Minute

type Store struct{ db *sql.DB }

func Open(ctx context.Context, dsn, migrationPath string, autoMigrate bool) (*Store, error) {
	if !strings.Contains(dsn, "parseTime=") {
		separator := "?"
		if strings.Contains(dsn, "?") {
			separator = "&"
		}
		dsn += separator + "parseTime=true&multiStatements=true&charset=utf8mb4"
	}
	db, err := sql.Open("mysql", dsn)
	if err != nil {
		return nil, err
	}
	db.SetMaxOpenConns(40)
	db.SetMaxIdleConns(10)
	db.SetConnMaxLifetime(30 * time.Minute)
	if err := db.PingContext(ctx); err != nil {
		db.Close()
		return nil, err
	}
	if autoMigrate {
		if err := applyMigrations(ctx, db, migrationPath); err != nil {
			db.Close()
			return nil, err
		}
	}
	return &Store{db: db}, nil
}

// applyMigrations 以文件名记录 Git Service 自己的 schema 版本。
// [REQ-GIT-AUDIT-4.1/7.4] 原行为只执行一个 V1 文件，已经部署的库永远不会接收后续
// 安全字段和审计表；新行为会按 V*.sql 顺序幂等执行，并先把既有 V1 的 IF NOT EXISTS
// 建表纳入版本记录。影响范围仅为 GIT_AUTO_MIGRATE=true 的 Git 独立数据库。
func applyMigrations(ctx context.Context, db *sql.DB, migrationPath string) error {
	if _, err := db.ExecContext(ctx, `CREATE TABLE IF NOT EXISTS pcd_git_schema_migration (
version_name VARCHAR(255) NOT NULL PRIMARY KEY,
applied_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci`); err != nil {
		return fmt.Errorf("initialize git schema migration ledger: %w", err)
	}
	files, err := migrationFiles(migrationPath)
	if err != nil {
		return err
	}
	for _, file := range files {
		name := filepath.Base(file)
		var applied int
		if err := db.QueryRowContext(ctx, `SELECT COUNT(*) FROM pcd_git_schema_migration WHERE version_name=?`, name).Scan(&applied); err != nil {
			return fmt.Errorf("check migration %s: %w", name, err)
		}
		if applied > 0 {
			continue
		}
		migration, err := os.ReadFile(file)
		if err != nil {
			return fmt.Errorf("read migration %s: %w", name, err)
		}
		if _, err := db.ExecContext(ctx, string(migration)); err != nil {
			return fmt.Errorf("apply migration %s: %w", name, err)
		}
		if _, err := db.ExecContext(ctx, `INSERT INTO pcd_git_schema_migration(version_name) VALUES(?)`, name); err != nil {
			return fmt.Errorf("record migration %s: %w", name, err)
		}
	}
	return nil
}

func migrationFiles(migrationPath string) ([]string, error) {
	info, err := os.Stat(migrationPath)
	if err != nil {
		return nil, fmt.Errorf("stat migration path: %w", err)
	}
	directory := migrationPath
	if !info.IsDir() {
		directory = filepath.Dir(migrationPath)
	}
	files, err := filepath.Glob(filepath.Join(directory, "V*__*.sql"))
	if err != nil {
		return nil, fmt.Errorf("discover migrations: %w", err)
	}
	if len(files) == 0 && !info.IsDir() {
		return []string{migrationPath}, nil
	}
	sort.Strings(files)
	return files, nil
}

func (s *Store) Close() error                   { return s.db.Close() }
func (s *Store) Ping(ctx context.Context) error { return s.db.PingContext(ctx) }

func scanRepository(scanner interface{ Scan(...any) error }) (domain.Repository, error) {
	var repo domain.Repository
	err := scanner.Scan(&repo.ID, &repo.SpaceID, &repo.OwnerID, &repo.Name, &repo.Slug,
		&repo.Description, &repo.Visibility, &repo.DefaultBranch, &repo.HashAlgorithm, &repo.Status,
		&repo.ObjectCount, &repo.ObjectBytes, &repo.CreatedAt, &repo.UpdatedAt, &repo.DeletedAt)
	if errors.Is(err, sql.ErrNoRows) {
		return repo, ErrNotFound
	}
	return repo, err
}

const repositoryColumns = `repo_id, space_id, owner_id, repo_name, repo_slug, description,
repository_visibility, default_branch, hash_algorithm, repo_status, object_count, object_bytes, created_at, updated_at, deleted_at`

func (s *Store) CreateRepository(ctx context.Context, repo domain.Repository) (domain.Repository, error) {
	_, err := s.db.ExecContext(ctx, `INSERT INTO pcd_git_repository(
repo_id, space_id, owner_id, repo_name, repo_slug, description, repository_visibility, default_branch, hash_algorithm, repo_status)
VALUES(?,?,?,?,?,?,?,?,?, 'ACTIVE')`, repo.ID, repo.SpaceID, repo.OwnerID, repo.Name, repo.Slug,
		repo.Description, repo.Visibility, repo.DefaultBranch, repo.HashAlgorithm)
	if err != nil {
		return domain.Repository{}, err
	}
	return s.GetRepository(ctx, repo.ID)
}

func (s *Store) GetRepository(ctx context.Context, id string) (domain.Repository, error) {
	return scanRepository(s.db.QueryRowContext(ctx, `SELECT `+repositoryColumns+`
FROM pcd_git_repository WHERE repo_id=? AND repo_status<>'DELETED'`, id))
}

func (s *Store) GetRepositoryBySpace(ctx context.Context, spaceID string) (domain.Repository, error) {
	return scanRepository(s.db.QueryRowContext(ctx, `SELECT `+repositoryColumns+`
FROM pcd_git_repository WHERE space_id=? AND repo_status<>'DELETED'`, spaceID))
}

func (s *Store) GetRepositoryBySlug(ctx context.Context, slug string) (domain.Repository, error) {
	return scanRepository(s.db.QueryRowContext(ctx, `SELECT `+repositoryColumns+`
FROM pcd_git_repository WHERE repo_slug=? AND repo_status<>'DELETED'`, slug))
}

// GetRepositorySocialStats is deliberately separate from repositoryColumns so existing
// repository reads remain compatible with V1/V2 schemas while V3 social data rolls out.
func (s *Store) GetRepositorySocialStats(ctx context.Context, repoID, userID string) (domain.RepositorySocialStats, error) {
	var result domain.RepositorySocialStats
	if err := s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM git_repo_star WHERE repo_id=?`, repoID).Scan(&result.StarCount); err != nil {
		return result, err
	}
	if err := s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM git_repo_fork WHERE repo_id=?`, repoID).Scan(&result.ForkCount); err != nil {
		return result, err
	}
	if userID != "" {
		var count int
		if err := s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM git_repo_star WHERE repo_id=? AND user_id=?`, repoID, userID).Scan(&count); err != nil {
			return result, err
		}
		result.Starred = count > 0
	}
	return result, nil
}

func (s *Store) StarRepository(ctx context.Context, repoID, userID string) error {
	_, err := s.db.ExecContext(ctx, `INSERT IGNORE INTO git_repo_star(repo_id,user_id) VALUES(?,?)`, repoID, userID)
	return err
}

func (s *Store) UnstarRepository(ctx context.Context, repoID, userID string) error {
	_, err := s.db.ExecContext(ctx, `DELETE FROM git_repo_star WHERE repo_id=? AND user_id=?`, repoID, userID)
	return err
}

func (s *Store) CreateForkRecord(ctx context.Context, fork domain.RepositoryFork) error {
	_, err := s.db.ExecContext(ctx, `INSERT INTO git_repo_fork(repo_id,forked_repo_id,user_id) VALUES(?,?,?)`, fork.RepoID, fork.ForkedRepoID, fork.UserID)
	return err
}

func (s *Store) ListStarredRepositoryIDs(ctx context.Context, userID string, limit, offset int) ([]string, error) {
	return s.listRepositoryIDs(ctx, `SELECT repo_id FROM git_repo_star WHERE user_id=? ORDER BY created_at DESC LIMIT ? OFFSET ?`, userID, limit, offset)
}

func (s *Store) ListForkedRepositoryIDs(ctx context.Context, userID string, limit, offset int) ([]string, error) {
	return s.listRepositoryIDs(ctx, `SELECT forked_repo_id FROM git_repo_fork WHERE user_id=? ORDER BY created_at DESC LIMIT ? OFFSET ?`, userID, limit, offset)
}

func (s *Store) listRepositoryIDs(ctx context.Context, query, userID string, limit, offset int) ([]string, error) {
	rows, err := s.db.QueryContext(ctx, query, userID, limit, offset)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	result := make([]string, 0)
	for rows.Next() {
		var id string
		if err := rows.Scan(&id); err != nil {
			return nil, err
		}
		result = append(result, id)
	}
	return result, rows.Err()
}

func (s *Store) SlugExists(ctx context.Context, slug string) (bool, error) {
	var count int
	err := s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM pcd_git_repository WHERE repo_slug=?`, slug).Scan(&count)
	return count > 0, err
}

func (s *Store) UpdateRepository(ctx context.Context, id, name string, description *string, visibility, defaultBranch string) error {
	result, err := s.db.ExecContext(ctx, `UPDATE pcd_git_repository SET
repo_name=COALESCE(NULLIF(?,''),repo_name), description=COALESCE(?,description),
repository_visibility=COALESCE(NULLIF(?,''),repository_visibility),
default_branch=COALESCE(NULLIF(?,''),default_branch) WHERE repo_id=? AND repo_status<>'DELETED'`,
		name, description, visibility, defaultBranch, id)
	if err != nil {
		return err
	}
	if rows, _ := result.RowsAffected(); rows != 1 {
		return ErrNotFound
	}
	return nil
}

func (s *Store) MarkRepositoryStatus(ctx context.Context, id, status string) error {
	_, err := s.db.ExecContext(ctx, `UPDATE pcd_git_repository SET repo_status=? WHERE repo_id=?`, status, id)
	return err
}

func (s *Store) SoftDeleteRepository(ctx context.Context, id string) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	// [REQ-GIT-SPACE-6.4] 原行为只软删仓库，导致全局 Object 引用计数永久偏高；
	// 新行为在同一事务中释放 repo-object 映射，并保留全局零引用对象供异步 GC 安全回收。
	if _, err = tx.ExecContext(ctx, `UPDATE pcd_git_object o JOIN pcd_git_repo_object ro
ON o.algorithm=ro.algorithm AND o.object_hash=ro.object_hash
SET o.reference_count=GREATEST(0,o.reference_count-1) WHERE ro.repo_id=?`, id); err != nil {
		return err
	}
	if _, err = tx.ExecContext(ctx, `DELETE FROM pcd_git_repo_object WHERE repo_id=?`, id); err != nil {
		return err
	}
	if _, err = tx.ExecContext(ctx, `DELETE FROM pcd_git_ref WHERE repo_id=?`, id); err != nil {
		return err
	}
	if _, err = tx.ExecContext(ctx, `DELETE FROM pcd_git_commit_index WHERE repo_id=?`, id); err != nil {
		return err
	}
	result, err := tx.ExecContext(ctx, `UPDATE pcd_git_repository SET repo_status='DELETED',object_count=0,object_bytes=0,deleted_at=NOW(6) WHERE repo_id=? AND repo_status<>'DELETED'`, id)
	if err != nil {
		return err
	}
	if rows, _ := result.RowsAffected(); rows != 1 {
		return ErrNotFound
	}
	return tx.Commit()
}

func (s *Store) ReplaceRefs(ctx context.Context, repoID string, refs []domain.Ref) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if _, err = tx.ExecContext(ctx, `DELETE FROM pcd_git_ref WHERE repo_id=?`, repoID); err != nil {
		return err
	}
	for _, ref := range refs {
		if _, err = tx.ExecContext(ctx, `INSERT INTO pcd_git_ref(repo_id,ref_name,object_hash,ref_type) VALUES(?,?,?,?)`,
			repoID, ref.Name, ref.ObjectHash, ref.Type); err != nil {
			return err
		}
	}
	return tx.Commit()
}

func (s *Store) ListRefs(ctx context.Context, repoID, refType string) ([]domain.Ref, error) {
	query := `SELECT r.ref_name,r.object_hash,r.ref_type,r.updated_at,
EXISTS(SELECT 1 FROM pcd_git_branch_protection p WHERE p.repo_id=r.repo_id AND r.ref_name LIKE REPLACE(p.ref_pattern,'*','%'))
FROM pcd_git_ref r WHERE r.repo_id=?`
	args := []any{repoID}
	if refType != "" {
		query += ` AND r.ref_type=?`
		args = append(args, refType)
	}
	query += ` ORDER BY r.ref_name`
	rows, err := s.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var refs []domain.Ref
	for rows.Next() {
		var ref domain.Ref
		if err := rows.Scan(&ref.Name, &ref.ObjectHash, &ref.Type, &ref.UpdatedAt, &ref.Protected); err != nil {
			return nil, err
		}
		refs = append(refs, ref)
	}
	return refs, rows.Err()
}

// SyncObjects 在同一事务中维护 repo-object 映射和全局引用计数。
// [REQ-GIT-OBJECT-6.4] 相同 hash 只对应一个 storage_path；仓库删除/重写引用时引用计数可追溯。
func (s *Store) SyncObjects(ctx context.Context, repoID, algorithm string, objects []domain.GitObject) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	rows, err := tx.QueryContext(ctx, `SELECT object_hash FROM pcd_git_repo_object WHERE repo_id=? AND algorithm=?`, repoID, algorithm)
	if err != nil {
		return err
	}
	existing := map[string]bool{}
	for rows.Next() {
		var hash string
		if err := rows.Scan(&hash); err != nil {
			rows.Close()
			return err
		}
		existing[hash] = true
	}
	rows.Close()

	incoming := make(map[string]domain.GitObject, len(objects))
	var totalBytes int64
	for _, object := range objects {
		incoming[object.Hash] = object
		totalBytes += object.Size
		_, err = tx.ExecContext(ctx, `INSERT INTO pcd_git_object(
algorithm,object_hash,object_type,object_size,storage_path,reference_count)
VALUES(?,?,?,?,?,0) ON DUPLICATE KEY UPDATE object_type=VALUES(object_type),object_size=VALUES(object_size),storage_path=VALUES(storage_path)`,
			algorithm, object.Hash, object.Type, object.Size, object.StoragePath)
		if err != nil {
			return err
		}
		if !existing[object.Hash] {
			if _, err = tx.ExecContext(ctx, `INSERT INTO pcd_git_repo_object(repo_id,algorithm,object_hash) VALUES(?,?,?)`,
				repoID, algorithm, object.Hash); err != nil {
				return err
			}
			if _, err = tx.ExecContext(ctx, `UPDATE pcd_git_object SET reference_count=reference_count+1 WHERE algorithm=? AND object_hash=?`,
				algorithm, object.Hash); err != nil {
				return err
			}
		}
	}
	for hash := range existing {
		if _, keep := incoming[hash]; keep {
			continue
		}
		if _, err = tx.ExecContext(ctx, `DELETE FROM pcd_git_repo_object WHERE repo_id=? AND algorithm=? AND object_hash=?`,
			repoID, algorithm, hash); err != nil {
			return err
		}
		if _, err = tx.ExecContext(ctx, `UPDATE pcd_git_object SET reference_count=GREATEST(0,reference_count-1) WHERE algorithm=? AND object_hash=?`,
			algorithm, hash); err != nil {
			return err
		}
	}
	if _, err = tx.ExecContext(ctx, `UPDATE pcd_git_repository SET object_count=?,object_bytes=?,repo_status='ACTIVE' WHERE repo_id=?`,
		len(objects), totalBytes, repoID); err != nil {
		return err
	}
	return tx.Commit()
}

func (s *Store) ListRepositoryObjects(ctx context.Context, repoID string) ([]domain.GitObject, error) {
	rows, err := s.db.QueryContext(ctx, `SELECT o.object_hash,o.object_type,o.object_size,o.storage_path
FROM pcd_git_repo_object ro JOIN pcd_git_object o ON o.algorithm=ro.algorithm AND o.object_hash=ro.object_hash
WHERE ro.repo_id=? ORDER BY o.object_hash`, repoID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var result []domain.GitObject
	for rows.Next() {
		var object domain.GitObject
		if err := rows.Scan(&object.Hash, &object.Type, &object.Size, &object.StoragePath); err != nil {
			return nil, err
		}
		result = append(result, object)
	}
	return result, rows.Err()
}

func (s *Store) ReplaceCommitIndex(ctx context.Context, repoID string, commits []domain.Commit) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if _, err := tx.ExecContext(ctx, `DELETE FROM pcd_git_commit_index WHERE repo_id=?`, repoID); err != nil {
		return err
	}
	for _, commit := range commits {
		parents, _ := json.Marshal(commit.Parents)
		if _, err := tx.ExecContext(ctx, `INSERT INTO pcd_git_commit_index(
repo_id,commit_hash,tree_hash,parent_hashes,author_name,author_email,authored_at,
committer_name,committed_at,subject,message_text) VALUES(?,?,?,?,?,?,?,?,?,?,?)`,
			repoID, commit.Hash, commit.TreeHash, parents, commit.AuthorName, commit.AuthorEmail,
			commit.AuthoredAt, commit.Committer, commit.CommittedAt, commit.Subject, commit.Message); err != nil {
			return err
		}
	}
	return tx.Commit()
}

func (s *Store) GetPermissionLevel(ctx context.Context, repoID, userID string) (string, error) {
	var level string
	err := s.db.QueryRowContext(ctx, `SELECT permission_level FROM pcd_git_permission
WHERE repo_id=? AND subject_type='USER' AND subject_id=?`, repoID, userID).Scan(&level)
	if errors.Is(err, sql.ErrNoRows) {
		return domain.PermissionNone, nil
	}
	return level, err
}

// ListTeamPermissions 返回仓库显式 TEAM 授权；成员关系由 Platform 实时校验。
func (s *Store) ListTeamPermissions(ctx context.Context, repoID string) ([]domain.Permission, error) {
	rows, err := s.db.QueryContext(ctx, `SELECT permission_id,repo_id,subject_id,subject_type,permission_level,created_at
FROM pcd_git_permission WHERE repo_id=? AND subject_type='TEAM' ORDER BY created_at`, repoID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var result []domain.Permission
	for rows.Next() {
		var item domain.Permission
		if err := rows.Scan(&item.ID, &item.RepoID, &item.SubjectID, &item.Type, &item.Level, &item.CreatedAt); err != nil {
			return nil, err
		}
		result = append(result, item)
	}
	return result, rows.Err()
}

func (s *Store) UpsertPermission(ctx context.Context, repoID, subjectType, subjectID, level, grantedBy string) error {
	_, err := s.db.ExecContext(ctx, `INSERT INTO pcd_git_permission(repo_id,subject_type,subject_id,permission_level,granted_by)
VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE permission_level=VALUES(permission_level),granted_by=VALUES(granted_by)`,
		repoID, subjectType, subjectID, level, grantedBy)
	return err
}

func (s *Store) DeletePermission(ctx context.Context, repoID, subjectID string) error {
	_, err := s.db.ExecContext(ctx, `DELETE FROM pcd_git_permission WHERE repo_id=? AND subject_id=?`, repoID, subjectID)
	return err
}

func (s *Store) ListPermissions(ctx context.Context, repoID string) ([]domain.Permission, error) {
	rows, err := s.db.QueryContext(ctx, `SELECT permission_id,repo_id,subject_id,subject_type,permission_level,created_at
FROM pcd_git_permission WHERE repo_id=? ORDER BY created_at`, repoID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var result []domain.Permission
	for rows.Next() {
		var item domain.Permission
		if err := rows.Scan(&item.ID, &item.RepoID, &item.SubjectID, &item.Type, &item.Level, &item.CreatedAt); err != nil {
			return nil, err
		}
		result = append(result, item)
	}
	return result, rows.Err()
}

// [FIX-GIT-CREDENTIAL-CONTRACT-20260816] 凭证 DTO 是管理 API 的出参，必须显式声明
// 与 Web 端 GitPAT/GitSSHKey 模型一致的 camelCase 字段。原实现依赖 encoding/json
// 的默认字段名，实际返回 ID/Prefix/Scopes 等大写键，导致前端 pat.scopes 为 undefined，
// 进而在渲染 scopes.join 时触发 Unhandled Promise Rejection。UserID 仅用于服务端查询，
// 不属于凭证管理接口的公开字段。
type TokenRecord struct {
	ID         string
	UserID     string
	Name       string
	Prefix     string
	Scopes     []string   `json:"scopes"`
	ExpiresAt  *time.Time `json:"expiresAt"`
	LastUsedAt *time.Time `json:"lastUsedAt"`
	CreatedAt  time.Time  `json:"createdAt"`
}

// [FIX-GIT-CREDENTIAL-CONTRACT-20260816] UserID 是内部关联字段，不向浏览器暴露；其余
// 字段与前端 GitSSHKey 保持稳定命名。PublicKey 仅在创建响应中使用，列表接口仍会清空。
func (record TokenRecord) MarshalJSON() ([]byte, error) {
	type tokenRecordResponse struct {
		ID         string     `json:"tokenId"`
		Name       string     `json:"name"`
		Prefix     string     `json:"tokenPrefix"`
		Scopes     []string   `json:"scopes"`
		ExpiresAt  *time.Time `json:"expiresAt"`
		LastUsedAt *time.Time `json:"lastUsedAt"`
		CreatedAt  time.Time  `json:"createdAt"`
	}
	return json.Marshal(tokenRecordResponse{
		ID: record.ID, Name: record.Name, Prefix: record.Prefix, Scopes: record.Scopes,
		ExpiresAt: record.ExpiresAt, LastUsedAt: record.LastUsedAt, CreatedAt: record.CreatedAt,
	})
}

func (s *Store) CreatePAT(ctx context.Context, userID, name, tokenHash, prefix string, scopes []string, expiresAt *time.Time) (TokenRecord, error) {
	id := uuid.NewString()
	scopesJSON, _ := json.Marshal(scopes)
	_, err := s.db.ExecContext(ctx, `INSERT INTO pcd_git_personal_access_token(
token_id,user_id,token_name,token_prefix,token_hash,scopes,expires_at) VALUES(?,?,?,?,?,?,?)`,
		id, userID, name, prefix, tokenHash, scopesJSON, expiresAt)
	return TokenRecord{ID: id, UserID: userID, Name: name, Prefix: prefix, Scopes: scopes, ExpiresAt: expiresAt, CreatedAt: time.Now().UTC()}, err
}

func (s *Store) AuthenticatePAT(ctx context.Context, token string) (string, []string, error) {
	digest := sha256.Sum256([]byte(token))
	hash := hex.EncodeToString(digest[:])
	var userID string
	var scopesJSON []byte
	err := s.db.QueryRowContext(ctx, `SELECT user_id,scopes FROM pcd_git_personal_access_token
WHERE token_hash=? AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at>NOW(6))`, hash).Scan(&userID, &scopesJSON)
	if errors.Is(err, sql.ErrNoRows) {
		return "", nil, ErrNotFound
	}
	if err != nil {
		return "", nil, err
	}
	_, _ = s.db.ExecContext(ctx, `UPDATE pcd_git_personal_access_token SET last_used_at=NOW(6) WHERE token_hash=?`, hash)
	var scopes []string
	_ = json.Unmarshal(scopesJSON, &scopes)
	return userID, scopes, nil
}

func (s *Store) ListPATs(ctx context.Context, userID string) ([]TokenRecord, error) {
	rows, err := s.db.QueryContext(ctx, `SELECT token_id,user_id,token_name,token_prefix,scopes,expires_at,last_used_at,created_at
FROM pcd_git_personal_access_token WHERE user_id=? AND revoked_at IS NULL ORDER BY created_at DESC`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var result []TokenRecord
	for rows.Next() {
		var record TokenRecord
		var scopesJSON []byte
		if err := rows.Scan(&record.ID, &record.UserID, &record.Name, &record.Prefix, &scopesJSON,
			&record.ExpiresAt, &record.LastUsedAt, &record.CreatedAt); err != nil {
			return nil, err
		}
		_ = json.Unmarshal(scopesJSON, &record.Scopes)
		result = append(result, record)
	}
	return result, rows.Err()
}

func (s *Store) RevokePAT(ctx context.Context, userID, tokenID string) error {
	result, err := s.db.ExecContext(ctx, `UPDATE pcd_git_personal_access_token SET revoked_at=NOW(6)
WHERE token_id=? AND user_id=? AND revoked_at IS NULL`, tokenID, userID)
	if err != nil {
		return err
	}
	if rows, _ := result.RowsAffected(); rows != 1 {
		return ErrNotFound
	}
	return nil
}

type SSHKeyRecord struct {
	ID, UserID, Name, PublicKey, Fingerprint string
	LastUsedAt                               *time.Time
	CreatedAt                                time.Time
}

// [FIX-GIT-CREDENTIAL-CONTRACT-20260816] SSH 凭证同样使用显式响应映射，避免默认 JSON
// 序列化返回 ID/Name/Fingerprint，导致 Web 端 keyId/keyName 无法读取。UserID 不出参。
func (record SSHKeyRecord) MarshalJSON() ([]byte, error) {
	type sshKeyRecordResponse struct {
		ID          string     `json:"keyId"`
		Name        string     `json:"keyName"`
		PublicKey   string     `json:"publicKey,omitempty"`
		Fingerprint string     `json:"fingerprint"`
		LastUsedAt  *time.Time `json:"lastUsedAt"`
		CreatedAt   time.Time  `json:"createdAt"`
	}
	return json.Marshal(sshKeyRecordResponse{
		ID: record.ID, Name: record.Name, PublicKey: record.PublicKey, Fingerprint: record.Fingerprint,
		LastUsedAt: record.LastUsedAt, CreatedAt: record.CreatedAt,
	})
}

// CreateSSHKey 在写入事务内锁定该用户的活跃密钥范围，防止两个并发请求都在控制器
// 查询到 19 个密钥后各自插入第 20/21 个。调用方传入上限而不是在 Store 硬编码策略，
// 让未来租户套餐可以使用不同配额。
// [REQ-GIT-AUDIT-4.8/6.15] 原行为在 API 层先查询再插入，存在 TOCTOU 竞态；新行为
// 将计数与写入合并到同一数据库事务。影响范围仅为新增 SSH 公钥，不影响已保存密钥。
func (s *Store) CreateSSHKey(ctx context.Context, userID, name, publicKey, fingerprint string, maxActive int) (SSHKeyRecord, error) {
	record := SSHKeyRecord{ID: uuid.NewString(), UserID: userID, Name: name, PublicKey: publicKey, Fingerprint: fingerprint, CreatedAt: time.Now().UTC()}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return record, err
	}
	defer tx.Rollback()
	rows, err := tx.QueryContext(ctx, `SELECT key_id FROM pcd_git_ssh_key WHERE user_id=? AND revoked_at IS NULL FOR UPDATE`, userID)
	if err != nil {
		return record, err
	}
	active := 0
	for rows.Next() {
		active++
	}
	if err := rows.Close(); err != nil {
		return record, err
	}
	if err := rows.Err(); err != nil {
		return record, err
	}
	if maxActive > 0 && active >= maxActive {
		return record, ErrLimit
	}
	_, err = tx.ExecContext(ctx, `INSERT INTO pcd_git_ssh_key(key_id,user_id,key_name,public_key,fingerprint_sha256) VALUES(?,?,?,?,?)`,
		record.ID, userID, name, publicKey, fingerprint)
	if err != nil {
		return record, err
	}
	if err := tx.Commit(); err != nil {
		return record, err
	}
	return record, nil
}

func (s *Store) AuthenticateSSHKey(ctx context.Context, fingerprint string) (string, error) {
	var userID string
	err := s.db.QueryRowContext(ctx, `SELECT user_id FROM pcd_git_ssh_key WHERE fingerprint_sha256=? AND revoked_at IS NULL`, fingerprint).Scan(&userID)
	if errors.Is(err, sql.ErrNoRows) {
		return "", ErrNotFound
	}
	if err == nil {
		_, _ = s.db.ExecContext(ctx, `UPDATE pcd_git_ssh_key SET last_used_at=NOW(6) WHERE fingerprint_sha256=?`, fingerprint)
	}
	return userID, err
}

func (s *Store) ListSSHKeys(ctx context.Context, userID string) ([]SSHKeyRecord, error) {
	rows, err := s.db.QueryContext(ctx, `SELECT key_id,user_id,key_name,public_key,fingerprint_sha256,last_used_at,created_at
FROM pcd_git_ssh_key WHERE user_id=? AND revoked_at IS NULL ORDER BY created_at DESC`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var result []SSHKeyRecord
	for rows.Next() {
		var record SSHKeyRecord
		if err := rows.Scan(&record.ID, &record.UserID, &record.Name, &record.PublicKey, &record.Fingerprint, &record.LastUsedAt, &record.CreatedAt); err != nil {
			return nil, err
		}
		result = append(result, record)
	}
	return result, rows.Err()
}

func (s *Store) RevokeSSHKey(ctx context.Context, userID, keyID string) error {
	result, err := s.db.ExecContext(ctx, `UPDATE pcd_git_ssh_key SET revoked_at=NOW(6) WHERE key_id=? AND user_id=? AND revoked_at IS NULL`, keyID, userID)
	if err != nil {
		return err
	}
	if rows, _ := result.RowsAffected(); rows != 1 {
		return ErrNotFound
	}
	return nil
}

func (s *Store) CreateMergeRequest(ctx context.Context, mr domain.MergeRequest) (domain.MergeRequest, error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return mr, err
	}
	defer tx.Rollback()
	if err := tx.QueryRowContext(ctx, `SELECT COALESCE(MAX(repo_number),0)+1 FROM pcd_git_merge_request WHERE repo_id=? FOR UPDATE`, mr.RepoID).Scan(&mr.Number); err != nil {
		return mr, err
	}
	_, err = tx.ExecContext(ctx, `INSERT INTO pcd_git_merge_request(
merge_request_id,repo_id,repo_number,title,description,source_branch,target_branch,author_id,merge_strategy)
VALUES(?,?,?,?,?,?,?,?,?)`, mr.ID, mr.RepoID, mr.Number, mr.Title, mr.Description, mr.SourceBranch, mr.TargetBranch, mr.AuthorID, mr.MergeStrategy)
	if err != nil {
		return mr, err
	}
	mr.Status, mr.ApprovalStatus = "OPEN", "PENDING"
	mr.CreatedAt, mr.UpdatedAt = time.Now().UTC(), time.Now().UTC()
	return mr, tx.Commit()
}

func (s *Store) ListMergeRequests(ctx context.Context, repoID, statusFilter string) ([]domain.MergeRequest, error) {
	query := `SELECT merge_request_id,repo_id,repo_number,title,description,source_branch,target_branch,author_id,
mr_status,approval_status,merge_strategy,merged_by,merged_at,created_at,updated_at FROM pcd_git_merge_request WHERE repo_id=?`
	args := []any{repoID}
	if statusFilter != "" {
		query += ` AND mr_status=?`
		args = append(args, statusFilter)
	}
	query += ` ORDER BY repo_number DESC`
	rows, err := s.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var result []domain.MergeRequest
	for rows.Next() {
		mr, err := scanMR(rows)
		if err != nil {
			return nil, err
		}
		result = append(result, mr)
	}
	return result, rows.Err()
}

func (s *Store) GetMergeRequest(ctx context.Context, repoID, mrID string) (domain.MergeRequest, error) {
	mr, err := scanMR(s.db.QueryRowContext(ctx, `SELECT merge_request_id,repo_id,repo_number,title,description,
source_branch,target_branch,author_id,mr_status,approval_status,merge_strategy,merged_by,merged_at,created_at,updated_at
FROM pcd_git_merge_request WHERE repo_id=? AND merge_request_id=?`, repoID, mrID))
	if errors.Is(err, sql.ErrNoRows) {
		return mr, ErrNotFound
	}
	return mr, err
}

func scanMR(scanner interface{ Scan(...any) error }) (domain.MergeRequest, error) {
	var mr domain.MergeRequest
	err := scanner.Scan(&mr.ID, &mr.RepoID, &mr.Number, &mr.Title, &mr.Description, &mr.SourceBranch,
		&mr.TargetBranch, &mr.AuthorID, &mr.Status, &mr.ApprovalStatus, &mr.MergeStrategy,
		&mr.MergedBy, &mr.MergedAt, &mr.CreatedAt, &mr.UpdatedAt)
	return mr, err
}

func (s *Store) UpsertApproval(ctx context.Context, mrID, reviewerID, decision string) error {
	_, err := s.db.ExecContext(ctx, `INSERT INTO pcd_git_merge_request_approval(merge_request_id,reviewer_id,decision)
VALUES(?,?,?) ON DUPLICATE KEY UPDATE decision=VALUES(decision),created_at=NOW(6)`, mrID, reviewerID, decision)
	if err != nil {
		return err
	}
	status := "CHANGES_REQUESTED"
	if decision == "APPROVED" {
		status = "APPROVED"
	}
	_, err = s.db.ExecContext(ctx, `UPDATE pcd_git_merge_request SET approval_status=? WHERE merge_request_id=?`, status, mrID)
	return err
}

// MergeApprovalRequirement 依据目标 ref 匹配保护规则，并返回最严格审批数。
// [REQ-GIT-SPACE-8.1/8.3] 匹配在 Go 中执行，避免把 Git glob 误当 SQL LIKE，尤其避免 '_'/'%' 语义漂移。
func (s *Store) MergeApprovalRequirement(ctx context.Context, repoID, targetRef string) (int, error) {
	rows, err := s.db.QueryContext(ctx, `SELECT ref_pattern,required_approvals FROM pcd_git_branch_protection
WHERE repo_id=? AND require_merge_request=1`, repoID)
	if err != nil {
		return 0, err
	}
	defer rows.Close()
	required := 0
	for rows.Next() {
		var pattern string
		var approvals int
		if err := rows.Scan(&pattern, &approvals); err != nil {
			return 0, err
		}
		if matched, matchErr := path.Match(pattern, targetRef); matchErr == nil && matched && approvals > required {
			required = approvals
		}
	}
	return required, rows.Err()
}

func (s *Store) MergeReviewSummary(ctx context.Context, mrID string) (approved, changesRequested int, err error) {
	err = s.db.QueryRowContext(ctx, `SELECT
COALESCE(SUM(decision='APPROVED'),0),COALESCE(SUM(decision='CHANGES_REQUESTED'),0)
FROM pcd_git_merge_request_approval WHERE merge_request_id=?`, mrID).Scan(&approved, &changesRequested)
	return
}

func (s *Store) MarkMerged(ctx context.Context, mrID, userID string) error {
	_, err := s.db.ExecContext(ctx, `UPDATE pcd_git_merge_request SET mr_status='MERGED',merged_by=?,merged_at=NOW(6) WHERE merge_request_id=? AND mr_status='OPEN'`, userID, mrID)
	return err
}

func (s *Store) AddMRComment(ctx context.Context, mrID, userID, body string) (string, error) {
	id := uuid.NewString()
	_, err := s.db.ExecContext(ctx, `INSERT INTO pcd_git_merge_request_comment(comment_id,merge_request_id,author_id,body) VALUES(?,?,?,?)`, id, mrID, userID, body)
	return id, err
}

func (s *Store) ListMRComments(ctx context.Context, mrID string) ([]domain.MergeRequestComment, error) {
	rows, err := s.db.QueryContext(ctx, `SELECT comment_id,merge_request_id,author_id,body,created_at
FROM pcd_git_merge_request_comment WHERE merge_request_id=? ORDER BY created_at`, mrID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	comments := make([]domain.MergeRequestComment, 0)
	for rows.Next() {
		var item domain.MergeRequestComment
		if err := rows.Scan(&item.ID, &item.MRID, &item.AuthorID, &item.Body, &item.CreatedAt); err != nil {
			return nil, err
		}
		comments = append(comments, item)
	}
	return comments, rows.Err()
}

func (s *Store) UpsertBranchProtection(ctx context.Context, repoID, pattern string, requireMR bool, approvals int, allowForce bool, actor string) error {
	_, err := s.db.ExecContext(ctx, `INSERT INTO pcd_git_branch_protection(
repo_id,ref_pattern,require_merge_request,required_approvals,allow_force_push,created_by)
VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE require_merge_request=VALUES(require_merge_request),
required_approvals=VALUES(required_approvals),allow_force_push=VALUES(allow_force_push),created_by=VALUES(created_by)`,
		repoID, pattern, requireMR, approvals, allowForce, actor)
	return err
}

func (s *Store) ListProtectedPatterns(ctx context.Context, repoID string) ([]string, error) {
	rows, err := s.db.QueryContext(ctx, `SELECT ref_pattern FROM pcd_git_branch_protection WHERE repo_id=? AND require_merge_request=1`, repoID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var result []string
	for rows.Next() {
		var value string
		if err := rows.Scan(&value); err != nil {
			return nil, err
		}
		result = append(result, value)
	}
	sort.Strings(result)
	return result, rows.Err()
}

func (s *Store) CreateWebhook(ctx context.Context, hook domain.Webhook, secret []byte, actor string) error {
	events, _ := json.Marshal(hook.Events)
	_, err := s.db.ExecContext(ctx, `INSERT INTO pcd_git_webhook(webhook_id,repo_id,webhook_url,secret_ciphertext,events,created_by) VALUES(?,?,?,?,?,?)`,
		hook.ID, hook.RepoID, hook.URL, secret, events, actor)
	return err
}

type WebhookSecret struct {
	domain.Webhook
	Secret []byte
}

func (s *Store) ListWebhooks(ctx context.Context, repoID string, includeSecret bool) ([]WebhookSecret, error) {
	rows, err := s.db.QueryContext(ctx, `SELECT webhook_id,repo_id,webhook_url,events,active,created_at,secret_ciphertext
FROM pcd_git_webhook WHERE repo_id=? ORDER BY created_at`, repoID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var result []WebhookSecret
	for rows.Next() {
		var item WebhookSecret
		var eventsJSON []byte
		if err := rows.Scan(&item.ID, &item.RepoID, &item.URL, &eventsJSON, &item.Active, &item.CreatedAt, &item.Secret); err != nil {
			return nil, err
		}
		_ = json.Unmarshal(eventsJSON, &item.Events)
		if !includeSecret {
			item.Secret = nil
		}
		result = append(result, item)
	}
	return result, rows.Err()
}

func (s *Store) DeleteWebhook(ctx context.Context, repoID, webhookID string) error {
	_, err := s.db.ExecContext(ctx, `DELETE FROM pcd_git_webhook WHERE repo_id=? AND webhook_id=?`, repoID, webhookID)
	return err
}

func (s *Store) CreateWorkflowBinding(ctx context.Context, binding domain.WorkflowBinding, actor string) error {
	events, _ := json.Marshal(binding.Events)
	_, err := s.db.ExecContext(ctx, `INSERT INTO pcd_git_workflow_binding(binding_id,repo_id,workflow_id,ref_pattern,events,enabled,created_by)
VALUES(?,?,?,?,?,?,?)`, binding.ID, binding.RepoID, binding.WorkflowID, binding.RefPattern, events, binding.Enabled, actor)
	return err
}

func (s *Store) ListWorkflowBindings(ctx context.Context, repoID string) ([]domain.WorkflowBinding, error) {
	rows, err := s.db.QueryContext(ctx, `SELECT binding_id,repo_id,workflow_id,ref_pattern,events,enabled FROM pcd_git_workflow_binding WHERE repo_id=?`, repoID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var result []domain.WorkflowBinding
	for rows.Next() {
		var item domain.WorkflowBinding
		var eventsJSON []byte
		if err := rows.Scan(&item.ID, &item.RepoID, &item.WorkflowID, &item.RefPattern, &eventsJSON, &item.Enabled); err != nil {
			return nil, err
		}
		_ = json.Unmarshal(eventsJSON, &item.Events)
		result = append(result, item)
	}
	return result, rows.Err()
}

func (s *Store) InsertAudit(ctx context.Context, repoID string, actorID *string, operation, ip, traceID string, detail any) error {
	payload, _ := json.Marshal(detail)
	_, err := s.db.ExecContext(ctx, `INSERT INTO pcd_git_audit_log(repo_id,actor_id,operation,client_ip,trace_id,detail_json)
VALUES(?,?,?,?,?,?)`, repoID, actorID, operation, ip, traceID, payload)
	return err
}

// InsertSecurityAudit 保留认证失败、隐藏仓库探测和非法 SSH 命令等安全事件。
// [REQ-GIT-AUDIT-4.19/6.20] 原业务审计表要求有效 repo_id，导致未知仓库和认证前失败
// 无法追溯；新表允许空 repo_id，且调用方只传认证状态/原因，绝不写入 PAT 明文。
// 影响范围只增加安全审计，不改变原有仓库操作审计数据。
func (s *Store) InsertSecurityAudit(ctx context.Context, repoID, actorID *string, operation, ip string, detail any) error {
	payload, err := json.Marshal(detail)
	if err != nil {
		return err
	}
	_, err = s.db.ExecContext(ctx, `INSERT INTO pcd_git_security_audit_log(repo_id,actor_id,operation,client_ip,detail_json)
VALUES(?,?,?,?,?)`, repoID, actorID, operation, ip, payload)
	return err
}

func (s *Store) ListAudit(ctx context.Context, repoID string, limit, offset int) ([]domain.AuditEntry, error) {
	rows, err := s.db.QueryContext(ctx, `SELECT audit_id,repo_id,actor_id,operation,client_ip,detail_json,created_at
FROM pcd_git_audit_log WHERE repo_id=? ORDER BY created_at DESC LIMIT ? OFFSET ?`, repoID, limit, offset)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var result []domain.AuditEntry
	for rows.Next() {
		var item domain.AuditEntry
		if err := rows.Scan(&item.ID, &item.RepoID, &item.ActorID, &item.Operation, &item.IP, &item.Detail, &item.CreatedAt); err != nil {
			return nil, err
		}
		result = append(result, item)
	}
	return result, rows.Err()
}

func (s *Store) InsertOutbox(ctx context.Context, aggregateID, eventType, exchange, routingKey string, payload any) error {
	encoded, err := json.Marshal(payload)
	if err != nil {
		return err
	}
	_, err = s.db.ExecContext(ctx, `INSERT INTO pcd_git_outbox(event_id,aggregate_id,event_type,exchange_name,routing_key,payload_json)
VALUES(?,?,?,?,?,?)`, uuid.NewString(), aggregateID, eventType, exchange, routingKey, encoded)
	return err
}

func (s *Store) ClaimOutbox(ctx context.Context, limit int) ([]domain.OutboxEvent, error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, err
	}
	defer tx.Rollback()
	rows, err := tx.QueryContext(ctx, `SELECT event_id,aggregate_id,event_type,exchange_name,routing_key,payload_json,attempt_count
FROM pcd_git_outbox WHERE outbox_status IN ('PENDING','FAILED','PUBLISHING') AND available_at<=NOW(6)
ORDER BY created_at LIMIT ? FOR UPDATE SKIP LOCKED`, limit)
	if err != nil {
		return nil, err
	}
	var events []domain.OutboxEvent
	for rows.Next() {
		var event domain.OutboxEvent
		if err := rows.Scan(&event.ID, &event.Aggregate, &event.EventType, &event.Exchange, &event.RoutingKey, &event.Payload, &event.Attempts); err != nil {
			rows.Close()
			return nil, err
		}
		events = append(events, event)
	}
	rows.Close()
	leaseUntil := time.Now().UTC().Add(outboxLease)
	for _, event := range events {
		if _, err := tx.ExecContext(ctx, `UPDATE pcd_git_outbox SET outbox_status='PUBLISHING',available_at=? WHERE event_id=?`, leaseUntil, event.ID); err != nil {
			return nil, err
		}
	}
	return events, tx.Commit()
}

func (s *Store) MarkOutboxSent(ctx context.Context, id string) error {
	_, err := s.db.ExecContext(ctx, `UPDATE pcd_git_outbox SET outbox_status='SENT',sent_at=NOW(6) WHERE event_id=?`, id)
	return err
}

func (s *Store) MarkOutboxFailed(ctx context.Context, id, message string, attempts int) error {
	delay := time.Duration(1<<min(attempts, 8)) * time.Second
	_, err := s.db.ExecContext(ctx, `UPDATE pcd_git_outbox SET outbox_status='FAILED',attempt_count=attempt_count+1,
available_at=?,last_error=? WHERE event_id=?`, time.Now().Add(delay), truncate(message, 500), id)
	return err
}

func truncate(value string, limit int) string {
	if len(value) <= limit {
		return value
	}
	return value[:limit]
}
