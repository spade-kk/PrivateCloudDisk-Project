package org.project.automation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Automation 下游地址、执行时限和可靠消息参数。 */
@ConfigurationProperties(prefix = "pcd")
public record AutomationProperties(
        String pluginServiceUrl,
        String runtimeServiceUrl,
        String internalServiceToken,
        int triggerMatchTimeoutMs,
        int runtimeTimeoutSeconds,
        int inboxLeaseSeconds,
        long outboxPollMs
) {
}

