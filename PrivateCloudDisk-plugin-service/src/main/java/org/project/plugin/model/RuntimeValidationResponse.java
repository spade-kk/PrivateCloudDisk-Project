package org.project.plugin.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/** Runtime 静态校验响应，定位信息可直接映射到 Monaco 标记。 */
public record RuntimeValidationResponse(
        boolean valid,
        @JsonProperty("error_type") String errorType,
        Integer line,
        Integer column,
        String message,
        String suggestion,
        List<Map<String, Object>> findings,
        Map<String, Object> metrics
) {
}
