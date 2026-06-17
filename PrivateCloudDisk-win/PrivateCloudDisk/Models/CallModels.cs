// ============================================================
// CallModels.cs — WebRTC 视频通话 + IM 消息数据模型
// 与后端 Java DTO 一一对应
// 后端对应：
//   org.project.im.common.dto.CallRecordDTO
//   org.project.im.server.signaling.model.CallSession
//   org.project.im.server.signaling.optimizer.AdaptiveVideoOptimizer
// ============================================================

using System.Text.Json.Serialization;

namespace PrivateCloudDisk.Models;

// ==================== 枚举 ====================

/// <summary>通话类型</summary>
public enum CallType { Voice = 1, Video = 2 }

/// <summary>通话状态</summary>
public enum CallStatus
{
    Ringing = 0, Active = 1, Rejected = 2,
    Cancelled = 3, Ended = 4, Timeout = 5, Busy = 6
}

/// <summary>通话模式</summary>
public enum CallMode { P2P = 1, Group = 2 }

/// <summary>网络质量等级</summary>
public enum NetworkQuality
{
    Excellent = 0, Good = 1, Fair = 2,
    Poor = 3, VeryPoor = 4
}

/// <summary>IM 消息类型</summary>
public enum IMMessageType { Text = 1, Image = 2, File = 3, CallInvite = 4, System = 5 }

/// <summary>IM 会话类型</summary>
public enum IMConversationType { Private = 1, Group = 2 }

// ==================== WebSocket 协议 ====================

/// <summary>WebSocket 通信协议消息</summary>
public class MessageProtocol
{
    [JsonPropertyName("version")]
    public int Version { get; set; } = 1;

    [JsonPropertyName("command")]
    public int Command { get; set; }

    [JsonPropertyName("seq")]
    public int? Seq { get; set; }

    [JsonPropertyName("timestamp")]
    public long Timestamp { get; set; }

    [JsonPropertyName("senderId")]
    public string? SenderId { get; set; }

    [JsonPropertyName("receiverId")]
    public string? ReceiverId { get; set; }

    [JsonPropertyName("payload")]
    public object? Payload { get; set; }
}

// ==================== 通话会话 ====================

/// <summary>通话会话</summary>
public class CallSession
{
    public string CallId { get; set; } = string.Empty;
    public string? RoomId { get; set; }
    public CallType CallType { get; set; }
    public CallMode CallMode { get; set; }
    public string CallerId { get; set; } = string.Empty;
    public string? CallerName { get; set; }
    public string? CallerAvatar { get; set; }
    public string? CalleeId { get; set; }
    public string? CalleeName { get; set; }
    public string? CalleeAvatar { get; set; }
    public CallStatus Status { get; set; }
    public DateTime? StartTime { get; set; }
    public DateTime? EndTime { get; set; }
    public long Duration { get; set; }
    public List<string> Participants { get; set; } = new();
    public bool VideoEnabled { get; set; }
    public bool AudioEnabled { get; set; } = true;
    public bool ScreenShareEnabled { get; set; }
    public EncoderParams? EncoderParams { get; set; }
    public NetworkQuality NetworkQuality { get; set; } = NetworkQuality.Excellent;
}

/// <summary>编码参数</summary>
public class EncoderParams
{
    public int Quality { get; set; }
    public int Width { get; set; }
    public int Height { get; set; }
    public int Fps { get; set; }
    public int MaxBitrate { get; set; }
    public int MinBitrate { get; set; }
    public int TargetBitrate { get; set; }
    public double ScaleResolutionDownBy { get; set; } = 1.0;
    public string Description { get; set; } = string.Empty;
}

/// <summary>网络质量快照</summary>
public class NetworkQualitySnapshot
{
    public double Rtt { get; set; }
    public double PacketLoss { get; set; }
    public double Jitter { get; set; }
    public double EstimatedBandwidth { get; set; }
    public bool IsScreenShare { get; set; }
    public long Timestamp { get; set; }
    public int QualityLevel { get; set; }
}

/// <summary>通话记录</summary>
public class CallRecord
{
    public string CallId { get; set; } = string.Empty;
    public string? RoomId { get; set; }
    public CallType CallType { get; set; }
    public CallMode CallMode { get; set; }
    public string CallerId { get; set; } = string.Empty;
    public string? CallerName { get; set; }
    public string? CallerAvatar { get; set; }
    public string? CalleeId { get; set; }
    public string? CalleeName { get; set; }
    public string? CalleeAvatar { get; set; }
    public CallStatus Status { get; set; }
    public DateTime? StartTime { get; set; }
    public DateTime? EndTime { get; set; }
    public long Duration { get; set; }
    public string? RejectReason { get; set; }
    public List<string> Participants { get; set; } = new();
    public bool VideoEnabled { get; set; }
    public bool ScreenShareEnabled { get; set; }
    public string? HangupBy { get; set; }
    public DateTime? CreateTime { get; set; }
}

