package task

import (
	"time"
)

// TaskStatus 任务状态
type TaskStatus string

const (
	StatusPending   TaskStatus = "PENDING"
	StatusRunning   TaskStatus = "RUNNING"
	StatusPaused    TaskStatus = "PAUSED"
	StatusCompleted TaskStatus = "COMPLETED"
	StatusFailed    TaskStatus = "FAILED"
	StatusCancelled TaskStatus = "CANCELLED"
)

// TaskType 任务类型
type TaskType string

const (
	TypeUpload   TaskType = "UPLOAD"
	TypeDownload TaskType = "DOWNLOAD"
	TypeSync     TaskType = "SYNC"
)

// Task 任务模型
type Task struct {
	ID          int64      `json:"id"`
	Type        TaskType   `json:"type"`
	Status      TaskStatus `json:"status"`
	Source      string     `json:"source"`       // 本地路径或远程路径
	Destination string     `json:"destination"`   // 远程路径或本地路径
	FileName    string     `json:"file_name"`
	FileSize    int64      `json:"file_size"`
	Progress    int64      `json:"progress"`      // 已传输字节
	TotalChunks int        `json:"total_chunks"`
	DoneChunks  int        `json:"done_chunks"`
	UploadsID   string     `json:"uploads_id"`    // 上传会话 ID
	FileID      string     `json:"file_id"`       // 文件 ID
	Error       string     `json:"error,omitempty"`
	CreatedAt   time.Time  `json:"created_at"`
	UpdatedAt   time.Time  `json:"updated_at"`
	CompletedAt *time.Time `json:"completed_at,omitempty"`
}

// TaskFilter 任务过滤条件
type TaskFilter struct {
	Type   TaskType
	Status TaskStatus
	Limit  int
	Offset int
}

// TaskSummary 任务摘要
type TaskSummary struct {
	Total     int            `json:"total"`
	Pending   int            `json:"pending"`
	Running   int            `json:"running"`
	Completed int            `json:"completed"`
	Failed    int            `json:"failed"`
	Types     map[TaskType]int `json:"types"`
}