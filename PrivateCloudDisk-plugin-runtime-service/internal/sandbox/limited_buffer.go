package sandbox

import (
	"bytes"
	"sync"
)

// LimitedBuffer 截断用户 stdout/stderr，防止日志放大导致 Runtime 内存耗尽。
type LimitedBuffer struct {
	mu        sync.Mutex
	buffer    bytes.Buffer
	remaining int64
	truncated bool
}

func NewLimitedBuffer(limit int64) *LimitedBuffer {
	return &LimitedBuffer{remaining: limit}
}

func (buffer *LimitedBuffer) Write(value []byte) (int, error) {
	buffer.mu.Lock()
	defer buffer.mu.Unlock()
	originalLength := len(value)
	if buffer.remaining <= 0 {
		buffer.truncated = true
		return originalLength, nil
	}
	if int64(len(value)) > buffer.remaining {
		value = value[:buffer.remaining]
		buffer.truncated = true
	}
	_, _ = buffer.buffer.Write(value)
	buffer.remaining -= int64(len(value))
	return originalLength, nil
}

func (buffer *LimitedBuffer) String() string {
	buffer.mu.Lock()
	defer buffer.mu.Unlock()
	value := buffer.buffer.String()
	if buffer.truncated {
		value += "\n[日志已按平台上限截断]"
	}
	return value
}
