package org.project.im.platform.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 好友申请实体；状态：0-待处理、1-已接受、2-已拒绝、3-已撤销。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImFriendRequest {
    private Long id;
    private String requestId;
    private String requesterId;
    private String recipientId;
    private String verificationMessage;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
