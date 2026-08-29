package org.project.workflow.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.project.workflow.model.WorkflowModels.ValidateWorkflowRequest;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 DSL 请求在迁移期间兼容字符串和源码包装对象，避免 Controller 直接返回 400。 */
class WorkflowModelsTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsCanonicalStringDsl() throws Exception {
        ValidateWorkflowRequest request = objectMapper.readValue(
                "{\"dsl\":\"workflow \\\"demo\\\" {}\",\"graph\":{}}",
                ValidateWorkflowRequest.class
        );

        assertThat(request.dsl()).isEqualTo("workflow \"demo\" {}");
    }

    @Test
    void unwrapsLegacySourceObject() throws Exception {
        ValidateWorkflowRequest request = objectMapper.readValue(
                "{\"dsl\":{\"source\":\"workflow \\\"demo\\\" {}\"},\"graph\":{}}",
                ValidateWorkflowRequest.class
        );

        assertThat(request.dsl()).isEqualTo("workflow \"demo\" {}");
    }
}
