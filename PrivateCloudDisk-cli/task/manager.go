package task

import (
	"fmt"
	"sync"
)

// Manager 任务管理器
type Manager struct {
	store *Store
	mu    sync.Mutex
}

// NewManager 创建任务管理器
func NewManager() (*Manager, error) {
	store, err := NewStore()
	if err != nil {
		return nil, err
	}
	return &Manager{store: store}, nil
}

// Store 获取底层存储
func (m *Manager) Store() *Store {
	return m.store
}

// CreateTask 创建任务
func (m *Manager) CreateTask(task *Task) error {
	return m.store.Create(task)
}

// GetTask 获取任务
func (m *Manager) GetTask(id int64) (*Task, error) {
	return m.store.GetByID(id)
}

// ListTasks 列出任务
func (m *Manager) ListTasks(filter TaskFilter) ([]*Task, error) {
	return m.store.List(filter)
}

// UpdateTaskStatus 更新任务状态
func (m *Manager) UpdateTaskStatus(id int64, status TaskStatus, errMsg string) error {
	return m.store.UpdateStatus(id, status, errMsg)
}

// UpdateTaskProgress 更新任务进度
func (m *Manager) UpdateTaskProgress(id int64, progress int64, doneChunks int) error {
	return m.store.UpdateProgress(id, progress, doneChunks)
}

// DeleteTask 删除任务
func (m *Manager) DeleteTask(id int64) error {
	return m.store.Delete(id)
}

// CleanCompleted 清理已完成任务
func (m *Manager) CleanCompleted() (int64, error) {
	return m.store.CleanCompleted()
}

// CleanFailed 清理失败任务
func (m *Manager) CleanFailed() (int64, error) {
	return m.store.CleanFailed()
}

// RetryFailed 重试失败任务
func (m *Manager) RetryFailed() (int64, error) {
	return m.store.RetryFailed()
}

// GetSummary 获取任务摘要
func (m *Manager) GetSummary() (*TaskSummary, error) {
	return m.store.GetSummary()
}

// PrintTaskList 打印任务列表
func (m *Manager) PrintTaskList(filter TaskFilter) error {
	tasks, err := m.ListTasks(filter)
	if err != nil {
		return err
	}
	if len(tasks) == 0 {
		fmt.Println("没有任务")
		return nil
	}

	fmt.Printf("%-4s %-10s %-10s %-30s %-12s %-10s\n", "ID", "类型", "状态", "文件名", "大小", "进度")
	fmt.Println("---- ---------- ---------- ------------------------------ ------------ ----------")
	for _, task := range tasks {
		progress := ""
		if task.TotalChunks > 0 {
			progress = fmt.Sprintf("%d/%d", task.DoneChunks, task.TotalChunks)
		} else if task.FileSize > 0 {
			progress = fmt.Sprintf("%.1f%%", float64(task.Progress)/float64(task.FileSize)*100)
		}
		fmt.Printf("%-4d %-10s %-10s %-30s %-12s %-10s\n",
			task.ID, task.Type, task.Status,
			truncate(task.FileName, 28), fmtSize(task.FileSize), progress)
	}
	return nil
}

func truncate(s string, n int) string {
	if len(s) <= n {
		return s
	}
	return s[:n-3] + "..."
}

func fmtSize(size int64) string {
	if size == 0 {
		return "-"
	}
	units := []string{"B", "KB", "MB", "GB", "TB"}
	unitIndex := 0
	fsize := float64(size)
	for fsize >= 1024 && unitIndex < len(units)-1 {
		fsize /= 1024
		unitIndex++
	}
	return fmt.Sprintf("%.1f%s", fsize, units[unitIndex])
}

// Close 关闭管理器
func (m *Manager) Close() error {
	return m.store.Close()
}