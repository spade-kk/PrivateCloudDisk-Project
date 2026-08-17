package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.model.entity.SpaceInvitationEntity;
import java.util.UUID;

@Mapper
public interface SpaceInvitationMapper {
    int insert(SpaceInvitationEntity invitation);
    SpaceInvitationEntity findActiveByHash(@Param("tokenHash") String tokenHash);
    int consume(@Param("invitationId") Long invitationId);
    int revoke(@Param("invitationId") Long invitationId, @Param("spaceId") UUID spaceId);
}
