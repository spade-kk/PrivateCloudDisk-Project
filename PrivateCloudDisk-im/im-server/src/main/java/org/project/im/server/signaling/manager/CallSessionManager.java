package org.project.im.server.signaling.manager;

import lombok.extern.slf4j.Slf4j;
import org.project.im.server.signaling.model.CallSession;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 通话会话管理器
 * <p>
 * 管理所有活跃的 WebRTC 通话会话，负责：
 * <ul>
 *   <li>创建/销毁通话会话</li>
 *   <li>查询用户的活跃通话</li>
 *   <li>通话超时自动清理</li>
 *   <li>通话统计</li>
 *   <li>并发安全（线程安全）</li>
 * </ul>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>一个用户同时只能有一通活跃通话（企业级约束）</li>
 *   <li>通话邀请 60 秒超时自动取消</li>
 *   <li>空闲通话 30 分钟自动清理</li>
 * </ul>
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
@Slf4j
@Component
public class CallSessionManager {

    /** 通话会话映射（callId → CallSession） */
    private final ConcurrentHashMap<String, CallSession> callSessions = new ConcurrentHashMap<>();

    /** 用户活跃通话映射（userId → callId） */
    private final ConcurrentHashMap<String, String> userActiveCall = new ConcurrentHashMap<>();

    /** 通话邀请超时（秒） */
    private static final long INVITE_TIMEOUT_SECONDS = 60;

    /** 空闲通话超时清理（分钟） */
    private static final long IDLE_CLEANUP_MINUTES = 30;

