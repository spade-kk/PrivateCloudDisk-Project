package org.project.im.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.im.platform.entity.ImGroupMember;

import java.util.List;

/**
 * 群组成员 Mapper
 * <p>
 * IM 群组成员表（im_group_member）的数据访问层。
 * 管理群组成员关系、角色、禁言等。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Mapper
public interface ImGroupMemberMapper {

    /**
     * 插入群成员
     */
    int insert(ImGroupMember member);

    /**
     * 删除群成员
     */
    int deleteByGroupIdAndUserId(@Param("groupId") String groupId,
                                 @Param("userId") String userId);

    /**
     * 查询群成员
     */
    ImGroupMember selectByGroupIdAndUserId(@Param("groupId") String groupId,
                                           @Param("userId") String userId);

    /**
     * 查询群所有成员
     */
    List<ImGroupMember> selectByGroupId(@Param("groupId") String groupId);

    /**
     * 查询群成员数量
     */
    int countByGroupId(@Param("groupId") String groupId);

    /**
     * 更新成员角色
     */
    int updateRole(@Param("groupId") String groupId,
                   @Param("userId") String userId,
                   @Param("role") int role);

    /**
     * 更新禁言状态
     */
    int updateMute(@Param("groupId") String groupId,
                   @Param("userId") String userId,
                   @Param("muteUntil") java.time.LocalDateTime muteUntil);

    /**
     * 更新最后阅读序号
     */
    int updateLastReadSeq(@Param("groupId") String groupId,
                          @Param("userId") String userId,
                          @Param("lastReadSeq") Long lastReadSeq);

    /**
     * 查询用户是否为群成员
     */
    int existsByGroupIdAndUserId(@Param("groupId") String groupId,
                                 @Param("userId") String userId);
}