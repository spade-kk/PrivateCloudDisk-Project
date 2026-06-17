package org.project.im.server.signaling.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通话会话模型
 * <p>
 * 表示一个活跃的 WebRTC 通话会话，管理通话状态、参与者、
 * 媒体流类型、网络质量等核心信息。
 * <p>
 * 支持两种通话模式：
 * <ul>
 *   <li>P2P 通话：1v1 视频/语音通话</li>
 *   <li>群组通话：多人视频会议（通过 SFU/MCU 或 Mesh 架构）</li>
 * </ul>
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallSession implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================== 基本信息 ====================

    /** 通话唯一 ID（雪花算法生成） */
    private String callId;

    /** 通话房间 ID（群组通话时使用） */
    private String roomId;

    /** 通话类型：1-语音通话 2-视频通话 */
    private Integer callType;

    /** 通话模式：1-P2P 2-群组（Mesh） */
    private Integer callMode;

    /** 发起者用户 ID */
    private String callerId;

    /** 被叫者用户 ID（P2P 模式） */
    private String calleeId;

    // ==================== 状态管理 ====================

    /**
     * 通话状态：
     * 0-等待接听（ringing）
     * 1-通话中（active）
     * 2-已拒绝（rejected）
     * 3-已取消（cancelled）
     * 4-已挂断（ended）
     * 5-超时（timeout）
     * 6-忙线（busy）
     */
    private Integer status;

    /** 通话开始时间 */
    private LocalDateTime startTime;

    /** 通话结束时间 */
    private LocalDateTime endTime;

    /** 通话持续时间（秒） */
    private Long duration;

    /** 通话被拒绝原因 */
    private String rejectReason;

    // ==================== 参与者管理 ====================

    /** 在线参与者集合（userId → 是否开启视频） */
    @Builder.Default
    private ConcurrentHashMap<String, Boolean> participants = new ConcurrentHashMap<>();

    /** 已接受但未连接的参与者 */
    @Builder.Default
    private Set<String> acceptedParticipants = ConcurrentHashMap.newKeySet();

    /** 已拒绝的参与者 */
    @Builder.Default
    private Set<String> rejectedParticipants = ConcurrentHashMap.newKeySet();

    // ==================== 媒体配置 ====================

    /** 是否启用音频 */
    @Builder.Default
    private Boolean audioEnabled = true;

    /** 是否启用视频 */
    @Builder.Default
    private Boolean videoEnabled = true;

    /** 是否启用屏幕共享 */
    @Builder.Default
    private Boolean screenShareEnabled = false;

    // ==================== 网络质量 ====================

    /** 当前目标码率（kbps） */
    @Builder.Default
    private Integer targetBitrate = 1500;

    /** 当前目标分辨率宽度 */
    @Builder.Default
    private Integer targetWidth = 1280;

    /** 当前目标分辨率高度 */
    @Builder.Default
    private Integer targetHeight = 720;

    /** 当前目标帧率 */
    @Builder.Default
    private Integer targetFps = 30;

    /** 编解码器：vp8, vp9, h264, h265, av1 */
    @Builder.Default
    private String codec = "vp8";

    /** 网络质量等级：0-优秀 1-良好 2-一般 3-差 4-极差 */
    @Builder.Default
    private Integer networkQuality = 0;

    // ==================== 便捷方法 ====================

    /** 是否是 P2P 通话 */
    public boolean isP2P() {
        return callMode == null || callMode == 1;
    }

    /** 是否是群组通话 */
    public boolean isGroup() {
        return callMode != null && callMode == 2;
    }

    /** 是否处于活跃状态 */
    public boolean isActive() {
        return status != null && status == 1;
    }

    /** 是否已结束 */
    public boolean isEnded() {
        return status != null && (status == 3 || status == 4 || status == 5 || status == 6);
    }

    /** 添加参与者 */
    public void addParticipant(String userId, boolean videoEnabled) {
        participants.put(userId, videoEnabled);
    }

    /** 移除参与者 */
    public void removeParticipant(String userId) {
        participants.remove(userId);
    }

    /** 获取参与者数量 */
    public int getParticipantCount() {
        return participants.size();
    }

    /** 标记参与者已接受 */
    public void markAccepted(String userId) {
        acceptedParticipants.add(userId);
    }

    /** 标记参与者已拒绝 */
    public void markRejected(String userId) {
        rejectedParticipants.add(userId);
    }

    /** 检查是否所有参与者都已接受 */
    public boolean allAccepted() {
        if (isP2P()) {
            return acceptedParticipants.contains(calleeId);
        }
        return acceptedParticipants.size() >= participants.size();
    }

    /** 获取通话对方用户 ID（P2P 模式） */
    public String getPeerId(String userId) {
        if (callerId.equals(userId)) return calleeId;
        if (calleeId.equals(userId)) return callerId;
        return null;
    }
}