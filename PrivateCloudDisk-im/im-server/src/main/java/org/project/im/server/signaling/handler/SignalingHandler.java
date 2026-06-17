package org.project.im.server.signaling.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.im.common.enums.CommandType;
import org.project.im.common.protocol.MessageProtocol;
import org.project.im.server.netty.SessionManager;
import org.project.im.server.signaling.manager.CallSessionManager;
import org.project.im.server.signaling.model.CallSession;
import org.project.im.server.signaling.optimizer.AdaptiveVideoOptimizer;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * WebRTC 信令处理器
 * <p>
 * 处理所有视频通话相关的 WebSocket 信令消息，包括：
 * <ul>
 *   <li>通话控制：邀请/接听/拒绝/挂断/取消/忙线/超时</li>
 *   <li>SDP 信令：Offer/Answer/ICE Candidate 交换</li>
 *   <li>网络质量上报与自适应编码调节</li>
 *   <li>屏幕共享、静音、摄像头开关等辅助功能</li>
 *   <li>群组通话房间管理</li>
 *   <li>ICE 服务器配置下发</li>
 * </ul>
 * <p>
 * 设计原则：
 * <ul>
 *   <li>信令消息不落盘，纯内存转发（低延迟）</li>
 *   <li>通话记录由 im-platform 异步持久化</li>
 *   <li>所有操作通过 SessionManager 路由到目标用户</li>
 * </ul>
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SignalingHandler {

    private final SessionManager sessionManager;
    private final CallSessionManager callSessionManager;
    private final AdaptiveVideoOptimizer videoOptimizer;
    private final ObjectMapper objectMapper;

    // ==================== 入口：根据命令字分发 ====================

    /**
     * 处理信令消息
     *
     * @param ctx      Netty Channel 上下文
     * @param protocol 消息协议
     */
    public void handle(ChannelHandlerContext ctx, MessageProtocol protocol) {
        CommandType command = CommandType.fromCode(protocol.getCommand());
        if (command == null) {
            sendError(ctx, "未知信令命令: " + protocol.getCommand());
            return;
        }

        try {
            switch (command) {
                // ---- 通话控制 ----
                case CALL_INVITE          -> handleCallInvite(ctx, protocol);
                case CALL_ACCEPT          -> handleCallAccept(ctx, protocol);
                case CALL_REJECT          -> handleCallReject(ctx, protocol);
                case CALL_CANCEL          -> handleCallCancel(ctx, protocol);
                case CALL_HANGUP          -> handleCallHangup(ctx, protocol);
                case CALL_BUSY            -> handleCallBusy(ctx, protocol);
                case CALL_TIMEOUT         -> handleCallTimeout(ctx, protocol);

                // ---- SDP 信令 ----
                case SIGNALING_OFFER      -> handleSignalingOffer(ctx, protocol);
                case SIGNALING_ANSWER     -> handleSignalingAnswer(ctx, protocol);
                case SIGNALING_ICE_CANDIDATE -> handleIceCandidate(ctx, protocol);
                case SIGNALING_RENEGOTIATE -> handleRenegotiate(ctx, protocol);

                // ---- 通话质量控制 ----
                case CALL_QUALITY_REPORT  -> handleQualityReport(ctx, protocol);

                // ---- 通话扩展功能 ----
                case CALL_SCREEN_SHARE_START -> handleScreenShareStart(ctx, protocol);
                case CALL_SCREEN_SHARE_STOP  -> handleScreenShareStop(ctx, protocol);
                case CALL_MUTE_TOGGLE     -> handleMuteToggle(ctx, protocol);
                case CALL_CAMERA_TOGGLE   -> handleCameraToggle(ctx, protocol);
                case CALL_SWITCH_TO_VOICE -> handleSwitchToVoice(ctx, protocol);
                case CALL_SWITCH_TO_VIDEO -> handleSwitchToVideo(ctx, protocol);

                // ---- 群组通话 ----
                case CALL_ROOM_CREATE     -> handleRoomCreate(ctx, protocol);
                case CALL_ROOM_JOIN       -> handleRoomJoin(ctx, protocol);
                case CALL_ROOM_LEAVE      -> handleRoomLeave(ctx, protocol);
                case CALL_ROOM_INVITE     -> handleRoomInvite(ctx, protocol);
                case CALL_ROOM_MEMBERS    -> handleRoomMembers(ctx, protocol);
                case CALL_ROOM_INFO       -> handleRoomInfo(ctx, protocol);

                // ---- ICE 服务器 ----
                case CALL_ICE_SERVERS     -> handleIceServers(ctx, protocol);

                default -> sendError(ctx, "不支持的信令命令: " + command.getDescription());
            }
        } catch (Exception e) {
            log.error("信令处理异常: command={}, userId={}", command, protocol.getSenderId(), e);
            sendError(ctx, "信令处理失败: " + e.getMessage());
        }
    }

    // ==================== 通话控制处理 ====================

    /**
     * 处理通话邀请
     * <p>
     * 流程：
     * 1. 校验发起者是否有活跃通话
     * 2. 创建 CallSession
     * 3. 检查被叫者在线状态
     * 4. 向被叫者推送来电通知
     * 5. 向发起者返回确认
     */
    @SuppressWarnings("unchecked")
    private void handleCallInvite(ChannelHandlerContext ctx, MessageProtocol protocol) {
        Map<String, Object> payload = parsePayload(protocol);
        String callerId = protocol.getSenderId();
        String calleeId = (String) payload.get("calleeId");
        Integer callType = (Integer) payload.getOrDefault("callType", 2); // 默认视频通话

        if (calleeId == null) {
            sendError(ctx, "被叫用户 ID 不能为空");
            return;
        }

        // 检查发起者是否已在通话中
        if (callSessionManager.isUserInCall(callerId)) {
            sendToUser(callerId, buildSimpleProtocol(
                    CommandType.CALL_BUSY, callerId, callerId,
                    Map.of("message", "您已在通话中，无法发起新通话")
            ));
            return;
        }

        // 检查被叫者是否在线
        if (!sessionManager.isOnline(calleeId)) {
            sendToUser(callerId, buildSimpleProtocol(
                    CommandType.SYSTEM_NOTIFY, callerId, callerId,
                    Map.of("message", "对方不在线")
            ));
            return;
        }

        // 检查被叫者是否在通话中
        if (callSessionManager.isUserInCall(calleeId)) {
            sendToUser(callerId, buildSimpleProtocol(
                    CommandType.CALL_BUSY, callerId, callerId,
                    Map.of("message", "对方正在通话中")
            ));
            return;
        }

        // 生成通话 ID
        String callId = UUID.randomUUID().toString().replace("-", "");

        // 创建通话会话
        CallSession session = CallSession.builder()
                .callId(callId)
                .callType(callType)
                .callMode(1) // P2P
                .callerId(callerId)
                .calleeId(calleeId)
                .status(0) // ringing
                .startTime(LocalDateTime.now())
                .build();

        String result = callSessionManager.createSession(session);
        if (result == null) {
            sendToUser(callerId, buildSimpleProtocol(
                    CommandType.CALL_BUSY, callerId, callerId,
                    Map.of("message", "无法创建通话，请稍后重试")
            ));
            return;
        }

        // 通知被叫者：有来电
        Map<String, Object> invitePayload = new LinkedHashMap<>();
        invitePayload.put("callId", callId);
        invitePayload.put("callerId", callerId);
        invitePayload.put("callerName", payload.getOrDefault("callerName", callerId));
        invitePayload.put("callerAvatar", payload.getOrDefault("callerAvatar", ""));
        invitePayload.put("callType", callType);
        invitePayload.put("timestamp", System.currentTimeMillis());

        sendToUser(calleeId, buildSimpleProtocol(
                CommandType.CALL_INVITE, callerId, calleeId, invitePayload
        ));

        // 向发起者确认
        Map<String, Object> ackPayload = new LinkedHashMap<>();
        ackPayload.put("callId", callId);
        ackPayload.put("status", "ringing");
        ackPayload.put("message", "正在呼叫对方...");

        sendToUser(callerId, buildSimpleProtocol(
                CommandType.CALL_INVITE, callerId, callerId, ackPayload
        ));

        log.info("通话邀请: callId={}, caller={}, callee={}, type={}", callId, callerId, calleeId, callType);
    }

    /**
     * 处理接听通话
     */
    @SuppressWarnings("unchecked")
    private void handleCallAccept(ChannelHandlerContext ctx, MessageProtocol protocol) {
        Map<String, Object> payload = parsePayload(protocol);
        String callId = (String) payload.get("callId");
        String userId = protocol.getSenderId();

        CallSession session = callSessionManager.getSession(callId);
        if (session == null) {
            sendError(ctx, "通话不存在或已结束");
            return;
        }

        if (!callSessionManager.acceptCall(callId, userId)) {
            sendError(ctx, "无法接听通话");
            return;
        }

        // 通知双方通话已建立
        Map<String, Object> acceptPayload = new LinkedHashMap<>();
        acceptPayload.put("callId", callId);
        acceptPayload.put("status", "active");
        acceptPayload.put("timestamp", System.currentTimeMillis());

        // 通知发起者
        sendToUser(session.getCallerId(), buildSimpleProtocol(
                CommandType.CALL_ACCEPT, userId, session.getCallerId(), acceptPayload
        ));
        // 通知接听者
        sendToUser(userId, buildSimpleProtocol(
                CommandType.CALL_ACCEPT, userId, userId, acceptPayload
        ));

        log.info("通话接听: callId={}, userId={}", callId, userId);
    }

    /**
     * 处理拒绝通话
     */
    @SuppressWarnings("unchecked")
    private void handleCallReject(ChannelHandlerContext ctx, MessageProtocol protocol) {
        Map<String, Object> payload = parsePayload(protocol);
        String callId = (String) payload.get("callId");
        String userId = protocol.getSenderId();
        String reason = (String) payload.getOrDefault("reason", "用户拒绝");

        CallSession session = callSessionManager.getSession(callId);
        if (session == null) {
            sendError(ctx, "通话不存在或已结束");
            return;
        }

        callSessionManager.rejectCall(callId, userId, reason);

        // 通知发起者
        Map<String, Object> rejectPayload = new LinkedHashMap<>();
        rejectPayload.put("callId", callId);
        rejectPayload.put("reason", reason);
        rejectPayload.put("timestamp", System.currentTimeMillis());

        sendToUser(session.getCallerId(), buildSimpleProtocol(
                CommandType.CALL_REJECT, userId, session.getCallerId(), rejectPayload
        ));

        log.info("通话被拒绝: callId={}, userId={}, reason={}", callId, userId, reason);
    }

    /**
     * 处理取消通话
     */
    @SuppressWarnings("unchecked")
    private void handleCallCancel(ChannelHandlerContext ctx, MessageProtocol protocol) {
        Map<String, Object> payload = parsePayload(protocol);
        String callId = (String) payload.get("callId");
        String userId = protocol.getSenderId();

        CallSession session = callSessionManager.getSession(callId);
        if (session == null) return;

        callSessionManager.cancelCall(callId, userId);

        // 通知被叫者
        Map<String, Object> cancelPayload = new LinkedHashMap<>();
        cancelPayload.put("callId", callId);
        cancelPayload.put("message", "对方已取消通话");

        sendToUser(session.getCalleeId(), buildSimpleProtocol(
                CommandType.CALL_CANCEL, userId, session.getCalleeId(), cancelPayload
        ));

        log.info("通话取消: callId={}, userId={}", callId, userId);
    }

    /**
     * 处理挂断通话
     */
    @SuppressWarnings("unchecked")
    private void handleCallHangup(ChannelHandlerContext ctx, MessageProtocol protocol) {
        Map<String, Object> payload = parsePayload(protocol);
        String callId = (String) payload.get("callId");
        String userId = protocol.getSenderId();

        CallSession session = callSessionManager.getSession(callId);
        if (session == null) return;

        callSessionManager.hangupCall(callId, userId);

        // 通知对方
        String peerId = session.getPeerId(userId);
        if (peerId != null) {
            Map<String, Object> hangupPayload = new LinkedHashMap<>();
            hangupPayload.put("callId", callId);
            hangupPayload.put("duration", session.getDuration());
            hangupPayload.put("message", "对方已挂断");

            sendToUser(peerId, buildSimpleProtocol(
                    CommandType.CALL_HANGUP, userId, peerId, hangupPayload
            ));
        }

        // 清理视频优化器数据
        videoOptimizer.cleanup(session.getCallerId());
        if (session.getCalleeId() != null) videoOptimizer.cleanup(session.getCalleeId());

        log.info("通话挂断: callId={}, userId={}, duration={}s", callId, userId, session.getDuration());
    }

    /**
     * 处理忙线
     */
    private void handleCallBusy(ChannelHandlerContext ctx, MessageProtocol protocol) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = parsePayload(protocol);
        String callId = (String) payload.get("callId");
        String userId = protocol.getSenderId();

        CallSession session = callSessionManager.getSession(callId);
        if (session == null) return;

        callSessionManager.markBusy(callId);

        String peerId = session.getPeerId(userId);
        if (peerId != null) {
            sendToUser(peerId, buildSimpleProtocol(
                    CommandType.CALL_BUSY, userId, peerId,
                    Map.of("callId", callId, "message", "对方正在通话中")
            ));
        }
    }

    /**
     * 处理通话超时
     */
    private void handleCallTimeout(ChannelHandlerContext ctx, MessageProtocol protocol) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = parsePayload(protocol);
        String callId = (String) payload.get("callId");

        CallSession session = callSessionManager.getSession(callId);
        if (session == null) return;

        callSessionManager.timeoutCall(callId);

        // 通知双方
        Map<String, Object> timeoutPayload = Map.of("callId", callId, "message", "通话超时");
        sendToUser(session.getCallerId(), buildSimpleProtocol(
                CommandType.CALL_TIMEOUT, "SYSTEM", session.getCallerId(), timeoutPayload
        ));
        if (session.getCalleeId() != null) {
            sendToUser(session.getCalleeId(), buildSimpleProtocol(
                    CommandType.CALL_TIMEOUT, "SYSTEM", session.getCalleeId(), timeoutPayload
            ));
        }
    }

    // ==================== SDP 信令处理 ====================

    /**
     * 转发 Offer SDP 到目标用户
     */
    @SuppressWarnings("unchecked")
    private void handleSignalingOffer(ChannelHandlerContext ctx, MessageProtocol protocol) {
        Map<String, Object> payload = parsePayload(protocol);
        String callId = (String) payload.get("callId");

        CallSession session = callSessionManager.getSession(callId);
        if (session == null) {
            sendError(ctx, "通话不存在");
            return;
        }

        String targetId = session.getPeerId(protocol.getSenderId());
        if (targetId == null) {
            sendError(ctx, "找不到通话对方");
            return;
        }

        // 转发 SDP Offer（包含编码参数调整信息）
        sendToUser(targetId, buildSimpleProtocol(
                CommandType.SIGNALING_OFFER, protocol.getSenderId(), targetId, payload
        ));
    }

    /**
     * 转发 Answer SDP 到目标用户
     */
    @SuppressWarnings("unchecked")
    private void handleSignalingAnswer(ChannelHandlerContext ctx, MessageProtocol protocol) {
        Map<String, Object> payload = parsePayload(protocol);
        String callId = (String) payload.get("callId");

        CallSession session = callSessionManager.getSession(callId);
        if (session == null) return;

        String targetId = session.getPeerId(protocol.getSenderId());
        if (targetId == null) return;

        sendToUser(targetId, buildSimpleProtocol(
                CommandType.SIGNALING_ANSWER, protocol.getSenderId(), targetId, payload
        ));
    }

    /**
     * 转发 ICE Candidate
     */
    @SuppressWarnings("unchecked")
    private void handleIceCandidate(ChannelHandlerContext ctx, MessageProtocol protocol) {
        Map<String, Object> payload = parsePayload(protocol);
        String callId = (String) payload.get("callId");

        CallSession session = callSessionManager.getSession(callId);
        if (session == null) return;

        String targetId = session.getPeerId(protocol.getSenderId());
        if (targetId == null) return;

        sendToUser(targetId, buildSimpleProtocol(
                CommandType.SIGNALING_ICE_CANDIDATE, protocol.getSenderId(), targetId, payload
        ));
    }

    /**
     * 处理 SDP 重协商
     */
    @SuppressWarnings("unchecked")
    private void handleRenegotiate(ChannelHandlerContext ctx, MessageProtocol protocol) {
        Map<String, Object> payload = parsePayload(protocol);
        String callId = (String) payload.get("callId");

        CallSession session = callSessionManager.getSession(callId);
        if (session == null) return;

        String targetId = session.getPeerId(protocol.getSenderId());
        if (targetId == null) return;

        sendToUser(targetId, buildSimpleProtocol(
                CommandType.SIGNALING_RENEGOTIATE, protocol.getSenderId(), targetId, payload
        ));
    }

    // ==================== 网络质量处理 ====================

    /**
     * 处理网络质量上报（核心自适应优化入口）
     */
    @SuppressWarnings("unchecked")
    private void handleQualityReport(ChannelHandlerContext ctx, MessageProtocol protocol) {
        Map<String, Object> payload = parsePayload(protocol);
        String callId = (String) payload.get("callId");
        String userId = protocol.getSenderId();

        // 构建质量快照
        AdaptiveVideoOptimizer.NetworkQualitySnapshot snapshot = AdaptiveVideoOptimizer.NetworkQualitySnapshot.builder()
                .rtt(toDouble(payload.get("rtt"), 0))
                .packetLoss(toDouble(payload.get("packetLoss"), 0))
                .jitter(toDouble(payload.get("jitter"), 0))
                .estimatedBandwidth(toDouble(payload.get("estimatedBandwidth"), 3000))
                .isScreenShare(Boolean.TRUE.equals(payload.get("isScreenShare")))
                .timestamp(System.currentTimeMillis())
                .qualityLevel(toInt(payload.get("qualityLevel"), 0))
                .build();

        // 获取推荐的编码参数
        AdaptiveVideoOptimizer.EncoderParams params = videoOptimizer.reportQualityAndGetParams(
                callId, userId, snapshot);

        if (params != null) {
            // 更新会话编码参数
            callSessionManager.updateEncoderParams(callId,
                    params.getTargetBitrate(), params.getWidth(), params.getHeight(), params.getFps());

            // 下发编码参数调整指令
            Map<String, Object> adjustPayload = new LinkedHashMap<>();
            adjustPayload.put("callId", callId);
            adjustPayload.put("encoderParams", params.toClientParams());

            sendToUser(userId, buildSimpleProtocol(
                    CommandType.CALL_ENCODER_ADJUST, "SYSTEM", userId, adjustPayload
            ));

            // 检查是否需要建议降级为纯语音
            if (videoOptimizer.shouldDowngradeToVoice(userId)) {
                Map<String, Object> voicePayload = new LinkedHashMap<>();
                voicePayload.put("callId", callId);
                voicePayload.put("message", "当前网络较差，建议切换为语音通话");

                sendToUser(userId, buildSimpleProtocol(
                        CommandType.CALL_SWITCH_TO_VOICE, "SYSTEM", userId, voicePayload
                ));
            }
        }
    }

    // ==================== 通话扩展功能 ====================

    @SuppressWarnings("unchecked")
    private void handleScreenShareStart(ChannelHandlerContext ctx, MessageProtocol protocol) {
        Map<String, Object> payload = parsePayload(protocol);
        String callId = (String) payload.get("callId");
        String userId = protocol.getSenderId();

        CallSession session = callSessionManager.getSession(callId);
        if (session == null) return;

        session.setScreenShareEnabled(true);
        String peerId = session.getPeerId(userId);
        if (peerId != null) {
            sendToUser(peerId, buildSimpleProtocol(
                    CommandType.CALL_SCREEN_SHARE_START, userId, peerId,
                    Map.of("callId", callId, "userId", userId)
            ));
        }
    }

    @SuppressWarnings("unchecked")
    private void handleScreenShareStop(ChannelHandlerContext ctx, MessageProtocol protocol) {
        Map<String, Object> payload = parsePayload(protocol);
        String callId = (String) payload.get("callId");
        String userId = protocol.getSenderId();

        CallSession session = callSessionManager.getSession(callId);
        if (session == null) return;

        session.setScreenShareEnabled(false);
        String peerId = session.getPeerId(userId);
        if (peerId != null) {
            sendToUser(peerId, buildSimpleProtocol(
                    CommandType.CALL_SCREEN_SHARE_STOP, userId, peerId,
                    Map.of("callId", callId)
            ));
        }
    }

    @SuppressWarnings("unchecked")
    private void handleMuteToggle(ChannelHandlerContext ctx, MessageProtocol protocol) {
        Map<String, Object> payload = parsePayload(protocol);
        String callId = (String) payload.get("callId");
        String userId = protocol.getSenderId();
        boolean muted = Boolean.TRUE.equals(payload.get("muted"));

        CallSession session = callSessionManager.getSession(callId);
        if (session == null) return;

        String peerId = session.getPeerId(userId);
        if (peerId != null) {
            sendToUser(peerId, buildSimpleProtocol(
                    CommandType.CALL_MUTE_TOGGLE, userId, peerId,
                    Map.of("callId", callId, "userId", userId, "muted", muted)
            ));
        }
    }

    @SuppressWarnings("unchecked")
    private void handleCameraToggle(ChannelHandlerContext ctx, MessageProtocol protocol) {
        Map<String, Object> payload = parsePayload(protocol);
        String callId = (String) payload.get("callId");
        String userId = protocol.getSenderId();
        boolean enabled = Boolean.TRUE.equals(payload.get("enabled"));

        CallSession session = callSessionManager.getSession(callId);
        if (session == null) return;

        String peerId = session.getPeerId(userId);
        if (peerId != null) {
            sendToUser(peerId, buildSimpleProtocol(
                    CommandType.CALL_CAMERA_TOGGLE, userId, peerId,
                    Map.of("callId", callId, "userId", userId, "enabled", enabled)
            ));
        }
    }

    @SuppressWarnings("unchecked")
    private void handleSwitchToVoice(ChannelHandlerContext ctx, MessageProtocol protocol) {
        Map<String, Object> payload = parsePayload(protocol);
        String callId = (String) payload.get("callId");
        String userId = protocol.getSenderId();

        CallSession session = callSessionManager.getSession(callId);
        if (session == null) return;

        session.setVideoEnabled(false);
        String peerId = session.getPeerId(userId);
        if (peerId != null) {
            sendToUser(peerId, buildSimpleProtocol(
                    CommandType.CALL_SWITCH_TO_VOICE, userId, peerId,
                    Map.of("callId", callId)
            ));
        }
    }

    @SuppressWarnings("unchecked")
    private void handleSwitchToVideo(ChannelHandlerContext ctx, MessageProtocol protocol) {
        Map<String, Object> payload = parsePayload(protocol);
        String callId = (String) payload.get("callId");
        String userId = protocol.getSenderId();

        CallSession session = callSessionManager.getSession(callId);
        if (session == null) return;

        session.setVideoEnabled(true);
        String peerId = session.getPeerId(userId);
        if (peerId != null) {
            sendToUser(peerId, buildSimpleProtocol(
                    CommandType.CALL_SWITCH_TO_VIDEO, userId, peerId,
                    Map.of("callId", callId)
            ));
        }
    }

    // ==================== 群组通话 ====================

    @SuppressWarnings("unchecked")
    private void handleRoomCreate(ChannelHandlerContext ctx, MessageProtocol protocol) {
        Map<String, Object> payload = parsePayload(protocol);
        String roomId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String userId = protocol.getSenderId();
        String roomName = (String) payload.getOrDefault("roomName", "群组通话");

        CallSession session = CallSession.builder()
                .callId(roomId)
                .roomId(roomId)
                .callType((Integer) payload.getOrDefault("callType", 2))
                .callMode(2) // 群组通话
                .callerId(userId)
                .status(1) // 直接 active
                .startTime(LocalDateTime.now())
                .build();
        session.addParticipant(userId, true);

        callSessionManager.createSession(session);

        Map<String, Object> roomPayload = new LinkedHashMap<>();
        roomPayload.put("roomId", roomId);
        roomPayload.put("roomName", roomName);
        roomPayload.put("creatorId", userId);
        roomPayload.put("participants", List.of(userId));

        sendToUser(userId, buildSimpleProtocol(
                CommandType.CALL_ROOM_CREATE, userId, userId, roomPayload
        ));

        log.info("创建群组通话房间: roomId={}, creator={}", roomId, userId);
    }

    @SuppressWarnings("unchecked")
    private void handleRoomJoin(ChannelHandlerContext ctx, MessageProtocol protocol) {
        Map<String, Object> payload = parsePayload(protocol);
        String roomId = (String) payload.get("roomId");
        String userId = protocol.getSenderId();

        CallSession session = callSessionManager.getSession(roomId);
        if (session == null) {
            sendError(ctx, "通话房间不存在");
            return;
        }

        session.addParticipant(userId, true);
        userActiveCallPut(userId, roomId);

        // 通知所有现有参与者
        Map<String, Object> joinPayload = new LinkedHashMap<>();
        joinPayload.put("roomId", roomId);
        joinPayload.put("userId", userId);
        joinPayload.put("participants", new ArrayList<>(session.getParticipants().keySet()));

        session.getParticipants().keySet().forEach(participantId ->
                sendToUser(participantId, buildSimpleProtocol(
                        CommandType.CALL_ROOM_JOIN, userId, participantId, joinPayload
                ))
        );

        log.info("用户加入群组通话: roomId={}, userId={}", roomId, userId);
    }

    private void handleRoomLeave(ChannelHandlerContext ctx, MessageProtocol protocol) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = parsePayload(protocol);
        String roomId = (String) payload.get("roomId");
        String userId = protocol.getSenderId();

        CallSession session = callSessionManager.getSession(roomId);
        if (session == null) return;

        session.removeParticipant(userId);
        userActiveCallRemove(userId);

        // 通知其他参与者
        session.getParticipants().keySet().forEach(participantId ->
                sendToUser(participantId, buildSimpleProtocol(
                        CommandType.CALL_ROOM_LEAVE, userId, participantId,
                        Map.of("roomId", roomId, "userId", userId)
                ))
        );

        // 如果房间为空，销毁
        if (session.getParticipantCount() == 0) {
            callSessionManager.removeSession(roomId);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleRoomInvite(ChannelHandlerContext ctx, MessageProtocol protocol) {
        Map<String, Object> payload = parsePayload(protocol);
        String roomId = (String) payload.get("roomId");
        String inviteeId = (String) payload.get("inviteeId");
        String userId = protocol.getSenderId();

        CallSession session = callSessionManager.getSession(roomId);
        if (session == null) return;

        if (!sessionManager.isOnline(inviteeId)) {
            sendError(ctx, "用户不在线");
            return;
        }

        Map<String, Object> invitePayload = new LinkedHashMap<>();
        invitePayload.put("roomId", roomId);
        invitePayload.put("inviterId", userId);
        invitePayload.put("inviterName", payload.getOrDefault("inviterName", userId));
        invitePayload.put("callType", session.getCallType());
        invitePayload.put("participantCount", session.getParticipantCount());

        sendToUser(inviteeId, buildSimpleProtocol(
                CommandType.CALL_ROOM_INVITE, userId, inviteeId, invitePayload
        ));
    }

    private void handleRoomMembers(ChannelHandlerContext ctx, MessageProtocol protocol) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = parsePayload(protocol);
        String roomId = (String) payload.get("roomId");
        String userId = protocol.getSenderId();

        CallSession session = callSessionManager.getSession(roomId);
        if (session == null) return;

        sendToUser(userId, buildSimpleProtocol(
                CommandType.CALL_ROOM_MEMBERS, "SYSTEM", userId,
                Map.of("roomId", roomId, "participants", new ArrayList<>(session.getParticipants().keySet()))
        ));
    }

    private void handleRoomInfo(ChannelHandlerContext ctx, MessageProtocol protocol) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = parsePayload(protocol);
        String roomId = (String) payload.get("roomId");
        String userId = protocol.getSenderId();

        CallSession session = callSessionManager.getSession(roomId);
        if (session == null) return;

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("roomId", roomId);
        info.put("callType", session.getCallType());
        info.put("callMode", session.getCallMode());
        info.put("participantCount", session.getParticipantCount());
        info.put("startTime", session.getStartTime().toString());
        info.put("status", session.getStatus());

        sendToUser(userId, buildSimpleProtocol(
                CommandType.CALL_ROOM_INFO, "SYSTEM", userId, info
        ));
    }

    // ==================== ICE 服务器 ====================

    /**
     * 下发 ICE 服务器配置（STUN/TURN）
     * <p>
     * 企业级部署建议：
     * <ul>
     *   <li>STUN：用于 P2P 打洞（免费）</li>
     *   <li>TURN：用于中继转发（付费，保证连通性）</li>
     *   <li>优先 P2P，失败时 fallback 到 TURN</li>
     * </ul>
     */
    private void handleIceServers(ChannelHandlerContext ctx, MessageProtocol protocol) {
        String userId = protocol.getSenderId();

        List<Map<String, Object>> iceServers = new ArrayList<>();

        // STUN 服务器（Google 公共 STUN）
        Map<String, Object> stunServer = new LinkedHashMap<>();
        stunServer.put("urls", List.of(
                "stun:stun.l.google.com:19302",
                "stun:stun1.l.google.com:19302"
        ));
        iceServers.add(stunServer);

        // TURN 服务器（企业环境需配置自己的 TURN 服务器）
        // 生产环境应从配置中心读取 TURN 服务器凭据
        Map<String, Object> turnServer = new LinkedHashMap<>();
        turnServer.put("urls", List.of("turn:turn.example.com:3478?transport=udp"));
        turnServer.put("username", "privateclouddisk");
        turnServer.put("credential", System.getProperty("turn.credential", "change_me_in_production"));
        turnServer.put("credentialType", "password");
        iceServers.add(turnServer);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iceServers", iceServers);
        payload.put("iceTransportPolicy", "all"); // "all" | "relay"
        payload.put("iceCandidatePoolSize", 2);

        sendToUser(userId, buildSimpleProtocol(
                CommandType.CALL_ICE_SERVERS, "SYSTEM", userId, payload
        ));
    }

    // ==================== 辅助方法 ====================

    /**
     * 发送消息给指定用户
     */
    private void sendToUser(String userId, MessageProtocol protocol) {
        try {
            String json = objectMapper.writeValueAsString(protocol);
            sessionManager.sendToUser(userId, json);
        } catch (Exception e) {
            log.error("发送信令消息失败: userId={}, command={}", userId, protocol.getCommand(), e);
        }
    }

    /**
     * 构建简单消息协议
     */
    private MessageProtocol buildSimpleProtocol(CommandType command, String senderId,
                                                 String receiverId, Object payload) {
        return MessageProtocol.builder()
                .version(1)
                .command(command.getCode())
                .senderId(senderId)
                .receiverId(receiverId)
                .payload(payload)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 发送错误消息
     */
    private void sendError(ChannelHandlerContext ctx, String message) {
        try {
            MessageProtocol protocol = MessageProtocol.builder()
                    .version(1)
                    .command(CommandType.ERROR_NOTIFY.getCode())
                    .timestamp(System.currentTimeMillis())
                    .payload(Map.of("message", message))
                    .build();
            String json = objectMapper.writeValueAsString(protocol);
            ctx.writeAndFlush(new TextWebSocketFrame(json));
        } catch (Exception e) {
            log.error("发送错误消息失败", e);
        }
    }

    /**
     * 解析 payload 为 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(MessageProtocol protocol) {
        Object payload = protocol.getPayload();
        if (payload instanceof Map) {
            return (Map<String, Object>) payload;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("解析 payload 失败", e);
            return new LinkedHashMap<>();
        }
    }

    /**
     * 安全转换为 double
     */
    private double toDouble(Object value, double defaultValue) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try { return Double.parseDouble((String) value); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    /**
     * 安全转换为 int
     */
    private int toInt(Object value, int defaultValue) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try { return Integer.parseInt((String) value); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    /**
     * 添加用户活跃通话映射（群组通话用）
     */
    private void userActiveCallPut(String userId, String callId) {
        callSessionManager.getSession(callId); // ensure session exists
        // 通过 CallSessionManager 间接管理，这里简化处理
        // 实际由 CallSessionManager.createSession 和 acceptCall 方法管理
    }

    /**
     * 移除用户活跃通话映射（群组通话用）
     */
    private void userActiveCallRemove(String userId) {
        // 通过 CallSessionManager 间接管理
        if (callSessionManager.isUserInCall(userId)) {
            String callId = callSessionManager.getUserActiveCallId(userId);
            if (callId != null) {
                CallSession session = callSessionManager.getSession(callId);
                if (session != null && session.getParticipantCount() <= 1) {
                    callSessionManager.removeSession(callId);
                }
            }
        }
    }
}