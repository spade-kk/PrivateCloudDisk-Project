package org.project.im.server.grpc;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.im.common.grpc.IMGrpcProto;
import org.project.im.common.grpc.IMServerServiceGrpc;
import org.project.im.common.mq.IMMQProto;
import org.project.im.common.protocol.v2.IMProtocolV2;
import org.project.im.server.netty.SessionManager;
import org.project.im.server.service.EventPublisher;
import org.springframework.stereotype.Component;

// ============================================================
// IM Server gRPC 服务实现 v2.0
// ============================================================
// 职责：
//   接收 IM Router 通过 gRPC 转发的消息推送请求，将消息通过 WebSocket
//   推送到目标用户的活跃连接。消息一经发出即视为送达：
//   - 推送成功 → 直接发布 MessageDeliveredEvent（消息已送达）
//   - 推送失败（未找到连接 / 异常）→ 发布 MessageFailedEvent（消息推送失败）
//   不再等待客户端 ACK，也不再进行指数级重发。
//   回执链路：IM Router 消费送达/失败事件后，以 RECEIPT 类型回推给发送方，
//   本类负责将事件转换为客户端可识别的回执信封（ReceiptPayload）并下发。
//
// 调用链路：
//   IM Business → MQ (push.command) → IM Router → gRPC PushMessage → IM Server
//   IM Server → SessionManager.sendToUser() → WebSocket Binary Frame → 客户端
//   IM Server → MQ (im.message.delivered/failed.event)
//   IM Router → gRPC PushMessage(RECEIPT) → IM Server → 发送方客户端
// ============================================================

