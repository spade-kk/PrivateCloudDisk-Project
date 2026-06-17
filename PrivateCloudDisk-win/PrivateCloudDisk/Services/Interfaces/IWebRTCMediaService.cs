// ============================================================
// IWebRTCMediaService.cs — WebRTC 媒体服务接口
// 管理摄像头/麦克风采集、屏幕共享、编码参数控制。
// ============================================================

using PrivateCloudDisk.Models;

namespace PrivateCloudDisk.Services.Interfaces;

/// <summary>WebRTC 媒体服务接口</summary>
public interface IWebRTCMediaService
{
    /// <summary>是否静音</summary>
    bool IsMuted { get; }

    /// <summary>是否关闭摄像头</summary>
    bool IsCameraOff { get; }

    /// <summary>是否正在屏幕共享</summary>
    bool IsScreenSharing { get; }

    /// <summary>当前编码参数</summary>
    EncoderParams CurrentEncoderParams { get; }

    /// <summary>初始化媒体设备</summary>
    Task<bool> InitializeAsync();

    /// <summary>开始本地媒体采集</summary>
    Task StartLocalMediaAsync(bool enableVideo = true, bool enableAudio = true);

    /// <summary>停止本地媒体采集</summary>
    void StopLocalMedia();

    /// <summary>应用编码参数</summary>
    Task ApplyVideoEncodingAsync(EncoderParams encoderParams);

    /// <summary>根据网络质量自适应调整</summary>
    Task AdaptToNetworkQualityAsync(NetworkQualitySnapshot snapshot);

    /// <summary>开始屏幕共享</summary>
    Task<bool> StartScreenShareAsync();

    /// <summary>停止屏幕共享</summary>
    void StopScreenShare();

    /// <summary>切换静音</summary>
    void ToggleMute();

    /// <summary>切换摄像头</summary>
    Task ToggleCameraAsync();

    /// <summary>切换前后摄像头</summary>
    Task SwitchCameraAsync();

    /// <summary>编码参数变更事件</summary>
    event Action<EncoderParams>? OnEncoderParamsChanged;

    /// <summary>媒体错误事件</summary>
    event Action<string>? OnMediaError;
}