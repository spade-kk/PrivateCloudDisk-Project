// ============================================================
// IIMWebSocketService.cs — IM WebSocket 服务接口
// 管理与后端 Netty IM 服务器的 WebSocket 长连接。
// 后端对应：org.project.im.server.netty.handler.MessageHandler
// ============================================================

using PrivateCloudDisk.Models;

namespace PrivateCloudDisk.Services.Interfaces;

/// <summary>IM WebSocket 服务接口</summary>
public interface IIMWebSocketService
{
    /// <summary>是否已连接</summary>
    bool IsConnected { get; }

    /// <summary>建立连接</summary>
    Task ConnectAsync();

    /// <summary>断开连接</summary>
    Task DisconnectAsync();

    /// <summary>发送消息（仅发送，不等待响应）</summary>
    Task SendAsync(int command, object? payload = null, string? receiverId = null);

    /// <summary>发送请求并等待响应</summary>
    Task<MessageProtocol> SendRequestAsync(int command, object? payload = null, int timeoutMs = 10000);

    /// <summary>发送 WebRTC 信令消息</summary>
    Task SendSignalingAsync(int command, object payload);

    /// <summary>注册命令字处理器</summary>
    void RegisterCommandHandler(int command, Action<MessageProtocol> handler);

    /// <summary>注销命令字处理器</summary>
    void UnregisterCommandHandler(int command, Action<MessageProtocol> handler);

    /// <summary>连接状态变更事件</summary>
    event Action<bool>? OnConnectionStateChanged;

    /// <summary>全局消息事件</summary>
    event Action<MessageProtocol>? OnMessage;

    /// <summary>错误事件</summary>
    event Action<string>? OnError;
}