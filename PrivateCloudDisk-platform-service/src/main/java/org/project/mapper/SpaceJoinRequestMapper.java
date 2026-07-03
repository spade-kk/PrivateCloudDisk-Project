package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.model.entity.SpaceJoinRequestEntity;
import java.util.List;
import java.util.UUID;

@Mapper
public interface SpaceJoinRequestMapper {
    List<SpaceJoinRequestEntity> findBySpaceId(@Param("spaceId") UUID spaceId);
    List<SpaceJoinRequestEntity> findBySpaceIdAndStatus(@Param("spaceId") UUID spaceId, @Param("status") String status);
    SpaceJoinRequestEntity findBySpaceAndUser(@Param("spaceId") UUID spaceId, @Param("userId") UUID userId);
    int insert(SpaceJoinRequestEntity request);
    int updateStatus(@Param("requestId") Long requestId, @Param("status") String status,
                     @Param("reviewedBy") UUID reviewedBy);
}