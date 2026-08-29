package org.project.workflow.service;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 能力键格式校验（需求五 5.22 防止能力键注入 / 一 1.9 命名空间规范）。
 *
 * <p>格式：{namespace}:{service}.{method}（如 builtin:date.now、api:file.metadata.get），
 * 插件键允许额外段（plugin:88d5b0e1-..:generate_report@1）。只允许小写字母、数字、点、
 * 下划线、冒号和连字符；空格、引号、反斜杠、${} 等一律拒绝，杜绝 SQL/表达式/REST 路径注入。</p>
 */
public final class CapabilityKeyValidator {
    private static final Set<String> NAMESPACES =
            Set.of("builtin", "api", "plugin", "local_plugin");
    private static final Pattern KEY_BODY =
            Pattern.compile("^[a-z0-9][a-z0-9_.:@-]{0,253}$");
    private static final int MAX_KEY_LENGTH = 255;

    private CapabilityKeyValidator() {
    }

    public static boolean isValid(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            return false;
        }
        int separator = key.indexOf(':');
        if (separator <= 0 || separator == key.length() - 1) {
            return false;
        }
        String namespace = key.substring(0, separator);
        String body = key.substring(separator + 1);
        return NAMESPACES.contains(namespace) && KEY_BODY.matcher(body).matches();
    }
}
