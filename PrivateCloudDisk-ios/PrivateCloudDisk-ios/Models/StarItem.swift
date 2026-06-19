//
//  StarItem.swift
//  PrivateCloudDisk-ios
//
//  收藏项模型
//

import Foundation

struct StarItem: Codable, Identifiable {
    let starId: String
    let targetType: StarTargetType
    let targetId: String
    let targetName: String
    let targetSize: Int64?
    let createdAt: String

    var id: String { starId }
    var isFolder: Bool { targetType == .folder }
    var formattedSize: String {
        guard let size = targetSize else { return "--" }
        return ByteCountFormatter.string(fromByteCount: size, countStyle: .file)
    }
    var systemIcon: String {
        isFolder ? "folder.fill" : "doc.fill"
    }

    enum StarTargetType: String, Codable {
        case file = "FILE"
        case folder = "FOLDER"
    }

    enum CodingKeys: String, CodingKey {
        case starId = "star_id"
        case targetType = "target_type"
        case targetId = "target_id"
        case targetName = "target_name"
        case targetSize = "target_size"
        case createdAt = "created_at"
    }
}

/// 创建收藏请求
struct CreateStarRequest: Codable {
    let targetType: String
    let fileId: String?
    let nodeId: String?

    enum CodingKeys: String, CodingKey {
        case targetType = "target_type"
        case fileId = "file_id"
        case nodeId = "node_id"
    }
}