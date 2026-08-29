package org.project.plugin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * [PLUGIN-EXEC-OBS-001] 日志与审计的第二道脱敏屏障。
 *
 * <p>原有 Runtime 已做一次脱敏；此处仍在持久化前按字段名递归屏蔽凭证并去除绝对宿主路径，
 * 防止未来任一内部调用方绕过 Runtime 时把敏感数据带到浏览器。</p>
 */
@Component
public class ExecutionObservabilitySanitizer {
    private static final Pattern UNIX_ABSOLUTE_PATH = Pattern.compile("(?<![A-Za-z0-9_.-])/(?:[^\\s\\\"']{1,180})");
    private static final Pattern TOKEN_VALUE = Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+/-]{12,}");
    private static final int MAX_TEXT = 16 * 1024;
    private final ObjectMapper objectMapper;

    public ExecutionObservabilitySanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String text(String value, int limit) {
        if (value == null) return "";
        String sanitized = TOKEN_VALUE.matcher(value).replaceAll("$1***");
        sanitized = UNIX_ABSOLUTE_PATH.matcher(sanitized).replaceAll("[path]");
        int safeLimit = Math.max(1, Math.min(limit, MAX_TEXT));
        return sanitized.length() <= safeLimit ? sanitized : sanitized.substring(0, safeLimit) + "…";
    }

    public Map<String, Object> map(Map<String, Object> value) {
        if (value == null || value.isEmpty()) return Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) sanitize(value, "");
        return result;
    }

    public String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(map(value));
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    public Map<String, Object> parseJsonObject(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            Map<String, Object> parsed = objectMapper.readValue(value, new TypeReference<>() {});
            return map(parsed);
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private Object sanitize(Object value, String fieldName) {
        if (isSensitive(fieldName)) return "***";
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            raw.forEach((key, nested) -> mapped.put(String.valueOf(key), sanitize(nested, String.valueOf(key))));
            return mapped;
        }
        if (value instanceof List<?> raw) {
            List<Object> mapped = new ArrayList<>();
            for (Object nested : raw) mapped.add(sanitize(nested, fieldName));
            return mapped;
        }
        if (value instanceof String text) return text(text, 4096);
        return value;
    }

    private static boolean isSensitive(String fieldName) {
        String normalized = fieldName == null ? "" : fieldName.toLowerCase(Locale.ROOT);
        return normalized.contains("password") || normalized.contains("secret")
                || normalized.contains("token") || normalized.contains("authorization")
                || normalized.contains("credential") || normalized.endsWith("_key");
    }
}
