package org.project.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建分享链接请求（v2 — 多资源分享模型）
 *
 * <p>变更：v1 使用单个 target_type + file_id/node_id 指定分享目标，
 * v2 改为 resources 列表，支持同时分享多个文件和文件夹。
 */
@Data
public class ShareCreateRequest {

    /** 分享资源列表（至少包含一个资源） */
    @NotEmpty(message = "分享资源列表不能为空")
    @Valid
    private List<ShareResourceItem> resources;

    /** 分享名称 */
    @NotBlank(message = "分享名称不能为空")
    @Size(max = 200, message = "分享名称最长200个字符")
    private String share_name;

    /** 分享说明（可选，支持富文本，展示端使用白名单净化） */
    @Size(max = 10000, message = "分享说明最长10000个字符")
    private String share_description;

    /** 提取码（明文，不传表示无密码）
     * <p>格式约束：4-20 位字母数字组合，禁止特殊字符，防止注入和编码问题 */
    @Size(min = 4, max = 20, message = "提取码长度必须为4-20位")
    @Pattern(regexp = "^[A-Za-z0-9]+$", message = "提取码只能包含字母和数字，不能包含特殊字符")
    private String password;

    /** 是否允许通过分享授权获取文件实际内容，默认允许以兼容旧分享。 */
    private Boolean allow_download = true;

    /** 有效期天数（0 表示永久有效） */
    @NotNull(message = "有效期不能为空")
    private Integer expires_in_days;
}
