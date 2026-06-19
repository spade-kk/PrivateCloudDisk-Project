//
//  WebSocketClient.swift
//  PrivateCloudDisk-ios
//
//  WebSocket 客户端 — 对接后端 IM WebSocket 服务
//  使用 iOS 原生 URLSessionWebSocketTask，支持自动重连、心跳、消息分发
//

import Foundation
import Combine

// MARK: - WebSocket 消息类型

enum WSMessageType: String {
    case message = "message"
    case messageAck = "message_ack"
    case messageRead = "message_read"
    case typing = "typing"
    case videoCall = "video_call"
    case callSignal = "call_signal"
    case callAccept = "call_accept"
    case callReject = "call_reject"
    case callHangup = "call_hangup"
    case callOffer = "call_offer"
    case callAnswer = "call_answer"
    case iceCandidate = "ice_candidate"
    case presence = "presence"
    case heartbeat = "heartbeat"
    case error = "error"
}

// MARK: - WebSocket 消息结构

struct IncomingWSMessage: Codable {
    let type: String
    let data: WSMessageData?
    let senderId: String?
    let timestamp: Int64?

    enum CodingKeys: String, CodingKey {
        case type, data
        case senderId = "sender_id"
        case timestamp
    }
}

struct WSMessageData: Codable {
    let messageId: String?
    let conversationId: String?
    let content: String?
    let messageType: String?
    let extra: [String: String]?
    let serverSeq: Int64?
    let createdAt: String?
    let callType: String?
    let callId: String?
    let sdp: String?
    let candidate: String?
    let sdpMid: String?
    let sdpMLineIndex: Int?
    let userId: String?
    let userName: String?

    enum CodingKeys: String, CodingKey {
        case messageId = "message_id"
        case conversationId = "conversation_id"
        case content
        case messageType = "message_type"
        case extra
        case serverSeq = "server_seq"
        case createdAt = "created_at"
        case callType = "call_type"
        case callId = "call_id"
        case sdp
        case candidate
        case sdpMid = "sdp_mid"
        case sdpMLineIndex = "sdp_m_line_index"
        case userId = "user_id"
        case userName = "user_name"
    }
}

// MARK: - WebSocket 客户端

@MainActor
class WebSocketClient: ObservableObject {
    static let shared = WebSocketClient()

    @Published var isConnected = false
    @Published var connectionState: ConnectionState = .disconnected

    enum ConnectionState {
        case disconnected
        case connecting
        case connected
        case reconnecting
    }

    private var task: URLSessionWebSocketTask?
    private var session: URLSession
    private var pingTimer: Timer?
    private var reconnectTimer: Timer?
    private var reconnectAttempts = 0
    private let maxReconnectAttempts = 10
    private let baseDelay: TimeInterval = 1.0
    private var wsURL: String = ""

    // 消息回调
    var onMessage: ((IncomingWSMessage) -> Void)?
    var onCallSignal: ((IncomingWSMessage) -> Void)?
    var onConnectionStateChange: ((ConnectionState) -> Void)?

    private init() {
        session = URLSession(configuration: .default)
    }

    deinit {
        pingTimer?.invalidate()
        reconnectTimer?.invalidate()
        task?.cancel(with: .goingAway, reason: nil)
    }

    // MARK: - 连接管理

    func connect(token: String, serverURL: String? = nil) {
        guard connectionState == .disconnected else { return }

        let urlStr = serverURL ?? "wss://im.cloud.example.com/ws"
        wsURL = urlStr

        guard let url = URL(string: "\(urlStr)?token=\(token)") else { return }

        updateState(.connecting)
        task = session.webSocketTask(with: url)
        task?.resume()
        startPing()
        receiveMessage()
        updateState(.connected)
    }

    func disconnect() {
        pingTimer?.invalidate()
        pingTimer = nil
        reconnectTimer?.invalidate()
        reconnectTimer = nil
        task?.cancel(with: .goingAway, reason: nil)
        task = nil
        reconnectAttempts = 0
        updateState(.disconnected)
    }

    /// 重新连接（App 从后台恢复时调用）
    func reconnect() {
        guard let token = KeychainManager.shared.getToken() else { return }
        disconnect()
        connect(token: token)
    }

    // MARK: - 发送消息

    func send(_ message: [String: Any]) {
        guard let jsonData = try? JSONSerialization.data(withJSONObject: message),
              let jsonString = String(data: jsonData, encoding: .utf8) else { return }
        let wsMessage = URLSessionWebSocketTask.Message.string(jsonString)
        task?.send(wsMessage) { [weak self] error in
            if let error = error {
                print("[WebSocket] Send error: \(error)")
            }
        }
    }

    func sendTyping(conversationId: String, isTyping: Bool) {
        send(["type": "typing", "conversation_id": conversationId, "is_typing": isTyping])
    }

    func sendCallSignal(type: String, calleeId: String, callId: String, sdp: String? = nil, candidate: String? = nil) {
        var msg: [String: Any] = [
            "type": type,
            "callee_id": calleeId,
            "call_id": callId
        ]
        if let sdp = sdp { msg["sdp"] = sdp }
        if let candidate = candidate { msg["candidate"] = candidate }
        send(msg)
    }

    // MARK: - 接收消息

    private func receiveMessage() {
        task?.receive { [weak self] result in
            guard let self = self else { return }
            switch result {
            case .success(let message):
                switch message {
                case .string(let text):
                    self.handleMessage(text)
                case .data(let data):
                    if let text = String(data: data, encoding: .utf8) {
                        self.handleMessage(text)
                    }
                @unknown default:
                    break
                }
                self.receiveMessage() // 继续接收
            case .failure(let error):
                print("[WebSocket] Receive error: \(error)")
                self.handleDisconnect()
            }
        }
    }

    private func handleMessage(_ text: String) {
        guard let data = text.data(using: .utf8),
              let msg = try? JSONDecoder().decode(IncomingWSMessage.self, from: data) else {
            return
        }

        // 心跳回复
        if msg.type == WSMessageType.heartbeat.rawValue { return }

        // 呼叫信令
        if ["video_call", "call_signal", "call_offer", "call_answer", "ice_candidate", "call_accept", "call_reject", "call_hangup"].contains(msg.type) {
            onCallSignal?(msg)
        }

        onMessage?(msg)
    }

    // MARK: - 心跳

    private func startPing() {
        pingTimer?.invalidate()
        pingTimer = Timer.scheduledTimer(withTimeInterval: 30, repeats: true) { [weak self] _ in
            self?.send(["type": "heartbeat", "timestamp": Int64(Date().timeIntervalSince1970 * 1000)])
        }
    }

    // MARK: - 重连

    private func handleDisconnect() {
        updateState(.reconnecting)
        scheduleReconnect()
    }

    private func scheduleReconnect() {
        guard reconnectAttempts < maxReconnectAttempts else {
            updateState(.disconnected)
            return
        }

        let delay = baseDelay * pow(2.0, Double(reconnectAttempts))
        reconnectAttempts += 1

        reconnectTimer?.invalidate()
        reconnectTimer = Timer.scheduledTimer(withTimeInterval: min(delay, 60), repeats: false) { [weak self] _ in
            Task { @MainActor in
                guard let self = self, let token = KeychainManager.shared.getToken() else { return }
                self.connect(token: token)
            }
        }
    }

    // MARK: - 状态更新

    private func updateState(_ state: ConnectionState) {
        connectionState = state
        isConnected = state == .connected
        onConnectionStateChange?(state)
    }
}