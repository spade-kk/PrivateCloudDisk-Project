package org.project.workflow.service;

import org.project.workflow.client.CloudFlowRuntimeClient;
import org.project.workflow.model.WorkflowModels.ValidationReport;
import org.springframework.stereotype.Component;

/**
 * CloudFlow 控制面校验入口。
 *
 * <p>改动点（CLOUDFLOW-RUNTIME-001）：原文件包含正则、行扫描、能力查询和 Java DAG 实现，
 * 造成与 Rust Compiler 的重复语义。现在所有源码均委托 CloudFlow Runtime 编译；本类只保留
 * 兼容调用入口，未通过 Runtime 的源码不能保存、发布或执行。</p>
 */
@Component
public final class CloudFlowDslValidator {
    private final CloudFlowRuntimeClient runtimeClient;

    public CloudFlowDslValidator(CloudFlowRuntimeClient runtimeClient) {
        this.runtimeClient = runtimeClient;
    }

    public ValidationReport validate(String source) {
        return validate(source, null, null, "workflow.flow");
    }

    public ValidationReport validate(String source, String userId, String spaceId, String filename) {
        return runtimeClient.compile(source, userId, spaceId, filename);
    }
}
