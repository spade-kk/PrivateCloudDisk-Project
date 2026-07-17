import Foundation
import SwiftUI

// MARK: - 文件分类枚举

/// 文件分类（用于图标、颜色映射）
enum FileCategory: String, Codable, CaseIterable {
    case document
    case image
    case video
    case audio
    case archive
    case code
    case other

    /// SF Symbol 图标名
    var sfSymbolName: String {
        switch self {
        case .document: return "doc.fill"
        case .image:    return "photo.fill"
        case .video:    return "play.rectangle.fill"
        case .audio:    return "waveform.circle.fill"
        case .archive:  return "archivebox.fill"
        case .code:     return "chevron.left.forwardslash.chevron.right"
        case .other:    return "file.fill"
        }
    }

    /// 分类颜色
    var color: Color {
        switch self {
        case .document: return AppColors.primary
        case .image:    return AppColors.fileImage
        case .video:    return AppColors.fileVideo
        case .audio:    return AppColors.fileAudio
        case .archive:  return AppColors.fileArchive
        case .code:     return AppColors.fileCode
        case .other:    return AppColors.textTertiary
        }
    }

    /// 根据文件名扩展名推导分类
    static func from(filename: String) -> FileCategory {
        let ext = filename.split(separator: ".").last?.lowercased() ?? ""
        switch ext {
        // 文档
        case "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
             "txt", "rtf", "csv", "pages", "numbers", "key":
            return .document
        // 图片
        case "jpg", "jpeg", "png", "gif", "bmp", "tiff", "tif",
             "webp", "heic", "heif", "svg", "ico", "raw", "psd":
            return .image
        // 视频
        case "mp4", "avi", "mov", "mkv", "wmv", "flv", "webm",
             "m4v", "mpg", "mpeg", "3gp", "ogv":
            return .video
        // 音频
        case "mp3", "wav", "aac", "flac", "ogg", "wma", "m4a",
             "aiff", "alac", "opus":
            return .audio
        // 压缩包
        case "zip", "rar", "7z", "tar", "gz", "bz2", "xz",
             "iso", "dmg", "pkg":
            return .archive
        // 代码
        case "swift", "java", "py", "js", "ts", "jsx", "tsx",
             "html", "css", "scss", "less", "json", "xml", "yaml", "yml",
             "c", "cpp", "h", "hpp", "go", "rs", "rb", "php",
             "sh", "bash", "zsh", "kt", "scala", "r", "sql",
             "md", "toml", "plist", "xcconfig", "entitlements":
            return .code
        default:
            return .other
        }
    }
}

// MARK: - 节点模型（与后端 NodeVO 完全对齐）

struct NodeVO: Codable {
    let nodeId: String
    let nodeType: String
    let nodeName: String
    let nodeSize: Int64?

    var isFolder: Bool { nodeType == "FOLDER" || nodeType == "folder" }
}

struct PathChildrenVO: Codable {
    let nodeId: String
    let children: [NodeVO]
}

// MARK: - 文件夹节点模型（与后端 FolderNodeVO 对齐）

struct FolderNodeVO: Codable {
    let nodeId: String
    let parentId: String?
    let name: String
    let createTime: String
}

// MARK: - 文件模型（与后端 FileVO 对齐）

struct FileVO: Codable {
    let id: String
    let name: String
    let type: String
    let size: Int64
    let uploadedTime: String
    let nodeId: String
    let totalChunks: Int

    var mimeType: String { type }
}

// MARK: - 收藏模型（与后端 FileStarVO 对齐）

struct FileStarVO: Codable {
    let starId: Int
    let targetType: String
    let targetId: String
    let targetName: String
    let targetSize: Int64
    let fileType: String?
    let fileStatus: String?
    let starredAt: String

    var isFolder: Bool { targetType == "folder" }
}

// MARK: - 回收站模型（与后端 TrashTargetVO 对齐）

struct TrashTargetVO: Codable {
    let trashId: Int
    let targetId: String
    let targetName: String
    let fileType: String?
    let targetSize: Int64
    let targetType: String
    let originalNodeId: String
    let deletedAt: String
    let expiresAt: String?

    var isFolder: Bool { targetType == "folder" }
}

// MARK: - 文件列表模型

struct FileNode: Identifiable, Hashable, Codable {
    let id: String
    let name: String
    let size: Int64
    let type: String
    let createdAt: String
    let updatedAt: String
    let nodeId: String
    let parentId: String?
    let mimeType: String?
    let isFolder: Bool
    let md5: String?
    let sha256: String?
    let isStarred: Bool
    let isDeleted: Bool
    let thumbnailUrl: String?
    let downloadUrl: String?
    let children: [FileNode]?
    var category: FileCategory

    init(nodeVO: NodeVO) {
        self.id = nodeVO.nodeId
        self.name = nodeVO.nodeName
        self.size = nodeVO.nodeSize ?? 0
        self.type = nodeVO.nodeType
        self.createdAt = ""
        self.updatedAt = ""
        self.nodeId = nodeVO.nodeId
        self.parentId = nil
        self.mimeType = nodeVO.isFolder ? nil : ""
        self.isFolder = nodeVO.isFolder
        self.md5 = nil
        self.sha256 = nil
        self.isStarred = false
        self.isDeleted = false
        self.thumbnailUrl = nil
        self.downloadUrl = nil
        self.children = nil
        self.category = nodeVO.isFolder ? .other : FileCategory.from(filename: nodeVO.nodeName)
    }

