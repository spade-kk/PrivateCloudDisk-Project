// ============================================================
// CallViewModel.cs — 视频通话 ViewModel
// 管理视频/语音通话的 UI 状态，绑定到 CallPage 视图。
// 遵循现有项目 MVVM 模式：继承 ObservableObject，使用 RelayCommand。
// ============================================================

using System.Collections.ObjectModel;
using Microsoft.Extensions.Logging;
using PrivateCloudDisk.Helpers;
using PrivateCloudDisk.Models;
using PrivateCloudDisk.Services.Interfaces;
using Microsoft.UI.Dispatching;

namespace PrivateCloudDisk.ViewModels;

public class CallViewModel : ObservableObject, IDisposable
{
    private readonly ILogger<CallViewModel> _logger;
    private readonly IWebRTCSignalingService _signaling;
    private readonly IWebRTCMediaService _mediaService;
    private readonly IAdaptiveEncoderService _encoderService;
    private readonly DispatcherQueue _dispatcher;

    private CallSession? _session;
    private bool _isMuted;
    private bool _isCameraOff;
    private bool _isScreenSharing;
    private string _callDurationText = "00:00";
    private string _remoteName = string.Empty;
    private string _remoteAvatar = string.Empty;
    private EncoderParams? _encoderParams;
    private NetworkQuality _networkQuality = NetworkQuality.Excellent;
    private string _networkQualityLabel = "网络优秀";
    private string? _errorMessage;
    private bool _isInCall;
    private bool _hasIncomingCall;
    private CallSession? _incomingCall;

    private DispatcherQueueTimer? _durationTimer;
    private DispatcherQueueTimer? _qualityTimer;

    // ── 属性 ────────────────────────────────────────────

    public CallSession? Session { get => _session; set => SetProperty(ref _session, value); }
    public bool IsMuted { get => _isMuted; set => SetProperty(ref _isMuted, value); }
    public bool IsCameraOff { get => _isCameraOff; set => SetProperty(ref _isCameraOff, value); }
    public bool IsScreenSharing { get => _isScreenSharing; set => SetProperty(ref _isScreenSharing, value); }
    public string CallDurationText { get => _callDurationText; set => SetProperty(ref _callDurationText, value); }
    public string RemoteName { get => _remoteName; set => SetProperty(ref _remoteName, value); }
    public string RemoteAvatar { get => _remoteAvatar; set => SetProperty(ref _remoteAvatar, value); }
    public EncoderParams? EncoderParams { get => _encoderParams; set => SetProperty(ref _encoderParams, value); }
    public NetworkQuality NetworkQuality { get => _networkQuality; set { SetProperty(ref _networkQuality, value); UpdateQualityLabel(); } }
    public string NetworkQualityLabel { get => _networkQualityLabel; set => SetProperty(ref _networkQualityLabel, value); }
    public string? ErrorMessage { get => _errorMessage; set => SetProperty(ref _errorMessage, value); }
    public bool IsInCall { get => _isInCall; set => SetProperty(ref _isInCall, value); }
    public bool HasIncomingCall { get => _hasIncomingCall; set => SetProperty(ref _hasIncomingCall, value); }
    public CallSession? IncomingCall { get => _incomingCall; set => SetProperty(ref _incomingCall, value); }

    // ── 命令 ────────────────────────────────────────────

    public RelayCommand AcceptCallCommand { get; }
    public RelayCommand RejectCallCommand { get; }
    public RelayCommand HangupCommand { get; }
    public RelayCommand ToggleMuteCommand { get; }
    public RelayCommand ToggleCameraCommand { get; }
    public RelayCommand ToggleScreenShareCommand { get; }

    public CallViewModel(
        ILogger<CallViewModel> logger,
        IWebRTCSignalingService signaling,
        IWebRTCMediaService mediaService,
        IAdaptiveEncoderService encoderService)
    {
        _logger = logger;
        _signaling = signaling;
        _mediaService = mediaService;
        _encoderService = encoderService;
        _dispatcher = DispatcherQueue.GetForCurrentThread();

        AcceptCallCommand = new RelayCommand(AcceptCall);
        RejectCallCommand = new RelayCommand(RejectCall);
        HangupCommand = new RelayCommand(Hangup);
        ToggleMuteCommand = new RelayCommand(ToggleMute);
        ToggleCameraCommand = new RelayCommand(ToggleCamera);
        ToggleScreenShareCommand = new RelayCommand(ToggleScreenShare);

        BindSignalingEvents();
    }

