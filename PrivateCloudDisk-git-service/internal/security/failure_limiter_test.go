package security

import (
	"testing"
	"time"
)

func TestFailureLimiterBlocksAndExpiresUsingInjectedClock(t *testing.T) {
	clock := time.Date(2026, 8, 17, 12, 0, 0, 0, time.UTC)
	limiter := NewFailureLimiter(2, time.Minute, 5*time.Minute)
	limiter.now = func() time.Time { return clock }
	if cooldown := limiter.RecordFailure("203.0.113.10"); cooldown != 0 {
		t.Fatalf("first failure should not block, got %s", cooldown)
	}
	if cooldown := limiter.RecordFailure("203.0.113.10"); cooldown != 5*time.Minute {
		t.Fatalf("second failure should start cooldown, got %s", cooldown)
	}
	allowed, retryAfter := limiter.Allow("203.0.113.10")
	if allowed || retryAfter != 5*time.Minute {
		t.Fatalf("expected blocked request for 5m, got allowed=%v retry=%s", allowed, retryAfter)
	}
	clock = clock.Add(5 * time.Minute)
	if allowed, _ := limiter.Allow("203.0.113.10"); !allowed {
		t.Fatal("entry should be allowed after cooldown")
	}
}
