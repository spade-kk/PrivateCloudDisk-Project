package org.project.plugin.model;

import java.time.LocalDateTime;

/** Docker 风格日志视图的已脱敏日志行投影。 */
public record PluginExecutionLogLine(
        long sequenceNo,
        LocalDateTime timestamp,
        String level,
        String source,
        String content,
        long byteOffset
) {
}
