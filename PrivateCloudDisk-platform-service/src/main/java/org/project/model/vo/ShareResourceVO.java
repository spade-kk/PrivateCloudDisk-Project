package org.project.model.vo;

import lombok.Data;

/**
 * 分享资源 VO（v2 — 多资源分享模型）
 *
 * <p>share_resource_id 是分享资源在分享表中的唯一标识，用于后续的
 * 文件夹浏览、文件下载等操作，绝不暴露内部 file_id/node_id。
 */
@Data
public class ShareResourceVO {
    /** 分享资源ID（用于导航子节点、下载等操作，不暴露内部 file_id/node_id） */
    private String share_resource_id;

    /** 资源类型：file / folder */
    private String resource_type;

    /** 资源名称 */
    private String resource_name;

    /** 资源大小（字节，仅文件时有值） */
    private Long resource_size;

    /** 文件类型（MIME 或扩展名，仅文件时有值） */
    private String file_type;
}