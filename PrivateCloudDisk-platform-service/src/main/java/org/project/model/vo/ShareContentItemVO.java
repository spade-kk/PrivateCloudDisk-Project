package org.project.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分享内容项 VO（用于展示分享文件夹内的文件和子文件夹）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareContentItemVO {

    /** 类型：file / folder */
    private String item_type;

    /** 文件ID（item_type=file 时有值） */
    private String file_id;

    /** 文件夹节点ID（item_type=folder 时有值） */
    private String node_id;

    /** 名称 */
    private String name;

    /** 文件大小（字节） */
    private Long size;

    /** 文件类型（MIME 或扩展名） */
    private String file_type;
}