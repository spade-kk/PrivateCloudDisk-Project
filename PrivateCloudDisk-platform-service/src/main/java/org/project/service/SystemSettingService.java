package org.project.service;

import org.project.model.entity.UserSettingEntity;
import org.project.model.vo.SystemSettingVO;

import java.util.Map;

/**
 * 用户系统设置服务接口
 *
 * <p>管理用户个性化的系统设置，包括偏好设置、通知设置、外观设置和语言。
 * 每个用户首次访问时自动初始化默认设置，后续支持 PATCH 部分更新。
 */
public interface SystemSettingService {

    /**
     * 获取用户的系统设置
     * <p>如果用户是首次访问（无设置记录），自动创建默认设置并返回。
     *
     * @param userId 用户ID
     * @return 系统设置 VO（包含 preferences, notificationSettings, appearance, language）
     */
    SystemSettingVO getUserSettings(String userId);

    /**
     * 更新用户的系统设置（PATCH 部分更新）
     * <p>仅更新请求中非 null 的字段，未传递的字段保持原值。
     * <p>JSON 字段（preferences, notificationSettings, appearance）使用合并策略：
     * 传入的 Map 中的 key 会与现有设置合并，未传入的 key 保持原值。
     *
     * @param userId              用户ID
     * @param preferences         偏好设置（可为 null，表示不更新）
     * @param notificationSettings 通知设置（可为 null，表示不更新）
     * @param appearance          外观设置（可为 null，表示不更新）
     * @param language            语言代码（可为 null，表示不更新）
     * @return 更新后的完整系统设置 VO
     */
    SystemSettingVO updateSettings(String userId,
                                   Map<String, Object> preferences,
                                   Map<String, Object> notificationSettings,
                                   Map<String, Object> appearance,
                                   String language);

    /**
     * 为新用户初始化默认系统设置
     * <p>在用户注册或首次访问设置页面时调用。
     *
     * @param userId 用户ID
     * @return 初始化的设置实体
     */
    UserSettingEntity initializeDefaultSettings(String userId);
}