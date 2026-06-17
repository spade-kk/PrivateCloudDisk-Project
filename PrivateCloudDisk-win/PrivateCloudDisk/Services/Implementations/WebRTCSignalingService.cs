// ============================================================
// WebRTCSignalingService.cs — WebRTC 信令服务实现
// 通过 IM WebSocket 通道与后端信令服务器交换 SDP/ICE 消息，
// 管理 PeerConnection 的 Offer/Answer 协商和 ICE 候选交换。
// 
// 后端对应：
//   org.project.im.server.signaling.handler.SignalingHandler
// 
// 命令字映射（与后端 CommandType 枚举一致）：
//   2001 — 发起通话邀请
//   2002 — 接受通话
//   2003 — 拒绝通话
//   2004 — 挂断通话
//   2101 — 发送 Offer SDP
//   2102 — 发送 Answer SDP
//   2103 — 发送 ICE Candidate
//   2201 — 网络质量上报
//   2202 — 编码参数调整指令
//   2203 — 建议降级语音
//   2301 — 获取 ICE 服务器配置
// ============================================================

using System.Text.Json;
using Microsoft.Extensions.Logging;
using PrivateCloudDisk.Models;
using PrivateCloudDisk.Services.Interfaces;

namespace PrivateCloudDisk.Services.Implementations;

public class WebRTCSignalingService : IWebRTCSignalingService
{
    private readonly ILogger<WebRTCSignalingService> _logger;
    private readonly IIMWebSocketService _wsClient;
    private readonly IAuthService _authService;

    private CallSession? _currentSession;
    public CallSession? CurrentSession => _currentSession;

    public event Action<CallSession>? OnCallInvite;
    public event Action<CallSession>? OnCallAccepted;
    public event Action<string>? OnCallRejected;
    public event Action<string>? OnCallEnded;
    public event Action<SdpPayload>? OnOfferReceived;
    public event Action<SdpPayload>? OnAnswerReceived;
    public event Action<IceCandidatePayload>? OnIceCandidateReceived;
    public event Action<EncoderParams>? OnEncoderAdjust;
    public event Action? OnSuggestDowngrade;

    public WebRTCSignalingService(
        ILogger<WebRTCSignalingService> logger,
        IIMWebSocketService wsClient,
        IAuthService authService)
    {
        _logger = logger;
        _wsClient = wsClient;
        _authService = authService;
        RegisterHandlers();
    }

    private void RegisterHandlers()
    {
        // 来电邀请
        _wsClient.RegisterCommandHandler(2001, protocol =>
        {
            var payload = DeserializePayload<CallInvitePayload>(protocol);
            if (payload == null) return;

            var session = new CallSession
            {
                CallId = payload.CallId,
                CallType = (CallType)payload.CallType,
                CallMode = CallMode.P2P,
                CallerId = payload.CallerId,
                CallerName = payload.CallerName,
                CallerAvatar = payload.CallerAvatar,
                CalleeId = _authService.CurrentUserId,
                Status = CallStatus.Ringing,
                VideoEnabled = payload.CallType == (int)CallType.Video
            };

            _currentSession = session;
            _logger.LogInformation("[Signaling] Incoming call: {CallId} from {CallerName}", session.CallId, session.CallerName);
            OnCallInvite?.Invoke(session);
        });

        // 通话被接听
        _wsClient.RegisterCommandHandler(2002, protocol =>
        {
            _logger.LogInformation("[Signaling] Call accepted");
            if (_currentSession != null)
            {
                _currentSession.Status = CallStatus.Active;
                _currentSession.StartTime = DateTime.UtcNow;
                OnCallAccepted?.Invoke(_currentSession);
            }
        });

        // 通话被拒绝
        _wsClient.RegisterCommandHandler(2003, protocol =>
        {
            var payload = DeserializePayload<Dictionary<string, JsonElement>>(protocol);
            var reason = "对方拒绝";
            if (payload?.TryGetValue("reason", out var r) == true)
                reason = r.GetString() ?? reason;

            _logger.LogInformation("[Signaling] Call rejected: {Reason}", reason);
            OnCallRejected?.Invoke(reason);
            _currentSession = null;
        });

        // 挂断
        _wsClient.RegisterCommandHandler(2004, protocol =>
        {
            var payload = DeserializePayload<Dictionary<string, JsonElement>>(protocol);
            var reason = "通话结束";
            if (payload?.TryGetValue("reason", out var r) == true)
                reason = r.GetString() ?? reason;

            _logger.LogInformation("[Signaling] Call ended: {Reason}", reason);
            if (_currentSession != null)
            {
                _currentSession.Status = CallStatus.Ended;
                _currentSession.EndTime = DateTime.UtcNow;
            }
            OnCallEnded?.Invoke(reason);
            _currentSession = null;
        });

        // Offer SDP
        _wsClient.RegisterCommandHandler(2101, protocol =>
        {
            var payload = DeserializePayload<SdpPayload>(protocol);
            if (payload != null)
            {
                _logger.LogInformation("[Signaling] Offer received for call {CallId}", payload.CallId);
                OnOfferReceived?.Invoke(payload);
            }
        });

        // Answer SDP
        _wsClient.RegisterCommandHandler(2102, protocol =>
        {
            var payload = DeserializePayload<SdpPayload>(protocol);
            if (payload != null)
            {
                _logger.LogInformation("[Signaling] Answer received for call {CallId}", payload.CallId);
                OnAnswerReceived?.Invoke(payload);
            }
        });

        // ICE Candidate
        _wsClient.RegisterCommandHandler(2103, protocol =>
        {
            var payload = DeserializePayload<IceCandidatePayload>(protocol);
            if (payload != null)
            {
                OnIceCandidateReceived?.Invoke(payload);
            }
        });

        // 编码参数调整
        _wsClient.RegisterCommandHandler(2202, protocol =>
        {
            var payload = DeserializePayload<EncoderAdjustPayload>(protocol);
            if (payload?.EncoderParams != null)
            {
                _logger.LogInformation("[Signaling] Encoder adjustment: {Desc}", payload.EncoderParams.Description);
                OnEncoderAdjust?.Invoke(payload.EncoderParams);
            }
        });

        // 建议降级语音
        _wsClient.RegisterCommandHandler(2203, protocol =>
        {
            _logger.LogInformation("[Signaling] Server suggests downgrade to voice");
            OnSuggestDowngrade?.Invoke();
        });
    }

