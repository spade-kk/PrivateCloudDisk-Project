//
//  User.swift
//  PrivateCloudDisk-ios
//
//  用户模型
//

import Foundation

/// 用户信息
struct UserProfile: Codable, Identifiable {
    let id: String?
    let name: String
    let account: String
    let email: String
    let phoneNumber: String
    let imagePath: String
    let role: String?
    let createdAt: String?

    var idOrEmpty: String { id ?? "" }
    var displayName: String { name.isEmpty ? account : name }
    var initial: String {
        let src = displayName.trimmingCharacters(in: .whitespaces)
        return src.isEmpty ? "U" : String(src.prefix(1)).uppercased()
    }
    var avatarURL: URL? {
        guard !imagePath.isEmpty else { return nil }
        return URL(string: imagePath)
    }

    enum CodingKeys: String, CodingKey {
        case id
        case name
        case account
        case email
        case phoneNumber = "phone_number"
        case imagePath = "image_path"
        case role
        case createdAt = "created_at"
    }
}

/// 好友/联系人
struct Friend: Codable, Identifiable, Hashable {
    let id: String
    let name: String
    let account: String
    let email: String?
    let role: String?
    let online: Bool
    let avatarPath: String?

    var displayName: String { name.isEmpty ? account : name }
    var initial: String {
        let src = displayName.trimmingCharacters(in: .whitespaces)
        return src.isEmpty ? "?" : String(src.prefix(1)).uppercased()
    }

    enum CodingKeys: String, CodingKey {
        case id, name, account, email, role, online
        case avatarPath = "avatar_path"
    }
}