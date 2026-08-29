package org.project.im.platform.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 用户级 IM 黑名单；写入 Redis 后即时生效，数据库作为持久化真相来源。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImBlacklist {
    private Long id;
    private String userId;
    private String blockedUserId;
    private LocalDateTime createdAt;
}
