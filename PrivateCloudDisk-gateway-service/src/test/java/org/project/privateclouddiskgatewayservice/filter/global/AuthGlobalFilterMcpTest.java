package org.project.privateclouddiskgatewayservice.filter.global;

import org.junit.jupiter.api.Test;
import org.project.privateclouddiskgatewayservice.config.properties.AiIdentitySigningProperties;
import org.project.privateclouddiskgatewayservice.config.properties.McpIdentitySigningProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Locks down the external MCP pre-authentication contract: a client without a
 * token must still be able to discover the canonical OAuth metadata endpoint.
 * JwtUtil is deliberately null because this branch rejects before token parsing.
 */
class AuthGlobalFilterMcpTest {

    @Test
    void missingMcpBearerAddsCanonicalResourceMetadataChallenge() {
        McpIdentitySigningProperties mcp = new McpIdentitySigningProperties();
        mcp.setPublicBaseUrl("https://gateway.example.com/");
        AuthGlobalFilter filter = new AuthGlobalFilter(null, new AiIdentitySigningProperties(), mcp);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.POST, "/api/v1/mcp").build()
        );

        filter.filter(exchange, ignored -> reactor.core.publisher.Mono.empty()).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertEquals(
                "Bearer realm=\"cloudflow-mcp\", resource_metadata=\""
                        + "https://gateway.example.com/api/v1/.well-known/oauth-protected-resource/mcp\"",
                exchange.getResponse().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)
        );
    }
}
