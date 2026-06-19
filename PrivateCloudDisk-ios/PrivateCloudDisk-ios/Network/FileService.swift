//
//  FileService.swift
//  PrivateCloudDisk-ios
//
//  文件管理网络服务 — 封装文件/节点 CRUD API
//

import Foundation

actor FileService {
    static let shared = FileService()
    private let client = APIClient.shared

    private init() {}

    // MARK: - 节点操作

    /// 获取根节点
    func getRootNode() async throws -> FileNode {
        let resp: APIResponse<FileNode> = try await client.request(.get, path: "/business/nodes/root")
        guard let data = resp.data else { throw APIError.noData }
        return data
    }

    /// 获取节点信息
    func getNodeInfo(nodeId: String) async throws -> FileNode {
        let resp: APIResponse<FileNode> = try await client.request(.get, path: "/business/nodes/\(nodeId)")
        guard let data = resp.data else { throw APIError.noData }
        return data
    }

    /// 获取子节点列表
    func getNodeChildren(nodeId: String) async throws -> [FileNode] {
        let resp: APIResponse<[FileNode]> = try await client.request(.get, path: "/business/nodes/\(nodeId)/children")
        return resp.data ?? []
    }

    /// 创建文件夹
    func createFolder(nodeId: String, folderName: String) async throws -> FileNode {
        struct CreateFolderBody: Encodable {
            let nodeId: String
            let folderName: String
            enum CodingKeys: String, CodingKey {
                case nodeId = "node_id"
                case folderName = "folder_name"
            }
        }
        let resp: APIResponse<FileNode> = try await client.request(
            .post, path: "/business/nodes/",
            body: CreateFolderBody(nodeId: nodeId, folderName: folderName)
        )
        guard let data = resp.data else { throw APIError.noData }
        return data
    }

    /// 删除节点
    func deleteNode(nodeId: String) async throws {
        let _: APIEmptyResponse = try await client.request(.delete, path: "/business/nodes/\(nodeId)")
    }

    /// 重命名节点
    func renameNode(nodeId: String, newName: String) async throws {
        struct RenameBody: Encodable {
            let newName: String
            enum CodingKeys: String, CodingKey { case newName = "new_name" }
        }
        let _: APIEmptyResponse = try await client.request(
            .patch, path: "/business/nodes/\(nodeId)/name",
            body: RenameBody(newName: newName)
        )
    }

    /// 移动节点
    func moveNode(nodeId: String, targetNodeId: String) async throws {
        struct MoveBody: Encodable {
            let targetNodeId: String
            enum CodingKeys: String, CodingKey { case targetNodeId = "target_node_id" }
        }
        let _: APIEmptyResponse = try await client.request(
            .patch, path: "/business/nodes/\(nodeId)/position",
            body: MoveBody(targetNodeId: targetNodeId)
        )
    }

    // MARK: - 文件操作

    /// 获取文件信息
    func getFileInfo(fileId: String) async throws -> FileDetail {
        let resp: APIResponse<FileDetail> = try await client.request(.get, path: "/business/files/\(fileId)")
        guard let data = resp.data else { throw APIError.noData }
        return data
    }

    /// 移动文件
    func moveFile(fileId: String, targetNodeId: String) async throws {
        struct MoveFileBody: Encodable {
            let targetNodeId: String
            enum CodingKeys: String, CodingKey { case targetNodeId = "target_node_id" }
        }
        let _: APIEmptyResponse = try await client.request(
            .patch, path: "/business/files/\(fileId)/position",
            body: MoveFileBody(targetNodeId: targetNodeId)
        )
    }

    /// 重命名文件
    func renameFile(fileId: String, newName: String) async throws {
        struct RenameBody: Encodable {
            let newName: String
            enum CodingKeys: String, CodingKey { case newName = "new_name" }
        }
        let _: APIEmptyResponse = try await client.request(
            .patch, path: "/business/files/\(fileId)/name",
            body: RenameBody(newName: newName)
        )
    }

    /// 删除文件
    func deleteFile(fileId: String) async throws {
        let _: APIEmptyResponse = try await client.request(.delete, path: "/business/files/\(fileId)/")
    }

    // MARK: - 上传

    /// 创建上传会话
    func createUploadSession(
        totalChunks: Int, fileSize: Int64, checksum: String,
        chunkSize: Int, fileType: String, fileName: String, nodeId: String
    ) async throws -> UploadSession {
        struct CreateUploadBody: Encodable {
            let totalChunks: Int; let fileSize: Int64; let fileChecksum: String
            let chunksMaxSize: Int; let fileType: String; let fileName: String; let nodeId: String
            enum CodingKeys: String, CodingKey {
                case totalChunks = "total_chunks"; case fileSize = "file_size"
                case fileChecksum = "file_checksum"; case chunksMaxSize = "chunks_max_size"
                case fileType = "file_type"; case fileName = "file_name"; case nodeId = "node_id"
            }
        }
        let resp: APIResponse<UploadSession> = try await client.request(
            .post, path: "/business/uploads/",
            body: CreateUploadBody(
                totalChunks: totalChunks, fileSize: fileSize, fileChecksum: checksum,
                chunksMaxSize: chunkSize, fileType: fileType, fileName: fileName, nodeId: nodeId
            )
        )
        guard let data = resp.data else { throw APIError.noData }
        return data
    }

    /// 上传分片
    func uploadChunk(uploadsId: String, chunkIndex: Int, chunkData: Data) async throws {
        let _ = try await client.upload(
            path: "/business/uploads/\(uploadsId)/chunks",
            fileData: chunkData,
            fileName: "chunk_\(chunkIndex)",
            mimeType: "application/octet-stream",
            extraFields: ["chunk_index": "\(chunkIndex)"]
        )
    }

    /// 完成上传会话
    func completeUploadSession(uploadsId: String) async throws {
        let _: APIEmptyResponse = try await client.request(.post, path: "/business/uploads/\(uploadsId)/complete")
    }

    // MARK: - 下载

    /// 创建操作令牌
    func createOperationToken(fileId: String, operationType: String) async throws -> String {
        struct TokenBody: Encodable {
            let fileId: String; let operationType: String
            enum CodingKeys: String, CodingKey {
                case fileId = "file_id"; case operationType = "operation_type"
            }
        }
        struct TokenResp: Codable {
            let operationToken: String
            enum CodingKeys: String, CodingKey { case operationToken = "operation_token" }
        }
        let resp: APIResponse<TokenResp> = try await client.request(
            .post, path: "/files/operation-tokens", body: TokenBody(fileId: fileId, operationType: operationType)
        )
        guard let data = resp.data else { throw APIError.noData }
        return data.operationToken
    }

    /// 下载文件内容
    func downloadFileContent(fileId: String, operationToken: String) async throws -> Data {
        let (data, _) = try await client.download(
            path: "/files/files/\(fileId)/content",
            extraHeaders: ["X-Operation-Token": operationToken]
        )
        return data
    }

    /// Range 下载
    func downloadFileRange(fileId: String, operationToken: String, range: ClosedRange<Int64>) async throws -> Data {
        return try await client.downloadRange(
            path: "/files/files/\(fileId)/content",
            range: range,
            extraHeaders: ["X-Operation-Token": operationToken]
        )
    }

    /// 取消操作令牌
    func cancelOperationToken(operationToken: String) async throws {
        struct CancelBody: Encodable {
            let operationToken: String
            enum CodingKeys: String, CodingKey { case operationToken = "operation_token" }
        }
        let _: APIEmptyResponse = try await client.request(.delete, path: "/files/operation-tokens/", body: CancelBody(operationToken: operationToken))
    }
}