package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.model.entity.SpaceMemberEntity;
import java.util.List;
import java.util.UUID;

@Mapper
public interface SpaceMemberMapper {
    SpaceMemberEntity findBySpaceAndUser(@Param("spaceId") UUID spaceId, @Param("userId") UUID userId);
    List<SpaceMemberEntity> findBySpaceId(@Param("spaceId") UUID spaceId);
    List<SpaceMemberEntity> findByUserId(@Param("userId") UUID userId);
    int insert(SpaceMemberEntity member);
    int updateRole(@Param("spaceId") UUID spaceId, @Param("userId") UUID userId, @Param("role") String role);
    int delete(@Param("spaceId") UUID spaceId, @Param("userId") UUID userId);
    int countBySpaceId(@Param("spaceId") UUID spaceId);
}