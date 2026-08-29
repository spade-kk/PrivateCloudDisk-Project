package org.project.im.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 群聊创建命令。
 *
 * <p>GROUP-CHAT-20260810 [4.2/4.6/4.22]：创建群聊从旧版 Query 参数扩展为 JSON
 * 命令，同时传递初始成员；服务端仍保留旧接口以兼容已发布客户端。头像字段保存文件服务
 * 已返回的可访问 URL 或文件访问 URL，不在 IM 服务内复制二进制文件。</p>
 */
@Data
public class GroupCreateCommand {
    @NotBlank(message = "群主 ID 不能为空")
    private String ownerId;

    @NotBlank(message = "群名称不能为空")
    @Size(max = 30, message = "群名称不能超过 30 个字符")
    private String groupName;

    /** 文件服务已授权的头像访问地址；为空时由前端用成员拼图回退渲染。 */
    @Size(max = 1024, message = "群头像地址过长")
    private String avatarFileId;

    @Size(max = 199, message = "初始成员最多 199 人")
    private List<String> memberIds;

    /** 0-自由加入，1-需审核，2-禁止加入。 */
    private Integer joinMode;
}
