package org.project.privateclouddiskgatewayservice.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Gateway-to-Cloud-AI-Agent trusted identity signing settings.
 *
 * <p>[AI-AGENT-IDENTITY-001] Browser headers are mutable and the existing
 * {@code X-User-Id} forwarding convention is not by itself proof to a new
 * standalone Agent Runtime that Gateway authenticated the request. This property
 * provides an explicit private-network signing secret. It is only used for
 * {@code /api/v1/ai/**}; unrelated downstream contracts remain unchanged.</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "gateway.ai")
public class AiIdentitySigningProperties {
    private String identitySigningSecret = "";
}
