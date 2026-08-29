package org.project.im.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ImFriendRequestBlockMapper {
    int insertIgnore(@Param("userId") String userId, @Param("blockedUserId") String blockedUserId);
    boolean exists(@Param("userId") String userId, @Param("blockedUserId") String blockedUserId);
}
