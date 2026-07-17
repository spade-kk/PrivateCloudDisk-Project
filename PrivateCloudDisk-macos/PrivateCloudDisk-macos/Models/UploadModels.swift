import Foundation

// MARK: - 上传模型

struct UploadInitRequest: Codable {
    let totalChunks: Int
    let fileSize: Int64
    let fileChecksum: String
    let chunksMaxSize: Int
    let fileType: String
    let fileName: String
    let nodeId: String?
}

struct UploadInitResponse: Codable {
    let uploadId: String
    let nodeId: String?
    let chunksMaxSize: Int
    let uploadedChunks: [Int]

    var chunkSize: Int { chunksMaxSize }
    var totalChunks: Int { 0 }
}

struct UploadCompleteResponse: Codable {
    let backendTaskId: String
}

struct UploadProgress: Codable {
    let uploadId: String
    let filename: String
    let totalBytes: Int64
    let uploadedBytes: Int64
    let totalChunks: Int
    let completedChunks: Int
    let status: UploadStatus

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

struct DownloadProgress: Codable {
    let nodeId: String
    let filename: String
    let totalBytes: Int64
    let downloadedBytes: Int64
    let status: DownloadStatus

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
    var backendTaskId: String?

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
        self.backendTaskId = nil
    }
}