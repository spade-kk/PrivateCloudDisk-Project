package org.project.scheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Scheduler 节点租约与追赶策略配置。 */
@ConfigurationProperties(prefix = "pcd")
public record SchedulerProperties(String internalServiceToken, Scheduler scheduler) {
    public record Scheduler(String nodeId, long scanDelayMs, int leaseSeconds, int catchUpLimit) {
    }
}
