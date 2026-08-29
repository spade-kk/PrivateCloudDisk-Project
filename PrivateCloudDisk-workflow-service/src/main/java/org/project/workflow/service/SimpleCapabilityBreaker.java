package org.project.workflow.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 能力级熔断器（需求二 2.18 / 六 6.25）。
 *
 * <p>连续失败超过阈值后在 OPEN 窗口内拒绝调用，成功一次即复位。
 * 按能力键隔离，不引入外部依赖；half-open 阶段放行探测请求。</p>
 */
@Component
public class SimpleCapabilityBreaker {
    private final Clock clock;
    private final int failureThreshold;
    private final int openSeconds;
    private final ConcurrentHashMap<String, BreakerState> states = new ConcurrentHashMap<>();

    public SimpleCapabilityBreaker() {
        this(Clock.systemUTC(), 3, 15);
    }

    public SimpleCapabilityBreaker(Clock clock, int failureThreshold, int openSeconds) {
        this.clock = clock;
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openSeconds = Math.max(1, openSeconds);
    }

    /** 返回 false 表示能力当前处于熔断打开状态，调用应被拒绝。 */
    public boolean tryAcquire(String capabilityKey) {
        BreakerState state = states.computeIfAbsent(capabilityKey, key -> new BreakerState());
        if (state.failures.get() < failureThreshold) {
            return true;
        }
        long openUntil = state.openUntil.get();
        long now = clock.millis();
        if (now >= openUntil) {
            // half-open：允许一次探测请求，并避免多路并发探测重复放行。
            return state.opened.compareAndSet(true, false)
                    || state.failures.get() < failureThreshold;
        }
        return false;
    }

    public void recordSuccess(String capabilityKey) {
        BreakerState state = states.get(capabilityKey);
        if (state != null) {
            state.failures.set(0);
            state.opened.set(false);
        }
    }

    public void recordFailure(String capabilityKey) {
        BreakerState state = states.computeIfAbsent(capabilityKey, key -> new BreakerState());
        state.failures.incrementAndGet();
        if (state.failures.get() >= failureThreshold) {
            state.openUntil.set(clock.millis() + openSeconds * 1000L);
            state.opened.set(true);
        }
    }

    private static final class BreakerState {
        final AtomicInteger failures = new AtomicInteger();
        final AtomicLong openUntil = new AtomicLong();
        final java.util.concurrent.atomic.AtomicBoolean opened = new java.util.concurrent.atomic.AtomicBoolean();
    }
}
