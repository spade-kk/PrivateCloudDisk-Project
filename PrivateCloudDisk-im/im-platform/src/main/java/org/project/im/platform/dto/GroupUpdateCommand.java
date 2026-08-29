package org.project.im.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 群资料更新命令；未提供的可编辑字段保持原值。 */
@Data
public class GroupUpdateCommand {
    @NotBlank(message = "操作者 ID 不能为空")
    private String operatorId;

    @Size(min = 1, max = 30, message = "群名称长度应为 1 至 30 个字符")
    private String name;

    @Size(max = 1024, message = "群头像地址过长")
    private String avatarFileId;

    @Size(max = 500, message = "群公告不能超过 500 个字符")
    private String announcement;

    @Size(max = 500, message = "群简介不能超过 500 个字符")
    private String description;

    private Integer joinMode;
}
