//
//  ShareService.swift
//  PrivateCloudDisk-ios
//
//  分享链接网络服务
//

import Foundation

actor ShareService {
    static let shared = ShareService()
    private let client = APIClient.shared

    private init() {}

    // MARK: - 分享管理

    /// 获取我的分享列表
    func getMyShares() async throws -> [ShareLinkItem] {
        let resp: APIResponse<[ShareLinkItem]> = try await client.request(.get, path: "/business/shares/mine")
        return resp.data ?? []
    }

    /// 创建分享链接
    func createShare(request: CreateShareRequest) async throws -> ShareLinkItem {
        let resp: APIResponse<ShareLinkItem> = try await client.request(
            .post, path: "/business/shares/", body: request
        )
        guard let data = resp.data else { throw APIError.noData }
        return data
    }

    /// 撤销分享
    func revokeShare(shareToken: String) async throws {
        let _: APIEmptyResponse = try await client.request(.delete, path: "/business/shares/\(shareToken)")
    }

    // MARK: - 分享访问

    /// 获取分享内容（无需密码）
    func getShareContent(shareToken: String) async throws -> ShareContent {
        let resp: APIResponse<ShareContent> = try await client.request(.get, path: "/business/shares/\(shareToken)/content")
        guard let data = resp.data else { throw APIError.noData }
        return data
    }

    /// 验证分享密码
    func verifySharePassword(shareToken: String, password: String) async throws -> ShareAccessToken {
        let hashedPwd = PasswordHasher.hashSharePassword(password)

        struct VerifyBody: Encodable {
            let shareToken: String; let password: String
            enum CodingKeys: String, CodingKey {
                case shareToken = "share_token"; case password
            }
        }

        let resp: APIResponse<ShareAccessToken> = try await client.request(
            .post, path: "/business/shares/verify",
            body: VerifyBody(shareToken: shareToken, password: hashedPwd)
        )
        guard let data = resp.data else { throw APIError.noData }
        return data
    }

    /// 获取分享文件夹的子内容（需 accessToken）
    func getShareChildren(shareToken: String, nodeId: String, accessToken: String) async throws -> [FileNode] {
        let resp: APIResponse<[FileNode]> = try await client.request(
            .get, path: "/business/shares/\(shareToken)/children",
            queryItems: [URLQueryItem(name: "node_id", value: nodeId)],
            extraHeaders: ["X-Share-Access-Token": accessToken]
        )
        return resp.data ?? []
    }

    /// 获取分享根目录内容
    func getShareRootChildren(shareToken: String, accessToken: String) async throws -> [FileNode] {
        let resp: APIResponse<[FileNode]> = try await client.request(
            .get, path: "/business/shares/\(shareToken)/root/children",
            extraHeaders: ["X-Share-Access-Token": accessToken]
        )
        return resp.data ?? []
    }

    /// 下载分享文件
    func downloadShareFile(shareToken: String, fileId: String, accessToken: String) async throws -> Data {
        let (data, _) = try await client.download(
            path: "/business/shares/\(shareToken)/files/\(fileId)/download",
            extraHeaders: ["X-Share-Access-Token": accessToken]
        )
        return data
    }
}