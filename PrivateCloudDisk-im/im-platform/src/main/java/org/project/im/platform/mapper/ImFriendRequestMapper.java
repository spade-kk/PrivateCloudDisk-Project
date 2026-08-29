package org.project.im.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.im.platform.entity.ImFriendRequest;

import java.util.List;

@Mapper
public interface ImFriendRequestMapper {
    int insert(ImFriendRequest request);
    ImFriendRequest selectByRequestId(@Param("requestId") String requestId);
    int acceptIfPending(@Param("requestId") String requestId, @Param("recipientId") String recipientId);
    int rejectIfPending(@Param("requestId") String requestId, @Param("recipientId") String recipientId);
    List<ImFriendRequest> selectPendingByRecipientId(@Param("recipientId") String recipientId);
    List<ImFriendRequest> selectByRecipientId(@Param("recipientId") String recipientId, @Param("offset") int offset, @Param("size") int size);
    long countByRecipientId(@Param("recipientId") String recipientId);
    List<ImFriendRequest> selectByRequesterId(@Param("requesterId") String requesterId, @Param("offset") int offset, @Param("size") int size);
    long countByRequesterId(@Param("requesterId") String requesterId);
    int cancelIfPending(@Param("requestId") String requestId, @Param("requesterId") String requesterId);
    ImFriendRequest selectPendingBetween(@Param("requesterId") String requesterId, @Param("recipientId") String recipientId);
}
