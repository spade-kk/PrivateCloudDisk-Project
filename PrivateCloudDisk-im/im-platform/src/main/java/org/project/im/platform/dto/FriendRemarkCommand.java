package org.project.im.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 联系人备注更新命令。 */
@Data
public class FriendRemarkCommand {
    @NotBlank
    @Size(max = 64)
    private String remark;
}
