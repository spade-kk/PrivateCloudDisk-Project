package org.project.workflow.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityKeyValidatorTest {
    @Test
    void acceptsRepositoryKeyConventions() {
        assertTrue(CapabilityKeyValidator.isValid("builtin:date.now"));
        assertTrue(CapabilityKeyValidator.isValid("builtin:text.transform"));
        assertTrue(CapabilityKeyValidator.isValid("api:user.notify"));
        assertTrue(CapabilityKeyValidator.isValid("api:file.metadata.get"));
        assertTrue(CapabilityKeyValidator.isValid("api:notification.send"));
        assertTrue(CapabilityKeyValidator.isValid("plugin:88d5b0e1-0000-0000-0000-000000000001:generate_report@1"));
        assertTrue(CapabilityKeyValidator.isValid("local_plugin:client.sync"));
    }

    @Test
    void rejectsMaliciousOrMalformedKeys() {
        assertFalse(CapabilityKeyValidator.isValid(null));
        assertFalse(CapabilityKeyValidator.isValid(""));
        assertFalse(CapabilityKeyValidator.isValid("   "));
        assertFalse(CapabilityKeyValidator.isValid("builtin"));
        assertFalse(CapabilityKeyValidator.isValid(":date.now"));
        assertFalse(CapabilityKeyValidator.isValid("builtin:"));
        // 注入向量：表达式、引号、空格、反斜杠、路径穿越、大写
        assertFalse(CapabilityKeyValidator.isValid("builtin:${jndi:ldap://evil}"));
        assertFalse(CapabilityKeyValidator.isValid("api:file.metadata.get\")); DROP TABLE pcd_capability_registry;--"));
        assertFalse(CapabilityKeyValidator.isValid("api:file content.get"));
        assertFalse(CapabilityKeyValidator.isValid("Api:file.content.get"));
        assertFalse(CapabilityKeyValidator.isValid("api:../etc/passwd"));
        assertFalse(CapabilityKeyValidator.isValid("builtin:date.now\n"));
        assertFalse(CapabilityKeyValidator.isValid("unknown:service.method"));
        assertFalse(CapabilityKeyValidator.isValid("api:file.search".concat("x".repeat(250))));
    }
}
