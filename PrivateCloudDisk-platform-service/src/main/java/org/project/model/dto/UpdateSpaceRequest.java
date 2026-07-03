package org.project.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新空间请求 DTO。
 * <p>
 * 所有字段可选，只更新传入的非空字段（部分更新 PATCH 语义）。
 * 至少需要提供一个字段。
 */
@Data
public class UpdateSpaceRequest {

    /** 空间名称，1-64 字符 */
    @Size(min = 1, max = 64, message = "空间名称长度必须为1-64个字符")
    @Pattern(regexp = "^[^\\\\/:*?\"<>|]+$", message = "空间名称不能包含非法字符")
    private String spaceName;

    /** 空间描述，最长 500 字符 */
    @Size(max = 500, message = "空间描述长度不能超过500个字符")
    private String spaceDescription;

    /** 可见性：private / public / whitelist / blacklist */
    @Pattern(regexp = "^(private|public|whitelist|blacklist)$", message = "可见性类型无效")
    private String spaceVisibility;

    /** 空间配额（字节），非负 */
    @Min(value = 0, message = "空间配额不能为负数")
    private Long spaceQuota;
}