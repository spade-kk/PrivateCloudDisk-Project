package org.project.model.vo;

import lombok.Data;

import java.util.UUID;

/**
 * 用户可访问空间配额视图。
 *
 * <p>需求：空间管理能力全量集成（五-10/六）。
 * usedQuota/fileCount 由有效文件实时聚合，避免仅依赖可漂移的计数字段。</p>
 */
@Data
public class SpaceQuotaVO {
    private UUID space_id;
    private String space_name;
    private String space_type;
    private Long total_quota;
    private Long used_quota;
    private Long reserved_quota;
    private Integer file_count;
    private Double usage_percent;
}
