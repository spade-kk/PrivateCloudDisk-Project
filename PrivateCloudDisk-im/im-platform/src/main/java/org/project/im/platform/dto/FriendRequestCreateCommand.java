package org.project.im.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 好友申请创建命令。
 *
 * <p>FRIEND-MANAGEMENT-20260810 [5.2]：当前 IM HTTP 接口尚未从网关透传
 * 已认证用户主体，因此调用方显式携带 requesterId；服务端仍会校验双方关系和黑名单。
 * 后续认证上下文统一后可在 Controller 层替换来源，DTO 字段保持兼容。</p>
 */
@Data
public class FriendRequestCreateCommand {
    @NotBlank
    private String requesterId;
    @NotBlank
    private String recipientId;
    @Size(max = 50)
    private String message;
}