    /** 定时清理线程池 */
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "call-session-cleanup");
        t.setDaemon(true);
        return t;
    });

    public CallSessionManager() {
        // 每分钟清理一次超时会话
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredSessions, 1, 1, TimeUnit.MINUTES);
    }

    // ==================== CRUD 操作 ====================

    /**
     * 创建新的通话会话
     *
     * @param callSession 通话会话
     * @return 创建成功返回 callId，用户忙线返回 null
     */
    public String createSession(CallSession callSession) {
        // 检查发起者是否已有活跃通话
        if (userActiveCall.containsKey(callSession.getCallerId())) {
            log.warn("用户 {} 已有活跃通话，无法发起新通话", callSession.getCallerId());
            return null;
        }
        // 检查被叫者是否已有活跃通话
        if (callSession.getCalleeId() != null && userActiveCall.containsKey(callSession.getCalleeId())) {
            log.warn("用户 {} 已有活跃通话，无法接收新通话", callSession.getCalleeId());
            return null;
        }

        callSession.setStartTime(LocalDateTime.now());
        callSession.setStatus(0); // ringing
        callSessions.put(callSession.getCallId(), callSession);
        userActiveCall.put(callSession.getCallerId(), callSession.getCallId());

        log.info("创建通话会话: callId={}, caller={}, callee={}, type={}",
                callSession.getCallId(), callSession.getCallerId(),
                callSession.getCalleeId(), callSession.getCallType());
        return callSession.getCallId();
    }

    /**
     * 获取通话会话
     */
    public CallSession getSession(String callId) {
        return callSessions.get(callId);
    }

    /**
     * 获取用户的活跃通话
     */
    public CallSession getUserActiveCall(String userId) {
        String callId = userActiveCall.get(userId);
        if (callId == null) return null;
        return callSessions.get(callId);
    }

    /**
     * 获取用户活跃通话 ID
     */
    public String getUserActiveCallId(String userId) {
        return userActiveCall.get(userId);
    }

    /**
     * 判断用户是否在通话中
     */
    public boolean isUserInCall(String userId) {
        return userActiveCall.containsKey(userId);
    }

    /**
     * 接听通话：将通话状态从 ringing 切换为 active
     */
    public boolean acceptCall(String callId, String userId) {
        CallSession session = callSessions.get(callId);
        if (session == null || !session.getStatus().equals(0)) {
            return false;
        }
        session.setStatus(1); // active
        session.setStartTime(LocalDateTime.now());
        session.markAccepted(userId);
        userActiveCall.put(userId, callId);
        log.info("用户 {} 接听通话: callId={}", userId, callId);
        return true;
    }

    /**
     * 拒绝通话
     */
    public boolean rejectCall(String callId, String userId, String reason) {
        CallSession session = callSessions.get(callId);
        if (session == null) return false;
        session.setStatus(2); // rejected
        session.setRejectReason(reason);
        session.setEndTime(LocalDateTime.now());
        session.markRejected(userId);
        cleanupUserCall(userId, callId);
        log.info("用户 {} 拒绝通话: callId={}, reason={}", userId, callId, reason);
        return true;
    }

    /**
     * 取消通话
     */
    public boolean cancelCall(String callId, String userId) {
        CallSession session = callSessions.get(callId);
        if (session == null) return false;
        session.setStatus(3); // cancelled
        session.setEndTime(LocalDateTime.now());
        // 清理所有参与者的活跃通话标记
        cleanupAllParticipants(session);
        log.info("用户 {} 取消通话: callId={}", userId, callId);
        return true;
    }

    /**
     * 挂断通话
     */
    public boolean hangupCall(String callId, String userId) {
        CallSession session = callSessions.get(callId);
        if (session == null) return false;
        session.setStatus(4); // ended
        session.setEndTime(LocalDateTime.now());
        if (session.getStartTime() != null) {
            session.setDuration(ChronoUnit.SECONDS.between(session.getStartTime(), session.getEndTime()));
        }
        cleanupAllParticipants(session);
        log.info("用户 {} 挂断通话: callId={}, duration={}s", userId, callId, session.getDuration());
        return true;
    }

    /**
     * 标记通话超时
     */
    public boolean timeoutCall(String callId) {
        CallSession session = callSessions.get(callId);
        if (session == null) return false;
        session.setStatus(5); // timeout
        session.setEndTime(LocalDateTime.now());
        cleanupAllParticipants(session);
        log.info("通话超时: callId={}", callId);
        return true;
    }

    /**
     * 标记忙线
     */
    public boolean markBusy(String callId) {
        CallSession session = callSessions.get(callId);
        if (session == null) return false;
        session.setStatus(6); // busy
        session.setEndTime(LocalDateTime.now());
        cleanupAllParticipants(session);
        log.info("通话忙线: callId={}", callId);
        return true;
    }

    /**
     * 更新网络质量
     */
    public void updateNetworkQuality(String callId, int quality) {
        CallSession session = callSessions.get(callId);
        if (session != null) {
            session.setNetworkQuality(quality);
        }
    }

    /**
     * 更新编码参数
     */
    public void updateEncoderParams(String callId, int bitrate, int width, int height, int fps) {
        CallSession session = callSessions.get(callId);
        if (session != null) {
            session.setTargetBitrate(bitrate);
            session.setTargetWidth(width);
            session.setTargetHeight(height);
            session.setTargetFps(fps);
        }
    }

    // ==================== 查询/统计 ====================

    /**
     * 获取所有活跃通话数
     */
    public int getActiveCallCount() {
        return (int) callSessions.values().stream()
                .filter(CallSession::isActive)
                .count();
    }

    /**
     * 获取所有在线参与者数
     */
    public int getOnlineParticipantCount() {
        return callSessions.values().stream()
                .filter(CallSession::isActive)
                .mapToInt(CallSession::getParticipantCount)
                .sum();
    }

    /**
     * 获取所有活跃通话
     */
    public List<CallSession> getActiveCalls() {
        return callSessions.values().stream()
                .filter(CallSession::isActive)
                .collect(Collectors.toList());
    }

    /**
     * 移除通话会话（用于通话结束后延迟清理）
     */
    public void removeSession(String callId) {
        CallSession session = callSessions.remove(callId);
        if (session != null) {
            cleanupAllParticipants(session);
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 清理用户活跃通话标记
     */
    private void cleanupUserCall(String userId, String callId) {
        userActiveCall.remove(userId, callId);
    }

    /**
     * 清理所有参与者的活跃通话标记
     */
    private void cleanupAllParticipants(CallSession session) {
        userActiveCall.remove(session.getCallerId());
        if (session.getCalleeId() != null) {
            userActiveCall.remove(session.getCalleeId());
        }
        session.getParticipants().keySet().forEach(userActiveCall::remove);
    }

    /**
     * 定时清理过期/超时会话
     */
    private void cleanupExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        List<String> toRemove = new ArrayList<>();

        callSessions.forEach((callId, session) -> {
            // 邀请超时（ringing 状态超过 60 秒）
            if (session.getStatus() == 0 && session.getStartTime() != null) {
                long elapsed = ChronoUnit.SECONDS.between(session.getStartTime(), now);
                if (elapsed > INVITE_TIMEOUT_SECONDS) {
                    timeoutCall(callId);
                    toRemove.add(callId);
                }
            }
            // 已结束的通话延迟清理（保留一段时间用于查询）
            if (session.isEnded() && session.getEndTime() != null) {
                long elapsed = ChronoUnit.MINUTES.between(session.getEndTime(), now);
                if (elapsed > IDLE_CLEANUP_MINUTES) {
                    toRemove.add(callId);
                }
            }
        });

        toRemove.forEach(callSessions::remove);
        if (!toRemove.isEmpty()) {
            log.debug("清理 {} 个过期通话会话", toRemove.size());
        }
    }
}