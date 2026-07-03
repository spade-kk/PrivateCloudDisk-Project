package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.model.entity.SpacePermissionEntity;
import java.util.UUID;

@Mapper
public interface SpacePermissionMapper {
    SpacePermissionEntity findBySpaceUserNode(@Param("spaceId") UUID spaceId, @Param("userId") UUID userId, @Param("targetNodeId") UUID targetNodeId);
    int upsert(SpacePermissionEntity permission);
    int delete(@Param("spaceId") UUID spaceId, @Param("userId") UUID userId);
}