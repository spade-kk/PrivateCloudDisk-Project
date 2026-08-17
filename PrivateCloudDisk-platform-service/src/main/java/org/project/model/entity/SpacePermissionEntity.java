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
    /** [SPACE-COLLAB-PERM-01] 新权限矩阵字段；保留旧字段以兼容既有接口。 */
    private Boolean canView;
    private Boolean canDownload;
    private Boolean canUpload;
    private Boolean canEdit;
    private Boolean canManageMembers;
    private Boolean canManagePlugins;
    private Boolean canManageSettings;
    private UUID grantedBy;
    private LocalDateTime grantedAt;
}
