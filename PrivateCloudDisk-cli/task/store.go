package task

import (
	"database/sql"
	"fmt"
	"os"
	"sync"
	"time"

	"github.com/privateclouddisk/cli/config"
	_ "github.com/mattn/go-sqlite3"
)

// Store 任务持久化存储 (SQLite)
type Store struct {
	db *sql.DB
	mu sync.RWMutex
}

// NewStore 创建任务存储
func NewStore() (*Store, error) {
	dbPath := config.TaskDBFile()
	os.MkdirAll(config.ConfigDir(), 0700)

	db, err := sql.Open("sqlite3", dbPath)
	if err != nil {
		return nil, fmt.Errorf("打开任务数据库失败: %w", err)
	}

	// 优化 SQLite 设置
	db.SetMaxOpenConns(1) // SQLite 单写
	db.SetMaxIdleConns(1)
	db.Exec("PRAGMA journal_mode=WAL")
	db.Exec("PRAGMA synchronous=NORMAL")
	db.Exec("PRAGMA foreign_keys=ON")

	store := &Store{db: db}
	if err := store.migrate(); err != nil {
		return nil, fmt.Errorf("数据库迁移失败: %w", err)
	}

	return store, nil
}

// migrate 创建表结构
func (s *Store) migrate() error {
	query := `
	CREATE TABLE IF NOT EXISTS tasks (
		id INTEGER PRIMARY KEY AUTOINCREMENT,
		type TEXT NOT NULL CHECK(type IN ('UPLOAD', 'DOWNLOAD', 'SYNC')),
		status TEXT NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','RUNNING','PAUSED','COMPLETED','FAILED','CANCELLED')),
		source TEXT NOT NULL,
		destination TEXT NOT NULL,
		file_name TEXT NOT NULL,
		file_size INTEGER NOT NULL DEFAULT 0,
		progress INTEGER NOT NULL DEFAULT 0,
		total_chunks INTEGER NOT NULL DEFAULT 0,
		done_chunks INTEGER NOT NULL DEFAULT 0,
		uploads_id TEXT DEFAULT '',
		file_id TEXT DEFAULT '',
		error TEXT DEFAULT '',
		created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
		updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
		completed_at DATETIME
	);

	CREATE INDEX IF NOT EXISTS idx_tasks_status ON tasks(status);
	CREATE INDEX IF NOT EXISTS idx_tasks_type ON tasks(type);
	CREATE INDEX IF NOT EXISTS idx_tasks_created ON tasks(created_at);
	`
	_, err := s.db.Exec(query)
	return err
}

