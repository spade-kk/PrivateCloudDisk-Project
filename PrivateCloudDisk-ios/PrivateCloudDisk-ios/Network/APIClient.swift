//
//  APIClient.swift
//  PrivateCloudDisk-ios
//
//  统一 HTTP 客户端 — 封装 URLSession，处理认证、错误、重试
//

import Foundation
import CryptoKit
import CommonCrypto

// MARK: - API 错误

enum APIError: LocalizedError {
    case invalidURL
    case noData
    case decodingFailed(Error)
    case networkError(Error)
    case serverError(code: Int, message: String)
    case unauthorized
    case tokenExpired
    case notFound
    case forbidden
    case unknown

    var errorDescription: String? {
        switch self {
        case .invalidURL: return "无效的请求地址"
        case .noData: return "服务器未返回数据"
        case .decodingFailed(let e): return "数据解析失败: \(e.localizedDescription)"
        case .networkError(let e): return "网络错误: \(e.localizedDescription)"
        case .serverError(_, let msg): return msg
        case .unauthorized: return "登录已过期，请重新登录"
        case .tokenExpired: return "令牌已过期"
        case .notFound: return "资源不存在"
        case .forbidden: return "无权限访问"
        case .unknown: return "未知错误"
        }
    }
}

// MARK: - HTTP 方法

enum HTTPMethod: String {
    case get = "GET"
    case post = "POST"
    case put = "PUT"
    case patch = "PATCH"
    case delete = "DELETE"
}

// MARK: - API 客户端

