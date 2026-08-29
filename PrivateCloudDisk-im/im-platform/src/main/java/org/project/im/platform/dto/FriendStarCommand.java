package org.project.im.platform.dto;

import lombok.Data;

/** 星标状态幂等更新命令。 */
@Data
public class FriendStarCommand {
    private Boolean starred;
}
