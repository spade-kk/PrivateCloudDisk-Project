//
//  MessagesViewModel.swift
//  PrivateCloudDisk-ios
//
//  消息中心 ViewModel — 管理会话、消息、通话
//

import Foundation
import SwiftUI
import Combine

@MainActor
class MessagesViewModel: ObservableObject {
    @Published var conversations: [Conversation] = []
    @Published var activeConversation: Conversation?
    @Published var messages: [ChatMessage] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var draft = ""
    @Published var isTyping = false
    @Published var peerTyping = false

    // 通话状态
    @Published var isInCall = false
    @Published var incomingCall: Bool = false
    @Published var incomingCallInfo: (callerName: String, callType: String, callId: String)?
    @Published var callDuration: String = "00:00"

    // 联系人
    @Published var friends: [Friend] = []
    @Published var friendAccount = ""

    private let imService = IMService.shared
    private let wsClient = WebSocketClient.shared
    private var callTimer: Timer?

    init() {
        setupWebSocketHandlers()
    }
    
    // MARK: - WebSocket

    private func setupWebSocketHandlers() {
        wsClient.onMessage = { [weak self] msg in
            Task { @MainActor in
                await self?.handleIncomingMessage(msg)
            }
        }
        wsClient.onCallSignal = { [weak self] msg in
            Task { @MainActor in
                await self?.handleCallSignal(msg)
            }
        }
    }

    func connectWebSocket() {
        guard let token = KeychainManager.shared.getToken() else { return }
        wsClient.connect(token: token)
    }

    // MARK: - 会话

    func loadConversations() async {
        do {
            conversations = try await imService.getConversations()
        } catch {
            errorMessage = "加载会话失败"
        }
    }

    func openConversation(_ conversation: Conversation) {
        activeConversation = conversation
        Task { await loadMessages() }
    }

    func createDirectConversation(peerId: String) async {
        do {
            let conv = try await imService.createDirectConversation(peerId: peerId)
            conversations.insert(conv, at: 0)
            openConversation(conv)
        } catch {
            errorMessage = "创建会话失败"
        }
    }

    // MARK: - 消息

    func loadMessages() async {
        guard let conv = activeConversation,
              let userId = KeychainManager.shared.getUserId() else { return }
        isLoading = true
        defer { isLoading = false }

        do {
            messages = try await imService.getHistory(conversationId: conv.id, userId: userId)
        } catch {
            errorMessage = "加载消息失败"
        }
    }

    func sendMessage() async {
        guard let conv = activeConversation, !draft.trimmingCharacters(in: .whitespaces).isEmpty else { return }

        let content = draft
        draft = ""

        // 乐观更新：添加本地消息
        let localMsg = ChatMessage(
            id: UUID().uuidString,
            conversationId: conv.id,
            senderId: "me",
            senderName: nil,
            type: .text,
            content: content,
            status: .sending,
            serverSeq: 0,
            createdAt: ISO8601DateFormatter().string(from: Date()),
            extra: nil
        )
        messages.append(localMsg)

        do {
            let sent = try await imService.sendMessage(
                conversationId: conv.id, type: "TEXT", content: content
            )
            // 更新本地消息
            if let index = messages.firstIndex(where: { $0.id == localMsg.id }) {
                messages[index] = sent
            }
        } catch {
            if let index = messages.firstIndex(where: { $0.id == localMsg.id }) {
                var failed = messages[index]
                failed = ChatMessage(
                    id: failed.id,
                    conversationId: failed.conversationId,
                    senderId: failed.senderId,
                    senderName: failed.senderName,
                    type: failed.type,
                    content: failed.content,
                    status: .failed,
                    serverSeq: failed.serverSeq,
                    createdAt: failed.createdAt,
                    extra: failed.extra
                )
                messages[index] = failed
            }
            errorMessage = "发送失败"
        }
    }

    func retryMessage(_ message: ChatMessage) async {
        guard let index = messages.firstIndex(where: { $0.id == message.id }) else { return }
        messages[index] = ChatMessage(
            id: message.id,
            conversationId: message.conversationId,
            senderId: message.senderId,
            senderName: message.senderName,
            type: message.type,
            content: message.content,
            status: .sending,
            serverSeq: message.serverSeq,
            createdAt: message.createdAt,
            extra: message.extra
        )

        do {
            let sent = try await imService.sendMessage(
                conversationId: message.conversationId, type: message.type.rawValue, content: message.content
            )
            messages[index] = sent
        } catch {
            messages[index] = ChatMessage(
                id: message.id,
                conversationId: message.conversationId,
                senderId: message.senderId,
                senderName: message.senderName,
                type: message.type,
                content: message.content,
                status: .failed,
                serverSeq: message.serverSeq,
                createdAt: message.createdAt,
                extra: message.extra
            )
        }
    }

