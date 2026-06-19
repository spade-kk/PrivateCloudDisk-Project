//
//  Message.swift
//  PrivateCloudDisk-ios
//
//  IM 消息模型
//

import Foundation

/// 聊天消息
struct ChatMessage: Codable, Identifiable, Hashable {
    let id: String
    let conversationId: String
    let senderId: String
    let senderName: String?
    let type: MessageType
    let content: String
    let status: MessageStatus
    let serverSeq: Int64
    let createdAt: String
    let extra: [String: String]?

    // 视频通话相关
    var callType: String? { extra?["call_type"] }
    var callId: String? { extra?["call_id"] }
    var isCallInvite: Bool { type == .videoCall }
    var isCallAccepted: Bool { extra?["call_status"] == "accepted" }
    var isCallRejected: Bool { extra?["call_status"] == "rejected" }
    var isCallEnded: Bool { extra?["call_status"] == "ended" }

    // 判断方向
    var isFromMe: Bool { senderId == "me" || senderId == UserDefaults.standard.string(forKey: "currentUserId") }

    enum MessageType: String, Codable {
        case text = "TEXT"
        case image = "IMAGE"
        case file = "FILE"
        case share = "SHARE"
        case videoCall = "VIDEO_CALL"
        case system = "SYSTEM"
    }

    enum MessageStatus: String, Codable {
        case sending = "SENDING"
        case sent = "SENT"
        case delivered = "DELIVERED"
        case read = "READ"
        case failed = "FAILED"
        case recalled = "RECALLED"
    }

    enum CodingKeys: String, CodingKey {
        case id
        case conversationId = "conversation_id"
        case senderId = "sender_id"
        case senderName = "sender_name"
        case type
        case content
        case status
        case serverSeq = "server_seq"
        case createdAt = "created_at"
        case extra
    }
}

/// 会话
struct Conversation: Codable, Identifiable, Hashable {
    let id: String
    let title: String
    let subtitle: String?
    let avatarURL: String?
    let type: ConversationType
    let lastMessage: ChatMessage?
    let unreadCount: Int
    let updatedAt: String

    enum ConversationType: String, Codable {
        case direct = "DIRECT"
        case group = "GROUP"
        case system = "SYSTEM"
    }

    var initial: String {
        let src = title.trimmingCharacters(in: .whitespaces)
        return src.isEmpty ? "?" : String(src.prefix(1)).uppercased()
    }

    enum CodingKeys: String, CodingKey {
        case id, title, subtitle, type
        case avatarURL = "avatar_url"
        case lastMessage = "last_message"
        case unreadCount = "unread_count"
        case updatedAt = "updated_at"
    }
}

/// 通话记录
struct CallRecord: Codable, Identifiable {
    let id: String
    let callerId: String
    let calleeId: String
    let callerName: String?
    let calleeName: String?
    let callType: CallType
    let status: CallStatus
    let duration: Int
    let createdAt: String

    var formattedDuration: String {
        let m = duration / 60
        let s = duration % 60
        return String(format: "%02d:%02d", m, s)
    }
    var isIncoming: Bool {
        calleeId == UserDefaults.standard.string(forKey: "currentUserId")
    }

    enum CallType: String, Codable {
        case video = "VIDEO"
        case voice = "VOICE"
    }

    enum CallStatus: String, Codable {
        case missed = "MISSED"
        case answered = "ANSWERED"
        case rejected = "REJECTED"
        case busy = "BUSY"
    }

    enum CodingKeys: String, CodingKey {
        case id
        case callerId = "caller_id"
        case calleeId = "callee_id"
        case callerName = "caller_name"
        case calleeName = "callee_name"
        case callType = "call_type"
        case status, duration
        case createdAt = "created_at"
    }
}