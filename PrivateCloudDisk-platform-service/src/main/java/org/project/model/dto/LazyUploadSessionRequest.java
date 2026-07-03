package org.project.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 懒上传会话创建请求 — 混合模型。
 * <p>
 * 支持两种模式：
 * <ul>
 *   <li><b>node_id + 相对路径：</b>设置 parent_node_id 和 relative_path，自动创建不存在的目录</li>
 *   <li><b>纯面包屑路径：</b>设置 breadcrumb_path，从根节点开始逐级创建不存在的目录</li>
 * </ul>
 * <p>
 * 两种模式互斥，优先使用 relative_path（如果设置）。
 * 如果都不设置，则等同于普通单文件上传（node_id 必须已存在）。
 */
@Data
public class LazyUploadSessionRequest {

    /** 总分块数，至少为 1 */
    @NotNull(message = "总分块数不能为空")
    @Min(value = 1, message = "总分块数必须大于等于1")
    private Integer total_chunks;

    /** 文件大小（字节），非负 */
    @NotNull(message = "文件大小不能为空")
    @Min(value = 0, message = "文件大小不能为负数")
    private Long file_size;

    /** 文件校验和 */
    @NotBlank(message = "文件校验和不能为空")
    @Size(max = 128, message = "文件校验和长度不正确")
    private String file_checksum;

    /** 分块最大大小（字节），至少为 1 */
    @NotNull(message = "分块最大大小不能为空")
    @Min(value = 1, message = "分块最大大小必须大于等于1")
    private Integer chunks_max_size;

    /** 文件类型，最长 120 字符 */
    @NotBlank(message = "文件类型不能为空")
    @Size(max = 120, message = "文件类型不能超过120个字符")
    private String file_type;

    /** 文件名，最长 255 字符 */
    @NotBlank(message = "文件名不能为空")
    @Size(max = 255, message = "文件名不能超过255个字符")
    private String file_name;

    /** 父节点 ID（node_id + 相对路径模式必填），UUID 格式 */
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
            message = "父节点ID必须是有效的UUID格式")
    private String parent_node_id;

    /** 相对路径，最长 1024 字符 */
    @Size(max = 1024, message = "路径长度不能超过1024个字符")
    private String relative_path;

    /** 纯面包屑路径，最长 1024 字符 */
    @Size(max = 1024, message = "路径长度不能超过1024个字符")
    private String breadcrumb_path;
}