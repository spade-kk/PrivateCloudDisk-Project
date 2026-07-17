import Foundation
import Combine

// MARK: - 即时通讯服务

/// IM 即时通讯服务
///
/// 支持：
/// - WebSocket 实时连接
/// - 消息收发
/// - 已读回执
/// - 在线状态
/// - 消息历史
/// - 文件分享到聊天
final class IMService: ObservableObject {

    static let shared = IMService()

    // MARK: - 发布属性

    @Published var messages: [IMMessage] = []
    @Published var conversations: [IMConversation] = []
    @Published var isConnected = false
    @Published var onlineUsers: [String] = []

    private var webSocketTask: URLSessionWebSocketTask?
    private var session: URLSession!
    private var pingTimer: Timer?
    private var reconnectAttempts = 0
    private let maxReconnectAttempts = 10
    private let reconnectDelay: TimeInterval = 3.0

    private init() {
        let config = URLSessionConfiguration.default
        session = URLSession(configuration: config, delegate: CertificateValidator.shared, delegateQueue: nil)
    }

    // MARK: - 连接管理

    /// 连接 WebSocket
    func connect() {
        guard let token = KeychainManager.shared.readAuthToken() else { return }

        let baseURL = UserDefaults.standard.string(forKey: "api_base_url") ?? "http://localhost:8000"
        let wsURL = baseURL
            .replacingOccurrences(of: "http://", with: "ws://")
            .replacingOccurrences(of: "https://", with: "wss://")
        let url = URL(string: "\(wsURL)/ws/im?token=\(token)")!

        webSocketTask = session.webSocketTask(with: url)
        webSocketTask?.resume()
        isConnected = true
        reconnectAttempts = 0

        startPing()
        receiveMessage()
    }

    /// 断开连接
    func disconnect() {
        webSocketTask?.cancel(with: .goingAway, reason: nil)
        webSocketTask = nil
        isConnected = false
        stopPing()
    }

    // MARK: - 消息收发

    /// 发送文本消息
    func sendTextMessage(to userId: String, content: String) async throws -> IMMessage {
        let message = IMMessage(
            id: UUID().uuidString,
            from: KeychainManager.shared.readUserId() ?? "",
            to: userId,
            content: content,
            type: .text,
            timestamp: Date(),
            status: .sending
        )

        // 通过 WebSocket 发送
        let payload: [String: Any] = [
            "type": "message",
            "to": userId,
            "content": content,
            "message_type": "text",
            "message_id": message.id
        ]
        let jsonData = try JSONSerialization.data(withJSONObject: payload)
        let wsMessage = URLSessionWebSocketTask.Message.data(jsonData)

        try await webSocketTask?.send(wsMessage)

        var updatedMessage = message
        updatedMessage.status = .sent
        return updatedMessage
    }

    /// 发送文件消息
    func sendFileMessage(to userId: String, nodeId: String, filename: String) async throws -> IMMessage {
        let message = IMMessage(
            id: UUID().uuidString,
            from: KeychainManager.shared.readUserId() ?? "",
            to: userId,
            content: "文件: \(filename)",
            type: .file,
            timestamp: Date(),
            status: .sending,
            fileNodeId: nodeId,
            fileName: filename
        )

        let payload: [String: Any] = [
            "type": "message",
            "to": userId,
            "content": nodeId,
            "message_type": "file",
            "message_id": message.id,
            "file_name": filename
        ]
        let jsonData = try JSONSerialization.data(withJSONObject: payload)
        let wsMessage = URLSessionWebSocketTask.Message.data(jsonData)

        try await webSocketTask?.send(wsMessage)

        var updatedMessage = message
        updatedMessage.status = .sent
        return updatedMessage
    }

    // MARK: - 获取消息历史

    func fetchConversations() async throws {
        struct ConversationsResponse: Codable {
            let conversations: [IMConversation]
        }
        let response: ConversationsResponse = try await APIClient.shared.get("/api/im/conversations")
        await MainActor.run {
            self.conversations = response.conversations
        }
    }

    func fetchMessages(conversationId: String, page: Int = 1) async throws -> [IMMessage] {
        struct MessagesResponse: Codable {
            let messages: [IMMessage]
        }
        let response: MessagesResponse = try await APIClient.shared.get(
            "/api/im/conversations/\(conversationId)/messages",
            params: ["page": "\(page)"]
        )
        return response.messages
    }

    /// 标记已读
    func markAsRead(conversationId: String) async throws {
        struct MarkReadRequest: Encodable {
            let conversationId: String
        }
        let _: EmptyResponse = try await APIClient.shared.post(
            "/api/im/read",
            body: MarkReadRequest(conversationId: conversationId)
        )
    }

