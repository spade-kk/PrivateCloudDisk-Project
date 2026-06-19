//
//  VideoService.swift
//  PrivateCloudDisk-ios
//
//  视频流媒体网络服务 — 对接后端视频流 API
//

import Foundation

actor VideoService {
    static let shared = VideoService()
    private let client = APIClient.shared

    private init() {}

    /// 获取视频流媒体信息
    func getStreamInfo(fileId: String) async throws -> VideoStreamInfo {
        let resp: APIResponse<VideoStreamInfo> = try await client.request(.get, path: "/video/stream/\(fileId)/info")
        guard let data = resp.data else { throw APIError.noData }
        return data
    }

    /// 获取视频播放 Token
    struct VideoTokenResult: Codable {
        let token: String; let expiresAt: String
        enum CodingKeys: String, CodingKey {
            case token; case expiresAt = "expires_at"
        }
    }

    func getVideoToken(fileId: String, resolution: String? = nil, expiresIn: Int = 3600) async throws -> VideoTokenResult {
        struct TokenBody: Encodable {
            let resolution: String?; let expiresIn: Int
            enum CodingKeys: String, CodingKey {
                case resolution; case expiresIn = "expires_in"
            }
        }
        let resp: APIResponse<VideoTokenResult> = try await client.request(
            .post, path: "/video/stream/\(fileId)/token",
            body: TokenBody(resolution: resolution, expiresIn: expiresIn)
        )
        guard let data = resp.data else { throw APIError.noData }
        return data
    }

    /// 保存播放进度
    func saveProgress(fileId: String, progress: VideoProgress) async throws {
        let _: APIEmptyResponse = try await client.request(
            .post, path: "/video/stream/\(fileId)/progress", body: progress
        )
    }

    /// 获取播放历史
    func getHistory(fileId: String) async throws -> VideoHistory {
        let resp: APIResponse<VideoHistory> = try await client.request(.get, path: "/video/stream/\(fileId)/history")
        guard let data = resp.data else { throw APIError.noData }
        return data
    }

    /// 获取缩略图
    func getThumbnail(fileId: String, time: Double) async throws -> Data {
        let (data, _) = try await client.download(
            path: "/video/stream/\(fileId)/thumbnail",
            extraHeaders: ["time": "\(time)"]
        )
        return data
    }
}