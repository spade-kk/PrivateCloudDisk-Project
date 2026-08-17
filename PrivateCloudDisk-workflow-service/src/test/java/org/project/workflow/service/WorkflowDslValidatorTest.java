package org.project.workflow.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.project.workflow.client.CloudFlowRuntimeClient;
import org.project.workflow.model.WorkflowModels.ValidationIssue;
import org.project.workflow.model.WorkflowModels.ValidationReport;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CloudFlow 校验只验证 Runtime 委托契约，不在 Java 重复实现语法。 */
class WorkflowDslValidatorTest {
    private CloudFlowRuntimeClient runtimeClient;
    private WorkflowDslValidator validator;

    @BeforeEach
    void setUp() {
        runtimeClient = mock(CloudFlowRuntimeClient.class);
        validator = new WorkflowDslValidator(runtimeClient);
    }

    @Test
    void delegatesSourceAndSpaceContextToRustRuntime() {
        ValidationReport expected = new ValidationReport(true, List.of(), Map.of("steps", List.of()), "a".repeat(64));
        when(runtimeClient.compile(anyString(), any(), any(), anyString())).thenReturn(expected);

        ValidationReport actual = validator.validate("workflow \"demo\" {}", "user-1", "space-1", "demo.flow");

        assertThat(actual).isEqualTo(expected);
        verify(runtimeClient).compile("workflow \"demo\" {}", "user-1", "space-1", "demo.flow");
    }

    @Test
    void preservesStructuredRuntimeDiagnostics() {
        ValidationReport expected = new ValidationReport(false,
                List.of(new ValidationIssue("CF1201", "line[2]:3", "语法错误")), Map.of(), "");
        when(runtimeClient.compile(anyString(), any(), any(), anyString())).thenReturn(expected);

        ValidationReport actual = validator.validate("broken", null, null, "broken.flow");

        assertThat(actual.valid()).isFalse();
        assertThat(actual.issues()).extracting("code").containsExactly("CF1201");
    }
}
