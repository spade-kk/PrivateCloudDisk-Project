package org.project.model.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class SpacePermissionEntity implements Serializable {
    private Long permissionId;
    private UUID spaceId;
    private UUID userId;
    private UUID targetNodeId;
    private Boolean canRead;
    private Boolean canWrite;
    private Boolean canDelete;
    private Boolean canShare;
    private Boolean canInvite;
    private Boolean canManage;
    private UUID grantedBy;
    private LocalDateTime grantedAt;
}