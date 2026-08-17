package org.project.model.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/** [SPACE-COLLAB-INVITE-01] 邀请链接实体；明文 token 仅在创建响应中存在，不落库。 */
@Data
public class SpaceInvitationEntity implements Serializable {
    private Long invitationId;
    private UUID spaceId;
    private String tokenHash;
    private UUID createdBy;
    private LocalDateTime expiresAt;
    private Integer maxUses;
    private Integer usedCount;
    private String status;
    private LocalDateTime createdAt;
    private transient String token;
}
