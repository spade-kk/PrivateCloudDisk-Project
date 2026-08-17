package org.project.workflow.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.project.workflow.model.WorkflowModels.WorkflowRow;
import org.project.workflow.model.WorkflowModels.WorkflowVersionRow;

import java.util.List;

/** 工作流与不可变版本持久层。BINARY UUID 在 SQL 边界统一转换。 */
@Mapper
public interface WorkflowMapper {
    @Insert("""
            INSERT INTO pcd_workflow(
                workflow_id, owner_user_id, owner_scope_type, owner_scope_id,
                name, slug, description
            ) VALUES (
                UUID_TO_BIN(#{workflowId}), UUID_TO_BIN(#{userId}), #{scopeType},
                UUID_TO_BIN(#{scopeId}), #{name}, #{slug}, #{description}
            )
            """)
    int insertWorkflow(
            @Param("workflowId") String workflowId,
            @Param("userId") String userId,
            @Param("scopeType") String scopeType,
            @Param("scopeId") String scopeId,
            @Param("name") String name,
            @Param("slug") String slug,
            @Param("description") String description
    );

    @Insert("""
            INSERT INTO pcd_workflow_version(
                version_id, workflow_id, version, dsl_text, dsl_sha256,
                graph_json, validation_report_json
            ) VALUES (
                UUID_TO_BIN(#{versionId}), UUID_TO_BIN(#{workflowId}), #{version},
                #{dsl}, UNHEX(#{sha256}), CAST(#{graphJson} AS JSON),
                CAST(#{reportJson} AS JSON)
            )
            """)
    int insertVersion(
            @Param("versionId") String versionId,
            @Param("workflowId") String workflowId,
            @Param("version") int version,
            @Param("dsl") String dsl,
            @Param("sha256") String sha256,
            @Param("graphJson") String graphJson,
            @Param("reportJson") String reportJson
    );

    @Update("""
            UPDATE pcd_workflow
               SET latest_version_id=UUID_TO_BIN(#{versionId}), row_version=row_version+1
             WHERE workflow_id=UUID_TO_BIN(#{workflowId})
            """)
    int attachLatestVersion(@Param("workflowId") String workflowId,
                            @Param("versionId") String versionId);

    @Select("""
            SELECT BIN_TO_UUID(workflow_id) workflow_id,
                   BIN_TO_UUID(owner_user_id) owner_user_id,
                   owner_scope_type,
                   BIN_TO_UUID(owner_scope_id) owner_scope_id,
                   name, slug, description, status,
                   BIN_TO_UUID(latest_version_id) latest_version_id,
                   row_version, created_at, updated_at
              FROM pcd_workflow
             WHERE workflow_id=UUID_TO_BIN(#{workflowId}) AND deleted_at IS NULL
            """)
    WorkflowRow findById(@Param("workflowId") String workflowId);

    @Select("""
            SELECT BIN_TO_UUID(workflow_id) workflow_id,
                   BIN_TO_UUID(owner_user_id) owner_user_id,
                   owner_scope_type,
                   BIN_TO_UUID(owner_scope_id) owner_scope_id,
                   name, slug, description, status,
                   BIN_TO_UUID(latest_version_id) latest_version_id,
                   row_version, created_at, updated_at
              FROM pcd_workflow
             WHERE deleted_at IS NULL
               AND (
                    (owner_scope_type='USER' AND owner_scope_id=UUID_TO_BIN(#{userId}))
                    OR
                    (#{spaceId} IS NOT NULL AND owner_scope_type='SPACE'
                     AND owner_scope_id=UUID_TO_BIN(#{spaceId}))
               )
             ORDER BY updated_at DESC, workflow_id
             LIMIT #{limit} OFFSET #{offset}
            """)
    List<WorkflowRow> listAccessible(
            @Param("userId") String userId,
            @Param("spaceId") String spaceId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select("""
            SELECT BIN_TO_UUID(version_id) version_id,
                   BIN_TO_UUID(workflow_id) workflow_id,
                   version, dsl_text, CAST(graph_json AS CHAR) graph_json,
                   schema_version, CAST(validation_report_json AS CHAR) validation_report_json,
                   immutable, published_at, created_at
              FROM pcd_workflow_version
             WHERE version_id=UUID_TO_BIN(#{versionId})
            """)
    WorkflowVersionRow findVersionById(@Param("versionId") String versionId);

    @Select("""
            SELECT BIN_TO_UUID(version_id) version_id,
                   BIN_TO_UUID(workflow_id) workflow_id,
                   version, dsl_text, CAST(graph_json AS CHAR) graph_json,
                   schema_version, CAST(validation_report_json AS CHAR) validation_report_json,
                   immutable, published_at, created_at
              FROM pcd_workflow_version
             WHERE workflow_id=UUID_TO_BIN(#{workflowId}) AND version=#{version}
            """)
    WorkflowVersionRow findVersion(@Param("workflowId") String workflowId,
                                   @Param("version") int version);

    @Select("""
            SELECT BIN_TO_UUID(v.version_id) version_id,
                   BIN_TO_UUID(v.workflow_id) workflow_id,
                   v.version, v.dsl_text, CAST(v.graph_json AS CHAR) graph_json,
                   v.schema_version, CAST(v.validation_report_json AS CHAR) validation_report_json,
                   v.immutable, v.published_at, v.created_at
              FROM pcd_workflow w
              JOIN pcd_workflow_version v ON v.version_id=w.latest_version_id
             WHERE w.workflow_id=UUID_TO_BIN(#{workflowId}) AND w.deleted_at IS NULL
            """)
    WorkflowVersionRow findLatestVersion(@Param("workflowId") String workflowId);

    @Select("""
            SELECT COALESCE(MAX(version), 0)
              FROM pcd_workflow_version
             WHERE workflow_id=UUID_TO_BIN(#{workflowId})
            """)
    int maxVersion(@Param("workflowId") String workflowId);

    @Update("""
            UPDATE pcd_workflow
               SET name=#{name}, description=#{description}, row_version=row_version+1
             WHERE workflow_id=UUID_TO_BIN(#{workflowId})
               AND owner_user_id=UUID_TO_BIN(#{userId})
               AND status IN ('DRAFT','PAUSED')
               AND row_version=#{expectedVersion}
               AND deleted_at IS NULL
            """)
    int updateMetadata(
            @Param("workflowId") String workflowId,
            @Param("userId") String userId,
            @Param("expectedVersion") long expectedVersion,
            @Param("name") String name,
            @Param("description") String description
    );

    @Update("""
            UPDATE pcd_workflow_version
               SET immutable=1, published_at=CURRENT_TIMESTAMP(3)
             WHERE version_id=UUID_TO_BIN(#{versionId})
               AND immutable=0
            """)
    int publishVersion(@Param("versionId") String versionId);

    @Update("""
            UPDATE pcd_workflow
               SET status='PUBLISHED', latest_version_id=UUID_TO_BIN(#{versionId}),
                   row_version=row_version+1
             WHERE workflow_id=UUID_TO_BIN(#{workflowId}) AND deleted_at IS NULL
            """)
    int markPublished(@Param("workflowId") String workflowId,
                      @Param("versionId") String versionId);

    @Update("""
            UPDATE pcd_workflow
               SET status='ARCHIVED', deleted_at=CURRENT_TIMESTAMP(3), row_version=row_version+1
             WHERE workflow_id=UUID_TO_BIN(#{workflowId})
               AND owner_user_id=UUID_TO_BIN(#{userId})
               AND deleted_at IS NULL
            """)
    int archive(@Param("workflowId") String workflowId, @Param("userId") String userId);
}
