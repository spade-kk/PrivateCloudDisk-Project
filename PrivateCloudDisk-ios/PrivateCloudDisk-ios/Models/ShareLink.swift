//
//  ShareLink.swift
//  PrivateCloudDisk-ios
//
//  分享链接模型
//

import Foundation

/// 分享链接
struct ShareLinkItem: Codable, Identifiable {
    let shareToken: String
    let ownerId: String
    let targetType: ShareTargetType
    let fileId: String?
    let nodeId: String?
    let targetName: String
    let hasPassword: Bool
    let expireDays: Int?
    let expireAt: String?
    let maxAccessCount: Int?
    let accessCount: Int
    let status: ShareStatus
    let createdAt: String
    let updatedAt: String?

    var id: String { shareToken }
    var isExpired: Bool {
        guard let expireStr = expireAt, let date = ISO8601DateFormatter().date(from: expireStr) else {
            return false
        }
        return date < Date()
    }
    var isActive: Bool { status == .active && !isExpired }
    var targetTypeLabel: String {
        targetType == .file ? "文件" : "文件夹"
    }
    var statusLabel: String {
        switch status {
        case .active: return "正常"
        case .revoked: return "已撤销"
        case .expired: return "已过期"
        }
    }
    var shareURL: String {
        "https://cloud.example.com/share/\(shareToken)"
    }

    enum ShareTargetType: String, Codable {
        case file = "FILE"
        case folder = "FOLDER"
    }

    enum ShareStatus: String, Codable {
        case active = "ACTIVE"
        case revoked = "REVOKED"
        case expired = "EXPIRED"
    }

    enum CodingKeys: String, CodingKey {
        case shareToken = "share_token"
        case ownerId = "owner_id"
        case targetType = "target_type"
        case fileId = "file_id"
        case nodeId = "node_id"
        case targetName = "target_name"
        case hasPassword = "has_password"
        case expireDays = "expire_days"
        case expireAt = "expire_at"
        case maxAccessCount = "max_access_count"
        case accessCount = "access_count"
        case status
        case createdAt = "created_at"
        case updatedAt = "updated_at"
    }
}

/// 分享内容（访问分享链接时返回）
struct ShareContent: Codable, Identifiable {
    let shareToken: String
    let targetType: ShareLinkItem.ShareTargetType
    let fileId: String?
    let nodeId: String?
    let targetName: String
    let ownerName: String
    let hasPassword: Bool
    let isExpired: Bool
    let children: [FileNode]?

    var id: String { shareToken }

    enum CodingKeys: String, CodingKey {
        case shareToken = "share_token"
        case targetType = "target_type"
        case fileId = "file_id"
        case nodeId = "node_id"
        case targetName = "target_name"
        case ownerName = "owner_name"
        case hasPassword = "has_password"
        case isExpired = "is_expired"
        case children
    }
}

/// 创建分享参数
struct CreateShareRequest: Codable {
    let targetType: String
    let fileId: String?
    let nodeId: String?
    let password: String?
    let expireDays: Int?
    let maxAccessCount: Int?

    enum CodingKeys: String, CodingKey {
        case targetType = "target_type"
        case fileId = "file_id"
        case nodeId = "node_id"
        case password
        case expireDays = "expire_days"
        case maxAccessCount = "max_access_count"
    }
}

/// 分享密码验证
struct SharePasswordVerify: Codable {
    let shareToken: String
    let password: String

    enum CodingKeys: String, CodingKey {
        case shareToken = "share_token"
        case password
    }
}

/// 分享访问令牌
struct ShareAccessToken: Codable {
    let accessToken: String
    let expiresAt: String

    enum CodingKeys: String, CodingKey {
        case accessToken = "access_token"
        case expiresAt = "expires_at"
    }
}