    private void BindSignalingEvents()
    {
        _signaling.OnCallInvite += session =>
        {
            _dispatcher.TryEnqueue(() =>
            {
                IncomingCall = session;
                HasIncomingCall = true;
                IsInCall = false;
            });
        };

        _signaling.OnCallAccepted += session =>
        {
            _dispatcher.TryEnqueue(async () =>
            {
                HasIncomingCall = false;
                IsInCall = true;
                Session = session;
                RemoteName = session.CallerName ?? session.CalleeName ?? "未知";
                RemoteAvatar = session.CallerAvatar ?? session.CalleeAvatar ?? string.Empty;

                await InitializeCallAsync(session);
                StartDurationTimer();
                StartQualityTimer();
            });
        };

        _signaling.OnCallEnded += _ =>
        {
            _dispatcher.TryEnqueue(() =>
            {
                StopTimers();
                _mediaService.StopLocalMedia();
                IsInCall = false;
                HasIncomingCall = false;
                IncomingCall = null;
                Session = null;
            });
        };

        _signaling.OnCallRejected += _ =>
        {
            _dispatcher.TryEnqueue(() =>
            {
                HasIncomingCall = false;
                IncomingCall = null;
            });
        };

        _signaling.OnEncoderAdjust += parameters =>
        {
            _dispatcher.TryEnqueue(async () =>
            {
                EncoderParams = parameters;
                await _mediaService.ApplyVideoEncodingAsync(parameters);
            });
        };

        _signaling.OnSuggestDowngrade += () =>
        {
            _dispatcher.TryEnqueue(() =>
            {
                ErrorMessage = "当前网络较差，建议切换为语音通话";
            });
        };
    }

    // ── 通话初始化 ──────────────────────────────────────

    public async Task InitializeCallAsync(CallSession session)
    {
        await _mediaService.InitializeAsync();
        await _mediaService.StartLocalMediaAsync(
            enableVideo: session.CallType == CallType.Video && !_isCameraOff,
            enableAudio: !_isMuted);
    }

    // ── 通话控制 ────────────────────────────────────────

    private async void AcceptCall()
    {
        await _signaling.AcceptCallAsync();
    }

    private async void RejectCall()
    {
        await _signaling.RejectCallAsync();
        HasIncomingCall = false;
        IncomingCall = null;
    }

    private async void Hangup()
    {
        await _signaling.HangupCallAsync();
        StopTimers();
        _mediaService.StopLocalMedia();
        IsInCall = false;
    }

    private void ToggleMute()
    {
        _mediaService.ToggleMute();
        IsMuted = _mediaService.IsMuted;
    }

    private async void ToggleCamera()
    {
        await _mediaService.ToggleCameraAsync();
        IsCameraOff = _mediaService.IsCameraOff;
    }

    private async void ToggleScreenShare()
    {
        if (IsScreenSharing)
        {
            _mediaService.StopScreenShare();
        }
        else
        {
            await _mediaService.StartScreenShareAsync();
        }
        IsScreenSharing = _mediaService.IsScreenSharing;
    }

    // ── 网络质量监控 ────────────────────────────────────

    private void StartQualityTimer()
    {
        _qualityTimer = _dispatcher.CreateTimer();
        _qualityTimer.Interval = TimeSpan.FromSeconds(2);
        _qualityTimer.Tick += async (_, _) =>
        {
            // 模拟网络质量检测（实际应从 WebRTC stats API 获取）
            var rng = new Random();
            var snapshot = new NetworkQualitySnapshot
            {
                Rtt = 30 + rng.NextDouble() * 50,
                PacketLoss = rng.NextDouble() * 3,
                Jitter = 5 + rng.NextDouble() * 15,
                EstimatedBandwidth = 2000 + rng.NextDouble() * 2000,
                IsScreenShare = false,
                Timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
            };

            // 计算质量等级
            if (snapshot.Rtt <= 50 && snapshot.PacketLoss <= 0.5)
                snapshot.QualityLevel = 0;
            else if (snapshot.Rtt <= 100 && snapshot.PacketLoss <= 2.0)
                snapshot.QualityLevel = 1;
            else if (snapshot.Rtt <= 200 && snapshot.PacketLoss <= 5.0)
                snapshot.QualityLevel = 2;
            else if (snapshot.Rtt <= 400 && snapshot.PacketLoss <= 10.0)
                snapshot.QualityLevel = 3;
            else
                snapshot.QualityLevel = 4;

            NetworkQuality = (NetworkQuality)snapshot.QualityLevel;
            await _mediaService.AdaptToNetworkQualityAsync(snapshot);
            await _signaling.ReportQualityAsync(snapshot);
        };
        _qualityTimer.Start();
    }

    // ── 计时器 ──────────────────────────────────────────

    private void StartDurationTimer()
    {
        _durationTimer = _dispatcher.CreateTimer();
        _durationTimer.Interval = TimeSpan.FromSeconds(1);
        _durationTimer.Tick += (_, _) =>
        {
            if (Session?.StartTime != null)
            {
                var elapsed = DateTime.UtcNow - Session.StartTime.Value;
                CallDurationText = $"{(int)elapsed.TotalMinutes:D2}:{elapsed.Seconds:D2}";
            }
        };
        _durationTimer.Start();
    }

    private void StopTimers()
    {
        _durationTimer?.Stop();
        _durationTimer = null;
        _qualityTimer?.Stop();
        _qualityTimer = null;
    }

    private void UpdateQualityLabel()
    {
        NetworkQualityLabel = NetworkQuality switch
        {
            Models.NetworkQuality.Excellent => "网络优秀",
            Models.NetworkQuality.Good => "网络良好",
            Models.NetworkQuality.Fair => "网络一般",
            Models.NetworkQuality.Poor => "网络较差",
            Models.NetworkQuality.VeryPoor => "网络极差",
            _ => ""
        };
    }

    public void Dispose()
    {
        StopTimers();
    }
}