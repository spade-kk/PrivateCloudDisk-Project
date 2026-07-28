package org.project.model.vo;

import lombok.Data;

import java.util.Map;

/**
 * 系统设置响应 VO（User System Settings View Object）
 *
 * <p>返回给前端的用户系统设置数据，包含偏好设置、通知设置、外观设置和语言。
 * JSON 字段在服务端解析为 Map 后返回，前端可直接使用。
 *
 * <p>响应示例：
 * <pre>{@code
 * {
 *   "preferences": {
 *     "defaultView": "grid",
 *     "itemsPerPage": 50,
 *     "autoPlay": true,
 *     "language": "zh-CN",
 *     "timezone": "Asia/Shanghai"
 *   },
 *   "notificationSettings": {
 *     "emailNotifications": true,
 *     "pushNotifications": true,
 *     "fileShared": true,
 *     "fileDownloaded": false,
 *     "storageWarning": true,
 *     "securityAlerts": true,
 *     "marketingEmails": false,
 *     "weeklyDigest": true
 *   },
 *   "appearance": {
 *     "theme": "light",
 *     "fontSize": "medium",
 *     "density": "comfortable",
 *     "sidebarCollapsed": false,
 *     "animationEnabled": true
 *   },
 *   "language": "zh-CN"
 * }
 * }</pre>
 */
@Data
public class SystemSettingVO {

    /** 偏好设置（默认视图、每页条数、自动播放、时区等） */
    private Map<String, Object> preferences;

    /** 通知设置（邮件通知、推送通知、通知频率等） */
    private Map<String, Object> notificationSettings;

    /** 外观设置（主题、字体大小、布局密度、侧边栏折叠等） */
    private Map<String, Object> appearance;

    /** UI 语言代码 */
    private String language;
}