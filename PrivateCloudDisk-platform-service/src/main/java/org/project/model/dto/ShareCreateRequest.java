package org.project.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建分享链接请求
 */
@Data
public class ShareCreateRequest {
    /** 分享目标类型：file / folder */
    @NotBlank(message = "分享目标类型不能为空")
    @Pattern(regexp = "file|folder", message = "分享目标类型必须是 file 或 folder")
    private String target_type;

    /** 分享的文件ID（target_type=file 时必填） */
    private String file_id;

    /** 分享的文件夹节点ID（target_type=folder 时必填） */
    private String node_id;

    /** 分享名称 */
    @NotBlank(message = "分享名称不能为空")
    @Size(max = 200, message = "分享名称最长200个字符")
    private String share_name;

    /** 提取码（明文，不传表示无密码） */
    @Size(max = 20, message = "提取码最长20个字符")
    private String password;

    /** 有效期天数（0 表示永久有效） */
    @NotNull(message = "有效期不能为空")
    private Integer expires_in_days;
}