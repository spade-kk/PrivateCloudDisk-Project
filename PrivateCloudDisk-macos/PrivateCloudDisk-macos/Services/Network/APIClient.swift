import Foundation

// MARK: - API 客户端

/// 网络请求客户端
///
/// 核心功能：
/// - 自动 Token 注入与刷新
/// - 设备身份签名（ECDSA-P256-SHA256，防重放保护）← 新增
/// - 请求重试与指数退避
/// - 网络状态监测
/// - 请求/响应日志
/// - 并发请求管理
final class APIClient: @unchecked Sendable {

    static let shared = APIClient()

    // MARK: - 配置

    private var baseURL: String {
        "http://localhost:8080/api/v1/"
    }

    private var token: String? {
        KeychainManager.shared.readAuthToken()
    }

    private let session: URLSession
    private let jsonEncoder: JSONEncoder
    private let jsonDecoder: JSONDecoder
    private let maxRetries = 3
    private let retryDelay: TimeInterval = 1.0

    // MARK: - 初始化

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 300
        config.waitsForConnectivity = true
        config.httpMaximumConnectionsPerHost = 6
        config.httpShouldUsePipelining = true
        config.urlCache = URLCache(
            memoryCapacity: 50 * 1024 * 1024,  // 50MB 内存缓存
            diskCapacity: 200 * 1024 * 1024,   // 200MB 磁盘缓存
            diskPath: "pcd.api.cache"
        )

        session = URLSession(
            configuration: config,
            delegate: CertificateValidator.shared,
            delegateQueue: nil
        )

        jsonEncoder = JSONEncoder()
        jsonEncoder.keyEncodingStrategy = .convertToSnakeCase

