package org.project.model.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class SpaceMemberEntity implements Serializable {
    private Long memberId;
    private UUID spaceId;
    private UUID userId;
    private String role;
    private LocalDateTime joinedAt;
    private UUID invitedBy;
}