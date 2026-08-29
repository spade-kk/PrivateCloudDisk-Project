package org.project.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 能力参数 JSON Schema 校验（需求五 5.23 / 六 6.10 / 七 7.9）。
 *
 * <p>原实现仅检查 required 字段是否存在；本组件按注册表 input_schema_json 校验
 * 类型、必填、enum、字符串长度、数值范围、长度类数组约束与正则，一次收集多条错误，
 * 非法参数直接拒绝，防止参数注入与超大参数（5.24/5.23）。</p>
 */
@Component
public class CapabilitySchemaValidator {
    private final ObjectMapper objectMapper;

    public CapabilitySchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<String> validate(String schemaJson, Map<String, Object> input) {
        List<String> errors = new ArrayList<>();
        if (input == null) {
            return List.of("缺少能力参数");
        }
        JsonNode schema;
        try {
            schema = objectMapper.readTree(schemaJson);
        } catch (Exception exception) {
            return List.of("能力输入 Schema 无法解析");
        }
        if (schema == null || !schema.isObject()) {
            return List.of("能力输入 Schema 格式无效");
        }
        JsonNode required = schema.get("required");
        List<String> requiredNames = new ArrayList<>();
        if (required != null && required.isArray()) {
            required.forEach(item -> requiredNames.add(item.asText()));
        }
        for (String name : requiredNames) {
            if (!input.containsKey(name) || input.get(name) == null) {
                errors.add("缺少必填参数 " + name);
            }
        }
        JsonNode properties = schema.get("properties");
        if (properties != null && properties.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> property = fields.next();
                String name = property.getKey();
                Object value = input.get(name);
                if (value == null) {
                    continue;
                }
                errors.addAll(validateProperty(name, property.getValue(), value));
            }
        }
        return errors;
    }

    private List<String> validateProperty(String name, JsonNode propertySchema, Object value) {
        List<String> errors = new ArrayList<>();
        String fieldLabel = "参数 " + name;
        if (propertySchema == null || !propertySchema.isObject()) {
            return errors;
        }
        JsonNode enumNode = propertySchema.get("enum");
        if (enumNode != null && enumNode.isArray()) {
            boolean matched = false;
            for (JsonNode candidate : enumNode) {
                if (candidate.asText().equals(String.valueOf(value))) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                errors.add(fieldLabel + "取值不在允许范围内");
            }
        }
        String type = propertySchema.path("type").asText();
        if (type.isEmpty() || "any".equals(type)) {
            return errors;
        }
        switch (type) {
            case "string" -> errors.addAll(validateString(name, propertySchema, value));
            case "integer" -> errors.addAll(validateInteger(name, propertySchema, value));
            case "number" -> errors.addAll(validateNumber(name, propertySchema, value));
            case "boolean" -> {
                if (!(value instanceof Boolean)) {
                    errors.add(fieldLabel + "必须是布尔值");
                }
            }
            case "array" -> errors.addAll(validateArray(name, propertySchema, value));
            case "object" -> {
                if (!(value instanceof Map<?, ?>)) {
                    errors.add(fieldLabel + "必须是对象");
                }
            }
            default -> {
                // 未知类型：不做更严格校验，保持向后兼容。
            }
        }
        return errors;
    }

    private List<String> validateString(String name, JsonNode schema, Object value) {
        List<String> errors = new ArrayList<>();
        if (!(value instanceof String text)) {
            errors.add("参数 " + name + " 必须是字符串");
            return errors;
        }
        int length = text.length();
        if (schema.has("minLength") && length < schema.get("minLength").asInt()) {
            errors.add("参数 " + name + " 长度不能小于 " + schema.get("minLength").asInt());
        }
        if (schema.has("maxLength") && length > schema.get("maxLength").asInt()) {
            errors.add("参数 " + name + " 长度不能超过 " + schema.get("maxLength").asInt());
        }
        if (schema.has("pattern")) {
            String pattern = schema.get("pattern").asText();
            try {
                if (!text.matches(pattern)) {
                    errors.add("参数 " + name + " 格式不合法");
                }
            } catch (java.util.regex.PatternSyntaxException ignored) {
                // 注册表模式非法时忽略，避免构造期异常。
            }
        }
        return errors;
    }

    private List<String> validateInteger(String name, JsonNode schema, Object value) {
        List<String> errors = new ArrayList<>();
        if (!(value instanceof Number numberValue)) {
            errors.add("参数 " + name + " 必须是整数");
            return errors;
        }
        Long number = numberValue.longValue();
        if (schema.has("minimum") && number < schema.get("minimum").asLong()) {
            errors.add("参数 " + name + " 不能小于 " + schema.get("minimum").asLong());
        }
        if (schema.has("maximum") && number > schema.get("maximum").asLong()) {
            errors.add("参数 " + name + " 不能大于 " + schema.get("maximum").asLong());
        }
        return errors;
    }

    private List<String> validateNumber(String name, JsonNode schema, Object value) {
        List<String> errors = new ArrayList<>();
        if (!(value instanceof Number)) {
            errors.add("参数 " + name + " 必须是数字");
            return errors;
        }
        double number = ((Number) value).doubleValue();
        if (schema.has("minimum") && number < schema.get("minimum").asDouble()) {
            errors.add("参数 " + name + " 不能小于 " + schema.get("minimum").asDouble());
        }
        if (schema.has("maximum") && number > schema.get("maximum").asDouble()) {
            errors.add("参数 " + name + " 不能大于 " + schema.get("maximum").asDouble());
        }
        return errors;
    }

    private List<String> validateArray(String name, JsonNode schema, Object value) {
        List<String> errors = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            errors.add("参数 " + name + " 必须是数组");
            return errors;
        }
        if (schema.has("maxItems") && list.size() > schema.get("maxItems").asInt()) {
            errors.add("参数 " + name + " 元素数量不能超过 " + schema.get("maxItems").asInt());
        }
        if (schema.has("minItems") && list.size() < schema.get("minItems").asInt()) {
            errors.add("参数 " + name + " 元素数量不能少于 " + schema.get("minItems").asInt());
        }
        JsonNode items = schema.get("items");
        if (items != null && items.isObject() && items.has("type")) {
            String itemType = items.get("type").asText();
            for (int index = 0; index < list.size(); index++) {
                Object item = list.get(index);
                boolean valid = switch (itemType) {
                    case "string" -> item instanceof String;
                    case "integer" -> item instanceof Number;
                    case "boolean" -> item instanceof Boolean;
                    default -> true;
                };
                if (!valid) {
                    errors.add("参数 " + name + "[" + index + "] 类型不合法");
                }
            }
        }
        return errors;
    }
}
