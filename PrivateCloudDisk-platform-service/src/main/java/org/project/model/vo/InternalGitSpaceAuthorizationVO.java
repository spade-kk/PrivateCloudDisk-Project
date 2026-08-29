package org.project.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * [REQ-GIT-PERM-9.1/9.6] Git Service 的最小空间授权投影。
 * 不返回成员清单、文件路径或权限表主键，仓库级授权继续由 Git Service 独立维护。
 */
public record InternalGitSpaceAuthorizationVO(
        boolean active,
        @JsonProperty("space_id") String spaceId,
        @JsonProperty("owner_id") String ownerId,
        @JsonProperty("resource_type") String resourceType,
        @JsonProperty("permission_level") String permissionLevel,
        @JsonProperty("allow_public_browse") boolean allowPublicBrowse,
        @JsonProperty("allow_public_download") boolean allowPublicDownload,
        @JsonProperty("allow_public_upload") boolean allowPublicUpload
) {}
