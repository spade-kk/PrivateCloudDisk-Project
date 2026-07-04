import FileProvider
import UniformTypeIdentifiers
import Foundation

// MARK: - File Provider Item

/// 文件提供者项目，代表云盘中的一个文件或文件夹
///
/// 实现 NSFileProviderItemProtocol，为 File Provider 扩展提供文件元数据、
/// 能力声明、内容类型等核心信息。这是系统与扩展交互的数据模型。
///
/// 核心职责：
/// - 提供文件标识符、名称、大小等基本元数据
/// - 声明文件能力（读取、写入、重命名、移动、删除等）
/// - 管理同步状态（已上传、已下载、是否为最新版本）
/// - 提供版本信息用于增量同步
/// - 区分文件和文件夹的不同行为
final class FileProviderItem: NSObject, NSFileProviderItem {

    // MARK: - 标识与名称

    let itemIdentifier: NSFileProviderItemIdentifier
    let parentItemIdentifier: NSFileProviderItemIdentifier
    let filename: String

    // MARK: - 内容属性

    let contentType: UTType
    let documentSize: NSNumber?
    let creationDate: Date?
    let contentModificationDate: Date?

    // MARK: - 同步状态

    let isUploaded: Bool
    let isDownloaded: Bool
    let isMostRecentVersionDownloaded: Bool
    let isShared: Bool
    let uploadingError: Error?
    let downloadingError: Error?

    /// 关联的原始文件节点数据（用于子项计数等）
    let fileNode: FileNode?

    // MARK: - 类型判断

    var isFolder: Bool {
        contentType == .folder
    }

    // MARK: - 子项计数

    var childItemCount: NSNumber? {
        guard isFolder, let node = fileNode, let children = node.children else { return nil }
        return NSNumber(value: children.count)
    }

    // MARK: - 能力声明

    var capabilities: NSFileProviderItemCapabilities {
        var caps: NSFileProviderItemCapabilities = [.allowsReading]

        if isFolder {
            // 文件夹：允许添加子项、枚举内容、重命名、删除
            caps.insert(.allowsAddingSubItems)
            caps.insert(.allowsContentEnumerating)
            caps.insert(.allowsRenaming)
            caps.insert(.allowsDeleting)
            caps.insert(.allowsReparenting)
        } else {
            // 文件：允许写入、重命名、移动、删除
            caps.insert(.allowsWriting)
            caps.insert(.allowsRenaming)
            caps.insert(.allowsReparenting)
            caps.insert(.allowsDeleting)
        }

        // 如果已下载，允许从本地读取
        if isDownloaded {
            caps.insert(.allowsReading)
        }

        return caps
    }

    // MARK: - 版本信息

    var itemVersion: NSFileProviderItemVersion {
        let versionData = "\(itemIdentifier.rawValue)-\(contentModificationDate?.timeIntervalSince1970 ?? 0)"
            .data(using: .utf8)!
        return NSFileProviderItemVersion(
            contentVersion: versionData,
            metadataVersion: versionData
        )
    }

    // MARK: - 文件系统标志

    var fileSystemFlags: NSFileProviderFileSystemFlags {
        if isFolder {
            // 文件夹在文件系统层面标记为隐藏
            return [.hidden]
        }
        return []
    }

    // MARK: - 初始化

    init(
        itemIdentifier: NSFileProviderItemIdentifier,
        parentItemIdentifier: NSFileProviderItemIdentifier,
        filename: String,
        contentType: UTType,
        documentSize: NSNumber? = nil,
        creationDate: Date? = nil,
        contentModificationDate: Date? = nil,
        isUploaded: Bool = true,
        isDownloaded: Bool = false,
        isMostRecentVersionDownloaded: Bool = false,
        isShared: Bool = false,
        uploadingError: Error? = nil,
        downloadingError: Error? = nil,
        fileNode: FileNode? = nil
    ) {
        self.itemIdentifier = itemIdentifier
        self.parentItemIdentifier = parentItemIdentifier
        self.filename = filename
        self.contentType = contentType
        self.documentSize = documentSize
        self.creationDate = creationDate
        self.contentModificationDate = contentModificationDate
        self.isUploaded = isUploaded
        self.isDownloaded = isDownloaded
        self.isMostRecentVersionDownloaded = isMostRecentVersionDownloaded
        self.isShared = isShared
        self.uploadingError = uploadingError
        self.downloadingError = downloadingError
        self.fileNode = fileNode
        super.init()
    }

    /// 从 FileNode 网络模型构造 FileProviderItem
    convenience init(from node: FileNode, parentIdentifier: NSFileProviderItemIdentifier) {
        let itemId = NSFileProviderItemIdentifier(rawValue: node.id)

        // 根据文件扩展名确定 UTType
        let contentType: UTType = if node.isFolder {
            .folder
        } else {
            UTType(filenameExtension: (node.name as NSString).pathExtension) ?? .data
        }

        // 解析 ISO 8601 日期
        let dateFormatter = ISO8601DateFormatter()
        dateFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let created = node.createdAt.flatMap { dateFormatter.date(from: $0) }
        let modified = node.updatedAt.flatMap { dateFormatter.date(from: $0) }

        self.init(
            itemIdentifier: itemId,
            parentItemIdentifier: parentIdentifier,
            filename: node.name,
            contentType: contentType,
            documentSize: NSNumber(value: node.size),
            creationDate: created,
            contentModificationDate: modified,
            isUploaded: true,
            isDownloaded: false,
            isMostRecentVersionDownloaded: true,
            fileNode: node
        )
    }

    /// 创建根容器（在 Finder 侧边栏中显示为 "PrivateCloudDisk"）
    static func rootContainer() -> FileProviderItem {
        FileProviderItem(
            itemIdentifier: .rootContainer,
            parentItemIdentifier: .rootContainer,
            filename: "PrivateCloudDisk",
            contentType: .folder,
            isUploaded: true,
            isDownloaded: true,
            isMostRecentVersionDownloaded: true
        )
    }

    /// 创建回收站容器
    static func trashContainer() -> FileProviderItem {
        FileProviderItem(
            itemIdentifier: .trashContainer,
            parentItemIdentifier: .trashContainer,
            filename: "Trash",
            contentType: .folder,
            isUploaded: true,
            isDownloaded: true,
            isMostRecentVersionDownloaded: true
        )
    }

    /// 创建工作集容器
    static func workingSet() -> FileProviderItem {
        FileProviderItem(
            itemIdentifier: .workingSet,
            parentItemIdentifier: .workingSet,
            filename: "Working Set",
            contentType: .folder,
            isUploaded: true,
            isDownloaded: true,
            isMostRecentVersionDownloaded: true
        )
    }
}

// MARK: - FileNode（网络文件节点模型）

/// 从服务端 API 返回的文件/文件夹节点
struct FileNode: Codable {
    let id: String
    let name: String
    let parentId: String?
    let isFolder: Bool
    let size: Int64
    let mimeType: String?
    let createdAt: String?
    let updatedAt: String?
    let isDeleted: Bool?
    let children: [FileNode]?

    enum CodingKeys: String, CodingKey {
        case id, name, size, children
        case parentId = "parent_id"
        case isFolder = "is_folder"
        case mimeType = "mime_type"
        case createdAt = "created_at"
        case updatedAt = "updated_at"
        case isDeleted = "is_deleted"
    }
}