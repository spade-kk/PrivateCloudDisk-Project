package org.project.im.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.im.platform.entity.ImConversation;

import java.util.List;

/** 会话元数据 Mapper；最后消息和未读数由 Redis 摘要缓存维护。 */
@Mapper
public interface ImConversationMapper {
    int insert(ImConversation conversation);
    ImConversation selectBySessionIdAndUserId(@Param("sessionId") String sessionId, @Param("userId") String userId);
    ImConversation selectByUserIdAndPeerId(@Param("userId") String userId, @Param("peerId") String peerId,
                                            @Param("sessionType") Integer sessionType);
    List<ImConversation> selectByUserId(@Param("userId") String userId);
    int updatePinned(@Param("sessionId") String sessionId, @Param("userId") String userId, @Param("isPinned") Boolean isPinned);
    int updateMuted(@Param("sessionId") String sessionId, @Param("userId") String userId, @Param("isMuted") Boolean isMuted);
}
