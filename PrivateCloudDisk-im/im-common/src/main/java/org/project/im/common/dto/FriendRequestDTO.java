package org.project.im.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 好友申请 DTO。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendRequestDTO implements Serializable {

    private String requestId;
    private String requesterId;
    private String recipientId;
    /** 请求两端仅暴露最小公开资料，供好友申请列表直接渲染。 */
    private String requesterName;
    private String requesterAccount;
    private String requesterAvatarPath;
    private String recipientName;
    private String recipientAccount;
    private String recipientAvatarPath;
    private String verificationMessage;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
