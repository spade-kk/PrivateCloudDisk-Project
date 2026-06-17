package org.project.im.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 群组成员 DTO
 * <p>
 * 群组成员关系的数据载体，包含成员在群中的角色、
 * 昵称、加入时间、禁言状态等信息。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 记录 ID */
    private Long id;

    /** 群组 ID */
    private String groupId;

    /** 用户 ID */
    private String userId;

    /** 用户昵称 */
    private String nickname;

    /** 用户头像 URL */
    private String avatar;

    /** 群内角色：1-群主 2-管理员 3-成员 4-禁言中 */
    private Integer role;

    /** 群内别名 */
    private String alias;

    /** 禁言截止时间（null 表示未禁言） */
    private LocalDateTime muteUntil;

    /** 最后阅读的消息序号 */
    private Long lastReadSeq;

    /** 加入时间 */
    private LocalDateTime joinTime;
}