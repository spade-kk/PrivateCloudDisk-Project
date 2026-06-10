package org.project.model.entity;

import lombok.Data;
import java.util.UUID;

@Data
public class DirectoryClosureEntity {
    private UUID userId;
    private UUID ancestorId;
    private UUID descendantId;
    private Integer depth;
}
