package org.project.im.platform.dto;

import lombok.Data;

/** 好友申请拒绝选项；blockFuture 为真时持久化拒收规则。 */
@Data
public class FriendRequestDecisionCommand {
    private Boolean blockFuture;
}
