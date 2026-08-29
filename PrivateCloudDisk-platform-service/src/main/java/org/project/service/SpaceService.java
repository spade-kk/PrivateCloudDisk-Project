package org.project.service;

import org.project.model.entity.*;
import org.project.model.dto.UpdatePermissionRequest;
import java.util.List;
import java.util.UUID;

public interface SpaceService {
    SpaceEntity createSpace(UUID userId, String spaceName, String spaceType, String resourceType,
                            String spaceDescription, String spaceVisibility, String joinPolicy);
    SpaceEntity getSpaceById(UUID spaceId, UUID userId);
    List<SpaceEntity> getUserSpaces(UUID userId);
    List<SpaceEntity> getUserSpacesByType(UUID userId, String spaceType);
    List<SpaceEntity> discoverPublicSpaces(String keyword);
    List<SpaceEntity> discoverCollaborationSpaces(String keyword);
    SpaceEntity getSpacePreview(UUID spaceId, UUID userId);
    List<SpaceJoinRequestEntity> getMyJoinRequests(UUID userId);
    void cancelJoinRequest(Long requestId, UUID userId);
    String createInvitation(UUID spaceId, UUID userId, int expiresHours, int maxUses);
    void redeemInvitation(String token, UUID userId);
    void revokeInvitation(UUID spaceId, UUID userId, Long invitationId);
    SpaceEntity getPublicSpaceByName(String spaceName);
    SpaceEntity updateSpace(UUID spaceId, UUID userId, String spaceName, String spaceDescription, String spaceVisibility, String joinPolicy, Long spaceQuota);
    /** 公开仓库设置；仅所有者可调用，成员模型不参与判断。 */
    SpaceEntity updatePublicRepository(UUID spaceId, UUID userId, String spaceName, String description,
                                        Boolean allowBrowse, Boolean allowDownload, Boolean allowUpload);
    void deleteSpace(UUID spaceId, UUID userId);

    void addMember(UUID spaceId, UUID userId, UUID targetUserId, String role);
    default List<SpaceMemberEntity> getMembers(UUID spaceId, UUID userId) {
        return getMembers(spaceId, userId, null);
    }
    List<SpaceMemberEntity> getMembers(UUID spaceId, UUID userId, String keyword);
    void updateMemberRole(UUID spaceId, UUID userId, UUID targetUserId, String role);
    void removeMember(UUID spaceId, UUID userId, UUID targetUserId);

    void updatePermission(UUID spaceId, UUID userId, UUID targetUserId, UpdatePermissionRequest request);
    SpacePermissionEntity getPermission(UUID spaceId, UUID targetUserId);

    default void requestJoin(UUID spaceId, UUID userId, String message) {
        requestJoin(spaceId, userId, message, null);
    }
    void requestJoin(UUID spaceId, UUID userId, String message, String inviteToken);
    List<SpaceJoinRequestEntity> getJoinRequests(UUID spaceId, UUID userId, String status);
    void reviewJoinRequest(UUID spaceId, UUID reqUserId, UUID reviewerId, String action);
    void reviewJoinRequestById(Long requestId, UUID reviewerId, String action);

    void updateVisibilityList(UUID spaceId, UUID userId, List<UUID> targetUserIds, String listType);
    List<SpaceVisibilityEntity> getVisibilityList(UUID spaceId, UUID userId);

    String getCurrentSpaceId(UUID userId);
    void setCurrentSpaceId(UUID userId, UUID spaceId);
}