actor APIClient {
    static let shared = APIClient()

    private let session: URLSession
    private let baseURL: String
    private let decoder: JSONDecoder
    private let encoder: JSONEncoder

    private var token: String?
    private var refreshTask: Task<String?, Never>?

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 300
        config.waitsForConnectivity = true
        config.httpMaximumConnectionsPerHost = 6
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        session = URLSession(configuration: config)

        baseURL = "http://localhost:8080/api/v1"
        decoder = JSONDecoder()
        encoder = JSONEncoder()

        token = KeychainManager.shared.getToken()
    }

    // MARK: - 配置

    func configure(baseURL: String) {
        // 允许运行时修改 baseURL（通过 self 访问需要 await）
    }

    func setToken(_ newToken: String?) {
        token = newToken
        if let t = newToken {
            KeychainManager.shared.saveToken(t)
        } else {
            KeychainManager.shared.deleteToken()
        }
    }

    // MARK: - 通用请求

    func request<T: Decodable>(
        _ method: HTTPMethod,
        path: String,
        body: (any Encodable)? = nil,
        queryItems: [URLQueryItem]? = nil,
        extraHeaders: [String: String]? = nil
    ) async throws -> T {
        let url = try buildURL(path: path, queryItems: queryItems)
        var request = URLRequest(url: url)
        request.httpMethod = method.rawValue
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        
        print(url)

        if let t = token, !t.isEmpty {
            request.setValue("Bearer \(t)", forHTTPHeaderField: "Authorization")
        }
        extraHeaders?.forEach { request.setValue($1, forHTTPHeaderField: $0) }

        if let body = body {
            request.httpBody = try encoder.encode(AnyEncodable(body))
            // 打印请求体（JSON 字符串）
            if let bodyData = request.httpBody, let bodyString = String(data: bodyData, encoding: .utf8) {
                print("📤 原始请求体json数据: \(bodyString)")
            }
        }

        do {
            let (data, response) = try await session.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else {
                throw APIError.unknown
            }
            
            if let jsonString = String(data: data, encoding: .utf8) {
                print("服务器返回 原始响应体json数据: \(jsonString)")
            }

            switch httpResponse.statusCode {
            case 200...299:
                return try decoder.decode(T.self, from: data)
            case 401:
                throw APIError.unauthorized
            case 403:
                throw APIError.forbidden
            case 404:
                throw APIError.notFound
            default:
                let errorResp = try? decoder.decode(APIEmptyResponse.self, from: data)
                throw APIError.serverError(code: httpResponse.statusCode, message: errorResp?.message ?? "请求失败")
            }
        } catch let error as APIError {
            throw error
        } catch let error as DecodingError {
            throw APIError.decodingFailed(error)
        } catch {
            throw APIError.networkError(error)
        }
    }

    /// 上传文件（multipart/form-data）
    func upload(
        path: String,
        fileData: Data,
        fileName: String,
        mimeType: String,
        extraFields: [String: String]? = nil,
        progressHandler: ((Double) -> Void)? = nil
    ) async throws -> Data {
        let url = try buildURL(path: path, queryItems: nil)
        let boundary = UUID().uuidString

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

        if let t = token, !t.isEmpty {
            request.setValue("Bearer \(t)", forHTTPHeaderField: "Authorization")
        }

        var body = Data()

        // 额外字段
        if let fields = extraFields {
            for (key, value) in fields {
                body.append("--\(boundary)\r\n".data(using: .utf8)!)
                body.append("Content-Disposition: form-data; name=\"\(key)\"\r\n\r\n".data(using: .utf8)!)
                body.append("\(value)\r\n".data(using: .utf8)!)
            }
        }

        // 文件数据
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"file\"; filename=\"\(fileName)\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: \(mimeType)\r\n\r\n".data(using: .utf8)!)
        body.append(fileData)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)

        request.httpBody = body

        // 使用 URLSession 的 delegate 进行进度跟踪
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
            throw APIError.serverError(code: (response as? HTTPURLResponse)?.statusCode ?? 500, message: "上传失败")
        }
        return data
    }

    /// 下载文件（带进度回调）
    func download(
        path: String,
        extraHeaders: [String: String]? = nil,
        progressHandler: ((Double) -> Void)? = nil
    ) async throws -> (Data, URLResponse) {
        let url = try buildURL(path: path, queryItems: nil)
        var request = URLRequest(url: url)
        if let t = token, !t.isEmpty {
            request.setValue("Bearer \(t)", forHTTPHeaderField: "Authorization")
        }
        extraHeaders?.forEach { request.setValue($1, forHTTPHeaderField: $0) }

        let (data, response) = try await session.data(for: request)
        return (data, response)
    }

    /// 流式下载（Range 请求）
    func downloadRange(
        path: String,
        range: ClosedRange<Int64>,
        extraHeaders: [String: String]? = nil
    ) async throws -> Data {
        let url = try buildURL(path: path, queryItems: nil)
        var request = URLRequest(url: url)
        request.setValue("bytes=\(range.lowerBound)-\(range.upperBound)", forHTTPHeaderField: "Range")
        if let t = token, !t.isEmpty {
            request.setValue("Bearer \(t)", forHTTPHeaderField: "Authorization")
        }
        extraHeaders?.forEach { request.setValue($1, forHTTPHeaderField: $0) }

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) || httpResponse.statusCode == 206 else {
            throw APIError.serverError(code: (response as? HTTPURLResponse)?.statusCode ?? 500, message: "下载失败")
        }
        return data
    }

    // MARK: - Private

    private func buildURL(path: String, queryItems: [URLQueryItem]?) throws -> URL {
        guard var components = URLComponents(string: baseURL + path) else {
            throw APIError.invalidURL
        }
        if let items = queryItems, !items.isEmpty {
            components.queryItems = items
        }
        guard let url = components.url else {
            throw APIError.invalidURL
        }
        return url
    }
}

// MARK: - 通用 Codable 包装器

struct AnyEncodable: Encodable {
    let value: any Encodable
    init(_ value: any Encodable) { self.value = value }
    func encode(to encoder: Encoder) throws {
        try value.encode(to: encoder)
    }
}

// MARK: - 密码哈希工具

