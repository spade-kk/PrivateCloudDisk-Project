package org.project.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 分享资源项（v2 新增）
 *
 * <p>表示创建分享时提交的单个资源项，包含资源类型和资源ID。
 */
@Data
public class ShareResourceItem {

    /** 资源类型：file 或 folder */
    @NotBlank(message = "资源类型不能为空")
    @Pattern(regexp = "file|folder", message = "资源类型必须是 file 或 folder")
    private String type;

    /** 资源ID（文件ID 或 文件夹节点ID） */
    @NotBlank(message = "资源ID不能为空")
    private String id;
}