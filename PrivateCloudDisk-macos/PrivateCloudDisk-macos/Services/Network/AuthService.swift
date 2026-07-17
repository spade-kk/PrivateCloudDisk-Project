import Foundation
import Combine
import AppKit

// MARK: - 认证服务

/// 用户认证服务
///
/// 对应后端 platform-service 的 UserController
/// 登录接口返回 data: "token_string"（String 类型）
/// 用户信息需通过 /business/users/me 接口获取
@MainActor
final class AuthService: ObservableObject {

    static let shared = AuthService()

    // MARK: - 发布属性

    @Published var currentUser: UserModel?
    @Published var isAuthenticated = false
    @Published var isLoading = false
    @Published var error: String?

    var isLoggedIn: Bool { isAuthenticated && currentUser != nil }

    private let api = APIClient.shared
    private let keychain = KeychainManager.shared

    private init() {
        // 尝试从 Keychain 恢复登录状态
        Task {
            await restoreSession()
        }
    }

    // MARK: - 登录

    /// 登录（与后端 LoginRequest 对齐）
    /// 后端返回格式: { "code": 200, "message": "success", "data": "token_string" }
    /// 用户信息需通过 /business/users/me 获取
    func login(phoneNumber: String, password: String, captchaToken: String = "", captchaAction: String = "login") async throws -> UserModel {
        isLoading = true
        error = nil
        defer { isLoading = false }

        let passwordHash = CryptoService.hashPasswordForTransport(password)

        let request = LoginRequest(
            phoneNumber: phoneNumber,
            password: passwordHash,
            captchaToken: captchaToken,
            captchaAction: captchaAction
        )

        let token: String = try await api.post("business/users/login", body: request)

        keychain.storeAuthToken(token)

        let user = try await fetchUserProfile()

        currentUser = user
        isAuthenticated = true

        Task {
            await DeviceIdentityManager.shared.triggerAutoRegistrationIfNeeded()
        }

        return user
    }

    // MARK: - 注册

    /// 注册（与后端 RegisterUserRequest 对齐）
    func register(phoneNumber: String, password: String, code: String, username: String, captchaToken: String = "", captchaAction: String = "register") async throws -> UserModel {
        isLoading = true
        error = nil
        defer { isLoading = false }

        let passwordHash = CryptoService.hashPasswordForTransport(password)

        let request = RegisterRequest(
            phoneNumber: phoneNumber,
            password: passwordHash,
            code: code,
            name: username,
            captchaToken: captchaToken,
            captchaAction: captchaAction
        )

        let token: String = try await api.post("business/users/", body: request)

        keychain.storeAuthToken(token)

        let user = try await fetchUserProfile()

        currentUser = user
        isAuthenticated = true

        Task {
            await DeviceIdentityManager.shared.triggerAutoRegistrationIfNeeded()
        }

        return user
    }

    // MARK: - 登出

    func logout() async {
        if let _ = keychain.readAuthToken() {
            let _: EmptyBody? = try? await api.post("auth/oauth2/token/revoke", body: EmptyBody())
        }

        keychain.clearAll()
        currentUser = nil
        isAuthenticated = false

        transitionToLoginWindow()
    }

    // MARK: - Token 验证

    func validateToken(_ token: String) async throws -> UserModel {
        return try await fetchUserProfile()
    }

    // MARK: - 会话恢复

    private func restoreSession() async {
        guard let token = keychain.readAuthToken(), !token.isEmpty else {
            isAuthenticated = false
            return
        }

        do {
            let user = try await fetchUserProfile()
            currentUser = user
            isAuthenticated = true

            Task {
                await DeviceIdentityManager.shared.triggerAutoRegistrationIfNeeded()
            }
        } catch {
            if let refreshToken = keychain.readRefreshToken(), !refreshToken.isEmpty {
                do {
                    let newToken: String = try await api.post("auth/oauth2/token/refresh", body: RefreshTokenRequest(refreshToken: refreshToken))
                    keychain.storeAuthToken(newToken)

                    let user = try await fetchUserProfile()
                    currentUser = user
                    isAuthenticated = true

                    Task {
                        await DeviceIdentityManager.shared.triggerAutoRegistrationIfNeeded()
                    }
                    return
                } catch {
                    // 刷新也失败
                }
            }
            keychain.clearAll()
            isAuthenticated = false
        }
    }

    // MARK: - 获取用户信息

    /// 获取当前用户信息（与后端 UserProfileVO 对齐）
    func fetchUserProfile() async throws -> UserModel {
        let user: UserModel = try await api.get("business/users/me")
        currentUser = user
        return user
    }

    /// 更新用户头像
    func updateAvatar(imageData: Data) async throws -> UserModel {
        let response: Data = try await api.upload(
            "business/users/me/avatar",
            fileData: imageData,
            filename: "avatar.jpg",
            mimeType: "image/jpeg"
        )
        let decoded = try JSONDecoder().decode(ApiResponse<UserModel>.self, from: response)
        guard let user = decoded.data else { throw ApiError.serverError(decoded.code, decoded.message ?? "更新失败") }
        currentUser = user
        return user
    }

    // MARK: - 强制登出

    func forceLogout() async {
        keychain.clearAll()
        currentUser = nil
        isAuthenticated = false
        transitionToLoginWindow()
    }

    // MARK: - 窗口切换

    private func transitionToLoginWindow() {
        DispatchQueue.main.async {
            guard let window = NSApp.keyWindow ?? NSApp.mainWindow else { return }
            window.transitionToLoginWindow()
        }
    }
}
