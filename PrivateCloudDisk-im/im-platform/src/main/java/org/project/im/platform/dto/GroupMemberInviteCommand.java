package org.project.im.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 群成员邀请命令。 */
@Data
public class GroupMemberInviteCommand {
    @NotBlank(message = "操作者 ID 不能为空")
    private String operatorId;

    @NotEmpty(message = "至少选择一位成员")
    @Size(max = 199, message = "单次最多邀请 199 人")
    private List<String> userIds;
}
