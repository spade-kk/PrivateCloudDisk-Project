import Foundation
import Combine
import SwiftUI

// MARK: - 文件列表视图模型

@MainActor
final class FileListViewModel: ObservableObject {

    @Published var files: [FileNode] = []
    @Published var isLoading = false
    @Published var isLoadingMore = false
    @Published var errorMessage: String?
    @Published var currentParentId: String? = nil
    @Published var currentPage = 1
    @Published var hasMorePages = true
    @Published var sortBy = "updated_at"
    @Published var sortOrder = "desc"
    @Published var searchQuery = ""
    @Published var searchResults: [FileNode] = []

    @Published var selectedNodeIds: Set<String> = []
    @Published var isSelectionMode = false

    @Published var contextMenuNode: FileNode?

    private let fileService = FileService.shared
    private let pageSize = 50

    // MARK: - 加载文件

    func loadFiles(parentId: String? = nil) async {
        isLoading = true
        errorMessage = nil
        currentParentId = parentId
        currentPage = 1

        do {
            var nodeVOs: [NodeVO] = []

            if let parentId = parentId {
                nodeVOs = try await fileService.getNodeChildren(nodeId: parentId)
            } else {
                let rootNode = try await fileService.getRootNode()
                nodeVOs = try await fileService.getNodeChildren(nodeId: rootNode.nodeId)
            }

            files = nodeVOs.map { FileNode(nodeVO: $0) }
            hasMorePages = false
        } catch {
            errorMessage = error.localizedDescription
        }

        isLoading = false
    }

    func loadMoreFiles() async {
        guard !isLoadingMore, hasMorePages else { return }

        isLoadingMore = true
        currentPage += 1

        isLoadingMore = false
    }

    func refreshFiles() async {
        await loadFiles(parentId: currentParentId)
    }

    // MARK: - 文件操作

    func createFolder(name: String) async {
        do {
            try await fileService.createFolder(name: name, parentId: currentParentId)
            await refreshFiles()
            showSuccessToast("文件夹创建成功")
        } catch {
            errorMessage = error.localizedDescription
            showErrorToast(error.localizedDescription)
        }
    }

    func renameNode(nodeId: String, newName: String) async {
        do {
            if let node = files.first(where: { $0.id == nodeId }) {
                if node.isFolder {
                    try await fileService.renameFolder(nodeId: nodeId, newName: newName)
                } else {
                    try await fileService.renameFile(fileId: nodeId, newName: newName)
                }
                if let index = files.firstIndex(where: { $0.id == nodeId }) {
                    var updatedNode = files[index]
                    updatedNode = FileNode(
                        id: updatedNode.id, name: newName, parentId: updatedNode.parentId,
                        isFolder: updatedNode.isFolder, size: updatedNode.size, mimeType: updatedNode.mimeType,
                        md5: updatedNode.md5, sha256: updatedNode.sha256,
                        createdAt: updatedNode.createdAt, updatedAt: updatedNode.updatedAt,
                        isStarred: updatedNode.isStarred, isDeleted: updatedNode.isDeleted,
                        thumbnailUrl: updatedNode.thumbnailUrl, downloadUrl: updatedNode.downloadUrl,
                        children: updatedNode.children
                    )
                    files[index] = updatedNode
                }
                showSuccessToast("重命名成功")
            }
        } catch {
            errorMessage = error.localizedDescription
            showErrorToast(error.localizedDescription)
        }
    }

    func deleteNodes(_ nodeIds: [String]) async {
        do {
            for nodeId in nodeIds {
                if let node = files.first(where: { $0.id == nodeId }) {
                    if node.isFolder {
                        try await fileService.deleteFolder(nodeId: nodeId)
                    } else {
                        try await fileService.deleteFile(fileId: nodeId)
                    }
                }
            }
            files.removeAll { nodeIds.contains($0.id) }
            selectedNodeIds.removeAll()
            showSuccessToast("已移至回收站")
        } catch {
            errorMessage = error.localizedDescription
            showErrorToast(error.localizedDescription)
        }
    }

