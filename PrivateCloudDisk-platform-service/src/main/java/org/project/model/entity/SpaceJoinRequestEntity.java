package org.project.model.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class SpaceJoinRequestEntity implements Serializable {
    private Long requestId;
    private UUID spaceId;
    private UUID userId;
    private String requestMessage;
    private String status;
    private UUID reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}