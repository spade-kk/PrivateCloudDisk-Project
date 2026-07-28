package org.project.model.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 系统设置更新请求（PATCH 接口）
 *
 * <p>使用 PATCH 语义，客户端只需传递需要更新的字段，未传递的字段保持原值不变。
 * 与 PUT 不同，PATCH 表示部分更新（partial update），更符合 RESTful 语义。
 *
 * <p>请求体示例：
 * <pre>{@code
 * {
 *   "preferences": { "defaultView": "list", "itemsPerPage": 100 },
 *   "appearance": { "theme": "dark" },
 *   "language": "en-US"
 * }
 * }</pre>
 *
 * <p>所有字段均为可选（nullable），服务端仅更新非 null 的字段。
 * 若需清空某个 JSON 分组，可传递空对象 {}。
 */
@Data
public class SystemSettingUpdateRequest {

    /**
     * 偏好设置（部分更新）
     * <p>可更新字段：defaultView, itemsPerPage, autoPlay, timezone 等
     * <p>传入的 Map 中的 key 会与现有设置合并，未传入的 key 保持原值
     */
    private Map<String, Object> preferences;

    /**
     * 通知设置（部分更新）
     * <p>可更新字段：emailNotifications, pushNotifications, fileShared,
     * fileDownloaded, storageWarning, securityAlerts, marketingEmails, weeklyDigest 等
     * <p>传入的 Map 中的 key 会与现有设置合并，未传入的 key 保持原值
     */
    private Map<String, Object> notificationSettings;

    /**
     * 外观设置（部分更新）
     * <p>可更新字段：theme, fontSize, density, sidebarCollapsed, animationEnabled
     * <p>传入的 Map 中的 key 会与现有设置合并，未传入的 key 保持原值
     */
    private Map<String, Object> appearance;

    /**
     * UI 语言设置
     * <p>格式：如 zh-CN, en-US, ja-JP 等
     */
    @Size(min = 2, max = 10, message = "语言代码长度必须在2-10个字符之间")
    private String language;
}