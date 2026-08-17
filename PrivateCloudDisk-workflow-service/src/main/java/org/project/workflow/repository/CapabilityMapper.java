package org.project.workflow.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.project.workflow.model.WorkflowModels.CapabilityRow;

import java.util.List;

/** 能力中心注册表持久层。 */
@Mapper
public interface CapabilityMapper {
    @Select("""
            SELECT capability_key, source_type, source_id, source_version,
                   display_name, description,
                   CAST(input_schema_json AS CHAR) input_schema_json,
                   CAST(output_schema_json AS CHAR) output_schema_json,
                   CAST(required_permissions_json AS CHAR) required_permissions_json,
                   CAST(availability_policy_json AS CHAR) availability_policy_json,
                   status, revision
              FROM pcd_capability_registry
             WHERE capability_key=#{key}
            """)
    CapabilityRow findByKey(@Param("key") String key);

    @Select("""
            SELECT capability_key, source_type, source_id, source_version,
                   display_name, description,
                   CAST(input_schema_json AS CHAR) input_schema_json,
                   CAST(output_schema_json AS CHAR) output_schema_json,
                   CAST(required_permissions_json AS CHAR) required_permissions_json,
                   CAST(availability_policy_json AS CHAR) availability_policy_json,
                   status, revision
              FROM pcd_capability_registry
             WHERE status IN ('ACTIVE','DEPRECATED')
               AND (#{sourceType} IS NULL OR source_type=#{sourceType})
               AND (#{query} IS NULL OR display_name LIKE CONCAT('%', #{query}, '%')
                    OR capability_key LIKE CONCAT('%', #{query}, '%'))
             ORDER BY source_type, display_name, capability_key
             LIMIT #{limit} OFFSET #{offset}
            """)
    List<CapabilityRow> search(
            @Param("sourceType") String sourceType,
            @Param("query") String query,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Update("""
            INSERT INTO pcd_capability_registry(
                capability_key, source_type, source_id, source_version,
                display_name, description, input_schema_json, output_schema_json,
                required_permissions_json, availability_policy_json, status, revision
            ) VALUES (
                #{key}, #{sourceType}, #{sourceId}, #{sourceVersion},
                #{displayName}, #{description}, CAST(#{inputSchemaJson} AS JSON),
                CAST(#{outputSchemaJson} AS JSON), CAST(#{permissionsJson} AS JSON),
                CAST(#{availabilityJson} AS JSON), #{status}, #{revision}
            )
            ON DUPLICATE KEY UPDATE
                source_type=IF(VALUES(revision) >= revision, VALUES(source_type), source_type),
                source_id=IF(VALUES(revision) >= revision, VALUES(source_id), source_id),
                source_version=IF(VALUES(revision) >= revision, VALUES(source_version), source_version),
                display_name=IF(VALUES(revision) >= revision, VALUES(display_name), display_name),
                description=IF(VALUES(revision) >= revision, VALUES(description), description),
                input_schema_json=IF(VALUES(revision) >= revision, VALUES(input_schema_json), input_schema_json),
                output_schema_json=IF(VALUES(revision) >= revision, VALUES(output_schema_json), output_schema_json),
                required_permissions_json=IF(VALUES(revision) >= revision, VALUES(required_permissions_json), required_permissions_json),
                availability_policy_json=IF(VALUES(revision) >= revision, VALUES(availability_policy_json), availability_policy_json),
                status=IF(VALUES(revision) >= revision, VALUES(status), status),
                revision=GREATEST(revision, VALUES(revision))
            """)
    int upsert(
            @Param("key") String key,
            @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId,
            @Param("sourceVersion") String sourceVersion,
            @Param("displayName") String displayName,
            @Param("description") String description,
            @Param("inputSchemaJson") String inputSchemaJson,
            @Param("outputSchemaJson") String outputSchemaJson,
            @Param("permissionsJson") String permissionsJson,
            @Param("availabilityJson") String availabilityJson,
            @Param("status") String status,
            @Param("revision") long revision
    );
}
