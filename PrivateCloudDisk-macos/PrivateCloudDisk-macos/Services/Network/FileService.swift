import Foundation

// MARK: - 文件服务（与 Vue 3 API 完全对齐）

/// 文件服务，处理文件和节点相关 API 调用
final class FileService {

    static let shared = FileService()
    private let api = APIClient.shared

    private init() {}

    // MARK: - 节点查询

    func getRootNode() async throws -> FolderNodeVO {
        return try await api.get("business/nodes/root")
    }

    func getNodeChildren(nodeId: String) async throws -> [NodeVO] {
        return try await api.get("business/nodes/\(nodeId)/children")
    }

    func getNodePath(nodeId: String) async throws -> PathChildrenVO {
        return try await api.get("business/nodes/\(nodeId)/path")
    }

    // MARK: - 文件夹操作

    func createFolder(name: String, parentId: String?) async throws {
        struct CreateFolderRequest: Codable {
            let nodeId: String?
            let folderName: String
        }
        let request = CreateFolderRequest(nodeId: parentId, folderName: name)
        try await api.post("business/nodes/", body: request) as EmptyBody
    }

    func deleteFolder(nodeId: String) async throws {
        try await api.delete("business/nodes/\(nodeId)") as EmptyBody
    }

    func renameFolder(nodeId: String, newName: String) async throws {
        struct RenameRequest: Codable {
            let newNodeName: String
        }
        let request = RenameRequest(newNodeName: newName)
        try await api.patch("business/nodes/\(nodeId)/name", body: request) as EmptyBody
    }

    func moveFolder(nodeId: String, targetPosition: String) async throws {
        struct MoveRequest: Codable {
            let targetPosition: String
        }
        let request = MoveRequest(targetPosition: targetPosition)
        try await api.patch("business/nodes/\(nodeId)/position", body: request) as EmptyBody
    }

    // MARK: - 文件操作

    func deleteFile(fileId: String) async throws {
        try await api.post("business/trash/files/\(fileId)", body: EmptyBody()) as EmptyBody
    }

    func renameFile(fileId: String, newName: String) async throws {
        struct RenameRequest: Codable {
            let newName: String
        }
        let request = RenameRequest(newName: newName)
        try await api.patch("business/files/\(fileId)/name", body: request) as EmptyBody
    }

    func moveFile(fileId: String, targetNodeId: String) async throws {
        struct MoveRequest: Codable {
            let targetNodeId: String
        }
        let request = MoveRequest(targetNodeId: targetNodeId)
        try await api.patch("business/files/\(fileId)/position", body: request) as EmptyBody
    }

    // MARK: - 收藏操作

    func getStarredItems() async throws -> [FileStarVO] {
        return try await api.get("business/stars/")
    }

    func addFileStar(fileId: String) async throws {
        try await api.post("business/stars/files/\(fileId)", body: EmptyBody()) as EmptyBody
    }

    func removeFileStar(fileId: String) async throws {
        try await api.delete("business/stars/files/\(fileId)") as EmptyBody
    }

    func addFolderStar(nodeId: String) async throws {
        try await api.post("business/stars/folders/\(nodeId)", body: EmptyBody()) as EmptyBody
    }

    func removeFolderStar(nodeId: String) async throws {
        try await api.delete("business/stars/folders/\(nodeId)") as EmptyBody
    }

    // MARK: - 回收站操作

    func getTrashItems() async throws -> [TrashTargetVO] {
        return try await api.get("business/trash/")
    }

    func restoreTrashItem(trashId: String) async throws {
        try await api.post("business/trash/\(trashId)/restore", body: EmptyBody()) as EmptyBody
    }

    func deleteFolderFromTrash(nodeId: String) async throws {
        try await api.delete("business/trash/folders/\(nodeId)") as EmptyBody
    }

    func deleteFileFromTrash(fileId: String) async throws {
        try await api.delete("business/trash/files/\(fileId)") as EmptyBody
    }

    func clearTrash() async throws {
        try await api.delete("business/trash/") as EmptyBody
    }

    func deleteTrashItem(trashId: String) async throws {
        try await api.delete("business/trash/\(trashId)") as EmptyBody
    }

    func emptyTrash() async throws {
        try await clearTrash()
    }

    func getFileDetail(fileId: String) async throws -> FileVO {
        return try await api.get("business/files/\(fileId)")
    }

    func getShareLinks(fileId: String) async throws -> [ShareLink] {
        return try await api.get("business/shares/files/\(fileId)")
    }

    func createShareLink(fileId: String, permission: String, expiresAt: String?) async throws -> ShareLink {
        struct CreateShareRequest: Codable {
            let permission: String
            let expiresAt: String?
        }
        let request = CreateShareRequest(permission: permission, expiresAt: expiresAt)
        return try await api.post("business/shares/files/\(fileId)", body: request)
    }

    func deleteShareLink(shareId: String) async throws {
        try await api.delete("business/shares/\(shareId)") as EmptyBody
    }

    func searchFiles(keyword: String, page: Int = 1, pageSize: Int = 50) async throws -> SearchResponse {
        var params: [String: String] = [
            "keyword": keyword,
            "page": "\(page)",
            "pageSize": "\(pageSize)"
        ]
        return try await api.get("business/nodes/search", params: params)
    }
}

struct SearchResponse: Codable {
    let hits: [SearchHit]
    let total: Int
}

struct SearchHit: Codable {
    let id: String
    let name: String
    let isFolder: Bool
    let size: Int64
    let mimeType: String?
    let updatedAt: String?
}