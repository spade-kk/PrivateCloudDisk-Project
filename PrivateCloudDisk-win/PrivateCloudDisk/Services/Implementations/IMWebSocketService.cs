// ============================================================
// IMWebSocketService.cs — IM WebSocket 服务实现
// 管理与后端 Netty IM 服务器的 WebSocket 长连接。
// 负责消息收发、心跳维持、自动重连。
// 
// 后端对应：
//   org.project.im.server.netty.handler.MessageHandler
//   org.project.im.server.netty.websocket.WebSocketServer
// 
// 协议：私有二进制协议 MessageProtocol
//   Version(4B) | Command(4B) | Seq(4B) | Timestamp(8B)
//   | SenderLen(2B) | SenderId | ReceiverLen(2B) | ReceiverId
//   | PayloadLen(4B) | Payload(JSON)
// ============================================================

using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using Microsoft.Extensions.Logging;
using PrivateCloudDisk.Models;
using PrivateCloudDisk.Services.Interfaces;

namespace PrivateCloudDisk.Services.Implementations;

public class IMWebSocketService : IIMWebSocketService
{
    private const string DefaultServerUrl = "ws://localhost:8080/ws";
    private const int HeartbeatIntervalMs = 20000;
    private const int MaxReconnectAttempts = 5;
    private const int ReconnectBaseDelayMs = 2000;
    private const int ReceiveBufferSize = 8192;

    private readonly ILogger<IMWebSocketService> _logger;
    private readonly IAuthService _authService;
    private readonly string _serverUrl;

    private ClientWebSocket? _ws;
    private CancellationTokenSource? _cts;
    private Task? _receiveLoop;
    private Task? _heartbeatLoop;
    private int _reconnectAttempts;
    private DateTime _lastHeartbeatReceived = DateTime.UtcNow;
    private readonly Dictionary<int, List<Action<MessageProtocol>>> _handlers = new();
    private readonly Dictionary<int, TaskCompletionSource<MessageProtocol>> _pendingRequests = new();
    private readonly object _lock = new();

    private bool _isConnected;
    public bool IsConnected
    {
        get => _isConnected;
        private set
        {
            if (_isConnected == value) return;
            _isConnected = value;
            OnConnectionStateChanged?.Invoke(value);
        }
    }

    public event Action<bool>? OnConnectionStateChanged;
    public event Action<MessageProtocol>? OnMessage;
    public event Action<string>? OnError;

    public IMWebSocketService(ILogger<IMWebSocketService> logger, IAuthService authService)
    {
        _logger = logger;
        _authService = authService;
        _serverUrl = AppConfig.PlatformBaseUrl.Replace("http://", "ws://").Replace("https://", "wss://") + "/ws";
    }

    // ── 连接管理 ────────────────────────────────────────────

