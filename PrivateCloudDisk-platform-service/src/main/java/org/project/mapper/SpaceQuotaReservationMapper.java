package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.UUID;

/**
 * 空间上传配额预占持久层。
 *
 * <p>空间管理能力全量集成（需求四-4、五-10）。
 * 自定义空间使用空间表的 quota/used/reserved 原子更新；个人空间继续复用原用户配额表，
 * 从而保持旧客户端、旧账单和原配额业务完全兼容。</p>
 */
@Mapper
public interface SpaceQuotaReservationMapper {

    @Select("""
            SELECT GREATEST(space_quota - space_used - space_reserved, 0)
            FROM pcd_space_table
            WHERE space_id = #{spaceId,typeHandler=org.project.util.UUIDBinaryTypeHandler}
              AND space_status = 'active'
            """)
    Long findAvailable(@Param("spaceId") UUID spaceId);

    @Update("""
            UPDATE pcd_space_table
            SET space_reserved = space_reserved + #{size},
                space_updated_at = CURRENT_TIMESTAMP
            WHERE space_id = #{spaceId,typeHandler=org.project.util.UUIDBinaryTypeHandler}
              AND space_status = 'active'
              AND space_quota - space_used - space_reserved >= #{size}
            """)
    int reserve(@Param("spaceId") UUID spaceId, @Param("size") long size);

    @Update("""
            UPDATE pcd_space_table
            SET space_reserved = GREATEST(space_reserved - #{size}, 0),
                space_used = space_used + #{size},
                space_file_count = space_file_count + 1,
                space_updated_at = CURRENT_TIMESTAMP
            WHERE space_id = #{spaceId,typeHandler=org.project.util.UUIDBinaryTypeHandler}
              AND space_status = 'active'
              AND space_reserved >= #{size}
            """)
    int commit(@Param("spaceId") UUID spaceId, @Param("size") long size);

    @Update("""
            UPDATE pcd_space_table
            SET space_reserved = GREATEST(space_reserved - #{size}, 0),
                space_updated_at = CURRENT_TIMESTAMP
            WHERE space_id = #{spaceId,typeHandler=org.project.util.UUIDBinaryTypeHandler}
              AND space_status = 'active'
              AND space_reserved >= #{size}
            """)
    int rollback(@Param("spaceId") UUID spaceId, @Param("size") long size);

    @Update("""
            UPDATE pcd_space_table
            SET space_used = GREATEST(space_used - #{size}, 0),
                space_file_count = GREATEST(space_file_count - 1, 0),
                space_updated_at = CURRENT_TIMESTAMP
            WHERE space_id = #{spaceId,typeHandler=org.project.util.UUIDBinaryTypeHandler}
              AND space_status = 'active'
            """)
    int decreaseUsed(@Param("spaceId") UUID spaceId, @Param("size") long size);

    @Update("""
            UPDATE pcd_space_table
            SET space_used = GREATEST(space_used + #{changeBytes}, 0),
                space_file_count = GREATEST(space_file_count + #{changeFileCount}, 0),
                space_updated_at = CURRENT_TIMESTAMP
            WHERE space_id = #{spaceId,typeHandler=org.project.util.UUIDBinaryTypeHandler}
              AND space_status = 'active'
            """)
    int adjustUsage(
            @Param("spaceId") UUID spaceId,
            @Param("changeBytes") long changeBytes,
            @Param("changeFileCount") int changeFileCount);
}
