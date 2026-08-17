package org.project.workflow.service;

import org.project.workflow.client.CloudFlowRuntimeClient;
import org.project.workflow.model.WorkflowModels.ValidationReport;
import org.springframework.stereotype.Service;

/**
 * 工作流 DSL 统一入口。
 *
 * <p>[CLOUDFLOW-DSL-001] 原有 automation.pcd/v1 YAML 校验实现已经退出生产路径；
 * 从本版本开始，保存、发布、执行和 IDE 校验全部使用 CloudFlow 自定义语言，避免前后端
 * 维护两套语义导致校验结果与运行结果不一致。</p>
 */
@Service
public class WorkflowDslValidator {
    private final CloudFlowDslValidator cloudFlowValidator;

    public WorkflowDslValidator(CloudFlowRuntimeClient runtimeClient) {
        this.cloudFlowValidator = new CloudFlowDslValidator(runtimeClient);
    }

    /** 仅接受 workflow.cloudflow.io/v1 编译结果；旧 YAML 由 Runtime 返回明确迁移诊断。 */
    public ValidationReport validate(String dsl) {
        return cloudFlowValidator.validate(dsl);
    }

    public ValidationReport validate(String dsl, String userId, String spaceId, String filename) {
        return cloudFlowValidator.validate(dsl, userId, spaceId, filename);
    }
}
