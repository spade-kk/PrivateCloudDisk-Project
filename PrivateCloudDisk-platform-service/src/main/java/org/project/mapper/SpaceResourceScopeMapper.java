package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.UUID;

/**
 * 空间资源归属查询 Mapper。
 *
 * <p>需求：空间管理能力全量集成（三-1）。
 * 查询同时兼容历史数据：个人空间的旧记录 space_id 为 NULL 时仍视为个人空间资源；
 * 非个人空间必须精确匹配 space_id，避免跨空间 IDOR。</p>
 */
@Mapper
public interface SpaceResourceScopeMapper {

    @Select("""
            SELECT COUNT(1)
            FROM pcd_file_info_table
            WHERE file_id = #{fileId,typeHandler=org.project.util.UUIDBinaryTypeHandler}
              AND (
                    file_space_id = #{spaceId,typeHandler=org.project.util.UUIDBinaryTypeHandler}
                    OR (#{personalSpace} = TRUE AND file_space_id IS NULL
                        AND file_author_id = #{userId,typeHandler=org.project.util.UUIDBinaryTypeHandler})
                  )
            """)
    int countFileInSpace(
            @Param("fileId") UUID fileId,
            @Param("spaceId") UUID spaceId,
            @Param("userId") UUID userId,
            @Param("personalSpace") boolean personalSpace);

    @Select("""
            SELECT COUNT(1)
            FROM pcd_directory_tree_table
            WHERE node_id = #{nodeId,typeHandler=org.project.util.UUIDBinaryTypeHandler}
              AND (
                    node_space_id = #{spaceId,typeHandler=org.project.util.UUIDBinaryTypeHandler}
                    OR (#{personalSpace} = TRUE AND node_space_id IS NULL
                        AND node_user_id = #{userId,typeHandler=org.project.util.UUIDBinaryTypeHandler})
                  )
            """)
    int countNodeInSpace(
            @Param("nodeId") UUID nodeId,
            @Param("spaceId") UUID spaceId,
            @Param("userId") UUID userId,
            @Param("personalSpace") boolean personalSpace);

    @Select("""
            SELECT COUNT(1)
            FROM pcd_uploads_session_table
            WHERE uploads_id = #{uploadsId,typeHandler=org.project.util.UUIDBinaryTypeHandler}
              AND (
                    uploads_space_id = #{spaceId,typeHandler=org.project.util.UUIDBinaryTypeHandler}
                    OR (#{personalSpace} = TRUE AND uploads_space_id IS NULL
                        AND uploads_user_id = #{userId,typeHandler=org.project.util.UUIDBinaryTypeHandler})
                  )
            """)
    int countUploadInSpace(
            @Param("uploadsId") UUID uploadsId,
            @Param("spaceId") UUID spaceId,
            @Param("userId") UUID userId,
            @Param("personalSpace") boolean personalSpace);
}
