//
//  IMService.swift
//  PrivateCloudDisk-ios
//
//  IM 即时通讯网络服务 — 封装 IM HTTP REST API
//

import Foundation

actor IMService {
    static let shared = IMService()
    private let client = APIClient.shared
    private let basePath = "/im/api/v1"

    private init() {}

    // MARK: - 消息

    /// 发送消息
    func sendMessage(conversationId: String, type: String, content: String, extra: [String: String]? = nil) async throws -> ChatMessage {
        struct SendBody: Encodable {
            let conversationId: String; let type: String
            let content: String; let extra: [String: String]?
            enum CodingKeys: String, CodingKey {
                case conversationId = "conversation_id"; case type; case content; case extra
            }
        }
        let resp: APIResponse<ChatMessage> = try await client.request(
            .post, path: "\(basePath)/messages/send",
            body: SendBody(conversationId: conversationId, type: type, content: content, extra: extra)
        )
        guard let data = resp.data else { throw APIError.noData }
        return data
    }

    /// 撤回消息
    func recallMessage(messageId: String, userId: String) async throws {
        let _: APIEmptyResponse = try await client.request(
            .post, path: "\(basePath)/messages/recall",
            queryItems: [URLQueryItem(name: "messageId", value: messageId), URLQueryItem(name: "userId", value: userId)]
        )
    }

    /// 标记已读
    func markRead(conversationId: String, userId: String) async throws {
        let _: APIEmptyResponse = try await client.request(
            .post, path: "\(basePath)/messages/read",
            queryItems: [URLQueryItem(name: "conversationId", value: conversationId), URLQueryItem(name: "userId", value: userId)]
        )
    }

    /// 获取历史消息
    func getHistory(conversationId: String, userId: String, page: Int = 1, size: Int = 20) async throws -> [ChatMessage] {
        let resp: APIResponse<[ChatMessage]> = try await client.request(
            .get, path: "\(basePath)/messages/history",
            queryItems: [
                URLQueryItem(name: "conversationId", value: conversationId),
                URLQueryItem(name: "userId", value: userId),
                URLQueryItem(name: "page", value: "\(page)"),
                URLQueryItem(name: "size", value: "\(size)")
            ]
        )
        return resp.data ?? []
    }

    /// 增量同步消息
    func syncMessages(conversationId: String, userId: String, serverSeq: Int64, limit: Int = 50) async throws -> [ChatMessage] {
        let resp: APIResponse<[ChatMessage]> = try await client.request(
            .get, path: "\(basePath)/messages/sync",
            queryItems: [
                URLQueryItem(name: "conversationId", value: conversationId),
                URLQueryItem(name: "userId", value: userId),
                URLQueryItem(name: "serverSeq", value: "\(serverSeq)"),
                URLQueryItem(name: "limit", value: "\(limit)")
            ]
        )
        return resp.data ?? []
    }

    // MARK: - 会话

    func getConversations() async throws -> [Conversation] {
        let resp: APIResponse<[Conversation]> = try await client.request(.get, path: "\(basePath)/conversations")
        return resp.data ?? []
    }

    func createDirectConversation(peerId: String) async throws -> Conversation {
        struct CreateBody: Encodable {
            let type: String; let peerId: String
            enum CodingKeys: String, CodingKey { case type; case peerId = "peer_id" }
        }
        let resp: APIResponse<Conversation> = try await client.request(
            .post, path: "\(basePath)/conversations",
            body: CreateBody(type: "DIRECT", peerId: peerId)
        )
        guard let data = resp.data else { throw APIError.noData }
        return data
    }

    func deleteConversation(conversationId: String) async throws {
        let _: APIEmptyResponse = try await client.request(.delete, path: "\(basePath)/conversations/\(conversationId)")
    }

    // MARK: - 联系人

    func getFriends() async throws -> [Friend] {
        let resp: APIResponse<[Friend]> = try await client.request(.get, path: "\(basePath)/contacts/friends")
        return resp.data ?? []
    }

    func addFriend(account: String) async throws {
        struct AddBody: Encodable { let account: String }
        let _: APIEmptyResponse = try await client.request(
            .post, path: "\(basePath)/contacts/friends", body: AddBody(account: account)
        )
    }

    func removeFriend(friendId: String) async throws {
        let _: APIEmptyResponse = try await client.request(.delete, path: "\(basePath)/contacts/friends/\(friendId)")
    }

    // MARK: - 通话记录

    func getCallRecords(page: Int = 1, size: Int = 20) async throws -> [CallRecord] {
        let resp: APIResponse<[CallRecord]> = try await client.request(
            .get, path: "\(basePath)/calls/records",
            queryItems: [URLQueryItem(name: "page", value: "\(page)"), URLQueryItem(name: "size", value: "\(size)")]
        )
        return resp.data ?? []
    }

    func deleteCallRecord(recordId: String) async throws {
        let _: APIEmptyResponse = try await client.request(.delete, path: "\(basePath)/calls/records/\(recordId)")
    }
}