import Foundation
import Combine
import SwiftUI

// MARK: - 文件详情视图模型

@MainActor
final class FileDetailViewModel: ObservableObject {

    @Published var node: FileNode?
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var shareLinks: [ShareLink] = []
    @Published var isDownloading = false
    @Published var downloadProgress: Double = 0

    private let fileService = FileService.shared
    private let downloadManager = DownloadManager.shared
    private let spotlightIndexer = SpotlightIndexer.shared

    // MARK: - 加载详情

    func loadDetail(nodeId: String) async {
        isLoading = true
        errorMessage = nil

        do {
            node = try await fileService.getFileDetail(nodeId: nodeId)
            // 索引到 Spotlight
            if let node = node {
                spotlightIndexer.indexFile(node)
            }
        } catch {
            errorMessage = error.localizedDescription
        }

        isLoading = false
    }

    // MARK: - 下载

    func downloadFile() async {
        guard let node = node, !node.isFolder else { return }

        isDownloading = true
        do {
            _ = try await downloadManager.downloadFile(
                nodeId: node.id,
                filename: node.name
            )
        } catch {
            errorMessage = error.localizedDescription
        }
        isDownloading = false
    }

    // MARK: - 分享

    func loadShareLinks() async {
        do {
            shareLinks = try await fileService.getShareLinks()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func createShareLink(expireDays: Int = 7, password: String? = nil) async {
        guard let node = node else { return }

        do {
            let link = try await fileService.createShareLink(
                nodeId: node.id,
                expireDays: expireDays,
                password: password
            )
            shareLinks.insert(link, at: 0)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func deleteShareLink(shareId: String) async {
        do {
            try await fileService.deleteShareLink(shareId: shareId)
            shareLinks.removeAll { $0.id == shareId }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    /// 复制分享链接到剪贴板
    func copyShareLink(_ url: String) {
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(url, forType: .string)
    }
}