package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.model.entity.UserSettingEntity;

import java.util.UUID;

/**
 * 用户系统设置 Mapper
 *
 * <p>每个用户拥有一行设置记录，首次访问时自动创建（INSERT），
 * 后续更新使用 UPDATE。
 */
@Mapper
public interface UserSettingMapper {

    /**
     * 根据用户ID查询系统设置
     *
     * @param userId 用户ID（UUID）
     * @return 用户设置实体，不存在时返回 null
     */
    UserSettingEntity findByUserId(@Param("userId") UUID userId);

    /**
     * 插入新用户的系统设置（首次初始化）
     * <p>新用户创建时调用，设置默认值。
     *
     * @param entity 用户设置实体
     * @return 影响行数
     */
    int insert(@Param("entity") UserSettingEntity entity);

    /**
     * 更新用户的偏好设置（JSON 字段）
     * <p>使用 PATCH 语义，仅更新传入的 JSON 字段。
     *
     * @param userId      用户ID
     * @param preferences 偏好设置 JSON 字符串
     * @return 影响行数
     */
    int updatePreferences(@Param("userId") UUID userId,
                          @Param("preferences") String preferences);

    /**
     * 更新用户的通知设置（JSON 字段）
     *
     * @param userId              用户ID
     * @param notificationSettings 通知设置 JSON 字符串
     * @return 影响行数
     */
    int updateNotificationSettings(@Param("userId") UUID userId,
                                   @Param("notificationSettings") String notificationSettings);

    /**
     * 更新用户的外观设置（JSON 字段）
     *
     * @param userId     用户ID
     * @param appearance 外观设置 JSON 字符串
     * @return 影响行数
     */
    int updateAppearance(@Param("userId") UUID userId,
                         @Param("appearance") String appearance);

    /**
     * 更新用户的语言设置
     *
     * @param userId   用户ID
     * @param language 语言代码
     * @return 影响行数
     */
    int updateLanguage(@Param("userId") UUID userId,
                       @Param("language") String language);
}