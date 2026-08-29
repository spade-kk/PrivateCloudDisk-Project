package org.project.plugin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** [PLUGIN-EXEC-OBS-001] 持久化前第二道脱敏的回归测试。 */
class ExecutionObservabilitySanitizerTest {
    private final ExecutionObservabilitySanitizer sanitizer = new ExecutionObservabilitySanitizer(new ObjectMapper());

    @Test
    void 应递归屏蔽凭证并隐藏宿主绝对路径() {
        Map<String, Object> actual = sanitizer.map(Map.of(
                "token", "very-secret-token",
                "nested", Map.of("password", "p@ss", "path", "/var/lib/runtime/private.txt")
        ));

        assertEquals("***", actual.get("token"));
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) actual.get("nested");
        assertEquals("***", nested.get("password"));
        assertFalse(String.valueOf(nested.get("path")).contains("/var/lib/runtime"));
    }

    @Test
    void 日志Token与长度应被限制() {
        String result = sanitizer.text("Authorization: Bearer abcdefghijklmnopqrstuvwxyz /tmp/sandbox/output.txt", 40);
        assertFalse(result.contains("abcdefghijklmnopqrstuvwxyz"));
        assertFalse(result.contains("/tmp/sandbox"));
        assertTrue(result.length() <= 41);
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("expected true");
    }
}
