package org.project.workflow.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.project.workflow.model.WorkflowMarketplaceModels.MarketplaceRow;
import org.project.workflow.model.WorkflowMarketplaceModels.ReviewRow;
import org.project.workflow.model.WorkflowMarketplaceModels.TemplateSourceRow;

import java.util.List;

/** 工作流模板市场持久层。 */
@Mapper
public interface WorkflowMarketplaceMapper {
    @Insert("""
            INSERT INTO pcd_workflow_marketplace_listing(
                workflow_id, review_status, tags_json, published_by
            )
            SELECT workflow_id, 'PENDING', JSON_ARRAY(), UUID_TO_BIN(#{userId})
              FROM pcd_workflow
             WHERE workflow_id=UUID_TO_BIN(#{workflowId})
               AND owner_user_id=UUID_TO_BIN(#{userId})
               AND status='PUBLISHED' AND deleted_at IS NULL
            ON DUPLICATE KEY UPDATE
                review_status='PENDING', published_by=VALUES(published_by), published_at=NULL
            """)
    int submit(@Param("workflowId") String workflowId, @Param("userId") String userId);

    @Update("""
            UPDATE pcd_workflow_marketplace_listing l
            JOIN pcd_workflow w ON w.workflow_id=l.workflow_id
               SET l.review_status=#{status},
                   l.published_at=IF(#{status}='APPROVED', CURRENT_TIMESTAMP(3), NULL)
             WHERE l.workflow_id=UUID_TO_BIN(#{workflowId})
               AND w.status='PUBLISHED' AND w.deleted_at IS NULL
            """)
    int review(@Param("workflowId") String workflowId, @Param("status") String status);

    @Select("""
            <script>
            SELECT BIN_TO_UUID(w.workflow_id) workflow_id,
                   w.name, w.slug, w.description,
                   COALESCE(l.category_code, 'other') category_code,
                   CAST(l.tags_json AS CHAR) tags_json,
                   l.install_count, l.rating_average, l.rating_count, l.published_at
              FROM pcd_workflow_marketplace_listing l
              JOIN pcd_workflow w ON w.workflow_id=l.workflow_id
             WHERE l.review_status='APPROVED'
               AND w.status='PUBLISHED' AND w.deleted_at IS NULL
               <if test="category != null and category != ''">AND l.category_code=#{category}</if>
               <if test="query != null and query != ''">
                 AND (w.name LIKE CONCAT('%', #{query}, '%')
                      OR w.description LIKE CONCAT('%', #{query}, '%')
                      OR w.slug LIKE CONCAT('%', #{query}, '%'))
               </if>
             ORDER BY l.published_at DESC
             LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<MarketplaceRow> list(@Param("category") String category,
                              @Param("query") String query,
                              @Param("limit") int limit,
                              @Param("offset") int offset);

    @Select("""
            SELECT BIN_TO_UUID(w.workflow_id) workflow_id,
                   w.name, w.description, v.dsl_text, CAST(v.graph_json AS CHAR) graph_json
              FROM pcd_workflow_marketplace_listing l
              JOIN pcd_workflow w ON w.workflow_id=l.workflow_id
              JOIN pcd_workflow_version v ON v.version_id=w.latest_version_id
             WHERE l.workflow_id=UUID_TO_BIN(#{workflowId})
               AND l.review_status='APPROVED'
               AND w.status='PUBLISHED' AND w.deleted_at IS NULL
               AND v.immutable=1
            """)
    TemplateSourceRow findTemplate(@Param("workflowId") String workflowId);

    @Update("""
            UPDATE pcd_workflow_marketplace_listing
               SET install_count=install_count+1
             WHERE workflow_id=UUID_TO_BIN(#{workflowId}) AND review_status='APPROVED'
            """)
    int incrementInstall(@Param("workflowId") String workflowId);

    @Insert("""
            INSERT INTO pcd_workflow_review(workflow_id, user_id, rating, comment_text)
            VALUES(UUID_TO_BIN(#{workflowId}), UUID_TO_BIN(#{userId}), #{rating}, #{comment})
            ON DUPLICATE KEY UPDATE rating=VALUES(rating), comment_text=VALUES(comment_text),
                status='VISIBLE', updated_at=CURRENT_TIMESTAMP(3)
            """)
    int upsertReview(@Param("workflowId") String workflowId,
                     @Param("userId") String userId,
                     @Param("rating") int rating,
                     @Param("comment") String comment);

    @Update("""
            UPDATE pcd_workflow_marketplace_listing l
               SET rating_average=(
                     SELECT COALESCE(AVG(r.rating), 0) FROM pcd_workflow_review r
                      WHERE r.workflow_id=l.workflow_id AND r.status='VISIBLE'
                   ),
                   rating_count=(
                     SELECT COUNT(*) FROM pcd_workflow_review r
                      WHERE r.workflow_id=l.workflow_id AND r.status='VISIBLE'
                   )
             WHERE l.workflow_id=UUID_TO_BIN(#{workflowId})
            """)
    int refreshRating(@Param("workflowId") String workflowId);

    @Select("""
            SELECT BIN_TO_UUID(user_id) user_id, rating, comment_text,
                   created_at, updated_at
              FROM pcd_workflow_review
             WHERE workflow_id=UUID_TO_BIN(#{workflowId}) AND status='VISIBLE'
             ORDER BY updated_at DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<ReviewRow> reviews(@Param("workflowId") String workflowId,
                            @Param("limit") int limit,
                            @Param("offset") int offset);
}
