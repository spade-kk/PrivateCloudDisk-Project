package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.model.entity.SpaceVisibilityEntity;
import java.util.List;
import java.util.UUID;

@Mapper
public interface SpaceVisibilityMapper {
    List<SpaceVisibilityEntity> findBySpaceIdAndType(@Param("spaceId") UUID spaceId, @Param("listType") String listType);
    int insert(SpaceVisibilityEntity entity);
    int deleteBySpaceIdAndType(@Param("spaceId") UUID spaceId, @Param("listType") String listType);
    int deleteBySpaceIdAndTypeAndUserIds(@Param("spaceId") UUID spaceId, @Param("listType") String listType,
                                         @Param("userIds") List<UUID> userIds);
}