    public async Task ConnectAsync()
    {
        if (_isConnected) return;

        try
        {
            _cts = new CancellationTokenSource();
            _ws = new ClientWebSocket();
            _ws.Options.KeepAliveInterval = TimeSpan.FromSeconds(30);

            var token = _authService.CurrentToken;
            if (string.IsNullOrEmpty(token))
            {
                _logger.LogWarning("[IM] No auth token, skipping connect");
                return;
            }

            var uri = new Uri($"{_serverUrl}?token={Uri.EscapeDataString(token)}");
            await _ws.ConnectAsync(uri, _cts.Token);
            IsConnected = true;
            _reconnectAttempts = 0;
            _lastHeartbeatReceived = DateTime.UtcNow;

            _logger.LogInformation("[IM] WebSocket connected to {Url}", _serverUrl);

            _receiveLoop = Task.Run(() => ReceiveLoopAsync(_cts.Token), _cts.Token);
            _ = Task.Run(() => HeartbeatLoopAsync(_cts.Token), _cts.Token);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "[IM] WebSocket connection failed");
            OnError?.Invoke($"连接失败: {ex.Message}");
            await ScheduleReconnectAsync();
        }
    }

    public async Task DisconnectAsync()
    {
        _reconnectAttempts = MaxReconnectAttempts; // 阻止重连
        _cts?.Cancel();

        if (_ws?.State == WebSocketState.Open)
        {
            try
            {
                await _ws.CloseAsync(WebSocketCloseStatus.NormalClosure, "Client closing", CancellationToken.None);
            }
            catch { }
        }

        _ws?.Dispose();
        _ws = null;
        IsConnected = false;
        _logger.LogInformation("[IM] WebSocket disconnected");
    }

    // ── 消息发送 ────────────────────────────────────────────

    public Task SendAsync(int command, object? payload = null, string? receiverId = null)
    {
        return SendInternalAsync(command, null, payload, receiverId);
    }

    public async Task<MessageProtocol> SendRequestAsync(int command, object? payload = null, int timeoutMs = 10000)
    {
        var seq = Environment.TickCount; // 简单序列号
        var tcs = new TaskCompletionSource<MessageProtocol>();

        lock (_lock)
        {
            _pendingRequests[seq] = tcs;
        }

        try
        {
            await SendInternalAsync(command, seq, payload);
            using var cts = new CancellationTokenSource(timeoutMs);
            cts.Token.Register(() => tcs.TrySetCanceled());
            return await tcs.Task;
        }
        finally
        {
            lock (_lock)
            {
                _pendingRequests.Remove(seq);
            }
        }
    }

    public Task SendSignalingAsync(int command, object payload)
    {
        return SendInternalAsync(command, null, payload);
    }

    private async Task SendInternalAsync(int command, int? seq, object? payload, string? receiverId = null)
    {
        if (_ws?.State != WebSocketState.Open)
        {
            _logger.LogWarning("[IM] Cannot send, not connected");
            return;
        }

        var protocol = new MessageProtocol
        {
            Version = 1,
            Command = command,
            Seq = seq,
            Timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
            SenderId = _authService.CurrentUserId,
            ReceiverId = receiverId,
            Payload = payload
        };

        var json = JsonSerializer.Serialize(protocol);
        var bytes = Encoding.UTF8.GetBytes(json);

        await _ws.SendAsync(
            new ArraySegment<byte>(bytes),
            WebSocketMessageType.Text,
            endOfMessage: true,
            _cts?.Token ?? CancellationToken.None);
    }

    // ── 消息接收 ────────────────────────────────────────────

    private async Task ReceiveLoopAsync(CancellationToken ct)
    {
        var buffer = new byte[ReceiveBufferSize];

        while (!ct.IsCancellationRequested && _ws?.State == WebSocketState.Open)
        {
            try
            {
                var result = await _ws.ReceiveAsync(new ArraySegment<byte>(buffer), ct);

                if (result.MessageType == WebSocketMessageType.Close)
                {
                    _logger.LogInformation("[IM] Server closed connection");
                    await HandleDisconnectAsync();
                    return;
                }

                if (result.MessageType == WebSocketMessageType.Text)
                {
                    var json = Encoding.UTF8.GetString(buffer, 0, result.Count);
                    await ProcessMessageAsync(json);
                }
            }
            catch (OperationCanceledException) { return; }
            catch (WebSocketException ex)
            {
                _logger.LogError(ex, "[IM] Receive error");
                await HandleDisconnectAsync();
                return;
            }
        }
    }

    private async Task ProcessMessageAsync(string json)
    {
        try
        {
            var protocol = JsonSerializer.Deserialize<MessageProtocol>(json);
            if (protocol == null) return;

            _lastHeartbeatReceived = DateTime.UtcNow;

            // 心跳响应
            if (protocol.Command == 0)
                return;

            // 命令字路由
            if (protocol.Command == 1)
            {
                // 心跳包
                await SendAsync(1); // 回复心跳
                return;
            }

            // 已注册的处理器
            lock (_lock)
            {
                if (_handlers.TryGetValue(protocol.Command, out var handlers))
                {
                    foreach (var handler in handlers)
                    {
                        _ = Task.Run(() => handler(protocol));
                    }
                }
            }

            // 待处理请求
            if (protocol.Seq.HasValue)
            {
                lock (_lock)
                {
                    if (_pendingRequests.TryGetValue(protocol.Seq.Value, out var tcs))
                    {
                        tcs.TrySetResult(protocol);
                        return;
                    }
                }
            }

            // 全局消息事件
            OnMessage?.Invoke(protocol);
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "[IM] Failed to process message");
        }
    }

    // ── 心跳 ──────────────────────────────────────────────────

    private async Task HeartbeatLoopAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested && _ws?.State == WebSocketState.Open)
        {
            try
            {
                await Task.Delay(HeartbeatIntervalMs, ct);

                // 检查心跳超时
                if (DateTime.UtcNow - _lastHeartbeatReceived > TimeSpan.FromSeconds(60))
                {
                    _logger.LogWarning("[IM] Heartbeat timeout");
                    await HandleDisconnectAsync();
                    return;
                }

                await SendAsync(1); // 发送心跳
            }
            catch (OperationCanceledException) { return; }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "[IM] Heartbeat error");
            }
        }
    }

    // ── 重连 ──────────────────────────────────────────────────

    private async Task HandleDisconnectAsync()
    {
        IsConnected = false;
        _ws?.Dispose();
        _ws = null;
        await ScheduleReconnectAsync();
    }

    private async Task ScheduleReconnectAsync()
    {
        while (_reconnectAttempts < MaxReconnectAttempts)
        {
            _reconnectAttempts++;
            var delay = ReconnectBaseDelayMs * (int)Math.Pow(2, _reconnectAttempts - 1);
            _logger.LogInformation("[IM] Reconnecting in {Delay}ms (attempt {Attempt}/{Max})",
                delay, _reconnectAttempts, MaxReconnectAttempts);

            await Task.Delay(delay);

            try
            {
                await ConnectAsync();
                if (IsConnected) return;
            }
            catch { }
        }

        _logger.LogError("[IM] Max reconnect attempts reached");
        OnError?.Invoke("连接失败，已达最大重试次数");
    }

    // ── 命令字处理器 ──────────────────────────────────────────

    public void RegisterCommandHandler(int command, Action<MessageProtocol> handler)
    {
        lock (_lock)
        {
            if (!_handlers.ContainsKey(command))
                _handlers[command] = new List<Action<MessageProtocol>>();
            _handlers[command].Add(handler);
        }
    }

    public void UnregisterCommandHandler(int command, Action<MessageProtocol> handler)
    {
        lock (_lock)
        {
            if (_handlers.TryGetValue(command, out var handlers))
                handlers.Remove(handler);
        }
    }
}