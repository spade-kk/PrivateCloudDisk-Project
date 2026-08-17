package org.project.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新权限请求 DTO。
 * <p>
 * 所有字段可选，只更新传入的非空字段。
 */
@Data
public class UpdatePermissionRequest {
    /** [SPACE-COLLAB-PERM-02] 角色和新权限矩阵字段；旧字段继续兼容。 */
    private String role;
    private Boolean canRead;
    private Boolean canWrite;
    private Boolean canDelete;
    private Boolean canShare;
    private Boolean canInvite;
    private Boolean canManage;
    private Boolean canView;
    private Boolean canDownload;
    private Boolean canUpload;
    private Boolean canEdit;
    private Boolean canManageMembers;
    private Boolean canManagePlugins;
    private Boolean canManageSettings;
}
