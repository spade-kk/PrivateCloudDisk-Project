// ============================================================
// IMChatViewModel.cs — IM 聊天 ViewModel
// 管理 IM 聊天消息列表、会话列表、发送消息、发起通话。
// ============================================================

using System.Collections.ObjectModel;
using Microsoft.Extensions.Logging;
using PrivateCloudDisk.Helpers;
using PrivateCloudDisk.Models;
using PrivateCloudDisk.Services.Interfaces;

namespace PrivateCloudDisk.ViewModels;

public class IMChatViewModel : ObservableObject
{
    private readonly ILogger<IMChatViewModel> _logger;
    private readonly IIMWebSocketService _wsClient;
    private readonly IWebRTCSignalingService _signaling;
    private readonly IAuthService _auth;

    private ObservableCollection<IMConversation> _conversations = new();
    private ObservableCollection<IMMessage> _messages = new();
    private IMConversation? _currentConversation;
    private string _messageText = string.Empty;
    private bool _isLoading;

    public ObservableCollection<IMConversation> Conversations
    {
        get => _conversations;
        set => SetProperty(ref _conversations, value);
    }

    public ObservableCollection<IMMessage> Messages
    {
        get => _messages;
        set => SetProperty(ref _messages, value);
    }

    public IMConversation? CurrentConversation
    {
        get => _currentConversation;
        set => SetProperty(ref _currentConversation, value);
    }

    public string MessageText
    {
        get => _messageText;
        set => SetProperty(ref _messageText, value);
    }

    public bool IsLoading
    {
        get => _isLoading;
        set => SetProperty(ref _isLoading, value);
    }

    public RelayCommand SendMessageCommand { get; }
    public RelayCommand RefreshCommand { get; }

    public IMChatViewModel(
        ILogger<IMChatViewModel> logger,
        IIMWebSocketService wsClient,
        IWebRTCSignalingService signaling,
        IAuthService auth)
    {
        _logger = logger;
        _wsClient = wsClient;
        _signaling = signaling;
        _auth = auth;

        SendMessageCommand = new RelayCommand(SendMessage, CanSendMessage);
        RefreshCommand = new RelayCommand(Refresh);

        // 监听 incoming 消息
        _wsClient.OnMessage += OnMessageReceived;
    }

    private bool CanSendMessage()
    {
        return !string.IsNullOrWhiteSpace(MessageText) && CurrentConversation != null;
    }

    private async void SendMessage()
    {
        if (!CanSendMessage()) return;

        try
        {
            var payload = new
            {
                conversationId = CurrentConversation!.ConversationId,
                content = MessageText,
                messageType = 1 // text
            };

            await _wsClient.SendAsync(1001, payload, CurrentConversation.ConversationId);

            // 本地立即显示
            Messages.Add(new IMMessage
            {
                MessageId = Guid.NewGuid().ToString("N"),
                ConversationId = CurrentConversation.ConversationId,
                SenderId = _auth.CurrentUserId ?? string.Empty,
                Content = MessageText,
                MessageType = IMMessageType.Text,
                SendTime = DateTime.Now
            });

            MessageText = string.Empty;
            OnPropertyChanged(nameof(MessageText));
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "[IMChat] Send message failed");
        }
    }

    private async void Refresh()
    {
        IsLoading = true;
        try
        {
            // 从后端拉取会话列表
            await Task.CompletedTask;
        }
        finally
        {
            IsLoading = false;
        }
    }

    private void OnMessageReceived(MessageProtocol protocol)
    {
        // 处理 IM 消息（命令字 1001 = 新消息）
        if (protocol.Command == 1001)
        {
            // 解析消息并添加到对应会话
        }
    }

    public async Task StartVideoCallAsync(string calleeId, string calleeName)
    {
        await _signaling.InviteCallAsync(calleeId, calleeName, CallType.Video);
    }

    public async Task StartVoiceCallAsync(string calleeId, string calleeName)
    {
        await _signaling.InviteCallAsync(calleeId, calleeName, CallType.Voice);
    }
}