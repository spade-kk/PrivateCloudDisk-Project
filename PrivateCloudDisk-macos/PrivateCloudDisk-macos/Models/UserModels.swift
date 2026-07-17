import Foundation

// MARK: - 用户模型（与后端 UserProfileVO 完全对齐）

struct UserModel: Codable, Identifiable, Equatable {
    let id: String
    let account: String?
    let phoneNumber: String?
    let email: String?
    let name: String?
    let imagePath: String?

    var avatar: String? { imagePath }
    var nickname: String? { name }
    var phone: String? { phoneNumber }
    var username: String { account ?? name ?? id }

    static func == (lhs: UserModel, rhs: UserModel) -> Bool {
        lhs.id == rhs.id
    }
}

// MARK: - 登录请求（与后端 LoginRequest 完全对齐）

struct LoginRequest: Codable {
    let account: String?
    let phoneNumber: String?
    let password: String
    let captchaToken: String?
    let captchaAction: String?

    init(phoneNumber: String, password: String, captchaToken: String, captchaAction: String) {
        self.account = nil
        self.phoneNumber = phoneNumber
        self.password = password
        self.captchaToken = captchaToken
        self.captchaAction = captchaAction
    }

    init(account: String, password: String, captchaToken: String, captchaAction: String) {
        self.account = account
        self.phoneNumber = nil
        self.password = password
        self.captchaToken = captchaToken
        self.captchaAction = captchaAction
    }
}

// MARK: - 注册请求（与后端 RegisterUserRequest 对齐）

struct RegisterRequest: Codable {
    let phoneNumber: String
    let password: String
    let code: String
    let name: String
    let captchaToken: String?
    let captchaAction: String?
}

// MARK: - Token 刷新请求

struct RefreshTokenRequest: Codable {
    let refreshToken: String
}

// MARK: - 配额信息（与后端 QuotaVO 完全对齐）

struct QuotaInfo: Codable {
    let userId: String
    let totalCapacity: Int64
    let usedCapacity: Int64
    let fileCount: Int
    let version: Int
    let createdAt: String
    let updatedAt: String

    var availableBytes: Int64 { totalCapacity - usedCapacity }
    var usagePercentage: Double {
        guard totalCapacity > 0 else { return 0 }
        return Double(usedCapacity) / Double(totalCapacity)
    }
}