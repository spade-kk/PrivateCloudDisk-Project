package org.project.im.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通话记录 DTO
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallRecordDTO {

    /** 通话唯一 ID */
    private String callId;

    /** 通话房间 ID */
    private String roomId;

    /** 通话类型：1-语音通话 2-视频通话 */
    private Integer callType;

    /** 通话模式：1-P2P 2-群组 */
    private Integer callMode;

    /** 发起者用户 ID */
    private String callerId;

    /** 发起者名称 */
    private String callerName;

    /** 发起者头像 */
    private String callerAvatar;

    /** 被叫者用户 ID */
    private String calleeId;

    /** 被叫者名称 */
    private String calleeName;

    /** 被叫者头像 */
    private String calleeAvatar;

    /** 通话状态 */
    private Integer status;

    /** 通话开始时间 */
    private LocalDateTime startTime;

    /** 通话结束时间 */
    private LocalDateTime endTime;

    /** 通话持续时间（秒） */
    private Long duration;

    /** 拒绝原因 */
    private String rejectReason;

    /** 参与者列表 */
    private List<String> participants;

    /** 是否启用视频 */
    private Boolean videoEnabled;

    /** 是否启用屏幕共享 */
    private Boolean screenShareEnabled;

    /** 挂断方 */
    private String hangupBy;

    /** 创建时间 */
    private LocalDateTime createTime;
}