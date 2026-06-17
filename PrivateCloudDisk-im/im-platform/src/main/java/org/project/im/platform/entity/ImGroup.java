package org.project.im.platform.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 群组实体
 * <p>
 * 对应数据库表 im_group，存储群组基本信息。
 * 群组是多人聊天的容器，包含群主、管理员、成员的管理。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImGroup {

    /** 主键 ID */
    private Long id;

    /** 群组唯一 ID（雪花算法） */
    private String groupId;

    /** 群组名称 */
    private String groupName;

    /** 群组头像 URL */
    private String avatar;

    /** 群主用户 ID */
    private String ownerId;

    /** 群公告 */
    private String announcement;

    /** 群简介 */
    private String description;

    /** 当前成员数 */
    private Integer memberCount;

    /** 最大成员数 */
    private Integer maxMembers;

    /** 加群方式：0-自由加入 1-需要审核 2-禁止加入 */
    private Integer joinMode;

    /** 是否全员禁言：0-否 1-是 */
    private Boolean isAllMuted;

    /** 状态：0-正常 1-已解散 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}