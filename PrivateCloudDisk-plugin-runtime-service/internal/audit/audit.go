// Package audit 提供插件执行审计台账（需求七 7.19 / 八 8.2 / 六 6.21）。
//
// 每个执行阶段写入一行结构化 JSON：事件、执行/步骤/插件/版本 ID、用户/空间、结果、
// 耗时与脱敏后的细节；写入失败记录到 slog 而不阻断执行。
package audit

import (
	"encoding/json"
	"log/slog"
	"os"
	"path/filepath"
	"sync"
	"time"

	"privateclouddisk/plugin-runtime-service/internal/sanitize"
)

// Event 是单行审计记录。
type Event struct {
	Timestamp   string          `json:"ts"`
	Event       string          `json:"event"`
	Outcome     string          `json:"outcome"`
	ExecutionID string          `json:"execution_id,omitempty"`
	StepID      string          `json:"step_id,omitempty"`
	PluginID    string          `json:"plugin_id,omitempty"`
	VersionID   string          `json:"version_id,omitempty"`
	UserID      string          `json:"user_id,omitempty"`
	SpaceID     string          `json:"space_id,omitempty"`
	DurationMs  int64           `json:"duration_ms,omitempty"`
	Detail      json.RawMessage `json:"detail,omitempty"`
}

// Sink 向指定文件追加结构化审计日志。
type Sink struct {
	mu   sync.Mutex
	path string
	file *os.File
}

// New 打开（必要时创建）审计日志文件，权限 0600；path 为空时返回 no-op sink。
func New(path string) (*Sink, error) {
	if path == "" {
		return &Sink{}, nil
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		return nil, err
	}
	file, err := os.OpenFile(path, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o600)
	if err != nil {
		return nil, err
	}
	return &Sink{path: path, file: file}, nil
}

// Write 写入一条脱敏后的审计记录；失败只记 slog，不阻断执行（6.21）。
func (s *Sink) Write(event Event) {
	if s == nil || s.file == nil {
		return
	}
	event.Timestamp = time.Now().UTC().Format(time.RFC3339Nano)
	if len(event.Detail) > 0 {
		event.Detail = sanitize.RawJSON(event.Detail)
	}
	encoded, err := json.Marshal(event)
	if err != nil {
		slog.Warn("审计记录序列化失败", "error", sanitize.Error(err, 500))
		return
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if _, err := s.file.Write(append(encoded, '\n')); err != nil {
		slog.Warn("审计日志写入失败", "path", s.path, "error", sanitize.Error(err, 500))
	}
}

// Close 关闭底层文件（进程退出时调用）。
func (s *Sink) Close() error {
	if s == nil || s.file == nil {
		return nil
	}
	return s.file.Close()
}
