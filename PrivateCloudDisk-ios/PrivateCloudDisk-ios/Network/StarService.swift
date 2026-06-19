//
//  StarService.swift
//  PrivateCloudDisk-ios
//
//  收藏网络服务
//

import Foundation

actor StarService {
    static let shared = StarService()
    private let client = APIClient.shared

    private init() {}

    /// 获取收藏列表
    func getStars() async throws -> [StarItem] {
        let resp: APIResponse<[StarItem]> = try await client.request(.get, path: "/business/stars/mine")
        return resp.data ?? []
    }

    /// 添加收藏
    func addStar(request: CreateStarRequest) async throws -> StarItem {
        let resp: APIResponse<StarItem> = try await client.request(.post, path: "/business/stars/", body: request)
        guard let data = resp.data else { throw APIError.noData }
        return data
    }

    /// 取消收藏
    func removeStar(starId: String) async throws {
        let _: APIEmptyResponse = try await client.request(.delete, path: "/business/stars/\(starId)")
    }

    /// 检查是否已收藏
    func isStarred(targetType: String, targetId: String) async throws -> Bool {
        let resp: APIResponse<Bool> = try await client.request(
            .get, path: "/business/stars/check",
            queryItems: [
                URLQueryItem(name: "target_type", value: targetType),
                URLQueryItem(name: "target_id", value: targetId)
            ]
        )
        return resp.data ?? false
    }
}