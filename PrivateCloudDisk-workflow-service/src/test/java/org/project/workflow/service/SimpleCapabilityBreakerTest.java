package org.project.workflow.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleCapabilityBreakerTest {
    private static final String KEY = "api:file.metadata.get";

    @Test
    void opensAfterConsecutiveFailures() {
        MutableClock clock = new MutableClock(0);
        SimpleCapabilityBreaker breaker = new SimpleCapabilityBreaker(clock, 3, 60);
        assertTrue(breaker.tryAcquire(KEY));
        breaker.recordFailure(KEY);
        breaker.recordFailure(KEY);
        breaker.recordFailure(KEY);
        assertFalse(breaker.tryAcquire(KEY));
    }

    @Test
    void recoversAfterOpenWindowElapses() {
        MutableClock clock = new MutableClock(0);
        SimpleCapabilityBreaker breaker = new SimpleCapabilityBreaker(clock, 2, 60);
        breaker.recordFailure(KEY);
        breaker.recordFailure(KEY);
        assertFalse(breaker.tryAcquire(KEY));
        clock.advance(61_000);
        assertTrue(breaker.tryAcquire(KEY));
    }

    @Test
    void successResetsFailureCount() {
        MutableClock clock = new MutableClock(0);
        SimpleCapabilityBreaker breaker = new SimpleCapabilityBreaker(clock, 3, 60);
        breaker.recordFailure(KEY);
        breaker.recordFailure(KEY);
        breaker.recordSuccess(KEY);
        assertTrue(breaker.tryAcquire(KEY));
        breaker.recordFailure(KEY);
        assertTrue(breaker.tryAcquire(KEY));
    }

    @Test
    void failureCountingIsIsolatedPerKey() {
        SimpleCapabilityBreaker breaker = new SimpleCapabilityBreaker(Clock.systemUTC(), 1, 60);
        breaker.recordFailure("api:file.metadata.get");
        assertFalse(breaker.tryAcquire("api:file.metadata.get"));
        assertTrue(breaker.tryAcquire("api:space.info"));
    }

    private static final class MutableClock extends Clock {
        private long millis;

        MutableClock(long millis) {
            this.millis = millis;
        }

        void advance(long delta) {
            millis += delta;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
