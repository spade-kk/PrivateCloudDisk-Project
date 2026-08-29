package org.project.im.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 群角色设置命令，仅群主可设置或取消管理员。 */
@Data
public class GroupMemberRoleCommand {
    @NotBlank(message = "操作者 ID 不能为空")
    private String operatorId;

    @NotNull(message = "角色不能为空")
    private Integer role;
}
