package org.project.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分享内容项 VO（用于展示分享文件夹内的文件和子文件夹，v2）
 *
 * <p>安全设计：share_resource_id 是通过 AES 加密生成的虚拟标识符，
 * 用于替代原始的 file_id/node_id 进行子目录导航和文件下载。
 * 绝不暴露内部 file_id/node_id 给客户端。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareContentItemVO {

    /** 类型：file / folder */
    private String item_type;

    /** 分享资源ID（虚拟标识符，用于导航子节点、下载等操作，不暴露内部 file_id/node_id） */
    private String share_resource_id;

    /** 名称 */
    private String name;

    /** 文件大小（字节） */
    private Long size;

    /** 文件类型（MIME 或扩展名） */
    private String file_type;
}