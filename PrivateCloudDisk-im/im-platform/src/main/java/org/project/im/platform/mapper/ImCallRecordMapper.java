package org.project.im.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.im.platform.entity.ImCallRecord;

import java.util.List;

/**
 * 通话记录 Mapper
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
@Mapper
public interface ImCallRecordMapper {

    /** 插入通话记录 */
    int insert(ImCallRecord record);

    /** 更新通话记录 */
    int update(ImCallRecord record);

    /** 根据通话 ID 查询 */
    ImCallRecord selectByCallId(@Param("callId") String callId);

    /** 查询用户的通话记录 */
    List<ImCallRecord> selectByUserId(@Param("userId") String userId,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    /** 查询用户通话记录总数 */
    int countByUserId(@Param("userId") String userId);

    /** 查询指定时间范围内的通话记录 */
    List<ImCallRecord> selectByTimeRange(@Param("userId") String userId,
                                          @Param("startTime") String startTime,
                                          @Param("endTime") String endTime);

    /** 批量删除通话记录 */
    int deleteByIds(@Param("ids") List<Long> ids);
}