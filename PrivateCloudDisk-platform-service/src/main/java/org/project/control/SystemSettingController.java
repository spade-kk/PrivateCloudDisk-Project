package org.project.control;

import lombok.RequiredArgsConstructor;
import org.project.control.result.JsonResult;
import org.project.model.dto.SystemSettingUpdateRequest;
import org.project.model.vo.SystemSettingVO;
import org.project.service.SystemSettingService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户系统设置控制器
 *
 * <p>提供用户个性化系统设置的查询和更新接口。
 * 使用 PATCH 方法进行部分更新（partial update），符合 RESTful 语义。
 *
 * <p>API 路径：
 * <ul>
 *   <li>GET  /business/settings — 获取用户系统设置</li>
 *   <li>PATCH /business/settings — 部分更新用户系统设置</li>
 * </ul>
 *
 * <p>安全：所有接口需要登录认证（通过 X-User-Id 请求头传递用户ID）。
 * 网关层已验证 JWT 并注入 X-User-Id，控制器层不再重复验证。
 *
 * <p>PATCH vs PUT 设计说明：
 * <ul>
 *   <li>PATCH 表示对资源的部分修改（partial update），请求体只需包含需要变更的字段</li>
 *   <li>PUT 表示对资源的完整替换（full replacement），请求体需包含所有字段</li>
 *   <li>系统设置包含多个分组（preferences, notificationSettings, appearance, language），
 *       用户通常只修改其中一个分组，因此使用 PATCH 更合理</li>
 * </ul>
 */
@RestController
@Validated
@RequiredArgsConstructor
public class SystemSettingController extends BaseController {

    private final SystemSettingService systemSettingService;

    // ============================================================
    // 获取用户系统设置
    // ============================================================

    /**
     * 获取当前用户的系统设置
     *
     * <p>返回用户的偏好设置、通知设置、外观设置和语言。
     * 如果是首次访问（无设置记录），自动创建默认设置并返回。
     *
     * <p>请求示例：
     * <pre>{@code
     * GET /api/v1/business/settings
     * Header: X-User-Id: aaa-bbb-ccc
     * }</pre>
     *
     * <p>响应示例：
     * <pre>{@code
     * {
     *   "code": 200,
     *   "message": null,
     *   "data": {
     *     "preferences": { "defaultView": "grid", "itemsPerPage": 50, ... },
     *     "notificationSettings": { "emailNotifications": true, ... },
     *     "appearance": { "theme": "light", "fontSize": "medium", ... },
     *     "language": "zh-CN"
     *   }
     * }
     * }</pre>
     *
     * @param userId 用户ID（由网关从 JWT 中提取并注入请求头）
     * @return 系统设置 VO
     */
    @GetMapping("/business/settings")
    public JsonResult<SystemSettingVO> getUserSettings(
            @RequestHeader("X-User-Id") String userId) {
        SystemSettingVO vo = systemSettingService.getUserSettings(userId);
        return new JsonResult<>(OK, vo);
    }

    // ============================================================
    // 部分更新用户系统设置（PATCH）
    // ============================================================

    /**
     * 部分更新当前用户的系统设置（PATCH）
     *
     * <p>使用 PATCH 语义，请求体只需包含需要变更的字段组。
     * 未传入的字段组保持原值不变。
     * JSON 字段组（preferences, notificationSettings, appearance）内部使用合并策略：
     * 传入的 key 覆盖旧值，未传入的 key 保持原值。
     *
     * <p>请求示例（仅更新偏好设置和外观）：
     * <pre>{@code
     * PATCH /api/v1/business/settings
     * Header: X-User-Id: aaa-bbb-ccc
     * Body:
     * {
     *   "preferences": { "defaultView": "list", "itemsPerPage": 100 },
     *   "appearance": { "theme": "dark" }
     * }
     * }</pre>
     *
     * <p>请求示例（仅更新语言）：
     * <pre>{@code
     * PATCH /api/v1/business/settings
     * Body:
     * {
     *   "language": "en-US"
     * }
     * }</pre>
     *
     * @param request 系统设置更新请求（所有字段均为可选）
     * @param userId  用户ID（由网关从 JWT 中提取并注入请求头）
     * @return 更新后的完整系统设置 VO
     */
    @PatchMapping("/business/settings")
    public JsonResult<SystemSettingVO> updateSettings(
            @RequestBody SystemSettingUpdateRequest request,
            @RequestHeader("X-User-Id") String userId) {
        SystemSettingVO vo = systemSettingService.updateSettings(
                userId,
                request.getPreferences(),
                request.getNotificationSettings(),
                request.getAppearance(),
                request.getLanguage());
        return new JsonResult<>(OK, vo);
    }
}