    // MARK: - WebSocket 接收循环

    private func receiveMessage() {
        webSocketTask?.receive { [weak self] result in
            switch result {
            case .success(let message):
                switch message {
                case .data(let data):
                    self?.handleIncomingData(data)
                case .string(let string):
                    if let data = string.data(using: .utf8) {
                        self?.handleIncomingData(data)
                    }
                @unknown default:
                    break
                }
                // 继续监听
                self?.receiveMessage()

            case .failure(let error):
                print("[IM] WebSocket 接收失败: \(error.localizedDescription)")
                self?.handleDisconnect()
            }
        }
    }

    private func handleIncomingData(_ data: Data) {
        do {
            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let type = json["type"] as? String else { return }

            switch type {
            case "message":
                handleIncomingMessage(json)
            case "ack":
                handleAck(json)
            case "status":
                handleStatusUpdate(json)
            case "typing":
                handleTyping(json)
            default:
                break
            }
        } catch {
            print("[IM] 消息解析失败: \(error)")
        }
    }

    private func handleIncomingMessage(_ json: [String: Any]) {
        guard let messageId = json["message_id"] as? String,
              let from = json["from"] as? String,
              let content = json["content"] as? String,
              let messageType = json["message_type"] as? String else { return }

        let type: IMMessage.MessageType = messageType == "file" ? .file : .text
        let filename = json["file_name"] as? String
        let fileNodeId = json["file_node_id"] as? String

        let message = IMMessage(
            id: messageId,
            from: from,
            to: KeychainManager.shared.readUserId() ?? "",
            content: content,
            type: type,
            timestamp: Date(),
            status: .delivered,
            fileNodeId: fileNodeId,
            fileName: filename
        )

        DispatchQueue.main.async {
            self.messages.append(message)
            // 发送已读回执
            Task { await self.sendAck(messageId: messageId) }
        }
    }

    private func handleAck(_ json: [String: Any]) {
        guard let messageId = json["message_id"] as? String,
              let status = json["status"] as? String else { return }

        DispatchQueue.main.async {
            if let index = self.messages.firstIndex(where: { $0.id == messageId }) {
                var msg = self.messages[index]
                msg.status = IMMessage.MessageStatus(rawValue: status) ?? .sent
                self.messages[index] = msg
            }
        }
    }

    private func handleStatusUpdate(_ json: [String: Any]) {
        if let onlineUsers = json["online_users"] as? [String] {
            DispatchQueue.main.async {
                self.onlineUsers = onlineUsers
            }
        }
    }

    private func handleTyping(_ json: [String: Any]) {
        // 对方正在输入... 指示
        // 由 ViewModel 处理
    }

    private func sendAck(messageId: String) async {
        let payload: [String: Any] = [
            "type": "ack",
            "message_id": messageId,
            "status": "read"
        ]
        guard let jsonData = try? JSONSerialization.data(withJSONObject: payload) else { return }
        let wsMessage = URLSessionWebSocketTask.Message.data(jsonData)
        try? await webSocketTask?.send(wsMessage)
    }

    // MARK: - 心跳保持

    private func startPing() {
        pingTimer = Timer.scheduledTimer(withTimeInterval: 30, repeats: true) { [weak self] _ in
            self?.webSocketTask?.sendPing { error in
                if let error = error {
                    print("[IM] Ping 失败: \(error.localizedDescription)")
                    self?.handleDisconnect()
                }
            }
        }
    }

    private func stopPing() {
        pingTimer?.invalidate()
        pingTimer = nil
    }

    // MARK: - 重连

    private func handleDisconnect() {
        DispatchQueue.main.async {
            self.isConnected = false
            self.stopPing()

            guard self.reconnectAttempts < self.maxReconnectAttempts else {
                print("[IM] 重连次数已达上限")
                return
            }

            self.reconnectAttempts += 1
            let delay = self.reconnectDelay * Double(self.reconnectAttempts)

            DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
                self.connect()
            }
        }
    }
}

// MARK: - IM 消息模型

struct IMMessage: Codable, Identifiable {
    let id: String
    let from: String
    let to: String
    let content: String
    let type: MessageType
    let timestamp: Date
    var status: MessageStatus
    var fileNodeId: String?
    var fileName: String?

    enum MessageType: String, Codable {
        case text
        case file
        case image
        case system
    }

    enum MessageStatus: String, Codable {
        case sending
        case sent
        case delivered
        case read
        case failed
    }
}

// MARK: - 会话模型

struct IMConversation: Codable, Identifiable {
    let id: String
    let userId: String
    let username: String
    let avatar: String?
    let lastMessage: String?
    let lastMessageTime: Date?
    let unreadCount: Int
    let isOnline: Bool
}