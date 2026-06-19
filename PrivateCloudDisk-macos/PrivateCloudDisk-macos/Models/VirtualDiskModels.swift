import Foundation

// MARK: - 虚拟磁盘模型

/// 虚拟磁盘状态
enum VirtualDiskStatus: String, Codable {
    case disconnected
    case connecting
    case connected
    case syncing
    case paused
    case error
    case unmounting

    var displayName: String {
        switch self {
        case .disconnected: return "未挂载"
        case .connecting: return "正在连接..."
        case .connected: return "已挂载"
        case .syncing: return "同步中..."
        case .paused: return "已暂停"
        case .error: return "错误"
        case .unmounting: return "正在卸载..."
        }
    }

    var sfSymbolName: String {
        switch self {
        case .disconnected: return "externaldrive.badge.timemachine"
        case .connecting: return "externaldrive.badge.plus"
        case .connected: return "externaldrive.fill"
        case .syncing: return "arrow.triangle.2.circlepath.externaldrive"
        case .paused: return "externaldrive.badge.minus"
        case .error: return "externaldrive.badge.exclamationmark"
        case .unmounting: return "externaldrive.badge.xmark"
        }
    }
}

/// 虚拟磁盘配置
struct VirtualDiskConfig: Codable {
    var mountPoint: String          // 挂载点目录（本地路径）
    var displayName: String         // 在 Finder 中显示的名称
    var apiBaseUrl: String          // 后端 API 地址
    var token: String               // 认证 Token
    var userId: String              // 当前用户 ID
    var quota: QuotaInfo?           // 配额信息
    var cacheMaxSize: Int64         // 最大缓存大小（字节）
    var autoSync: Bool              // 是否自动同步
    var syncInterval: TimeInterval  // 同步间隔（秒）

    static let `default` = VirtualDiskConfig(
        mountPoint: NSHomeDirectory() + "/PrivateCloudDisk",
        displayName: "PrivateCloudDisk",
        apiBaseUrl: "http://localhost:8000",
        token: "",
        userId: "",
        quota: nil,
        cacheMaxSize: 5 * 1024 * 1024 * 1024, // 5GB
        autoSync: true,
        syncInterval: 30
    )
}

/// 同步事件
struct SyncEvent: Codable {
    let type: SyncEventType
    let nodeId: String?
    let path: String?
    let timestamp: Date
    let details: String?

    enum SyncEventType: String, Codable {
        case fileCreated
        case fileModified
        case fileDeleted
        case fileRenamed
        case fileMoved
        case syncStarted
        case syncCompleted
        case syncFailed
        case conflictDetected
        case cacheEvicted
    }
}

/// 缓存条目
struct CacheEntry: Codable {
    let nodeId: String
    let localPath: String
    let size: Int64
    let lastAccessed: Date
    let lastModified: Date
    let isDirty: Bool  // 是否有未同步的本地修改
}

/// 文件占位符信息（用于 File Provider）
struct PlaceholderInfo: Codable {
    let nodeId: String
    let filename: String
    let parentId: String?
    let size: Int64
    let mimeType: String?
    let isFolder: Bool
    let createdAt: Date
    let modifiedAt: Date
    let isDownloaded: Bool  // 内容是否已下载到本地
    let isUploaded: Bool    // 内容是否已上传到云端
    let downloadProgress: Double?  // 下载进度 0.0-1.0
    let uploadProgress: Double?    // 上传进度 0.0-1.0
}