        jsonDecoder = JSONDecoder()
        jsonDecoder.keyDecodingStrategy = .convertFromSnakeCase
    }

    // MARK: - 公开方法

    /// 发送 GET 请求
    func get<T: Decodable>(_ path: String, params: [String: String]? = nil) async throws -> T {
        return try await request(method: "GET", path: path, params: params, body: nil as EmptyBody?)
    }

    /// 发送 POST 请求
    func post<T: Decodable, B: Encodable>(_ path: String, body: B) async throws -> T {
        return try await request(method: "POST", path: path, params: nil, body: body)
    }

    /// 发送 PUT 请求
    func put<T: Decodable, B: Encodable>(_ path: String, body: B) async throws -> T {
        return try await request(method: "PUT", path: path, params: nil, body: body)
    }

    /// 发送 PATCH 请求
    func patch<T: Decodable, B: Encodable>(_ path: String, body: B) async throws -> T {
        return try await request(method: "PATCH", path: path, params: nil, body: body)
    }

    /// 发送 DELETE 请求
    func delete<T: Decodable>(_ path: String, params: [String: String]? = nil) async throws -> T {
        return try await request(method: "DELETE", path: path, params: params, body: nil as EmptyBody?)
    }

    /// 上传文件（multipart/form-data）
    func upload(
        _ path: String,
        fileData: Data,
        filename: String,
        mimeType: String,
        additionalFields: [String: String] = [:],
        progressHandler: ((Double) -> Void)? = nil
    ) async throws -> Data {
        let url = try buildURL(path: path, params: nil)
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        await addCommonHeaders(to: &request)

        let boundary = "Boundary-\(UUID().uuidString)"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

        var body = Data()
        // 添加字段
        for (key, value) in additionalFields {
            body.append("--\(boundary)\r\n".data(using: .utf8)!)
            body.append("Content-Disposition: form-data; name=\"\(key)\"\r\n\r\n".data(using: .utf8)!)
            body.append("\(value)\r\n".data(using: .utf8)!)
        }
        // 添加文件
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"file\"; filename=\"\(filename)\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: \(mimeType)\r\n\r\n".data(using: .utf8)!)
        body.append(fileData)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)

        request.httpBody = body

        // 使用 URLSession 的 delegate 获取上传进度
        let (data, response) = try await session.data(for: request)
        try validateResponse(response, data: data)
        return data
    }

    /// 下载文件
    func download(
        _ path: String,
        to destinationURL: URL,
        progressHandler: ((Double) -> Void)? = nil
    ) async throws {
        let url = try buildURL(path: path, params: nil)
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        await addCommonHeaders(to: &request)

        let (data, response) = try await session.data(for: request)
        try validateResponse(response, data: data)
        try data.write(to: destinationURL, options: .atomic)
    }

    // MARK: - 核心请求方法

    private func request<T: Decodable, B: Encodable>(
        method: String,
        path: String,
        params: [String: String]?,
        body: B?
    ) async throws -> T {
        var lastError: Error?

        for attempt in 0..<maxRetries {
            do {
                let url = try buildURL(path: path, params: params)
                var request = URLRequest(url: url)
                request.httpMethod = method

                if let body = body, !(body is EmptyBody) {
                    request.httpBody = try jsonEncoder.encode(body)
                    request.setValue("application/json", forHTTPHeaderField: "Content-Type")
                    print(request.httpBody)
                }
                //等body注入完成的时候才能添加安全请求头 因为接口签名需要签名httpBody 否则与后端服务的签名荷载不一致
                await addCommonHeaders(to: &request)

                let (data, response) = try await session.data(for: request)
                try validateResponse(response, data: data)
                
                // 解码响应
                if T.self == EmptyBody.self {
                    return EmptyBody() as! T
                }
                // 在请求回调中
                if let jsonString = String(data: data, encoding: .utf8) {
                    print("服务器返回 原始响应体json数据: \(jsonString)")
                }
                print(T.self)
                let decoded = try jsonDecoder.decode(ApiResponse<T>.self, from: data)
                guard decoded.isSuccess, let result = decoded.data else {
                    throw ApiError.serverError(decoded.code, decoded.message!)
                }
                print("====================json解码提取API标准响应体ApiResponse中data业务数据====================", result, "========================================")
                return result

            } catch let error as ApiError {
                if error == .unauthorized, attempt == 0 {
                    if let newToken = try? await refreshTokenAndRetry() {
                        continue
                    } else {
                        Task {
                            await AuthService.shared.forceLogout()
                        }
                        throw error
                    }
                }
                if case .serverError(let code, _) = error, (400...499).contains(code) {
                    throw error
                }
                lastError = error
                try await Task.sleep(nanoseconds: UInt64(retryDelay * pow(2.0, Double(attempt)) * 1_000_000_000))
            } catch {
                lastError = error
                if attempt < maxRetries - 1 {
                    try await Task.sleep(nanoseconds: UInt64(retryDelay * pow(2.0, Double(attempt)) * 1_000_000_000))
                }
            }
        }
        
        print(lastError)

        throw lastError ?? ApiError.networkError("请求失败")
    }

    // MARK: - 辅助方法

    private func buildURL(path: String, params: [String: String]?) throws -> URL {
        var components = URLComponents(string: "\(baseURL)\(path)")
        print("\(baseURL)\(path)")
        if let params = params, !params.isEmpty {
            components?.queryItems = params.map { URLQueryItem(name: $0.key, value: $0.value) }
        }
        guard let url = components?.url else {
            throw ApiError.invalidURL
        }
        return url
    }

    private func addCommonHeaders(to request: inout URLRequest) async {
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("PrivateCloudDisk-macOS/\(appVersion)", forHTTPHeaderField: "User-Agent")
        if let token = token {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        // 设备身份签名（防重放 + ECDSA 签名）
        // 如果客户端已注册，自动添加签名头；未注册时静默跳过
        let method = request.httpMethod ?? "GET"
        let path = request.url?.path ?? "/"
        if let securityHeaders = await RequestSigningService.shared.signRequest(
            method: method,
            path: path,
            body: request.httpBody
        ) {
            for (key, value) in securityHeaders.httpHeaders {
                request.setValue(value, forHTTPHeaderField: key)
            }
        }
    }

    private func validateResponse(_ response: URLResponse, data: Data) throws {
        guard let httpResponse = response as? HTTPURLResponse else {
            throw ApiError.networkError("无效的响应")
        }

        switch httpResponse.statusCode {
        case 200, 201, 204:
            return
        case 401:
            throw ApiError.unauthorized
        case 403:
            // 检查是否为客户端身份被吊销
            if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let code = json["code"] as? String,
               code == "CLIENT_REVOKED" {
                print("[APIClient] 客户端身份已被吊销")
                DeviceIdentityManager.shared.refreshCache()
                throw ApiError.serverError(403, "客户端身份已被吊销，请重新注册")
            }
            throw ApiError.unauthorized
        case 400...499:
            let message = (try? JSONDecoder().decode(ApiResponse<EmptyBody>.self, from: data).message) ?? "请求错误"
            throw ApiError.serverError(httpResponse.statusCode, message)
        case 500...599:
            throw ApiError.serverError(httpResponse.statusCode, "服务器内部错误")
        default:
            throw ApiError.serverError(httpResponse.statusCode, "未知错误")
        }
    }

    private func refreshTokenAndRetry() async throws -> String? {
        guard let refreshToken = KeychainManager.shared.readRefreshToken() else {
            return nil
        }

        let body = RefreshTokenRequest(refreshToken: refreshToken)
        let url = try buildURL(path: "auth/oauth2/token/refresh", params: nil)
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.httpBody = try jsonEncoder.encode(body)

        let (data, response) = try await session.data(for: request)
        try validateResponse(response, data: data)

        let authResponse = try jsonDecoder.decode(ApiResponse<String>.self, from: data)
        guard let token = authResponse.data else { return nil }

        KeychainManager.shared.updateTokens(
            accessToken: token,
            refreshToken: refreshToken,
            userId: nil
        )
        return token
    }

    private var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
    }
}

// MARK: - 文件上传/下载专用扩展

extension APIClient {

    /// 分块上传（断点续传）
    func uploadChunk(
        _ path: String,
        chunkData: Data,
        chunkIndex: Int,
        uploadId: String,
        checksum: String
    ) async throws -> UploadInitResponse {
        struct ChunkUploadBody: Encodable {
            let uploadId: String
            let chunkIndex: Int
            let checksum: String
        }

        let url = try buildURL(path: path, params: nil)
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        await addCommonHeaders(to: &request)

        let boundary = "Chunk-Boundary-\(UUID().uuidString)"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

        var body = Data()
        let chunkInfo = ChunkUploadBody(uploadId: uploadId, chunkIndex: chunkIndex, checksum: checksum)
        let chunkInfoJSON = try jsonEncoder.encode(chunkInfo)

        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"chunk_info\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: application/json\r\n\r\n".data(using: .utf8)!)
        body.append(chunkInfoJSON)
        body.append("\r\n".data(using: .utf8)!)
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"chunk\"; filename=\"chunk_\(chunkIndex)\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: application/octet-stream\r\n\r\n".data(using: .utf8)!)
        body.append(chunkData)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)

        request.httpBody = body

        let (data, response) = try await session.data(for: request)
        try validateResponse(response, data: data)
        return try jsonDecoder.decode(UploadInitResponse.self, from: data)
    }
}
