package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.model.entity.RecentAccessEntity;

import java.util.List;
import java.util.UUID;

/**
 * 最近访问记录 Mapper
 */
@Mapper
public interface RecentAccessMapper {

    /** 插入访问记录 */
    int insert(RecentAccessEntity entity);

    /**
     * 更新访问时间（去重：同一用户+同一文件+同一类型，只更新时间）
     * 返回受影响行数，0 表示需要 INSERT
     */
    int updateAccessTime(RecentAccessEntity entity);

    /**
     * 获取用户最近访问列表（按类型筛选，分页）
     */
    List<RecentAccessEntity> findByUserIdAndType(@Param("user_id") UUID user_id,
                                                   @Param("access_type") String access_type,
                                                   @Param("offset") int offset,
                                                   @Param("limit") int limit);

    /**
     * 获取用户所有最近访问（分页）
     */
    List<RecentAccessEntity> findByUserId(@Param("user_id") UUID user_id,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit);

    /**
     * 清理超过限制的旧记录（每个用户每种类型保留最近 N 条）
     */
    int deleteOldRecords(@Param("user_id") UUID user_id,
                         @Param("access_type") String access_type,
                         @Param("keep_count") int keepCount);

    /** 统计用户某种访问类型记录数 */
    int countByUserIdAndType(@Param("user_id") UUID user_id,
                             @Param("access_type") String access_type);
}