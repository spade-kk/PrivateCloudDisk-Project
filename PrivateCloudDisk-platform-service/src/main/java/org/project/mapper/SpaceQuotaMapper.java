package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.project.model.vo.SpaceQuotaVO;

import java.util.List;
import java.util.UUID;

/**
 * 空间配额只读查询 Mapper。
 *
 * <p>需求：空间管理能力全量集成（五-10）。
 * 通过成员表限制结果范围，并对文件表进行按空间聚合；个人空间同时兼容历史 NULL 记录。</p>
 */
@Mapper
public interface SpaceQuotaMapper {

    @Select("""
            SELECT
                s.space_id,
                s.space_name,
                s.space_type,
                s.space_quota AS total_quota,
                COALESCE(SUM(CASE
                    WHEN f.file_status = 'active' THEN f.file_size
                    ELSE 0 END), 0) AS used_quota,
                COALESCE(s.space_reserved, 0) AS reserved_quota,
                COALESCE(SUM(CASE
                    WHEN f.file_status = 'active' THEN 1
                    ELSE 0 END), 0) AS file_count,
                CASE
                    WHEN s.space_quota = 0 THEN 0
                    ELSE ROUND(
                        COALESCE(SUM(CASE WHEN f.file_status = 'active' THEN f.file_size ELSE 0 END), 0)
                        * 100.0 / s.space_quota, 2)
                END AS usage_percent
            FROM pcd_space_table s
            INNER JOIN pcd_space_member_table m
                ON m.space_id = s.space_id
               AND m.user_id = #{userId,typeHandler=org.project.util.UUIDBinaryTypeHandler}
            LEFT JOIN pcd_file_info_table f
                ON f.file_space_id = s.space_id
                OR (s.space_type = 'personal'
                    AND s.space_owner_id = f.file_author_id
                    AND f.file_space_id IS NULL)
            WHERE s.space_status = 'active'
            GROUP BY s.space_id, s.space_name, s.space_type, s.space_quota, s.space_reserved
            ORDER BY CASE WHEN s.space_type = 'personal' THEN 0 ELSE 1 END,
                     s.space_created_at ASC
            """)
    List<SpaceQuotaVO> findAllVisibleSpaceQuotas(@Param("userId") UUID userId);
}
