package org.project.im.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 群组 DTO
 * <p>
 * 群组是多人聊天的载体，包含群基本信息、成员管理、
 * 权限控制等功能。支持群公告、群头像、成员上限等配置。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 群组唯一 ID */
    private String groupId;

    /** 群组名称 */
    private String groupName;

    /** 群组头像 URL */
    private String avatar;

    /** 群主用户 ID */
    private String ownerId;

    /** 群主昵称 */
    private String ownerName;

    /** 当前查看者在群内的角色；列表/详情接口按请求用户填充。 */
    private Integer currentUserRole;

    /** 当前查看者对应的群会话 ID（固定为 group*{groupId}）。 */
    private String conversationId;

    /** 群公告 */
    private String announcement;

    /** 群简介 */
    private String description;

    /** 当前成员数 */
    private Integer memberCount;

    /** 最大成员数（默认 500） */
    private Integer maxMembers;

    /** 加群方式：0-自由加入 1-需要审核 2-禁止加入 */
    private Integer joinMode;

    /** 是否全员禁言 */
    private Boolean isAllMuted;

    /** 群组状态：0-正常 1-已解散 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
