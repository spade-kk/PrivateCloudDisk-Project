package org.project.workflow.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.project.workflow.config.CloudFlowRuntimeProperties;
import org.project.workflow.model.WorkflowModels.ValidationReport;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/** CloudFlow Runtime HTTP 契约：新路径、结构化诊断、多行 CLI 与 fail-closed 熔断。 */
class CloudFlowRuntimeClientTest {

    @Test
    void mapsRuntimeDiagnosticsForMonacoAndTerminal() {
        RestClient.Builder builder = RestClient.builder().defaultHeader("X-PCD-Service-Token", "test-token");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String url = "http://cloudflow-runtime/api/v1/compile";
        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header("X-PCD-Service-Token", "test-token"))
                .andExpect(content().json("""
                        {"source":"workflow \\"bad\\" {}","filename":"workflow.flow","target_ir_version":"v1","userId":"user-1","spaceId":"space-1"}
                        """))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                        {
                          "valid": false,
                          "ir": null,
                          "diagnostics": [{
                            "code": "CF1202",
                            "severity": "ERROR",
                            "category": "SYNTAX_ERROR",
                            "message": "未知关键字",
                            "location": {"line": 4, "column": 9},
                            "suggestions": ["trigger"],
                            "help": "使用 trigger",
                            "documentationUrl": "/docs/cloudflow/errors/CF1202",
                            "cliOutput": "ERROR CF1202\\n\\nworkflow.flow:4:9\\n\\n未知关键字"
                          }]
                        }
                        """));

        CloudFlowRuntimeClient client = new CloudFlowRuntimeClient(
                builder,
                new CloudFlowRuntimeProperties(url, 3, 15, "REJECT"),
                new ObjectMapper(),
                Clock.systemUTC()
        );
        ValidationReport report = client.compile("workflow \"bad\" {}", "user-1", "space-1", "workflow.flow");

        assertThat(report.valid()).isFalse();
        assertThat(report.issues()).hasSize(1);
        assertThat(report.issues().get(0).line()).isEqualTo(4);
        assertThat(report.issues().get(0).column()).isEqualTo(9);
        assertThat(report.issues().get(0).cliOutput()).contains("\n\nworkflow.flow:4:9");
        server.verify();
    }

    @Test
    void opensCircuitAndNeverMarksUnavailableRuntimeAsValidated() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String url = "http://cloudflow-runtime/api/v1/compile";
        server.expect(times(2), requestTo(url)).andRespond(withException(new IOException("runtime down")));
        CloudFlowRuntimeClient client = new CloudFlowRuntimeClient(
                builder,
                new CloudFlowRuntimeProperties(url, 2, 30, "REJECT"),
                new ObjectMapper(),
                Clock.systemUTC()
        );

        assertThat(client.compile("workflow \"a\" {}", "u", "s", "a.flow").valid()).isFalse();
        assertThat(client.compile("workflow \"b\" {}", "u", "s", "b.flow").valid()).isFalse();
        ValidationReport openCircuit = client.compile("workflow \"c\" {}", "u", "s", "c.flow");

        assertThat(openCircuit.valid()).isFalse();
        assertThat(openCircuit.issues().get(0).code()).isEqualTo("CF-RUNTIME-UNAVAILABLE");
        assertThat(openCircuit.issues().get(0).cliOutput()).contains("\n");
        server.verify();
    }
}