// Create 创建任务
func (s *Store) Create(task *Task) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	task.CreatedAt = time.Now()
	task.UpdatedAt = time.Now()
	if task.Status == "" {
		task.Status = StatusPending
	}

	result, err := s.db.Exec(
		`INSERT INTO tasks (type, status, source, destination, file_name, file_size, progress, total_chunks, done_chunks, uploads_id, file_id, error, created_at, updated_at)
		 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		task.Type, task.Status, task.Source, task.Destination, task.FileName,
		task.FileSize, task.Progress, task.TotalChunks, task.DoneChunks,
		task.UploadsID, task.FileID, task.Error, task.CreatedAt, task.UpdatedAt,
	)
	if err != nil {
		return fmt.Errorf("创建任务失败: %w", err)
	}

	task.ID, _ = result.LastInsertId()
	return nil
}

// UpdateStatus 更新任务状态
func (s *Store) UpdateStatus(id int64, status TaskStatus, errMsg string) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	now := time.Now()
	if status == StatusCompleted || status == StatusFailed || status == StatusCancelled {
		_, err := s.db.Exec(
			`UPDATE tasks SET status = ?, error = ?, updated_at = ?, completed_at = ? WHERE id = ?`,
			status, errMsg, now, now, id,
		)
		return err
	}

	_, err := s.db.Exec(
		`UPDATE tasks SET status = ?, error = ?, updated_at = ? WHERE id = ?`,
		status, errMsg, now, id,
	)
	return err
}

// UpdateProgress 更新任务进度
func (s *Store) UpdateProgress(id int64, progress int64, doneChunks int) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	_, err := s.db.Exec(
		`UPDATE tasks SET progress = ?, done_chunks = ?, updated_at = ? WHERE id = ?`,
		progress, doneChunks, time.Now(), id,
	)
	return err
}

// GetByID 根据 ID 获取任务
func (s *Store) GetByID(id int64) (*Task, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	row := s.db.QueryRow(
		`SELECT id, type, status, source, destination, file_name, file_size, progress, total_chunks, done_chunks, uploads_id, file_id, error, created_at, updated_at, completed_at
		 FROM tasks WHERE id = ?`, id,
	)
	return scanTask(row)
}

// List 列出任务
func (s *Store) List(filter TaskFilter) ([]*Task, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	query := "SELECT id, type, status, source, destination, file_name, file_size, progress, total_chunks, done_chunks, uploads_id, file_id, error, created_at, updated_at, completed_at FROM tasks WHERE 1=1"
	args := []interface{}{}

	if filter.Type != "" {
		query += " AND type = ?"
		args = append(args, filter.Type)
	}
	if filter.Status != "" {
		query += " AND status = ?"
		args = append(args, filter.Status)
	}

	query += " ORDER BY created_at DESC"

	if filter.Limit > 0 {
		query += " LIMIT ?"
		args = append(args, filter.Limit)
	}
	if filter.Offset > 0 {
		query += " OFFSET ?"
		args = append(args, filter.Offset)
	}

	rows, err := s.db.Query(query, args...)
	if err != nil {
		return nil, fmt.Errorf("查询任务失败: %w", err)
	}
	defer rows.Close()

	var tasks []*Task
	for rows.Next() {
		task, err := scanTaskFromRows(rows)
		if err != nil {
			return nil, err
		}
		tasks = append(tasks, task)
	}

	return tasks, nil
}

// GetSummary 获取任务摘要
func (s *Store) GetSummary() (*TaskSummary, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	summary := &TaskSummary{
		Types: make(map[TaskType]int),
	}

	rows, err := s.db.Query(`SELECT status, type, COUNT(*) FROM tasks GROUP BY status, type`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	for rows.Next() {
		var status TaskStatus
		var taskType TaskType
		var count int
		if err := rows.Scan(&status, &taskType, &count); err != nil {
			return nil, err
		}
		summary.Total += count
		summary.Types[taskType] += count
		switch status {
		case StatusPending:
			summary.Pending += count
		case StatusRunning:
			summary.Running += count
		case StatusCompleted:
			summary.Completed += count
		case StatusFailed:
			summary.Failed += count
		}
	}

	return summary, nil
}

// Delete 删除任务
func (s *Store) Delete(id int64) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	_, err := s.db.Exec("DELETE FROM tasks WHERE id = ?", id)
	return err
}

// CleanCompleted 清理已完成的任务
func (s *Store) CleanCompleted() (int64, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	result, err := s.db.Exec("DELETE FROM tasks WHERE status IN ('COMPLETED', 'CANCELLED')")
	if err != nil {
		return 0, err
	}
	return result.RowsAffected()
}

// CleanFailed 清理失败的任务
func (s *Store) CleanFailed() (int64, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	result, err := s.db.Exec("DELETE FROM tasks WHERE status = 'FAILED'")
	if err != nil {
		return 0, err
	}
	return result.RowsAffected()
}

// Close 关闭数据库连接
func (s *Store) Close() error {
	return s.db.Close()
}

// RetryFailed 重试所有失败任务（重置为 PENDING）
func (s *Store) RetryFailed() (int64, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	result, err := s.db.Exec(
		`UPDATE tasks SET status = 'PENDING', error = '', updated_at = ? WHERE status = 'FAILED'`,
		time.Now(),
	)
	if err != nil {
		return 0, err
	}
	return result.RowsAffected()
}

// scanTask 从 row 扫描任务
func scanTask(row *sql.Row) (*Task, error) {
	var task Task
	var completedAt sql.NullTime
	err := row.Scan(
		&task.ID, &task.Type, &task.Status, &task.Source, &task.Destination,
		&task.FileName, &task.FileSize, &task.Progress, &task.TotalChunks,
		&task.DoneChunks, &task.UploadsID, &task.FileID, &task.Error,
		&task.CreatedAt, &task.UpdatedAt, &completedAt,
	)
	if err != nil {
		if err == sql.ErrNoRows {
			return nil, nil
		}
		return nil, err
	}
	if completedAt.Valid {
		task.CompletedAt = &completedAt.Time
	}
	return &task, nil
}

// scanTaskFromRows 从 rows 扫描任务
func scanTaskFromRows(rows *sql.Rows) (*Task, error) {
	var task Task
	var completedAt sql.NullTime
	err := rows.Scan(
		&task.ID, &task.Type, &task.Status, &task.Source, &task.Destination,
		&task.FileName, &task.FileSize, &task.Progress, &task.TotalChunks,
		&task.DoneChunks, &task.UploadsID, &task.FileID, &task.Error,
		&task.CreatedAt, &task.UpdatedAt, &completedAt,
	)
	if err != nil {
		return nil, err
	}
	if completedAt.Valid {
		task.CompletedAt = &completedAt.Time
	}
	return &task, nil
}