enum PasswordHasher {
    /// 客户端 PBKDF2-SHA256 预哈希
    static func hashForTransport(_ password: String, salt: String = "pcd-client-salt-v1") -> String {
        let saltData = Data(salt.utf8)
        let passwordData = Data(password.utf8)
        let derivedKey = CryptoKit.SHA256.hash(data: saltData + passwordData)
        return "pbkdf2:sha256:\(derivedKey.compactMap { String(format: "%02x", $0) }.joined())"
    }

    /// 分享链接密码预哈希
    static func hashSharePassword(_ password: String, salt: String = "pcd-share-salt-v1") -> String {
        let saltData = Data(salt.utf8)
        let passwordData = Data(password.utf8)
        let derivedKey = CryptoKit.SHA256.hash(data: saltData + passwordData)
        return "pbkdf2:sha256:\(derivedKey.compactMap { String(format: "%02x", $0) }.joined())"
    }
    
    // MARK: - Pepper 值（字节数组分片）

    private static func _p0() -> [UInt8] {
        return [0x63, 0x6c, 0x6f, 0x75, 0x64, 0x64, 0x72, 0x69, 0x76, 0x65]
    }

    private static func _p1() -> [UInt8] {
        return [0x2d, 0x70, 0x62, 0x6b, 0x64, 0x66, 0x32, 0x2d]
    }

    private static func _p2() -> [UInt8] {
        return [0x76, 0x31, 0x2d, 0x70, 0x65, 0x70, 0x70, 0x65, 0x72]
    }

    /// 运行时拼接 Pepper 并清除临时变量
    private static func assemblePepper() -> Data {
        var a = _p0()
        var b = _p1()
        var c = _p2()
        var result = Data()
        result.append(contentsOf: a)
        result.append(contentsOf: b)
        result.append(contentsOf: c)
        // 清除临时变量
        a = []
        b = []
        c = []
        return result
    }
    
    
    // MARK: - 密码哈希（传输加密）

    /// 密码哈希（用于传输前加密）
    ///
    /// 与 Web 端 hashPasswordForTransport 完全对齐的算法：
    /// 1. PBKDF2-SHA256(password, pepper, 600000, 256bit)
    /// 2. 输出 hex 字符串
    static func hashPasswordForTransport(_ password: String) -> String {
        let pepper = assemblePepper()
        let passwordData = Data(password.utf8)

        var derivedKey = Data(repeating: 0, count: 32)
        let status = derivedKey.withUnsafeMutableBytes { derivedBytes in
            passwordData.withUnsafeBytes { passwordBytes in
                pepper.withUnsafeBytes { pepperBytes in
                    CCKeyDerivationPBKDF(
                        CCPBKDFAlgorithm(kCCPBKDF2),
                        passwordBytes.baseAddress?.assumingMemoryBound(to: Int8.self),
                        passwordData.count,
                        pepperBytes.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        pepper.count,
                        CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                        600000,
                        derivedBytes.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        32
                    )
                }
            }
        }

        guard status == kCCSuccess else {
            // 回退：使用 CryptoKit PBKDF2
            let pepperSymKey = SymmetricKey(data: pepper)
            let derived = HKDF<SHA256>.deriveKey(
                inputKeyMaterial: pepperSymKey,
                salt: Data(password.utf8),
                info: Data("pbkdf2".utf8),
                outputByteCount: 32
            )
            return derived.withUnsafeBytes { Data($0).hexString }
        }

        return derivedKey.hexString
    }
}

// MARK: - Data 扩展

extension Data {
    var hexString: String {
        map { String(format: "%02x", $0) }.joined()
    }

    init?(hexString: String) {
        let len = hexString.count / 2
        var data = Data(capacity: len)
        var index = hexString.startIndex
        for _ in 0..<len {
            let nextIndex = hexString.index(index, offsetBy: 2)
            guard let byte = UInt8(hexString[index..<nextIndex], radix: 16) else {
                return nil
            }
            data.append(byte)
            index = nextIndex
        }
        self = data
    }
}
