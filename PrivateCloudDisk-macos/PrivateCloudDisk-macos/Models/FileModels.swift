import Foundation

// MARK: - 文件/节点模型

/// 文件节点（对应 API 返回的 node 对象）
struct FileNode: Codable, Identifiable, Equatable {
    let id: String
    let name: String
    let parentId: String?
    let isFolder: Bool
    let size: Int64
    let mimeType: String?
    let md5: String?
    let sha256: String?
    let createdAt: String?
    let updatedAt: String?
    let isStarred: Bool?
    let isDeleted: Bool?
    let thumbnailUrl: String?
    let downloadUrl: String?
    let children: [FileNode]?

    enum CodingKeys: String, CodingKey {
        case id, name, size, children
        case parentId = "parent_id"
        case isFolder = "is_folder"
        case mimeType = "mime_type"
        case md5, sha256
        case createdAt = "created_at"
        case updatedAt = "updated_at"
        case isStarred = "is_starred"
        case isDeleted = "is_deleted"
        case thumbnailUrl = "thumbnail_url"
        case downloadUrl = "download_url"
    }

    static func == (lhs: FileNode, rhs: FileNode) -> Bool {
        lhs.id == rhs.id
    }

    /// 文件类型分类
    enum FileCategory: String {
        case document, image, video, audio, archive, code, other

        var sfSymbolName: String {
            switch self {
            case .document: return "doc.text"
            case .image: return "photo"
            case .video: return "film"
            case .audio: return "music.note"
            case .archive: return "archivebox"
            case .code: return "chevron.left.forwardslash.chevron.right"
            case .other: return "doc"
            }
        }
    }

    var category: FileCategory {
        guard let mime = mimeType else { return .other }
        if mime.hasPrefix("image/") { return .image }
        if mime.hasPrefix("video/") { return .video }
        if mime.hasPrefix("audio/") { return .audio }
        if mime.contains("pdf") || mime.contains("document") || mime.contains("text") { return .document }
        if mime.contains("zip") || mime.contains("rar") || mime.contains("tar") || mime.contains("gzip") { return .archive }
        if mime.contains("json") || mime.contains("xml") || mime.contains("javascript") || mime.contains("swift") { return .code }
        return .other
    }

    var formattedSize: String {
        ByteCountFormatter.string(fromByteCount: size, countStyle: .file)
    }

    var formattedDate: String {
        guard let dateStr = updatedAt ?? createdAt else { return "" }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = formatter.date(from: dateStr) {
            return date.formatted(date: .abbreviated, time: .shortened)
        }
        return dateStr
    }
}

// MARK: - 文件列表请求

struct FileListRequest: Codable {
    let parentId: String?
    let page: Int
    let pageSize: Int
    let sortBy: String?
    let sortOrder: String?

    enum CodingKeys: String, CodingKey {
        case parentId = "parent_id"
        case page
        case pageSize = "page_size"
        case sortBy = "sort_by"
        case sortOrder = "sort_order"
    }
}

// MARK: - 创建文件夹请求

struct CreateFolderRequest: Codable {
    let name: String
    let parentId: String?

    enum CodingKeys: String, CodingKey {
        case name
        case parentId = "parent_id"
    }
}

// MARK: - 重命名请求

struct RenameRequest: Codable {
    let newName: String

    enum CodingKeys: String, CodingKey {
        case newName = "new_name"
    }
}

// MARK: - 移动请求

struct MoveRequest: Codable {
    let targetParentId: String
    let nodeIds: [String]

    enum CodingKeys: String, CodingKey {
        case targetParentId = "target_parent_id"
        case nodeIds = "node_ids"
    }
}

// MARK: - 文件搜索结果

struct FileSearchResult: Codable, Identifiable {
    let id: String
    let name: String
    let parentId: String?
    let isFolder: Bool
    let size: Int64
    let mimeType: String?
    let updatedAt: String?
    let highlight: String?

    enum CodingKeys: String, CodingKey {
        case id, name, size, highlight
        case parentId = "parent_id"
        case isFolder = "is_folder"
        case mimeType = "mime_type"
        case updatedAt = "updated_at"
    }
}

// MARK: - 收藏操作

struct StarRequest: Codable {
    let nodeId: String
    let starred: Bool

    enum CodingKeys: String, CodingKey {
        case nodeId = "node_id"
        case starred
    }
}

// MARK: - 回收站恢复

struct TrashActionRequest: Codable {
    let nodeIds: [String]

    enum CodingKeys: String, CodingKey {
        case nodeIds = "node_ids"
    }
}