package org.project.im.platform.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 通话记录实体
 * <p>
 * 对应数据库表 im_call_record，存储所有通话记录。
 * 包括通话基本信息、参与方、开始/结束时间、通话时长、通话类型等。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImCallRecord {

    /** 主键 ID */
    private Long id;

    /** 通话唯一 ID（与信令服务器 callId 一致） */
    private String callId;

    /** 通话房间 ID（群组通话时使用） */
    private String roomId;

    /** 通话类型：1-语音通话 2-视频通话 */
    private Integer callType;

    /** 通话模式：1-P2P 2-群组 */
    private Integer callMode;

    /** 发起者用户 ID */
    private String callerId;

    /** 被叫者用户 ID（P2P 模式） */
    private String calleeId;

    /**
     * 通话状态：
     * 0-等待接听 1-通话中 2-已拒绝 3-已取消
     * 4-已挂断 5-超时 6-忙线
     */
    private Integer status;

    /** 通话开始时间 */
    private LocalDateTime startTime;

    /** 通话结束时间 */
    private LocalDateTime endTime;

    /** 通话持续时间（秒） */
    private Long duration;

    /** 拒绝原因 */
    private String rejectReason;

    /** 参与者列表（JSON 数组） */
    private String participants;

    /** 是否启用视频 */
    private Boolean videoEnabled;

    /** 是否启用屏幕共享 */
    private Boolean screenShareEnabled;

    /** 挂断方用户 ID */
    private String hangupBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}