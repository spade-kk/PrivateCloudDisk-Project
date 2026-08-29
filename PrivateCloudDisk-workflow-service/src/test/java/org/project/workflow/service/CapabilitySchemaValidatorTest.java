package org.project.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilitySchemaValidatorTest {
    private CapabilitySchemaValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CapabilitySchemaValidator(new ObjectMapper());
    }

    private static final String FILE_LIST_SCHEMA = """
            {"type":"object","required":["file_id"],
              "properties":{
                "file_id":{"type":"string","pattern":"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$","maxLength":36},
                "size":{"type":"integer","minimum":1,"maximum":100},
                "tags":{"type":"array","maxItems":3,"items":{"type":"string"}},
                "mode":{"type":"string","enum":["fast","full"]}
              }}""";

    @Test
    void acceptsValidInput() {
        List<String> errors = validator.validate(FILE_LIST_SCHEMA, Map.of(
                "file_id", "00000000-0000-0000-0000-000000000001",
                "size", 10,
                "tags", List.of("a", "b"),
                "mode", "fast"));
        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test
    void rejectsMissingRequiredParameter() {
        List<String> errors = validator.validate(FILE_LIST_SCHEMA, Map.of("size", 1));
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(error -> error.contains("缺少必填参数 file_id")));
    }

    @Test
    void rejectsTypeMismatchAndOverflow() {
        List<String> errors = validator.validate(FILE_LIST_SCHEMA, Map.of(
                "file_id", "00000000-0000-0000-0000-000000000001",
                "size", "10",
                "tags", List.of("a", "b", "c", "d"),
                "mode", "turbo"));
        assertTrue(errors.stream().anyMatch(error -> error.contains("必须是整数")), errors.toString());
        assertTrue(errors.stream().anyMatch(error -> error.contains("不能超过 3")), errors.toString());
        assertTrue(errors.stream().anyMatch(error -> error.contains("不在允许范围内")), errors.toString());
    }

    @Test
    void rejectsMalformedUuidByPattern() {
        List<String> errors = validator.validate(FILE_LIST_SCHEMA, Map.of("file_id", "not-a-uuid"));
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(error -> error.contains("格式不合法")), errors.toString());
    }

    @Test
    void rejectsArrayElementTypeMismatch() {
        List<String> errors = validator.validate(FILE_LIST_SCHEMA, Map.of(
                "file_id", "00000000-0000-0000-0000-000000000001",
                "tags", List.of("a", 42)));
        assertTrue(errors.stream().anyMatch(error -> error.contains("tags[1]")), errors.toString());
    }

    @Test
    void rejectsNumericRangeViolation() {
        List<String> errors = validator.validate(FILE_LIST_SCHEMA, Map.of(
                "file_id", "00000000-0000-0000-0000-000000000001",
                "size", 200));
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(error -> error.contains("不能大于 100")), errors.toString());
    }
}
