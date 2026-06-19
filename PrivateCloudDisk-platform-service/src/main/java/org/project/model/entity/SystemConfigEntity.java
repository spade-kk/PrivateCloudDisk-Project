package org.project.model.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 系统配置数据类
 */
@Data
public class SystemConfigEntity implements Serializable {
    private Long configId;
    private String configKey;
    private String configValue;
    private String configGroup;
    private String configDescription;
    private boolean configEditable;
    private Integer configVersion;
    private UUID configUpdatedBy;
    private LocalDateTime configUpdatedAt;
    private LocalDateTime configCreatedAt;
}