    // MARK: - 通话

    func startCall(peerId: String, peerName: String, callType: String) async {
        let callId = UUID().uuidString
        do {
            let _ = try await imService.sendMessage(
                conversationId: activeConversation?.id ?? "",
                type: "VIDEO_CALL",
                content: "邀请你进行\(callType == "video" ? "视频" : "语音")通话",
                extra: ["call_type": callType, "call_id": callId, "call_status": "inviting"]
            )
            isInCall = true
            startCallTimer()
        } catch {
            errorMessage = "发起通话失败"
        }
    }

    func acceptCall() {
        guard let info = incomingCallInfo else { return }
        incomingCall = false
        isInCall = true
        startCallTimer()
        // 发送接受信令
        wsClient.sendCallSignal(type: "call_accept", calleeId: "", callId: info.callId)
    }

    func rejectCall() {
        guard let info = incomingCallInfo else { return }
        incomingCall = false
        incomingCallInfo = nil
        wsClient.sendCallSignal(type: "call_reject", calleeId: "", callId: info.callId)
    }

    func hangupCall() {
        isInCall = false
        stopCallTimer()
        callDuration = "00:00"
    }

    private func startCallTimer() {
        var seconds = 0
        callTimer?.invalidate()
        callTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            seconds += 1
            let m = seconds / 60
            let s = seconds % 60
            self?.callDuration = String(format: "%02d:%02d", m, s)
        }
    }

    private func stopCallTimer() {
        callTimer?.invalidate()
        callTimer = nil
    }

    // MARK: - 联系人

    func loadFriends() async {
        do {
            friends = try await imService.getFriends()
        } catch {
            errorMessage = "加载联系人失败"
        }
    }

    // MARK: - 预览数据（仅 #Preview 使用）

    func loadPreviewData() {
        conversations = Conversation.previewConversations
        friends = Friend.previewFriends
    }

    func loadPreviewMessages(conversationId: String) {
        messages = ChatMessage.previewMessages(conversationId: conversationId)
    }

    func addFriend() async {
        guard !friendAccount.isEmpty else { return }
        do {
            try await imService.addFriend(account: friendAccount)
            friendAccount = ""
            await loadFriends()
        } catch {
            errorMessage = "添加好友失败"
        }
    }

    // MARK: - WebSocket 消息处理

    private func handleIncomingMessage(_ msg: IncomingWSMessage) async {
        guard let data = msg.data else { return }

        switch msg.type {
        case "message":
            if let convId = data.conversationId {
                let chatMsg = ChatMessage(
                    id: data.messageId ?? UUID().uuidString,
                    conversationId: convId,
                    senderId: msg.senderId ?? "",
                    senderName: nil,
                    type: ChatMessage.MessageType(rawValue: data.messageType ?? "TEXT") ?? .text,
                    content: data.content ?? "",
                    status: .delivered,
                    serverSeq: data.serverSeq ?? 0,
                    createdAt: data.createdAt ?? "",
                    extra: data.extra
                )
                if activeConversation?.id == convId {
                    messages.append(chatMsg)
                }
                // 更新未读计数
                await loadConversations()
            }
        case "typing":
            peerTyping = true
            DispatchQueue.main.asyncAfter(deadline: .now() + 3) { [weak self] in
                self?.peerTyping = false
            }
        default:
            break
        }
    }

    private func handleCallSignal(_ msg: IncomingWSMessage) async {
        guard let data = msg.data else { return }

        switch msg.type {
        case "video_call":
            incomingCall = true
            incomingCallInfo = (
                callerName: data.userName ?? "未知用户",
                callType: data.callType ?? "video",
                callId: data.callId ?? UUID().uuidString
            )
        case "call_accept":
            isInCall = true
            startCallTimer()
            incomingCall = false
        case "call_reject", "call_hangup":
            incomingCall = false
            incomingCallInfo = nil
            if isInCall {
                isInCall = false
                stopCallTimer()
            }
        default:
            break
        }
    }
}
