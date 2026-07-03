package org.project.model.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class SpaceEntity implements Serializable {
    public enum SpaceType { PERSONAL, ENTERPRISE, PUBLIC, TEAM }
    public enum SpaceVisibility { private_space, public_space, whitelist, blacklist }
    public enum SpaceStatus { active, disabled, deleted }

    private UUID spaceId;
    private String spaceName;
    private String spaceType;
    private UUID spaceOwnerId;
    private Long spaceQuota;
    private Long spaceUsed;
    private Integer spaceFileCount;
    private String spaceVisibility;
    private String spaceDescription;
    private String spaceAvatarPath;
    private String spaceImGroupId;
    private LocalDateTime spaceCreatedAt;
    private LocalDateTime spaceUpdatedAt;
    private String spaceStatus;
}