package utils

import (
	"fmt"
	"os"
	"sync"
	"sync/atomic"
	"time"

	"golang.org/x/term"
)

// ProgressTracker 进度追踪器
type ProgressTracker struct {
	total     int64
	current   int64
	startTime time.Time
	label     string
	mu        sync.Mutex
}

// NewProgressTracker 创建进度追踪器
func NewProgressTracker(total int64, label string) *ProgressTracker {
	return &ProgressTracker{
		total:     total,
		startTime: time.Now(),
		label:     label,
	}
}

// Update 更新进度
func (p *ProgressTracker) Update(current int64) {
	atomic.StoreInt64(&p.current, current)
	p.render()
}

// Finish 完成进度
func (p *ProgressTracker) Finish() {
	atomic.StoreInt64(&p.current, p.total)
	p.render()
	fmt.Println()
}

// render 渲染进度条
func (p *ProgressTracker) render() {
	current := atomic.LoadInt64(&p.current)
	elapsed := time.Since(p.startTime)

	width := 40
	if w, _, err := term.GetSize(int(os.Stdout.Fd())); err == nil && w > 0 {
		width = w - 60
		if width < 20 {
			width = 20
		}
	}

	percent := float64(current) / float64(p.total)
	if percent > 1.0 {
		percent = 1.0
	}

	filled := int(percent * float64(width))
	bar := ""
	for i := 0; i < width; i++ {
		if i < filled {
			bar += "█"
		} else {
			bar += "░"
		}
	}

	speed := ""
	if elapsed.Seconds() > 0 {
		bytesPerSec := float64(current) / elapsed.Seconds()
		speed = FormatSize(int64(bytesPerSec)) + "/s"
	}

	eta := ""
	if current > 0 && current < p.total {
		remaining := time.Duration(float64(elapsed) * float64(p.total-current) / float64(current))
		eta = FormatDuration(remaining)
	}

	fmt.Fprintf(os.Stderr, "\r  %s [%s] %3.0f%% %s/%s %s %s    ",
		p.label, bar, percent*100,
		FormatSize(current), FormatSize(p.total),
		speed, eta)
}

// ============================================================
// 简单进度显示
// ============================================================

// SimpleProgress 简单进度条
type SimpleProgress struct {
	total     int64
	current   int64
	startTime time.Time
}

// NewSimpleProgress 创建简单进度条
func NewSimpleProgress(total int64) *SimpleProgress {
	return &SimpleProgress{
		total:     total,
		startTime: time.Now(),
	}
}

// Update 更新进度
func (s *SimpleProgress) Update(n int64) {
	atomic.AddInt64(&s.current, n)
	s.render()
}

// render 渲染
func (s *SimpleProgress) render() {
	current := atomic.LoadInt64(&s.current)
	percent := float64(current) / float64(s.total) * 100
	if percent > 100 {
		percent = 100
	}
	elapsed := time.Since(s.startTime)
	speed := float64(current) / elapsed.Seconds()
	fmt.Fprintf(os.Stderr, "\r  %.1f%% (%s/%s) %.1f MB/s    ",
		percent,
		FormatSize(current),
		FormatSize(s.total),
		speed/1024/1024)
}