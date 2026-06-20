//
//  FileItem.swift
//  PrivateCloudDisk-ios
//
//  文件/文件夹节点模型
//

import Foundation
import UniformTypeIdentifiers

/// 文件/文件夹节点
struct FileNode: Codable, Identifiable, Hashable {
    let nodeId: String
    let nodeName: String
    let nodeType: NodeType
    let nodeSize: Int64?
    let createdAt: String?
    let updatedAt: String?
    let parentId: String?
    let fileId: String?
    let fileType: String?
    let mimeType: String?
    let path: String?

    enum NodeType: String, Codable {
        case folder = "FOLDER"
        case file = "FILE"
    }

    var id: String { nodeId }

    var isFolder: Bool { nodeType == .folder }
    var isVideo: Bool {
        guard let mime = mimeType ?? fileType else { return false }
        return mime.hasPrefix("video/") || ["mp4", "mov", "m4v", "avi", "mkv", "webm"].contains(mime.lowercased())
    }
    var isImage: Bool {
        guard let mime = mimeType ?? fileType else { return false }
        return mime.hasPrefix("image/") || ["jpg", "jpeg", "png", "gif", "heic", "webp"].contains(mime.lowercased())
    }
    var isAudio: Bool {
        guard let mime = mimeType ?? fileType else { return false }
        return mime.hasPrefix("audio/") || ["mp3", "wav", "aac", "m4a", "flac"].contains(mime.lowercased())
    }
    var isPDF: Bool {
        mimeType == "application/pdf" || fileType?.lowercased() == "pdf"
    }
    var isDocument: Bool {
        guard let mime = mimeType else { return false }
        return mime.contains("document") || mime.contains("spreadsheet") || mime.contains("presentation")
            || ["doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "rtf"].contains(fileType?.lowercased() ?? "")
    }
    var isArchive: Bool {
        guard let mime = mimeType ?? fileType else { return false }
        return ["zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso"].contains(mime.lowercased())
            || mime.contains("archive") || mime.contains("compressed")
    }
    var isCode: Bool {
        guard let ext = fileType?.lowercased() else { return false }
        return ["swift", "js", "ts", "py", "java", "go", "rs", "c", "cpp", "h", "html",
                "css", "scss", "json", "xml", "yaml", "yml", "sh", "rb", "php", "sql",
                "kt", "dart", "vue", "jsx", "tsx"].contains(ext)
    }
    var fileExtension: String {
        if isFolder { return "" }
        let name = nodeName
        guard let dotIndex = name.lastIndex(of: ".") else { return "" }
        return String(name[name.index(after: dotIndex)...]).lowercased()
    }
    var formattedSize: String {
        guard let size = nodeSize else { return "--" }
        return ByteCountFormatter.string(fromByteCount: size, countStyle: .file)
    }

    /// 系统文件图标
    var systemIcon: String {
        if isFolder { return "folder.fill" }
        if isVideo { return "play.rectangle.fill" }
        if isImage { return "photo.fill" }
        if isAudio { return "music.note.list" }
        if isPDF { return "doc.richtext.fill" }
        if isDocument { return "doc.fill" }
        return "doc.fill"
    }

    /// 文件图标颜色
    var iconColor: String {
        if isFolder { return "folder" }
        if isVideo { return "video" }
        if isImage { return "image" }
        if isAudio { return "audio" }
        if isPDF { return "pdf" }
        return "file"
    }

    enum CodingKeys: String, CodingKey {
        case nodeId = "node_id"
        case nodeName = "node_name"
        case nodeType = "node_type"
        case nodeSize = "node_size"
        case createdAt = "created_at"
        case updatedAt = "updated_at"
        case parentId = "parent_id"
        case fileId = "file_id"
        case fileType = "file_type"
        case mimeType = "mime_type"
        case path
    }
}

/// 文件详细信息（文件级别）
struct FileDetail: Codable, Identifiable {
    let fileId: String
    let fileName: String
    let fileSize: Int64
    let fileType: String
    let mimeType: String?
    let createdAt: String?
    let updatedAt: String?
    let nodeId: String?
    let checksum: String?

    var id: String { fileId }
    var isVideo: Bool {
        let mime = mimeType ?? fileType
        return mime.hasPrefix("video/") || ["mp4", "mov", "m4v", "avi", "mkv", "webm"].contains(mime.lowercased())
    }
    var formattedSize: String {
        ByteCountFormatter.string(fromByteCount: fileSize, countStyle: .file)
    }

    enum CodingKeys: String, CodingKey {
        case fileId = "file_id"
        case fileName = "file_name"
        case fileSize = "file_size"
        case fileType = "file_type"
        case mimeType = "mime_type"
        case createdAt = "created_at"
        case updatedAt = "updated_at"
        case nodeId = "node_id"
        case checksum
    }
}

/// 上传会话
struct UploadSession: Codable, Identifiable {
    let uploadsId: String
    let totalChunks: Int
    let fileSize: Int64
    let fileName: String
    let nodeId: String
    var uploadedChunks: Set<Int> = []

    var id: String { uploadsId }
    var progress: Double {
        guard totalChunks > 0 else { return 0 }
        return Double(uploadedChunks.count) / Double(totalChunks)
    }

    enum CodingKeys: String, CodingKey {
        case uploadsId = "uploads_id"
        case totalChunks = "total_chunks"
        case fileSize = "file_size"
        case fileName = "file_name"
        case nodeId = "node_id"
    }
}

/// 下载任务
struct DownloadTask: Codable, Identifiable {
    let fileId: String
    let fileName: String
    let fileSize: Int64
    let operationToken: String
    var downloadedBytes: Int64 = 0
    var status: DownloadStatus = .pending

    var id: String { fileId }
    var progress: Double {
        guard fileSize > 0 else { return 0 }
        return Double(downloadedBytes) / Double(fileSize)
    }
    var formattedSize: String {
        ByteCountFormatter.string(fromByteCount: fileSize, countStyle: .file)
    }

    enum DownloadStatus: String, Codable {
        case pending
        case downloading
        case paused
        case completed
        case failed
    }

    enum CodingKeys: String, CodingKey {
        case fileId = "file_id"
        case fileName = "file_name"
        case fileSize = "file_size"
        case operationToken = "operation_token"
        case downloadedBytes = "downloaded_bytes"
        case status
    }
}