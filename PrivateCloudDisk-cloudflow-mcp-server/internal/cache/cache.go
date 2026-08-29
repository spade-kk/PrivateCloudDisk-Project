package cache

import (
	"sync"
	"time"
)

// TTLCache is intentionally in-process and stores only the already filtered
// tool descriptors, never bearer tokens, raw Capability policy, or tool output.
// A future event subscriber can call InvalidateUser when Hub emits a permission
// or plugin capability change; TTL is the safe fallback across replicas.
type TTLCache[T any] struct {
	mu      sync.RWMutex
	entries map[string]entry[T]
}

type entry[T any] struct {
	value     T
	expiresAt time.Time
}

func New[T any]() *TTLCache[T] { return &TTLCache[T]{entries: make(map[string]entry[T])} }

func (cache *TTLCache[T]) Get(key string, now time.Time) (T, bool) {
	cache.mu.RLock()
	item, ok := cache.entries[key]
	cache.mu.RUnlock()
	if !ok || !now.Before(item.expiresAt) {
		if ok {
			cache.mu.Lock()
			delete(cache.entries, key)
			cache.mu.Unlock()
		}
		var empty T
		return empty, false
	}
	return item.value, true
}

func (cache *TTLCache[T]) Put(key string, value T, ttl time.Duration, now time.Time) {
	cache.mu.Lock()
	cache.entries[key] = entry[T]{value: value, expiresAt: now.Add(ttl)}
	cache.mu.Unlock()
}

func (cache *TTLCache[T]) Delete(key string) {
	cache.mu.Lock()
	delete(cache.entries, key)
	cache.mu.Unlock()
}

func (cache *TTLCache[T]) Clear() {
	cache.mu.Lock()
	clear(cache.entries)
	cache.mu.Unlock()
}
