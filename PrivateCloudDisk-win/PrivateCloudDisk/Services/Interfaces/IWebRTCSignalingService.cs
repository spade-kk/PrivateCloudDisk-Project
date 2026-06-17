// ============================================================
// IWebRTCSignalingService.cs — WebRTC 信令服务接口
// 通过 IM WebSocket 通道与后端信令服务器交换 SDP/ICE 消息。
// 后端对应：org.project.im.server.signaling.handler.SignalingHandler
// ============================================================

using PrivateCloudDisk.Models;

namespace PrivateCloudDisk.Services.Interfaces;

/// <summary>WebRTC 信令服务接口</summary>
public interface IWebRTCSignalingService
{
    /// <summary>当前通话会话</summary>
    CallSession? CurrentSession { get; }

    /// <summary>发起通话邀请</summary>
    Task InviteCallAsync(string calleeId, string calleeName, CallType callType);

    /// <summary>接受来电</summary>
    Task AcceptCallAsync();

    /// <summary>拒绝来电</summary>
    Task RejectCallAsync(string reason = "用户拒绝");

    /// <summary>挂断通话</summary>
    Task HangupCallAsync();

    /// <summary>发送 Offer SDP</summary>
    Task SendOfferAsync(string sdp);

    /// <summary>发送 Answer SDP</summary>
    Task SendAnswerAsync(string sdp);

    /// <summary>发送 ICE Candidate</summary>
    Task SendIceCandidateAsync(string candidate, string sdpMid, int sdpMLineIndex);

    /// <summary>上报网络质量</summary>
    Task ReportQualityAsync(NetworkQualitySnapshot snapshot);

    /// <summary>获取 ICE 服务器配置</summary>
    Task<IceServerConfig?> FetchIceServerConfigAsync();

    /// <summary>来电事件</summary>
    event Action<CallSession>? OnCallInvite;

    /// <summary>通话被接听事件</summary>
    event Action<CallSession>? OnCallAccepted;

    /// <summary>通话被拒绝事件</summary>
    event Action<string>? OnCallRejected;

    /// <summary>通话结束事件</summary>
    event Action<string>? OnCallEnded;

    /// <summary>收到 Offer SDP</summary>
    event Action<SdpPayload>? OnOfferReceived;

    /// <summary>收到 Answer SDP</summary>
    event Action<SdpPayload>? OnAnswerReceived;

    /// <summary>收到 ICE Candidate</summary>
    event Action<IceCandidatePayload>? OnIceCandidateReceived;

    /// <summary>编码参数调整</summary>
    event Action<EncoderParams>? OnEncoderAdjust;

    /// <summary>建议降级语音</summary>
    event Action? OnSuggestDowngrade;
}