package org.project.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 公开仓库详情；不暴露成员、物理路径或内部权限记录。 */
@Data
public class PublicSpaceDetailVO {
    private String spaceId;
    private String spaceName;
    /** [REQ-GIT-SPACE-12.2] 前端据此动态加载文件仓库或 Git 仓库组件。 */
    private String resourceType;
    private String description;
    private String ownerId;
    private String ownerName;
    private String ownerAvatar;
    private Boolean allowPublicBrowse;
    private Boolean allowPublicDownload;
    private Boolean allowPublicUpload;
    private Integer fileCount;
    private Long usedBytes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
