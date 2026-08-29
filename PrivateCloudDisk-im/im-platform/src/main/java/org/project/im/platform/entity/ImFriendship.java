package org.project.im.platform.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 对称好友关系的一侧记录；状态：0-有效、1-已解除。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImFriendship {
    private Long id;
    private String userId;
    private String friendId;
    private Integer status;
    private String remark;
    private Boolean starred;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
