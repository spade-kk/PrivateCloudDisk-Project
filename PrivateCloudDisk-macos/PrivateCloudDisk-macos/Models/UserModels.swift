import Foundation

// MARK: - 用户模型

/// 用户信息
struct UserModel: Codable, Identifiable, Equatable {
    let id: String
    let username: String
    let email: String
    let avatar: String?
    let phone: String?
    let nickname: String?
    let createdAt: String?
    let updatedAt: String?

    enum CodingKeys: String, CodingKey {
        case id, username, email, avatar, phone, nickname
        case createdAt = "created_at"
        case updatedAt = "updated_at"
    }

    static func == (lhs: UserModel, rhs: UserModel) -> Bool {
        lhs.id == rhs.id
    }
}

/// 登录请求
struct LoginRequest: Codable {
    let username: String
    let passwordHash: String
    let turnstileToken: String?
    let deviceInfo: DeviceInfo

    enum CodingKeys: String, CodingKey {
        case username
        case passwordHash = "password_hash"
        case turnstileToken = "turnstile_token"
        case deviceInfo = "device_info"
    }
}

/// 注册请求
struct RegisterRequest: Codable {
    let username: String
    let email: String
    let passwordHash: String
    let turnstileToken: String?
    let deviceInfo: DeviceInfo

    enum CodingKeys: String, CodingKey {
        case username, email
        case passwordHash = "password_hash"
        case turnstileToken = "turnstile_token"
        case deviceInfo = "device_info"
    }
}

/// 设备信息
struct DeviceInfo: Codable {
    let deviceName: String
    let deviceModel: String
    let osVersion: String
    let appVersion: String
    let platform: String

    enum CodingKeys: String, CodingKey {
        case deviceName = "device_name"
        case deviceModel = "device_model"
        case osVersion = "os_version"
        case appVersion = "app_version"
        case platform
    }

    static func current() -> DeviceInfo {
        let processInfo = ProcessInfo.processInfo
        return DeviceInfo(
            deviceName: Host.current().localizedName ?? "Mac",
            deviceModel: DeviceInfo.macModelIdentifier(),
            osVersion: processInfo.operatingSystemVersionString,
            appVersion: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0",
            platform: "macOS"
        )
    }

    private static func macModelIdentifier() -> String {
        var size = 0
        sysctlbyname("hw.model", nil, &size, nil, 0)
        var model = [CChar](repeating: 0, count: size)
        sysctlbyname("hw.model", &model, &size, nil, 0)
        return String(cString: model)
    }
}

/// 登录/注册响应
struct AuthResponse: Codable {
    let token: String
    let refreshToken: String?
    let user: UserModel
    let expiresIn: Int?

    enum CodingKeys: String, CodingKey {
        case token
        case refreshToken = "refresh_token"
        case user
        case expiresIn = "expires_in"
    }
}

/// Token 刷新请求
struct RefreshTokenRequest: Codable {
    let refreshToken: String

    enum CodingKeys: String, CodingKey {
        case refreshToken = "refresh_token"
    }
}

/// 配额信息
struct QuotaInfo: Codable {
    let totalBytes: Int64
    let usedBytes: Int64
    let fileCount: Int

    enum CodingKeys: String, CodingKey {
        case totalBytes = "total_bytes"
        case usedBytes = "used_bytes"
        case fileCount = "file_count"
    }

    var availableBytes: Int64 { totalBytes - usedBytes }
    var usagePercentage: Double {
        guard totalBytes > 0 else { return 0 }
        return Double(usedBytes) / Double(totalBytes)
    }
}