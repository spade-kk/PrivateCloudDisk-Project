package org.project.model.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 最近访问记录实体
 *
 * <p>记录用户最近打开、下载、上传的文件/文件夹。
 * 用于展示「最近访问」列表，帮助用户快速定位近期操作的文件。
 */
@Data
public class RecentAccessEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 记录ID */
    private Long ra_id;

    /** 用户ID */
    private UUID ra_user_id;

    /** 空间管理能力全量集成（需求五-9）：访问发生时的空间快照。 */
    private UUID ra_space_id;

    /** 访问来源：space=普通空间，share=分享资源。 */
    private String ra_access_source;

    /** 分享来源的虚拟资源 ID；仅用于分享最近访问展示与审计。 */
    private String ra_share_resource_id;

    /** 目标类型：file / folder */
    private String ra_target_type;

    /** 文件ID */
    private UUID ra_file_id;

    /** 文件夹节点ID */
    private UUID ra_node_id;

    /** 访问类型：upload / download / open */
    private String ra_access_type;

    /** 文件/文件夹名称（冗余存储，避免JOIN） */
    private String ra_file_name;

    /** 文件大小（冗余） */
    private Long ra_file_size;

    /** 文件类型（冗余） */
    private String ra_file_type;

    /** 访问时间 */
    private LocalDateTime ra_accessed_at;
}
