package org.project.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.mapper.UserSettingMapper;
import org.project.model.entity.UserSettingEntity;
import org.project.model.vo.SystemSettingVO;
import org.project.service.SystemSettingService;
import org.project.service.ex.InsertException;
import org.project.service.ex.ServiceException;
import org.project.service.ex.UpdateException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 用户系统设置服务实现
 *
 * <p>管理用户个性化的系统设置，包括偏好设置、通知设置、外观设置和语言。
 * 使用 Jackson ObjectMapper 进行 JSON 字段的序列化和反序列化。
 *
 * <p>更新策略（PATCH 语义）：
 * <ul>
 *   <li>JSON 字段（preferences, notificationSettings, appearance）：传入的 Map 与现有设置合并，
 *       即传入的 key 覆盖旧值，未传入的 key 保持原值</li>
 *   <li>language 字段：传入非 null 值时直接替换</li>
 *   <li>所有字段均为可选，仅更新传入的非 null 字段</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemSettingServiceImpl implements SystemSettingService {

    private final UserSettingMapper userSettingMapper;
    private final ObjectMapper objectMapper;

    /** 默认语言 */
    private static final String DEFAULT_LANGUAGE = "zh-CN";

    /** 默认偏好设置 JSON */
    private static final String DEFAULT_PREFERENCES = """
            {
                "defaultView": "grid",
                "itemsPerPage": 50,
                "autoPlay": true,
                "timezone": "Asia/Shanghai"
            }""";

    /** 默认通知设置 JSON */
    private static final String DEFAULT_NOTIFICATION_SETTINGS = """
            {
                "emailNotifications": true,
                "pushNotifications": true,
                "fileShared": true,
                "fileDownloaded": false,
                "storageWarning": true,
                "securityAlerts": true,
                "marketingEmails": false,
                "weeklyDigest": true
            }""";

    /** 默认外观设置 JSON */
    private static final String DEFAULT_APPEARANCE = """
            {
                "theme": "light",
                "fontSize": "medium",
                "density": "comfortable",
                "sidebarCollapsed": false,
                "animationEnabled": true
            }""";

    // ============================================================
    // 获取用户系统设置
    // ============================================================

    @Override
    public SystemSettingVO getUserSettings(String userId) {
        UUID uid = UUID.fromString(userId);
        UserSettingEntity entity = userSettingMapper.findByUserId(uid);

        // 首次访问：自动初始化默认设置
        if (entity == null) {
            entity = initializeDefaultSettings(userId);
        }

        return toVO(entity);
    }

    // ============================================================
    // 更新用户系统设置（PATCH 部分更新）
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SystemSettingVO updateSettings(String userId,
                                          Map<String, Object> preferences,
                                          Map<String, Object> notificationSettings,
                                          Map<String, Object> appearance,
                                          String language) {
        UUID uid = UUID.fromString(userId);

        // 确保用户有设置记录
        UserSettingEntity existing = userSettingMapper.findByUserId(uid);
        if (existing == null) {
            existing = initializeDefaultSettings(userId);
        }

        // 更新偏好设置（JSON 合并）
        if (preferences != null && !preferences.isEmpty()) {
            Map<String, Object> merged = mergeJson(existing.getPreferences(), preferences);
            String json = toJson(merged);
            int rows = userSettingMapper.updatePreferences(uid, json);
            if (rows <= 0) {
                throw new UpdateException("更新偏好设置失败");
            }
            existing.setPreferences(json);
        }

        // 更新通知设置（JSON 合并）
        if (notificationSettings != null && !notificationSettings.isEmpty()) {
            Map<String, Object> merged = mergeJson(existing.getNotificationSettings(), notificationSettings);
            String json = toJson(merged);
            int rows = userSettingMapper.updateNotificationSettings(uid, json);
            if (rows <= 0) {
                throw new UpdateException("更新通知设置失败");
            }
            existing.setNotificationSettings(json);
        }

        // 更新外观设置（JSON 合并）
        if (appearance != null && !appearance.isEmpty()) {
            Map<String, Object> merged = mergeJson(existing.getAppearance(), appearance);
            String json = toJson(merged);
            int rows = userSettingMapper.updateAppearance(uid, json);
            if (rows <= 0) {
                throw new UpdateException("更新外观设置失败");
            }
            existing.setAppearance(json);
        }

        // 更新语言
        if (language != null && !language.isBlank()) {
            int rows = userSettingMapper.updateLanguage(uid, language);
            if (rows <= 0) {
                throw new UpdateException("更新语言设置失败");
            }
            existing.setLanguage(language);
        }

        log.info("用户 {} 系统设置更新成功", userId);
        return toVO(existing);
    }

    // ============================================================
    // 初始化默认设置
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserSettingEntity initializeDefaultSettings(String userId) {
        UUID uid = UUID.fromString(userId);

        UserSettingEntity entity = new UserSettingEntity();
        entity.setUserId(uid);
        entity.setPreferences(DEFAULT_PREFERENCES);
        entity.setNotificationSettings(DEFAULT_NOTIFICATION_SETTINGS);
        entity.setAppearance(DEFAULT_APPEARANCE);
        entity.setLanguage(DEFAULT_LANGUAGE);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        int rows = userSettingMapper.insert(entity);
        if (rows <= 0) {
            throw new InsertException("初始化用户系统设置失败");
        }

        log.info("用户 {} 系统设置已初始化", userId);
        return entity;
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

    /**
     * 将 JSON 字符串解析为 Map
     *
     * @param json JSON 字符串
     * @return Map 对象，解析失败返回空 Map
     */
    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.error("JSON 解析失败: {}", json, e);
            return new HashMap<>();
        }
    }

    /**
     * 将 Map 序列化为 JSON 字符串
     *
     * @param map Map 对象
     * @return JSON 字符串
     */
    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败", e);
            throw new ServiceException("系统设置序列化失败");
        }
    }

    /**
     * JSON 字段合并：将 newValues 合并到 existing JSON 中
     * <p>newValues 中的 key 覆盖旧值，未传入的 key 保持原值
     *
     * @param existingJson 现有 JSON 字符串
     * @param newValues    新值 Map
     * @return 合并后的 Map
     */
    private Map<String, Object> mergeJson(String existingJson, Map<String, Object> newValues) {
        Map<String, Object> existing = parseJson(existingJson);
        existing.putAll(newValues);
        return existing;
    }

    /**
     * 将 UserSettingEntity 转换为 SystemSettingVO
     *
     * @param entity 用户设置实体
     * @return 系统设置 VO
     */
    private SystemSettingVO toVO(UserSettingEntity entity) {
        SystemSettingVO vo = new SystemSettingVO();
        vo.setPreferences(parseJson(entity.getPreferences()));
        vo.setNotificationSettings(parseJson(entity.getNotificationSettings()));
        vo.setAppearance(parseJson(entity.getAppearance()));
        vo.setLanguage(entity.getLanguage());
        return vo;
    }
}