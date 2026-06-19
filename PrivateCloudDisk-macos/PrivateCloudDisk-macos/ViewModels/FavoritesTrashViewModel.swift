import Foundation
import Combine
import SwiftUI

// MARK: - 收藏/回收站视图模型

@MainActor
final class FavoritesTrashViewModel: ObservableObject {

    @Published var items: [FileNode] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var selectedNodeIds: Set<String> = []
    @Published var isEmpty = true

    private let fileService = FileService.shared

    // MARK: - 收藏

    func loadFavorites() async {
        isLoading = true
        errorMessage = nil

        do {
            let result = try await fileService.getFavorites()
            items = result.items
            isEmpty = items.isEmpty
        } catch {
            errorMessage = error.localizedDescription
        }

        isLoading = false
    }

    func removeFromFavorites(nodeId: String) async {
        do {
            try await fileService.toggleStar(nodeId: nodeId, starred: false)
            items.removeAll { $0.id == nodeId }
            isEmpty = items.isEmpty
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    // MARK: - 回收站

    func loadTrash() async {
        isLoading = true
        errorMessage = nil

        do {
            let result = try await fileService.getTrash()
            items = result.items
            isEmpty = items.isEmpty
        } catch {
            errorMessage = error.localizedDescription
        }

        isLoading = false
    }

    func restoreNodes(_ nodeIds: [String]) async {
        do {
            try await fileService.restore(nodeIds: nodeIds)
            items.removeAll { nodeIds.contains($0.id) }
            isEmpty = items.isEmpty
            selectedNodeIds.removeAll()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func permanentDelete(_ nodeIds: [String]) async {
        do {
            try await fileService.permanentDelete(nodeIds: nodeIds)
            items.removeAll { nodeIds.contains($0.id) }
            isEmpty = items.isEmpty
            selectedNodeIds.removeAll()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func emptyTrash() async {
        do {
            try await fileService.emptyTrash()
            items.removeAll()
            isEmpty = true
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func toggleSelection(nodeId: String) {
        if selectedNodeIds.contains(nodeId) {
            selectedNodeIds.remove(nodeId)
        } else {
            selectedNodeIds.insert(nodeId)
        }
    }
}