import Foundation
import CoreSpotlight
import UniformTypeIdentifiers

// MARK: - Spotlight 索引器

/// macOS Spotlight 搜索集成
///
/// 将虚拟磁盘中的文件索引到 Spotlight，使文件可以在系统全局搜索中找到
/// 这是 macOS 独有的强大功能，Windows 无直接对应
///
/// 实现方式：
/// - CSSearchableIndex 索引文件元数据
/// - 支持文件名、内容摘要、标签搜索
/// - 搜索结果可点击打开（通过 URL Scheme）
final class SpotlightIndexer {

    static let shared = SpotlightIndexer()

    private let index = CSSearchableIndex.default()

    private init() {}

    // MARK: - 索引文件

    /// 索引单个文件
    func indexFile(_ node: FileNode) {
        let attributeSet = CSSearchableItemAttributeSet(contentType: utType(for: node.mimeType))
        attributeSet.title = node.name
        attributeSet.displayName = node.name
        attributeSet.contentDescription = "PrivateCloudDisk 文件"
        attributeSet.fileSize = NSNumber(value: node.size)
        attributeSet.contentCreationDate = parseDate(node.createdAt)
        attributeSet.contentModificationDate = parseDate(node.updatedAt)
        attributeSet.keywords = ["PrivateCloudDisk", "私有云", "云盘"]
        attributeSet.addedDate = Date()

        let item = CSSearchableItem(
            uniqueIdentifier: "pcd.file.\(node.id)",
            domainIdentifier: "com.privateclouddisk.files",
            attributeSet: attributeSet
        )
        item.expirationDate = Date().addingTimeInterval(30 * 24 * 3600) // 30 天

        index.indexSearchableItems([item]) { error in
            if let error = error {
                print("[Spotlight] 索引失败: \(error.localizedDescription)")
            }
        }
    }

    /// 索引多个文件
    func indexFiles(_ nodes: [FileNode]) {
        let items = nodes.map { node -> CSSearchableItem in
            let attributeSet = CSSearchableItemAttributeSet(contentType: utType(for: node.mimeType))
            attributeSet.title = node.name
            attributeSet.displayName = node.name
            attributeSet.fileSize = NSNumber(value: node.size)
            attributeSet.contentModificationDate = parseDate(node.updatedAt)
            attributeSet.keywords = ["PrivateCloudDisk", "私有云"]

            return CSSearchableItem(
                uniqueIdentifier: "pcd.file.\(node.id)",
                domainIdentifier: "com.privateclouddisk.files",
                attributeSet: attributeSet
            )
        }

        index.indexSearchableItems(items) { error in
            if let error = error {
                print("[Spotlight] 批量索引失败: \(error.localizedDescription)")
            }
        }
    }

    /// 删除文件索引
    func deindexFile(nodeId: String) {
        index.deleteSearchableItems(withIdentifiers: ["pcd.file.\(nodeId)"]) { error in
            if let error = error {
                print("[Spotlight] 删除索引失败: \(error.localizedDescription)")
            }
        }
    }

    /// 清除所有索引
    func clearAllIndex() {
        index.deleteSearchableItems(
            withDomainIdentifiers: ["com.privateclouddisk.files"]
        ) { error in
            if let error = error {
                print("[Spotlight] 清除索引失败: \(error.localizedDescription)")
            }
        }
    }

    // MARK: - 辅助方法

    private func utType(for mimeType: String?) -> UTType {
        guard let mime = mimeType else { return .data }
        return UTType(mimeType: mime) ?? .data
    }

    private func parseDate(_ dateString: String?) -> Date? {
        guard let dateStr = dateString else { return nil }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.date(from: dateStr)
    }
}