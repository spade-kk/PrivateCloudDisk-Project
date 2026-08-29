package ratelimit

import (
	"sync"
	"time"
)

// FixedWindow is an in-process secondary protection.  The edge gateway remains
// the distributed rate-limit authority; this guard keeps a compromised or
// bypassed internal route from exhausting the Adapter process.
type FixedWindow struct {
	mu     sync.Mutex
	limit  int
	period time.Duration
	items  map[string]bucket
}

type bucket struct {
	start time.Time
	count int
}

func New(limit int, period time.Duration) *FixedWindow {
	return &FixedWindow{limit: limit, period: period, items: make(map[string]bucket)}
}

func (limiter *FixedWindow) Allow(key string, now time.Time) bool {
	limiter.mu.Lock()
	defer limiter.mu.Unlock()
	item := limiter.items[key]
	if item.start.IsZero() || now.Sub(item.start) >= limiter.period {
		limiter.items[key] = bucket{start: now, count: 1}
		return true
	}
	if item.count >= limiter.limit {
		return false
	}
	item.count++
	limiter.items[key] = item
	return true
}
