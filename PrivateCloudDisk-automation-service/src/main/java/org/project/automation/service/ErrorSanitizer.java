package org.project.automation.service;

/** 插件/下游异常摘要脱敏；完整堆栈只保留在受限服务日志。 */
public final class ErrorSanitizer {
    private ErrorSanitizer() {
    }

    public static String summarize(Throwable throwable) {
        String value = throwable == null || throwable.getMessage() == null
                ? "内部服务执行失败"
                : throwable.getMessage();
        value = value
                .replaceAll("(?i)(token|password|secret|authorization)=?[^\\s,;]+", "$1=[REDACTED]")
                .replaceAll("(/[^\\s:]+)+", "[INTERNAL_PATH]");
        return value.substring(0, Math.min(value.length(), 1000));
    }
}