    func moveNodes(_ nodeIds: [String], to targetParentId: String) async {
        do {
            for nodeId in nodeIds {
                if let node = files.first(where: { $0.id == nodeId }) {
                    if node.isFolder {
                        try await fileService.moveFolder(nodeId: nodeId, targetPosition: targetParentId)
                    } else {
                        try await fileService.moveFile(fileId: nodeId, targetNodeId: targetParentId)
                    }
                }
            }
            files.removeAll { nodeIds.contains($0.id) }
            selectedNodeIds.removeAll()
            showSuccessToast("移动成功")
        } catch {
            errorMessage = error.localizedDescription
            showErrorToast(error.localizedDescription)
        }
    }

    func toggleStar(nodeId: String, starred: Bool) async {
        do {
            if let node = files.first(where: { $0.id == nodeId }) {
                if node.isFolder {
                    if starred {
                        try await fileService.addFolderStar(nodeId: nodeId)
                    } else {
                        try await fileService.removeFolderStar(nodeId: nodeId)
                    }
                } else {
                    if starred {
                        try await fileService.addFileStar(fileId: nodeId)
                    } else {
                        try await fileService.removeFileStar(fileId: nodeId)
                    }
                }
                if let index = files.firstIndex(where: { $0.id == nodeId }) {
                    var updatedNode = files[index]
                    updatedNode = FileNode(
                        id: updatedNode.id, name: updatedNode.name, parentId: updatedNode.parentId,
                        isFolder: updatedNode.isFolder, size: updatedNode.size, mimeType: updatedNode.mimeType,
                        md5: updatedNode.md5, sha256: updatedNode.sha256,
                        createdAt: updatedNode.createdAt, updatedAt: updatedNode.updatedAt,
                        isStarred: starred, isDeleted: updatedNode.isDeleted,
                        thumbnailUrl: updatedNode.thumbnailUrl, downloadUrl: updatedNode.downloadUrl,
                        children: updatedNode.children
                    )
                    files[index] = updatedNode
                }
                showSuccessToast(starred ? "已添加收藏" : "已取消收藏")
            }
        } catch {
            errorMessage = error.localizedDescription
            showErrorToast(error.localizedDescription)
        }
    }

    // MARK: - 搜索

    func search() async {
        guard !searchQuery.trimmingCharacters(in: .whitespaces).isEmpty else {
            searchResults = []
            return
        }

        isLoading = true
        do {
            let searchVO = try await fileService.searchFiles(keyword: searchQuery)
            searchResults = searchVO.hits.map { hit in
                FileNode(
                    id: hit.id,
                    name: hit.name,
                    parentId: nil,
                    isFolder: hit.isFolder,
                    size: hit.size,
                    mimeType: hit.mimeType,
                    md5: nil,
                    sha256: nil,
                    createdAt: "",
                    updatedAt: hit.updatedAt ?? "",
                    isStarred: false,
                    isDeleted: false,
                    thumbnailUrl: nil,
                    downloadUrl: nil,
                    children: nil
                )
            }
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    func clearSearch() {
        searchQuery = ""
        searchResults = []
    }

    // MARK: - 选择

    func toggleSelection(nodeId: String) {
        if selectedNodeIds.contains(nodeId) {
            selectedNodeIds.remove(nodeId)
        } else {
            selectedNodeIds.insert(nodeId)
        }
        isSelectionMode = !selectedNodeIds.isEmpty
    }

    func clearSelection() {
        selectedNodeIds.removeAll()
        isSelectionMode = false
    }

    func selectAll() {
        selectedNodeIds = Set(files.map { $0.id })
        isSelectionMode = true
    }

    // MARK: - Toast 通知

    private func showErrorToast(_ message: String) {
        ContentViewModel.shared.showToast(message, type: .error)
    }

    private func showSuccessToast(_ message: String) {
        ContentViewModel.shared.showToast(message, type: .success)
    }
}