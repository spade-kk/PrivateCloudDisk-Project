package org.project.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/**
 * 文件/文件夹打标签请求 DTO。
 */
@Data
public class FileTagRequest {

    /** 标签 ID 列表 */
    @NotNull(message = "标签ID不能为空")
    @NotEmpty(message = "标签ID列表不能为空")
    private List<Long> tag_ids;

    /** 目标类型：file / folder */
    @NotBlank(message = "目标类型不能为空")
    @Pattern(regexp = "^(file|folder)$", message = "target_type 必须是 file 或 folder")
    private String target_type;

    /** 目标 ID（文件 ID 或 文件夹节点 ID），UUID 格式 */
    @NotBlank(message = "目标ID不能为空")
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
            message = "目标ID必须是有效的UUID格式")
    private String target_id;
}