package org.project.plugin.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.project.plugin.model.PluginTestTaskRow;

/** [PLUGIN-TEST-001] 测试任务状态持久层；task_id 唯一保证重复请求不创建第二个任务。 */
@Mapper
public interface PluginTestTaskMapper {
    @Insert("""
            INSERT IGNORE INTO pcd_plugin_execution_task(
                task_id, plugin_id, version_id, user_id, space_id,
                execution_type, test_entrypoint, status, expires_at
            ) VALUES (
                UUID_TO_BIN(#{taskId}), UUID_TO_BIN(#{pluginId}), UUID_TO_BIN(#{versionId}),
                UUID_TO_BIN(#{userId}), UUID_TO_BIN(NULLIF(#{spaceId}, '')),
                'TEST', #{testEntrypoint}, 'PENDING', DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 1 DAY)
            )
            """)
    int insertPending(@Param("taskId") String taskId,
                      @Param("pluginId") String pluginId,
                      @Param("versionId") String versionId,
                      @Param("userId") String userId,
                      @Param("spaceId") String spaceId,
                      @Param("testEntrypoint") String testEntrypoint);

    @Select("""
            SELECT BIN_TO_UUID(task_id) taskId, BIN_TO_UUID(plugin_id) pluginId,
                   BIN_TO_UUID(version_id) versionId, BIN_TO_UUID(user_id) userId,
                   BIN_TO_UUID(space_id) spaceId, status, CAST(result_json AS CHAR) resultJson,
                   error_code errorCode, error_summary errorSummary,
                   started_at startedAt, ended_at endedAt, expires_at expiresAt
              FROM pcd_plugin_execution_task
             WHERE task_id=UUID_TO_BIN(#{taskId}) AND user_id=UUID_TO_BIN(#{userId})
            """)
    PluginTestTaskRow findOwned(@Param("taskId") String taskId, @Param("userId") String userId);

    @Update("""
            UPDATE pcd_plugin_execution_task
               SET status=#{status}, result_json=CAST(#{resultJson} AS JSON),
                   error_code=NULLIF(#{errorCode}, ''), error_summary=NULLIF(#{errorSummary}, ''),
                   started_at=COALESCE(#{startedAt}, started_at), ended_at=#{endedAt},
                   row_version=row_version+1
             WHERE task_id=UUID_TO_BIN(#{taskId}) AND user_id=UUID_TO_BIN(#{userId})
            """)
    int updateStatus(@Param("taskId") String taskId,
                     @Param("userId") String userId,
                     @Param("status") String status,
                     @Param("resultJson") String resultJson,
                     @Param("errorCode") String errorCode,
                     @Param("errorSummary") String errorSummary,
                     @Param("startedAt") String startedAt,
                     @Param("endedAt") String endedAt);
}
