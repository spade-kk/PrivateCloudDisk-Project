package org.project.plugin.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** 仅受信内部服务可写入的单条脱敏 Runtime 日志。 */
public record ExecutionLogInput(
        @JsonProperty("timestamp") Instant timestamp,
        @NotBlank @Pattern(regexp = "DEBUG|INFO|WARN|ERROR") String level,
        @NotBlank @Pattern(regexp = "STDOUT|STDERR|PYCLOUDSDK|SYSTEM|RUNNER") String source,
        @NotBlank @Size(max = 65536) String message,
        @JsonProperty("byte_offset") Long byteOffset
) {
}
