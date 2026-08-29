package org.project.workflow.service;

import java.util.Set;

/**
 * Reviewed external MCP export allow-list.
 *
 * <p>The registry remains dynamic: a tool appears only when it is registered, ACTIVE and visible
 * to the requesting user. This class is a second, intentionally narrow boundary that prevents a
 * newly registered administrative/destructive capability from silently becoming available to an
 * external Agent before a security review adds it here.</p>
 */
public final class McpCapabilityExportPolicy {
    private McpCapabilityExportPolicy() {
    }

    private static final Set<String> ALLOWED = Set.of(
            "api:file.list",
            "api:file.search",
            "api:file.content.get",
            "api:file.metadata.get",
            "api:space.info",
            "api:workflow.list",
            "api:workflow.execute",
            "api:workflow.status"
    );

    public static boolean isExportable(String capabilityKey) {
        return capabilityKey != null && ALLOWED.contains(capabilityKey);
    }
}
