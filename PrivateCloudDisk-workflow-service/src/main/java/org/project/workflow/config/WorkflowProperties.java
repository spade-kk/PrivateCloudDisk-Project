package org.project.workflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 工作流服务外部依赖与可恢复 Worker 配置。 */
@ConfigurationProperties(prefix = "pcd")
public record WorkflowProperties(
        String platformUrl,
        String pluginServiceUrl,
        String pluginRuntimeUrl,
        String cloudflowRuntimeUrl,
        String schedulerUrl,
        String internalServiceToken,
        Worker worker
) {
    public record Worker(
            boolean enabled,
            long pollDelayMs,
            int staleSeconds,
            int maxStepOutputBytes
    ) {
    }
}
