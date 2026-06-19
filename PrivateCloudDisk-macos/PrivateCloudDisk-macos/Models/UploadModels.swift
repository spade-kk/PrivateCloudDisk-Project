import Foundation

// MARK: - 上传模型

/// 上传初始化请求
struct UploadInitRequest: Codable {
    let filename: String
    let fileSize: Int64
    let mimeType: String
    let parentId: String?
    let chunkSize: Int?

    enum CodingKeys: String, CodingKey {
        case filename
        case fileSize = "file_size"
        case mimeType = "mime_type"
        case parentId = "parent_id"
        case chunkSize = "chunk_size"
    }
}

/// 上传初始化响应
struct UploadInitResponse: Codable {
    let uploadId: String
    let nodeId: String?
    let chunkSize: Int
    let totalChunks: Int
    let uploadedChunks: [Int]?

    enum CodingKeys: String, CodingKey {
        case uploadId = "upload_id"
        case nodeId = "node_id"
        case chunkSize = "chunk_size"
        case totalChunks = "total_chunks"
        case uploadedChunks = "uploaded_chunks"
    }
}

/// 上传进度信息
struct UploadProgress: Codable {
    let uploadId: String
    let filename: String
    let totalBytes: Int64
    let uploadedBytes: Int64
    let totalChunks: Int
    let completedChunks: Int
    let status: UploadStatus

    enum CodingKeys: String, CodingKey {
        case uploadId = "upload_id"
        case filename
        case totalBytes = "total_bytes"
        case uploadedBytes = "uploaded_bytes"
        case totalChunks = "total_chunks"
        case completedChunks = "completed_chunks"
        case status
    }

    var progress: Double {
        guard totalBytes > 0 else { return 0 }
        return Double(uploadedBytes) / Double(totalBytes)
    }
}

enum UploadStatus: String, Codable {
    case pending
    case uploading
    case completed
    case failed
    case cancelled
    case paused
}

// MARK: - 下载模型

/// 下载进度信息
struct DownloadProgress: Codable {
    let nodeId: String
    let filename: String
    let totalBytes: Int64
    let downloadedBytes: Int64
    let status: DownloadStatus

    enum CodingKeys: String, CodingKey {
        case nodeId = "node_id"
        case filename
        case totalBytes = "total_bytes"
        case downloadedBytes = "downloaded_bytes"
        case status
    }

    var progress: Double {
        guard totalBytes > 0 else { return 0 }
        return Double(downloadedBytes) / Double(totalBytes)
    }
}

enum DownloadStatus: String, Codable {
    case pending
    case downloading
    case completed
    case failed
    case cancelled
    case paused
}

// MARK: - 下载任务

/// 本地下载任务记录
final class DownloadTask: Identifiable, Codable {
    let id: String
    let nodeId: String
    let filename: String
    let remotePath: String
    let localPath: String
    var totalBytes: Int64
    var downloadedBytes: Int64
    var status: DownloadStatus
    let createdAt: Date
    var resumeData: Data?

    init(
        nodeId: String,
        filename: String,
        remotePath: String,
        localPath: String,
        totalBytes: Int64
    ) {
        self.id = UUID().uuidString
        self.nodeId = nodeId
        self.filename = filename
        self.remotePath = remotePath
        self.localPath = localPath
        self.totalBytes = totalBytes
        self.downloadedBytes = 0
        self.status = .pending
        self.createdAt = Date()
    }
}

// MARK: - 上传任务

/// 本地上传任务记录
final class UploadTask: Identifiable, Codable {
    let id: String
    let uploadId: String
    let filename: String
    let localPath: String
    let parentId: String?
    let totalBytes: Int64
    var uploadedBytes: Int64
    var status: UploadStatus
    let createdAt: Date
    var mimeType: String

    init(
        uploadId: String,
        filename: String,
        localPath: String,
        parentId: String?,
        totalBytes: Int64,
        mimeType: String
    ) {
        self.id = UUID().uuidString
        self.uploadId = uploadId
        self.filename = filename
        self.localPath = localPath
        self.parentId = parentId
        self.totalBytes = totalBytes
        self.uploadedBytes = 0
        self.status = .pending
        self.createdAt = Date()
        self.mimeType = mimeType
    }
}