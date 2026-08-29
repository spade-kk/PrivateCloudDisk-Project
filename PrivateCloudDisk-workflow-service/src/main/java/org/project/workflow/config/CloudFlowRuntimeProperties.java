package org.project.workflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CloudFlow Runtime 编译接口配置。
 *
 * <p>需求对应：CLOUDFLOW-INTEGRATION-001。compileUrl 使用完整 URL，避免 Java 层继续绑定
 * Runtime 内部路由结构；不可用策略固定为 REJECT，防止未校验 DSL 绕过发布门禁。</p>
 */
@ConfigurationProperties(prefix = "cloudflow.runtime")
public record CloudFlowRuntimeProperties(
        String compileUrl,
        int circuitFailureThreshold,
        int circuitOpenSeconds,
        String unavailablePolicy
) {
    public CloudFlowRuntimeProperties {
        if (compileUrl == null || compileUrl.isBlank()) compileUrl = "http://localhost:8091/api/v1/compile";
        if (circuitFailureThreshold < 1) circuitFailureThreshold = 3;
        if (circuitOpenSeconds < 1) circuitOpenSeconds = 15;
        if (unavailablePolicy == null || unavailablePolicy.isBlank()) unavailablePolicy = "REJECT";
    }
}
