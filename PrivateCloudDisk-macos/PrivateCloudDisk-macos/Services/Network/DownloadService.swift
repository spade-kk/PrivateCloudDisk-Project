import Foundation

// MARK: - 下载服务

final class DownloadService {

    static let shared = DownloadService()
    private let api = APIClient.shared

    private init() {}

    // MARK: - 操作令牌管理

    func createDownloadGrant(fileId: String) async throws -> DownloadGrant {
        struct CreateGrantRequest: Codable {
            let fileId: String
        }
        let request = CreateGrantRequest(fileId: fileId)
        return try await api.post("files/download-grants", body: request)
    }

    func cancelDownloadGrant(downloadGrant: String) async throws {
        struct CancelGrantRequest: Codable {
            let downloadGrant: String
        }
        let request = CancelGrantRequest(downloadGrant: downloadGrant)
        try await api.post("files/download-grants/cancel", body: request) as EmptyBody
    }

    func releaseDownloadGrant(downloadGrant: String) async throws {
        struct ReleaseGrantRequest: Codable {
            let downloadGrant: String
        }
        let request = ReleaseGrantRequest(downloadGrant: downloadGrant)
        try await api.post("files/download-grants/release", body: request) as EmptyBody
    }

    // MARK: - 文件内容下载

    func getFileContent(fileId: String, downloadGrant: String) async throws -> Data {
        let baseURL = UserDefaults.standard.string(forKey: "api_base_url") ?? "http://localhost:8080"
        guard let url = URL(string: "\(baseURL)/files/files/\(fileId)/content") else {
            throw ApiError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("application/octet-stream", forHTTPHeaderField: "Accept")
        if let token = KeychainManager.shared.readAuthToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.setValue(downloadGrant, forHTTPHeaderField: "X-Download-Grant")

        let (data, response) = try await URLSession.shared.data(for: request)

        if let httpResponse = response as? HTTPURLResponse, !(200...299).contains(httpResponse.statusCode) {
            throw ApiError.serverError(httpResponse.statusCode, "下载失败")
        }

        return data
    }

    func getFileContentChunk(fileId: String, downloadGrant: String, start: Int, end: Int) async throws -> Data {
        let baseURL = UserDefaults.standard.string(forKey: "api_base_url") ?? "http://localhost:8080"
        guard let url = URL(string: "\(baseURL)/files/files/\(fileId)/content") else {
            throw ApiError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("application/octet-stream", forHTTPHeaderField: "Accept")
        if let token = KeychainManager.shared.readAuthToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.setValue(downloadGrant, forHTTPHeaderField: "X-Download-Grant")
        request.setValue("bytes=\(start)-\(end)", forHTTPHeaderField: "Range")

        let (data, response) = try await URLSession.shared.data(for: request)

        if let httpResponse = response as? HTTPURLResponse, !(200...299).contains(httpResponse.statusCode) && httpResponse.statusCode != 206 {
            throw ApiError.serverError(httpResponse.statusCode, "下载失败")
        }

        return data
    }
}

// MARK: - 下载操作令牌模型

struct DownloadGrant: Codable {
    let operationToken: String
    let expiresAt: String
}