// ==================== 信令 Payload 类型 ====================

/// <summary>通话邀请 Payload</summary>
public class CallInvitePayload
{
    [JsonPropertyName("callId")] public string CallId { get; set; } = string.Empty;
    [JsonPropertyName("callerId")] public string CallerId { get; set; } = string.Empty;
    [JsonPropertyName("callerName")] public string CallerName { get; set; } = string.Empty;
    [JsonPropertyName("callerAvatar")] public string CallerAvatar { get; set; } = string.Empty;
    [JsonPropertyName("callType")] public int CallType { get; set; }
    [JsonPropertyName("timestamp")] public long Timestamp { get; set; }
}

/// <summary>SDP 消息</summary>
public class SdpPayload
{
    [JsonPropertyName("callId")] public string CallId { get; set; } = string.Empty;
    [JsonPropertyName("sdp")] public SdpDescription Sdp { get; set; } = new();
}

public class SdpDescription
{
    [JsonPropertyName("type")] public string Type { get; set; } = string.Empty;
    [JsonPropertyName("sdp")] public string Sdp { get; set; } = string.Empty;
}

/// <summary>ICE Candidate</summary>
public class IceCandidatePayload
{
    [JsonPropertyName("callId")] public string CallId { get; set; } = string.Empty;
    [JsonPropertyName("candidate")] public IceCandidateDesc Candidate { get; set; } = new();
}

public class IceCandidateDesc
{
    [JsonPropertyName("candidate")] public string Candidate { get; set; } = string.Empty;
    [JsonPropertyName("sdpMid")] public string SdpMid { get; set; } = string.Empty;
    [JsonPropertyName("sdpMLineIndex")] public int SdpMLineIndex { get; set; }
}

/// <summary>网络质量上报</summary>
public class QualityReportPayload
{
    [JsonPropertyName("callId")] public string CallId { get; set; } = string.Empty;
    [JsonPropertyName("rtt")] public double Rtt { get; set; }
    [JsonPropertyName("packetLoss")] public double PacketLoss { get; set; }
    [JsonPropertyName("jitter")] public double Jitter { get; set; }
    [JsonPropertyName("estimatedBandwidth")] public double EstimatedBandwidth { get; set; }
    [JsonPropertyName("isScreenShare")] public bool IsScreenShare { get; set; }
    [JsonPropertyName("qualityLevel")] public int QualityLevel { get; set; }
}

/// <summary>编码参数调整指令</summary>
public class EncoderAdjustPayload
{
    [JsonPropertyName("callId")] public string CallId { get; set; } = string.Empty;
    [JsonPropertyName("encoderParams")] public EncoderParams? EncoderParams { get; set; }
}

/// <summary>ICE 服务器配置</summary>
public class IceServerConfig
{
    [JsonPropertyName("iceServers")] public List<IceServerEntry> IceServers { get; set; } = new();
    [JsonPropertyName("iceTransportPolicy")] public string IceTransportPolicy { get; set; } = "all";
    [JsonPropertyName("iceCandidatePoolSize")] public int IceCandidatePoolSize { get; set; } = 2;
}

public class IceServerEntry
{
    [JsonPropertyName("urls")] public List<string> Urls { get; set; } = new();
    [JsonPropertyName("username")] public string? Username { get; set; }
    [JsonPropertyName("credential")] public string? Credential { get; set; }
}

// ==================== IM 聊天模型 ====================

/// <summary>IM 消息</summary>
public class IMMessage
{
    public string MessageId { get; set; } = string.Empty;
    public string ConversationId { get; set; } = string.Empty;
    public string SenderId { get; set; } = string.Empty;
    public string? SenderName { get; set; }
    public string? SenderAvatar { get; set; }
    public string? ReceiverId { get; set; }
    public string Content { get; set; } = string.Empty;
    public IMMessageType MessageType { get; set; } = IMMessageType.Text;
    public long ServerSeq { get; set; }
    public DateTime SendTime { get; set; }
    public bool IsRead { get; set; }
}

/// <summary>IM 会话</summary>
public class IMConversation
{
    public string ConversationId { get; set; } = string.Empty;
    public string? Name { get; set; }
    public string? Avatar { get; set; }
    public string? LastMessage { get; set; }
    public DateTime? LastMessageTime { get; set; }
    public int UnreadCount { get; set; }
    public IMConversationType ConversationType { get; set; }
}