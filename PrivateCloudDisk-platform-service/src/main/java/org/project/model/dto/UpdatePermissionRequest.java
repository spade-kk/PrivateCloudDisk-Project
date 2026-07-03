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
    private Boolean canRead;
    private Boolean canWrite;
    private Boolean canDelete;
    private Boolean canShare;
    private Boolean canInvite;
    private Boolean canManage;
}