package security

import (
	"sync"
	"time"
)

type failureEntry struct {
	failures     []time.Time
	blockedUntil time.Time
}

// FailureLimiter 为 HTTP PAT 与 SSH 公钥认证复用同一类轻量内存冷却机制。
// [REQ-GIT-AUDIT-4.20/6.15] 原行为对同一来源的错误凭据没有限制，攻击者可以无限
// 消耗数据库认证查询和 SSH 握手资源；新行为在达到阈值后暂时拒绝该 IP。该机制仅是
// Git Service 的自保护层，不替代边缘 WAF/风控，也不会记录 PAT 或公钥内容。
// 进程重启会清空冷却记录，影响范围仅限当前 Git Service 实例。
type FailureLimiter struct {
	mu       sync.Mutex
	entries  map[string]*failureEntry
	limit    int
	window   time.Duration
	cooldown time.Duration
	now      func() time.Time
}

func NewFailureLimiter(limit int, window, cooldown time.Duration) *FailureLimiter {
	if limit < 1 {
		limit = 10
	}
	if window < time.Second {
		window = 5 * time.Minute
	}
	if cooldown < time.Second {
		cooldown = 15 * time.Minute
	}
	return &FailureLimiter{
		entries:  make(map[string]*failureEntry),
		limit:    limit,
		window:   window,
		cooldown: cooldown,
		now:      time.Now,
	}
}

func (l *FailureLimiter) Allow(key string) (bool, time.Duration) {
	if key == "" {
		return true, 0
	}
	l.mu.Lock()
	defer l.mu.Unlock()
	entry := l.entries[key]
	if entry == nil {
		return true, 0
	}
	now := l.now()
	if entry.blockedUntil.After(now) {
		return false, entry.blockedUntil.Sub(now)
	}
	l.prune(entry, now)
	if len(entry.failures) == 0 {
		delete(l.entries, key)
	}
	return true, 0
}

// RecordFailure 返回新进入冷却期时的等待时长，调用方据此立即返回 429。
func (l *FailureLimiter) RecordFailure(key string) time.Duration {
	if key == "" {
		return 0
	}
	l.mu.Lock()
	defer l.mu.Unlock()
	now := l.now()
	entry := l.entries[key]
	if entry == nil {
		entry = &failureEntry{}
		l.entries[key] = entry
	}
	l.prune(entry, now)
	entry.failures = append(entry.failures, now)
	if len(entry.failures) >= l.limit {
		entry.blockedUntil = now.Add(l.cooldown)
		entry.failures = nil
		return l.cooldown
	}
	return 0
}

func (l *FailureLimiter) RecordSuccess(key string) {
	if key == "" {
		return
	}
	l.mu.Lock()
	defer l.mu.Unlock()
	delete(l.entries, key)
}

func (l *FailureLimiter) prune(entry *failureEntry, now time.Time) {
	cutoff := now.Add(-l.window)
	index := 0
	for index < len(entry.failures) && entry.failures[index].Before(cutoff) {
		index++
	}
	if index > 0 {
		entry.failures = append([]time.Time(nil), entry.failures[index:]...)
	}
}
