import Foundation

// MARK: - 最近访问服务

final class RecentService {

    static let shared = RecentService()
    private let api = APIClient.shared

    private init() {}

    func getRecentAccess(type: AccessType? = nil, page: Int = 1, pageSize: Int = 50) async throws -> [RecentAccessVO] {
        var params: [String: String] = [
            "page": "\(page)",
            "pageSize": "\(pageSize)"
        ]
        if let type = type {
            params["type"] = type.rawValue
        }
        return try await api.get("business/recent", params: params)
    }

    func getRecentUploads(page: Int = 1, pageSize: Int = 50) async throws -> [RecentAccessVO] {
        return try await getRecentAccess(type: .upload, page: page, pageSize: pageSize)
    }

    func getRecentDownloads(page: Int = 1, pageSize: Int = 50) async throws -> [RecentAccessVO] {
        return try await getRecentAccess(type: .download, page: page, pageSize: pageSize)
    }

    func getRecentOpens(page: Int = 1, pageSize: Int = 50) async throws -> [RecentAccessVO] {
        return try await getRecentAccess(type: .open, page: page, pageSize: pageSize)
    }
}

// MARK: - 访问类型

enum AccessType: String, Codable {
    case upload = "upload"
    case download = "download"
    case open = "open"
}

// MARK: - 最近访问记录模型

struct RecentAccessVO: Codable, Identifiable {
    let raId: Int
    let targetId: String
    let targetType: String
    let accessType: AccessType
    let targetName: String
    let targetSize: Int64
    let fileType: String?
    let accessedAt: String

    var id: String { "\(raId)" }
}