package org.project.im.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.im.platform.entity.ImFriendship;

import java.util.List;

@Mapper
public interface ImFriendshipMapper {
    int insert(ImFriendship friendship);
    int reactivate(@Param("userId") String userId, @Param("friendId") String friendId);
    ImFriendship selectByUsers(@Param("userId") String userId, @Param("friendId") String friendId);
    List<ImFriendship> selectActiveByUserId(@Param("userId") String userId);
    int updateRemark(@Param("userId") String userId, @Param("friendId") String friendId, @Param("remark") String remark);
    int updateStarred(@Param("userId") String userId, @Param("friendId") String friendId, @Param("starred") boolean starred);
    int releaseSymmetric(@Param("userId") String userId, @Param("friendId") String friendId);
}
