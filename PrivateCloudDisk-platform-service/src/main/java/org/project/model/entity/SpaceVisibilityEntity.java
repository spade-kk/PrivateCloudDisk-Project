package org.project.model.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class SpaceVisibilityEntity implements Serializable {
    private Long visibilityId;
    private UUID spaceId;
    private UUID userId;
    private String listType;
    private LocalDateTime createdAt;
}