    init(fileStarVO: FileStarVO) {
        self.id = String(fileStarVO.starId)
        self.name = fileStarVO.targetName
        self.size = fileStarVO.targetSize
        self.type = fileStarVO.targetType
        self.createdAt = fileStarVO.starredAt
        self.updatedAt = ""
        self.nodeId = fileStarVO.targetId
        self.parentId = nil
        self.mimeType = fileStarVO.fileType
        self.isFolder = fileStarVO.isFolder
        self.md5 = nil
        self.sha256 = nil
        self.isStarred = true
        self.isDeleted = false
        self.thumbnailUrl = nil
        self.downloadUrl = nil
        self.children = nil
        self.category = fileStarVO.isFolder ? .other : FileCategory.from(filename: fileStarVO.targetName)
    }

    init(trashTargetVO: TrashTargetVO) {
        self.id = String(trashTargetVO.trashId)
        self.name = trashTargetVO.targetName
        self.size = trashTargetVO.targetSize
        self.type = trashTargetVO.targetType
        self.createdAt = trashTargetVO.deletedAt
        self.updatedAt = ""
        self.nodeId = trashTargetVO.targetId
        self.parentId = nil
        self.mimeType = trashTargetVO.fileType
        self.isFolder = trashTargetVO.isFolder
        self.md5 = nil
        self.sha256 = nil
        self.isStarred = false
        self.isDeleted = true
        self.thumbnailUrl = nil
        self.downloadUrl = nil
        self.children = nil
        self.category = trashTargetVO.isFolder ? .other : FileCategory.from(filename: trashTargetVO.targetName)
    }

    init(fileVO: FileVO) {
        self.id = fileVO.id
        self.name = fileVO.name
        self.size = fileVO.size
        self.type = fileVO.type
        self.createdAt = fileVO.uploadedTime
        self.updatedAt = ""
        self.nodeId = fileVO.nodeId
        self.parentId = nil
        self.mimeType = fileVO.type
        self.isFolder = false
        self.md5 = nil
        self.sha256 = nil
        self.isStarred = false
        self.isDeleted = false
        self.thumbnailUrl = nil
        self.downloadUrl = nil
        self.children = nil
        self.category = FileCategory.from(filename: fileVO.name)
    }

    init(
        id: String, name: String, parentId: String?,
        isFolder: Bool, size: Int64, mimeType: String?,
        md5: String?, sha256: String?,
        createdAt: String, updatedAt: String,
        isStarred: Bool, isDeleted: Bool,
        thumbnailUrl: String?, downloadUrl: String?,
        children: [FileNode]?
    ) {
        self.id = id
        self.name = name
        self.size = size
        self.type = isFolder ? "folder" : "file"
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.nodeId = id
        self.parentId = parentId
        self.mimeType = mimeType
        self.isFolder = isFolder
        self.md5 = md5
        self.sha256 = sha256
        self.isStarred = isStarred
        self.isDeleted = isDeleted
        self.thumbnailUrl = thumbnailUrl
        self.downloadUrl = downloadUrl
        self.children = children
        self.category = isFolder ? .other : FileCategory.from(filename: name)
    }

    var formattedSize: String {
        let bytes = size
        if bytes < 1024 {
            return "\(bytes) B"
        } else if bytes < 1024 * 1024 {
            return String(format: "%.1f KB", Double(bytes) / 1024)
        } else if bytes < 1024 * 1024 * 1024 {
            return String(format: "%.1f MB", Double(bytes) / (1024 * 1024))
        } else {
            return String(format: "%.1f GB", Double(bytes) / (1024 * 1024 * 1024))
        }
    }

    var formattedDate: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm"
        if let date = ISO8601DateFormatter().date(from: createdAt) {
            return formatter.string(from: date)
        }
        return createdAt
    }
}
// MARK: - 分享链接模型

struct ShareLink: Codable, Identifiable {
    let id: String
    let nodeId: String
    let filename: String
    let shareUrl: String
    let password: String?
    let expireAt: String
    let createdAt: String
    let downloadCount: Int
}

// MARK: - 文件列表请求

struct FileListRequest: Codable {
    let parentId: String?
    let page: Int
    let pageSize: Int
    let sortBy: String?
    let sortOrder: String?
}

// MARK: - 创建文件夹请求

struct CreateFolderRequest: Codable {
    let name: String
    let parentId: String?
}

// MARK: - 重命名请求

struct RenameRequest: Codable {
    let newName: String
}

// MARK: - 移动请求

struct MoveRequest: Codable {
    let targetParentId: String
    let nodeIds: [String]
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
}

// MARK: - 收藏操作

struct StarRequest: Codable {
    let nodeId: String
    let starred: Bool
}

// MARK: - 回收站恢复

struct TrashActionRequest: Codable {
    let nodeIds: [String]
}
