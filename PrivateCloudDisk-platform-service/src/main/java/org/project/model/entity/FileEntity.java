package org.project.model.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 文件数据类
 */
@Data
public class FileEntity implements Serializable {
    public enum FileStatus {
        active,
        deleted,
        trashed,
        merging,
        merged,
        merge_failed,
        scanning,
        scan_failed,
        reject
    }

    private UUID id;
    private String name;
    private String type;
    private Long size;
    private UUID user_id;
    private LocalDateTime uploaded_time;
    private String checksum;
    private UUID node_id;
    private Integer total_chunks;
    private String storage_path;
    private FileStatus status;
    /**
     * 需求：空间管理能力全量集成（六）。
     * 新增资源定位维度；历史个人网盘记录允许为 NULL，由统一空间查询规则兼容。
     */
    private UUID space_id;
}
