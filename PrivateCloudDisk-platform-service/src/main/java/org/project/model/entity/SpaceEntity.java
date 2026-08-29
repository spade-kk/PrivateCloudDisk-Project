package org.project.model.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class SpaceEntity implements Serializable {
    public enum SpaceType { PERSONAL, PRIVATE, ENTERPRISE, PUBLIC, TEAM }
    public enum SpaceVisibility { private_space, public_space, whitelist, blacklist }
    public enum SpaceStatus { active, disabled, deleted }

    private UUID spaceId;
    private String spaceName;
    private String spaceType;
    /**
     * [REQ-GIT-SPACE-2.2] 空间挂载的资源实现类型。
     * 原行为：public 空间隐式等同文件仓库；新行为：空间只负责身份、成员和策略，
     * file/git 由资源提供者实现。历史记录由数据库默认值继续解析为 file。
     * 影响范围：公开空间详情、资源初始化与 Git Service 授权，不改变既有 spaceType 语义。
     */
    private String resourceType;
    private UUID spaceOwnerId;
    private Long spaceQuota;
    private Long spaceUsed;
    private Integer spaceFileCount;
    private String spaceVisibility;
    /** [SPACE-COLLAB-DB-01] 加入策略；旧记录为空时按 invite_only 处理。 */
    private String joinPolicy;
    /**
     * 公开仓库权限开关。仅 spaceType=public 生效；其他类型保留默认值，避免影响既有空间权限模型。
     */
    private Boolean allowPublicBrowse;
    private Boolean allowPublicDownload;
    private Boolean allowPublicUpload;
    private String spaceDescription;
    private String spaceAvatarPath;
    private String spaceImGroupId;
    private LocalDateTime spaceCreatedAt;
    private LocalDateTime spaceUpdatedAt;
    private String spaceStatus;
}
