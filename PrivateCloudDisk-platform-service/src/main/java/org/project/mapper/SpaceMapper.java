package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.model.entity.SpaceEntity;
import java.util.List;
import java.util.UUID;

@Mapper
public interface SpaceMapper {
    SpaceEntity findById(@Param("spaceId") UUID spaceId);
    List<SpaceEntity> findByOwnerId(@Param("ownerId") UUID ownerId);
    List<SpaceEntity> findByMemberUserId(@Param("userId") UUID userId);
    List<SpaceEntity> findPublicSpaces(@Param("keyword") String keyword);
    SpaceEntity findPublicByName(@Param("spaceName") String spaceName);
    int insert(SpaceEntity space);
    int update(SpaceEntity space);
    int softDelete(@Param("spaceId") UUID spaceId);
    int updateQuota(@Param("spaceId") UUID spaceId, @Param("quota") Long quota);
    int incrementUsed(@Param("spaceId") UUID spaceId, @Param("size") Long size);
    int decrementUsed(@Param("spaceId") UUID spaceId, @Param("size") Long size);
    int incrementFileCount(@Param("spaceId") UUID spaceId, @Param("count") Integer count);
    int decrementFileCount(@Param("spaceId") UUID spaceId, @Param("count") Integer count);
}