    // ── 通话控制 ────────────────────────────────────────────

    public async Task InviteCallAsync(string calleeId, string calleeName, CallType callType)
    {
        var payload = new
        {
            calleeId,
            calleeName,
            callType = (int)callType,
            timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
        };

        await _wsClient.SendSignalingAsync(2001, payload);
        _logger.LogInformation("[Signaling] Call invite sent to {CalleeName}", calleeName);
    }

    public async Task AcceptCallAsync()
    {
        if (_currentSession == null) return;
        var payload = new { callId = _currentSession.CallId };
        await _wsClient.SendSignalingAsync(2002, payload);
        _logger.LogInformation("[Signaling] Call accepted");
    }

    public async Task RejectCallAsync(string reason = "用户拒绝")
    {
        if (_currentSession == null) return;
        var payload = new
        {
            callId = _currentSession.CallId,
            reason
        };
        await _wsClient.SendSignalingAsync(2003, payload);
        _logger.LogInformation("[Signaling] Call rejected: {Reason}", reason);
        _currentSession = null;
    }

    public async Task HangupCallAsync()
    {
        if (_currentSession == null) return;
        var payload = new
        {
            callId = _currentSession.CallId,
            reason = "用户挂断",
            duration = _currentSession.StartTime.HasValue
                ? (long)(DateTime.UtcNow - _currentSession.StartTime.Value).TotalSeconds
                : 0
        };
        await _wsClient.SendSignalingAsync(2004, payload);
        _logger.LogInformation("[Signaling] Call hung up");
        _currentSession = null;
    }

    // ── SDP 交换 ────────────────────────────────────────────

    public async Task SendOfferAsync(string sdp)
    {
        if (_currentSession == null) return;
        var payload = new
        {
            callId = _currentSession.CallId,
            sdp = new
            {
                type = "offer",
                sdp
            }
        };
        await _wsClient.SendSignalingAsync(2101, payload);
    }

    public async Task SendAnswerAsync(string sdp)
    {
        if (_currentSession == null) return;
        var payload = new
        {
            callId = _currentSession.CallId,
            sdp = new
            {
                type = "answer",
                sdp
            }
        };
        await _wsClient.SendSignalingAsync(2102, payload);
    }

    // ── ICE Candidate ───────────────────────────────────────

    public async Task SendIceCandidateAsync(string candidate, string sdpMid, int sdpMLineIndex)
    {
        if (_currentSession == null) return;
        var payload = new
        {
            callId = _currentSession.CallId,
            candidate = new
            {
                candidate,
                sdpMid,
                sdpMLineIndex
            }
        };
        await _wsClient.SendSignalingAsync(2103, payload);
    }

    // ── 网络质量 ────────────────────────────────────────────

    public async Task ReportQualityAsync(NetworkQualitySnapshot snapshot)
    {
        if (_currentSession == null) return;
        var payload = new QualityReportPayload
        {
            CallId = _currentSession.CallId,
            Rtt = snapshot.Rtt,
            PacketLoss = snapshot.PacketLoss,
            Jitter = snapshot.Jitter,
            EstimatedBandwidth = snapshot.EstimatedBandwidth,
            IsScreenShare = snapshot.IsScreenShare,
            QualityLevel = snapshot.QualityLevel
        };
        await _wsClient.SendSignalingAsync(2201, payload);
    }

    public async Task<IceServerConfig?> FetchIceServerConfigAsync()
    {
        try
        {
            var response = await _wsClient.SendRequestAsync(2301);
            if (response.Payload is JsonElement json)
            {
                return JsonSerializer.Deserialize<IceServerConfig>(json.GetRawText());
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "[Signaling] Failed to fetch ICE server config");
        }
        return null;
    }

    // ── 辅助方法 ────────────────────────────────────────────

    private static T? DeserializePayload<T>(MessageProtocol protocol)
    {
        try
        {
            if (protocol.Payload is JsonElement json)
                return JsonSerializer.Deserialize<T>(json.GetRawText());
            return default;
        }
        catch { return default; }
    }
}