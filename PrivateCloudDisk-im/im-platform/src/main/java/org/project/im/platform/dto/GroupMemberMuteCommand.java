package org.project.im.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 群成员禁言命令。 */
@Data
public class GroupMemberMuteCommand {
    @NotBlank(message = "操作者 ID 不能为空")
    private String operatorId;

    /** -1 为长期禁言；正数为分钟。 */
    @NotNull(message = "禁言时长不能为空")
    private Integer durationMinutes;
}
