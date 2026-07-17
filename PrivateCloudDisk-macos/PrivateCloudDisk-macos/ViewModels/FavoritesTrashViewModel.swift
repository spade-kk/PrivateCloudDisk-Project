import Foundation
import Combine
import SwiftUI

// MARK: - 收藏/回收站视图模型

@MainActor
final class FavoritesTrashViewModel: ObservableObject {

    enum Mode { case favorites, trash }

    @Published var mode: Mode = .favorites
    @Published var items: [FileNode] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var selectedNodeIds: Set<String> = []
    @Published var isEmpty = true

    private let fileService = FileService.shared

    // MARK: - 通用加载方法

    func loadData(mode: Mode) async {
        self.mode = mode
        switch mode {
        case .favorites: await loadFavorites()
        case .trash: await loadTrash()
        }
    }

    // MARK: - 收藏

    func loadFavorites() async {
        mode = .favorites
        isLoading = true
        errorMessage = nil

        do {
            let starredItems = try await fileService.getStarredItems()
            items = starredItems.map { FileNode(fileStarVO: $0) }
            isEmpty = items.isEmpty
        } catch {
            errorMessage = error.localizedDescription
            showErrorToast(error.localizedDescription)
        }

        isLoading = false
    }

    func removeFromFavorites(nodeId: String) async {
        guard mode == .favorites else { return }

        do {
            if let item = items.first(where: { $0.id == nodeId }) {
                if item.isFolder {
                    try await fileService.removeFolderStar(nodeId: nodeId)
                } else {
                    try await fileService.removeFileStar(fileId: nodeId)
                }
                items.removeAll { $0.id == nodeId }
                isEmpty = items.isEmpty
                selectedNodeIds.remove(nodeId)
            }
        } catch {
            errorMessage = error.localizedDescription
            showErrorToast(error.localizedDescription)
        }
    }

    // MARK: - 回收站

    func loadTrash() async {
        mode = .trash
        isLoading = true
        errorMessage = nil

        do {
            let trashItems = try await fileService.getTrashItems()
            items = trashItems.map { FileNode(trashTargetVO: $0) }
            isEmpty = items.isEmpty
        } catch {
            errorMessage = error.localizedDescription
            showErrorToast(error.localizedDescription)
        }

        isLoading = false
    }

    func restoreNodes(_ nodeIds: [String]) async {
        guard mode == .trash else { return }

        do {
            for nodeId in nodeIds {
                try await fileService.restoreTrashItem(trashId: nodeId)
            }
            items.removeAll { nodeIds.contains($0.id) }
            isEmpty = items.isEmpty
            selectedNodeIds.removeAll()
            showSuccessToast("恢复成功")
        } catch {
            errorMessage = error.localizedDescription
            showErrorToast(error.localizedDescription)
        }
    }

    func permanentDelete(_ nodeIds: [String]) async {
        guard mode == .trash else { return }

        do {
            for nodeId in nodeIds {
                try await fileService.deleteTrashItem(trashId: nodeId)
            }
            items.removeAll { nodeIds.contains($0.id) }
            isEmpty = items.isEmpty
            selectedNodeIds.removeAll()
            showSuccessToast("删除成功")
        } catch {
            errorMessage = error.localizedDescription
            showErrorToast(error.localizedDescription)
        }
    }

    func emptyTrash() async {
        guard mode == .trash else { return }

        do {
            try await fileService.emptyTrash()
            items.removeAll()
            isEmpty = true
            selectedNodeIds.removeAll()
            showSuccessToast("回收站已清空")
        } catch {
            errorMessage = error.localizedDescription
            showErrorToast(error.localizedDescription)
        }
    }

    // MARK: - 选择

    func toggleSelection(nodeId: String) {
        if selectedNodeIds.contains(nodeId) {
            selectedNodeIds.remove(nodeId)
        } else {
            selectedNodeIds.insert(nodeId)
        }
    }

    func clearSelection() {
        selectedNodeIds.removeAll()
    }

    // MARK: - Toast 通知

    private func showErrorToast(_ message: String) {
        ContentViewModel.shared.showToast(message, type: .error)
    }

    private func showSuccessToast(_ message: String) {
        ContentViewModel.shared.showToast(message, type: .success)
    }
}

// MARK: - ContentViewModel 单例扩展

extension ContentViewModel {
    static let shared = ContentViewModel()
}