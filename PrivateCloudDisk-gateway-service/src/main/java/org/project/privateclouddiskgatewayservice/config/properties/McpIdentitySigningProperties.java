package org.project.privateclouddiskgatewayservice.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Gateway-to-CloudFlow-MCP trusted identity signing settings.
 *
 * <p>The MCP service is intentionally not a second JWT verifier and must never forward an
 * external bearer token to Capability Hub. Gateway validates the bearer token at the public
 * boundary, then signs the method/path/request/user/tenant/space tuple for the private MCP hop.</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "gateway.mcp")
public class McpIdentitySigningProperties {
    private String identitySigningSecret = "";
    /**
     * Canonical external Gateway base URL used in the OAuth protected-resource
     * challenge. Keeping it configured avoids reflecting an untrusted Host or
     * X-Forwarded-Host header in an authentication response.
     */
    private String publicBaseUrl = "";
    /** Optional production policy: the OAuth access token must contain this audience. */
    private String requiredAudience = "";
    /** Optional production policy: the OAuth access token must contain this scope. */
    private String requiredScope = "";
}
