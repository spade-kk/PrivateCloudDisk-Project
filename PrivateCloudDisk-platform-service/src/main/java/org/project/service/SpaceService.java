package org.project.service;

import org.project.model.entity.*;
import org.project.model.dto.UpdatePermissionRequest;
import java.util.List;
import java.util.UUID;

public interface SpaceService {
    SpaceEntity createSpace(UUID userId, String spaceName, String spaceType, String spaceDescription, String spaceVisibility);
    SpaceEntity getSpaceById(UUID spaceId, UUID userId);
    List<SpaceEntity> getUserSpaces(UUID userId);
    List<SpaceEntity> getUserSpacesByType(UUID userId, String spaceType);
    List<SpaceEntity> discoverPublicSpaces(String keyword);
    SpaceEntity getPublicSpaceByName(String spaceName);
    SpaceEntity updateSpace(UUID spaceId, UUID userId, String spaceName, String spaceDescription, String spaceVisibility, Long spaceQuota);
    void deleteSpace(UUID spaceId, UUID userId);

    void addMember(UUID spaceId, UUID userId, UUID targetUserId, String role);
    List<SpaceMemberEntity> getMembers(UUID spaceId, UUID userId);
    void updateMemberRole(UUID spaceId, UUID userId, UUID targetUserId, String role);
    void removeMember(UUID spaceId, UUID userId, UUID targetUserId);

    void updatePermission(UUID spaceId, UUID userId, UUID targetUserId, UpdatePermissionRequest request);
    SpacePermissionEntity getPermission(UUID spaceId, UUID targetUserId);

    void requestJoin(UUID spaceId, UUID userId, String message);
    List<SpaceJoinRequestEntity> getJoinRequests(UUID spaceId, UUID userId, String status);
    void reviewJoinRequest(UUID spaceId, UUID reqUserId, UUID reviewerId, String action);

    void updateVisibilityList(UUID spaceId, UUID userId, List<UUID> targetUserIds, String listType);
    List<SpaceVisibilityEntity> getVisibilityList(UUID spaceId, UUID userId);

    String getCurrentSpaceId(UUID userId);
    void setCurrentSpaceId(UUID userId, UUID spaceId);
}