/**
 * IM Server gRPC 服务实现
 * <p>
 * 实现 {@code IMServerService} 接口，供 IM Router 通过 gRPC 调用。
 * 所有 gRPC 方法运行在 gRPC 自己的线程池中，不阻塞 Netty I/O 线程。
 * </p>
 *
 * <h3>接口说明</h3>
 * <ul>
 *   <li>{@code PushMessage} — 推送单条消息到目标用户</li>
 *   <li>{@code BatchPushMessages} — 批量推送消息（群聊场景）</li>
 *   <li>{@code CheckUserOnline} — 检查用户是否在本节点在线</li>
 *   <li>{@code KickUser} — 强制踢出用户连接（管理员操作）</li>
 * </ul>
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IMServerServiceImpl extends IMServerServiceGrpc.IMServerServiceImplBase {

    private final SessionManager sessionManager;
    private final EventPublisher eventPublisher;

    // ============================================================
    // PushMessage — 推送单条消息
    // ============================================================

    /**
     * 推送消息到目标用户
     * <p>
     * IM Router 通过 gRPC 调用此方法，将消息推送到目标用户的 WebSocket 连接。
     * 消息一经发出即视为送达：推送成功后直接发布 MessageDeliveredEvent；
     * 若未找到接收方连接或推送异常，则发布 MessageFailedEvent。
     * </p>
     *
     * <h3>响应码</h3>
     * <ul>
     *   <li>0 = 成功推送</li>
     *   <li>1 = 用户不在线（本节点无活跃连接）</li>
     *   <li>2 = 推送失败（异常）</li>
     * </ul>
     */
    @Override
    public void pushMessage(
            IMGrpcProto.PushMessageRequest request,
            StreamObserver<IMGrpcProto.PushMessageResponse> responseObserver) {

        String receiverId = request.getReceiverId();
        String messageId = request.getMessageId();
        String senderId = request.getSenderId();
        String conversationId = request.getConversationId();
        byte[] rawEnvelopeBytes = request.getEnvelopeBytes().toByteArray();

        try {
            // 0. 回执通知（RECEIPT）：将 Router 传入的裸事件 Protobuf
            //    转换为客户端可识别的回执信封；普通消息原样透传
            byte[] envelopeBytes = toOutboundEnvelopeBytes(request.getMessageType(), rawEnvelopeBytes);

            // 1. 检查用户是否在本节点存在活跃连接
            if (!sessionManager.isOnline(receiverId)) {
                log.debug("PushMessage 用户不在线: messageId={}, receiverId={}",
                        messageId, receiverId);
                // 未找到接收方连接 → 发布消息推送失败事件
                eventPublisher.publishMessageFailedEvent(
                        messageId, receiverId, senderId, conversationId,
                        1, // failCode=1 (未找到接收方连接)
                        "Receiver connection not found on this node",
                        request.getMessageType(),
                        request.getOriginalMessageId(),
                        request.getOriginalSenderId());
                responseObserver.onNext(IMGrpcProto.PushMessageResponse.newBuilder()
                        .setCode(1) // 用户不在线
                        .setMessage("User not online on this node")
                        .build());
                responseObserver.onCompleted();
                return;
            }

            // 2. 通过 WebSocket 推送二进制消息帧
            sessionManager.sendToUser(receiverId, envelopeBytes);

            // 3. 消息一经发出即视为送达 → 直接发布消息送达事件
            eventPublisher.publishMessageDeliveredEvent(
                    messageId, receiverId, senderId, conversationId,
                    request.getMessageType(),
                    request.getOriginalMessageId(),
                    request.getOriginalSenderId());

            log.debug("PushMessage 成功: messageId={}, receiverId={}, traceId={}",
                    messageId, receiverId, request.getTraceId());

            responseObserver.onNext(IMGrpcProto.PushMessageResponse.newBuilder()
                    .setCode(0) // 成功
                    .setMessage("OK")
                    .build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("PushMessage 异常: messageId={}, receiverId={}",
                    messageId, receiverId, e);
            // 推送异常 → 发布消息推送失败事件
            eventPublisher.publishMessageFailedEvent(
                    messageId, receiverId, senderId, conversationId,
                    2, // failCode=2 (推送异常/其他)
                    "Push failed: " + e.getMessage(),
                    request.getMessageType(),
                    request.getOriginalMessageId(),
                    request.getOriginalSenderId());
            responseObserver.onNext(IMGrpcProto.PushMessageResponse.newBuilder()
                    .setCode(2) // 推送失败
                    .setMessage("Push failed: " + e.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    // ============================================================
    // 出站 Envelope 构造（回执通知转换 + 普通消息透传）
    // ============================================================

    /**
     * 将 gRPC 传入的 envelope_bytes 转换为可直接下发的 IMEnvelope 字节。
     * <ul>
     *   <li>回执通知（message_type=RECEIPT）：Router 传入的是裸事件
     *       Protobuf（MessageDeliveredEvent/MessageFailedEvent/MessageSendFailedEvent），
     *       此处转换为客户端可识别的回执信封（携带 ReceiptPayload 明文负载，
     *       由 SessionManager 做 Layer 2 加密）。</li>
     *   <li>普通业务消息：原样透传（envelope_bytes 即为 IMEnvelope，携带明文负载）。</li>
     * </ul>
     */
    private byte[] toOutboundEnvelopeBytes(
            IMMQProto.MessageType messageType, byte[] rawEnvelopeBytes) {
        try {
            if (messageType == IMMQProto.MessageType.RECEIPT) {
                // 回执负载可能是已送达事件、推送失败事件或发送失败事件，按类型尝试解析
                try {
                    return buildDeliveredReceiptEnvelope(
                            IMMQProto.MessageDeliveredEvent.parseFrom(rawEnvelopeBytes));
                } catch (Exception e) {
                    try {
                        return buildFailedReceiptEnvelope(
                                IMMQProto.MessageFailedEvent.parseFrom(rawEnvelopeBytes));
                    } catch (Exception e2) {
                        return buildSendFailedReceiptEnvelope(
                                IMMQProto.MessageSendFailedEvent.parseFrom(rawEnvelopeBytes));
                    }
                }
            }
            return rawEnvelopeBytes;
        } catch (Exception e) {
            // 转换失败则退回原始字节（由 SessionManager 尝试解析；无法解析时跳过）
            log.warn("回执通知转换失败，使用原始字节: messageType={}, error={}",
                    messageType, e.getMessage());
            return rawEnvelopeBytes;
        }
    }

    /**
     * 构建回执通知信封（RECEIPT，93）：负载为 ReceiptPayload，
     * 客户端据此将该条消息标记为已送达/推送失败/发送失败（如红色感叹号）。
     */
    private byte[] buildReceiptEnvelope(
            String messageId, String conversationId, String senderId, String receiverId,
            IMProtocolV2.ReceiptStatus status, int failCode, String failReason, long receiptAt) {
        IMProtocolV2.ReceiptPayload receipt = IMProtocolV2.ReceiptPayload.newBuilder()
                .setOriginalMessageId(nullToEmpty(messageId))
                .setConversationId(nullToEmpty(conversationId))
                .setSenderId(nullToEmpty(senderId))
                .setReceiverId(nullToEmpty(receiverId))
                .setStatus(status)
                .setFailCode(failCode)
                .setFailReason(nullToEmpty(failReason))
                .setReceiptAt(receiptAt)
                .build();
        return IMProtocolV2.IMEnvelope.newBuilder()
                .setVersion(2)
                .setMessageId("receipt-" + messageId + "-" + System.currentTimeMillis())
                .setMessageType(IMProtocolV2.IMMessageType.RECEIPT)
                .setSenderId(nullToEmpty(senderId))
                .setReceiverId(nullToEmpty(senderId)) // 回执推送给发送方
                .setConversationId(nullToEmpty(conversationId))
                .setTimestamp(receiptAt)
                .setEncryptedPayload(com.google.protobuf.ByteString.copyFrom(receipt.toByteArray()))
                .build()
                .toByteArray();
    }

    /**
     * 送达回执（MessageDeliveredEvent → DELIVERED）。
     */
    private byte[] buildDeliveredReceiptEnvelope(IMMQProto.MessageDeliveredEvent evt) {
        return buildReceiptEnvelope(
                evt.getMessageId(), evt.getConversationId(),
                evt.getSenderId(), evt.getReceiverId(),
                IMProtocolV2.ReceiptStatus.RECEIPT_DELIVERED, 0, "", evt.getDeliveredAt());
    }

    /**
     * 推送失败回执（MessageFailedEvent → PUSH_FAILED）。
     */
    private byte[] buildFailedReceiptEnvelope(IMMQProto.MessageFailedEvent evt) {
        return buildReceiptEnvelope(
                evt.getMessageId(), evt.getConversationId(),
                evt.getSenderId(), evt.getReceiverId(),
                IMProtocolV2.ReceiptStatus.RECEIPT_PUSH_FAILED, evt.getFailCode(), evt.getFailReason(),
                evt.getFailedAt());
    }

    /**
     * 发送失败回执（MessageSendFailedEvent → SEND_FAILED）。
     */
    private byte[] buildSendFailedReceiptEnvelope(IMMQProto.MessageSendFailedEvent evt) {
        return buildReceiptEnvelope(
                evt.getMessageId(), evt.getConversationId(),
                evt.getSenderId(), evt.getReceiverId(),
                IMProtocolV2.ReceiptStatus.RECEIPT_SEND_FAILED, evt.getErrorCode(), evt.getErrorMessage(),
                evt.getTimestamp());
    }

    /** 将空值归一为空字符串，避免 Protobuf 默认值歧义 */
    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    // ============================================================
    // BatchPushMessages — 批量推送消息（群聊场景）
    // ============================================================

    /**
     * 批量推送消息
     * <p>
     * 群聊场景下，IM Router 将消息批量推送到多个接收者。
     * 逐个检查在线状态并推送，统计成功/失败数量。
     * 每个接收者推送成功后直接发布 MessageDeliveredEvent，
     * 推送失败（未找到连接 / 异常）则发布 MessageFailedEvent。
     * </p>
     */
    @Override
    public void batchPushMessages(
            IMGrpcProto.BatchPushMessageRequest request,
            StreamObserver<IMGrpcProto.BatchPushMessageResponse> responseObserver) {

        byte[] envelopeBytes = request.getEnvelopeBytes().toByteArray();
        String messageId = request.getMessageId();
        int successCount = 0;
        int failedCount = 0;
        java.util.List<String> failedUserIds = new java.util.ArrayList<>();

        for (String receiverId : request.getReceiverIdsList()) {
            try {
                if (sessionManager.isOnline(receiverId)) {
                    sessionManager.sendToUser(receiverId, envelopeBytes);

                    // 推送成功 → 直接发布消息送达事件
                    eventPublisher.publishMessageDeliveredEvent(
                            messageId, receiverId,
                            request.getSenderId(),
                            request.getConversationId(),
                            IMMQProto.MessageType.CHAT_MESSAGE, // 群聊批量推送为普通聊天消息
                            "", "");
                    successCount++;
                } else {
                    // 未找到接收方连接 → 发布消息推送失败事件
                    eventPublisher.publishMessageFailedEvent(
                            messageId, receiverId,
                            request.getSenderId(),
                            request.getConversationId(),
                            1, // failCode=1 (未找到接收方连接)
                            "Receiver connection not found on this node",
                            IMMQProto.MessageType.CHAT_MESSAGE,
                            "", "");
                    failedCount++;
                    failedUserIds.add(receiverId);
                }
            } catch (Exception e) {
                log.error("BatchPush 推送异常: messageId={}, receiverId={}",
                        messageId, receiverId, e);
                // 推送异常 → 发布消息推送失败事件
                eventPublisher.publishMessageFailedEvent(
                        messageId, receiverId,
                        request.getSenderId(),
                        request.getConversationId(),
                        2, // failCode=2 (推送异常/其他)
                        "Push failed: " + e.getMessage(),
                        IMMQProto.MessageType.CHAT_MESSAGE,
                        "", "");
                failedCount++;
                failedUserIds.add(receiverId);
            }
        }

        log.info("BatchPush 完成: messageId={}, success={}, failed={}, traceId={}",
                messageId, successCount, failedCount, request.getTraceId());

        responseObserver.onNext(IMGrpcProto.BatchPushMessageResponse.newBuilder()
                .setSuccessCount(successCount)
                .setFailedCount(failedCount)
                .addAllFailedUserIds(failedUserIds)
                .build());
        responseObserver.onCompleted();
    }

    // ============================================================
    // CheckUserOnline — 检查用户在线状态
    // ============================================================

    /**
     * 检查用户是否在本节点在线
     * <p>
     * IM Router 健康检查接口，确认用户连接是否仍在当前节点。
     * </p>
     */
    @Override
    public void checkUserOnline(
            IMGrpcProto.CheckUserOnlineRequest request,
            StreamObserver<IMGrpcProto.CheckUserOnlineResponse> responseObserver) {

        String userId = request.getUserId();
        boolean online = sessionManager.isOnline(userId);

        responseObserver.onNext(IMGrpcProto.CheckUserOnlineResponse.newBuilder()
                .setOnline(online)
                .setServerNodeId(online ? sessionManager.getNodeId() : "")
                .build());
        responseObserver.onCompleted();
    }

    // ============================================================
    // KickUser — 强制踢出用户
    // ============================================================

    /**
     * 强制踢出用户连接
     * <p>
     * 管理员操作，通过 gRPC 调用踢出指定用户的所有连接。
     * </p>
     */
    @Override
    public void kickUser(
            IMGrpcProto.KickUserRequest request,
            StreamObserver<IMGrpcProto.KickUserResponse> responseObserver) {

        String userId = request.getUserId();
        String reason = request.getReason();

        try {
            sessionManager.kickUser(userId);
            log.info("用户被踢出: userId={}, reason={}", userId, reason);

            responseObserver.onNext(IMGrpcProto.KickUserResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("User kicked: " + userId)
                    .build());
        } catch (Exception e) {
            log.error("踢出用户失败: userId={}", userId, e);
            responseObserver.onNext(IMGrpcProto.KickUserResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Kick failed: " + e.getMessage())
                    .build());
        }
        responseObserver.onCompleted();
    }
}
