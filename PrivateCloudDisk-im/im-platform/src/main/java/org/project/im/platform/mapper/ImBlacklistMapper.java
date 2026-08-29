package org.project.im.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.im.platform.entity.ImBlacklist;

import java.util.List;

@Mapper
public interface ImBlacklistMapper {
    int insertIgnore(ImBlacklist blacklist);
    int delete(@Param("userId") String userId, @Param("blockedUserId") String blockedUserId);
    boolean exists(@Param("userId") String userId, @Param("blockedUserId") String blockedUserId);
    List<ImBlacklist> selectByUserId(@Param("userId") String userId);
}
