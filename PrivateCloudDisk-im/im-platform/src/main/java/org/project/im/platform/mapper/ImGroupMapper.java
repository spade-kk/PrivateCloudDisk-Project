package org.project.im.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.im.platform.entity.ImGroup;

import java.util.List;

/**
 * 群组 Mapper
 * <p>
 * IM 群组表（im_group）的数据访问层，提供群组的 CRUD 操作。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Mapper
public interface ImGroupMapper {

    /**
     * 插入群组
     */
    int insert(ImGroup group);

    /**
     * 根据群组 ID 查询
     */
    ImGroup selectByGroupId(@Param("groupId") String groupId);

    /**
     * 查询用户加入的所有群组
     */
    List<ImGroup> selectByUserId(@Param("userId") String userId);

    /**
     * 更新群组信息
     */
    int updateGroupInfo(ImGroup group);

    /**
     * 更新群公告
     */
    int updateAnnouncement(@Param("groupId") String groupId,
                           @Param("announcement") String announcement);

    /**
     * 递增成员数
     */
    int incrementMemberCount(@Param("groupId") String groupId);

    /**
     * 递减成员数
     */
    int decrementMemberCount(@Param("groupId") String groupId);

    /**
     * 更新全员禁言状态
     */
    int updateAllMuted(@Param("groupId") String groupId,
                       @Param("isAllMuted") Boolean isAllMuted);

    /**
     * 解散群组
     */
    int dissolve(@Param("groupId") String groupId);
}