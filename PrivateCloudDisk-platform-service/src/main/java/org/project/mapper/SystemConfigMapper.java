package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.model.entity.SystemConfigEntity;

import java.util.List;

@Mapper
public interface SystemConfigMapper {
    SystemConfigEntity findByConfigKey(@Param("config_key") String configKey);

    List<SystemConfigEntity> findAll();

    int updateConfigValue(@Param("config_key") String configKey,
                          @Param("config_value") String configValue,
                          @Param("config_version") Integer configVersion);
}