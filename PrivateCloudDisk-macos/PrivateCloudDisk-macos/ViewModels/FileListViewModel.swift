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
    @Published var searchResults: [FileSearchResult] = []

    // 选择状态
    @Published var selectedNodeIds: Set<String> = []
    @Published var isSelectionMode = false

    // 右键菜单
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
            let result = try await fileService.listFiles(
                parentId: parentId,
                page: 1,
                pageSize: pageSize,
                sortBy: sortBy,
                sortOrder: sortOrder
            )
            files = result.items
            hasMorePages = result.totalPages > 1
        } catch {
            errorMessage = error.localizedDescription
        }

        isLoading = false
    }

    func loadMoreFiles() async {
        guard !isLoadingMore, hasMorePages else { return }

        isLoadingMore = true
        currentPage += 1

        do {
            let result = try await fileService.listFiles(
                parentId: currentParentId,
                page: currentPage,
                pageSize: pageSize,
                sortBy: sortBy,
                sortOrder: sortOrder
            )
            files.append(contentsOf: result.items)
            hasMorePages = currentPage < result.totalPages
        } catch {
            errorMessage = error.localizedDescription
            currentPage -= 1
        }

        isLoadingMore = false
    }

    func refreshFiles() async {
        await loadFiles(parentId: currentParentId)
    }

    // MARK: - 文件操作

    func createFolder(name: String) async {
        do {
            let folder = try await fileService.createFolder(name: name, parentId: currentParentId)
            files.insert(folder, at: 0)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func renameNode(nodeId: String, newName: String) async {
        do {
            let updated = try await fileService.rename(nodeId: nodeId, newName: newName)
            if let index = files.firstIndex(where: { $0.id == nodeId }) {
                files[index] = updated
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func deleteNodes(_ nodeIds: [String]) async {
        do {
            try await fileService.delete(nodeIds: nodeIds)
            files.removeAll { nodeIds.contains($0.id) }
            selectedNodeIds.removeAll()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func moveNodes(_ nodeIds: [String], to targetParentId: String) async {
        do {
            try await fileService.move(nodeIds: nodeIds, targetParentId: targetParentId)
            files.removeAll { nodeIds.contains($0.id) }
            selectedNodeIds.removeAll()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func toggleStar(nodeId: String, starred: Bool) async {
        do {
            try await fileService.toggleStar(nodeId: nodeId, starred: starred)
            if let index = files.firstIndex(where: { $0.id == nodeId }) {
                var node = files[index]
                node = FileNode(
                    id: node.id, name: node.name, parentId: node.parentId,
                    isFolder: node.isFolder, size: node.size, mimeType: node.mimeType,
                    md5: node.md5, sha256: node.sha256,
                    createdAt: node.createdAt, updatedAt: node.updatedAt,
                    isStarred: starred, isDeleted: node.isDeleted,
                    thumbnailUrl: node.thumbnailUrl, downloadUrl: node.downloadUrl,
                    children: node.children
                )
                files[index] = node
            }
        } catch {
            errorMessage = error.localizedDescription
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
            searchResults = try await fileService.search(query: searchQuery)
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
}