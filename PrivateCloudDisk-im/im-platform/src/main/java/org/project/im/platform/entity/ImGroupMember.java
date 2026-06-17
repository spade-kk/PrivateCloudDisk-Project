package org.project.im.platform.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 群组成员实体
 * <p>
 * 对应数据库表 im_group_member，存储群组成员关系。
 * 包含成员在群中的角色、禁言状态、最后阅读位置等信息。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImGroupMember {

    /** 主键 ID */
    private Long id;

    /** 群组 ID */
    private String groupId;

    /** 用户 ID */
    private String userId;

    /** 群内角色：1-群主 2-管理员 3-成员 */
    private Integer role;

    /** 群内别名 */
    private String alias;

    /** 禁言截止时间 */
    private LocalDateTime muteUntil;

    /** 最后阅读的消息序号 */
    private Long lastReadSeq;

    /** 加入时间 */
    private LocalDateTime joinTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}