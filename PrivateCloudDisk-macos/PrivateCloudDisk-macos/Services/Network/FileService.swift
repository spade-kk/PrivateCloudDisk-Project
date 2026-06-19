import Foundation

// MARK: - 文件服务

/// 文件/节点管理服务
///
/// 提供文件 CRUD、搜索、收藏、回收站等功能
final class FileService {

    static let shared = FileService()
    private let api = APIClient.shared

    private init() {}

    // MARK: - 文件列表

    /// 获取文件列表（分页）
    func listFiles(parentId: String? = nil, page: Int = 1, pageSize: Int = 50,
                   sortBy: String = "updated_at", sortOrder: String = "desc") async throws -> PaginatedData<FileNode> {
        let request = FileListRequest(
            parentId: parentId,
            page: page,
            pageSize: pageSize,
            sortBy: sortBy,
            sortOrder: sortOrder
        )
        let response: PaginatedResponse<FileNode> = try await api.post("/api/files/list", body: request)
        guard let data = response.data else {
            throw ApiError.serverError(response.code, response.message)
        }
        return data
    }

    /// 获取文件详情
    func getFileDetail(nodeId: String) async throws -> FileNode {
        return try await api.get("/api/files/\(nodeId)")
    }

    // MARK: - 文件夹操作

    /// 创建文件夹
    func createFolder(name: String, parentId: String?) async throws -> FileNode {
        let request = CreateFolderRequest(name: name, parentId: parentId)
        return try await api.post("/api/folders", body: request)
    }

    // MARK: - 文件操作

    /// 重命名
    func rename(nodeId: String, newName: String) async throws -> FileNode {
        let request = RenameRequest(newName: newName)
        return try await api.put("/api/files/\(nodeId)/rename", body: request)
    }

    /// 移动
    func move(nodeIds: [String], targetParentId: String) async throws -> EmptyResponse {
        let request = MoveRequest(targetParentId: targetParentId, nodeIds: nodeIds)
        return try await api.post("/api/files/move", body: request)
    }

    /// 删除（移到回收站）
    func delete(nodeIds: [String]) async throws -> EmptyResponse {
        let request = TrashActionRequest(nodeIds: nodeIds)
        return try await api.post("/api/files/delete", body: request)
    }

    /// 永久删除
    func permanentDelete(nodeIds: [String]) async throws -> EmptyResponse {
        let request = TrashActionRequest(nodeIds: nodeIds)
        return try await api.post("/api/files/permanent-delete", body: request)
    }

    // MARK: - 收藏

    /// 获取收藏列表
    func getFavorites(page: Int = 1, pageSize: Int = 50) async throws -> PaginatedData<FileNode> {
        return try await api.get("/api/files/favorites", params: [
            "page": "\(page)",
            "page_size": "\(pageSize)"
        ])
    }

    /// 切换收藏状态
    func toggleStar(nodeId: String, starred: Bool) async throws -> EmptyResponse {
        let request = StarRequest(nodeId: nodeId, starred: starred)
        return try await api.post("/api/files/star", body: request)
    }

    // MARK: - 回收站

    /// 获取回收站列表
    func getTrash(page: Int = 1, pageSize: Int = 50) async throws -> PaginatedData<FileNode> {
        return try await api.get("/api/files/trash", params: [
            "page": "\(page)",
            "page_size": "\(pageSize)"
        ])
    }

    /// 从回收站恢复
    func restore(nodeIds: [String]) async throws -> EmptyResponse {
        let request = TrashActionRequest(nodeIds: nodeIds)
        return try await api.post("/api/files/restore", body: request)
    }

    /// 清空回收站
    func emptyTrash() async throws -> EmptyResponse {
        return try await api.delete("/api/files/trash")
    }

    // MARK: - 搜索

    /// 搜索文件
    func search(query: String, page: Int = 1, pageSize: Int = 50) async throws -> [FileSearchResult] {
        struct SearchResponse: Codable {
            let items: [FileSearchResult]
            let total: Int
        }
        let response: SearchResponse = try await api.get("/api/files/search", params: [
            "q": query,
            "page": "\(page)",
            "page_size": "\(pageSize)"
        ])
        return response.items
    }

    // MARK: - 分享

    /// 创建分享链接
    func createShareLink(nodeId: String, expireDays: Int = 7, password: String? = nil) async throws -> ShareLink {
        struct CreateShareRequest: Encodable {
            let nodeId: String
            let expireDays: Int
            let password: String?
        }
        let request = CreateShareRequest(nodeId: nodeId, expireDays: expireDays, password: password)
        return try await api.post("/api/share/create", body: request)
    }

    /// 获取分享链接列表
    func getShareLinks() async throws -> [ShareLink] {
        return try await api.get("/api/share/list")
    }

    /// 删除分享链接
    func deleteShareLink(shareId: String) async throws -> EmptyResponse {
        return try await api.delete("/api/share/\(shareId)")
    }
}

// MARK: - 分享链接模型

struct ShareLink: Codable, Identifiable {
    let id: String
    let nodeId: String
    let filename: String
    let shareUrl: String
    let password: String?
    let expireAt: String
    let createdAt: String
    let downloadCount: Int

    enum CodingKeys: String, CodingKey {
        case id
        case nodeId = "node_id"
        case filename
        case shareUrl = "share_url"
        case password
        case expireAt = "expire_at"
        case createdAt = "created_at"
        case downloadCount = "download_count"
    }
}