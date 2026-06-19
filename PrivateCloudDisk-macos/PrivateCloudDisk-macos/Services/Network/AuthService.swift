import Foundation
import Combine

// MARK: - 认证服务

/// 用户认证服务
///
/// 对应 Windows 的 AuthService + AuthTokenStore
/// 使用 Keychain 持久化 Token，支持自动刷新和设备管理
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

    func login(username: String, password: String, turnstileToken: String?) async throws -> UserModel {
        isLoading = true
        error = nil
        defer { isLoading = false }

        // 1. 前端加密密码
        let passwordHash = CryptoService.hashPasswordForTransport(password)

        // 2. 构建请求
        let request = LoginRequest(
            username: username,
            passwordHash: passwordHash,
            turnstileToken: turnstileToken,
            deviceInfo: DeviceInfo.current()
        )

        // 3. 发送请求
        let auth: AuthResponse = try await api.post("/api/auth/login", body: request)

        // 4. 存储凭据
        keychain.storeAuthToken(auth.token)
        if let refresh = auth.refreshToken {
            keychain.storeRefreshToken(refresh)
        }
        keychain.storeUserId(auth.user.id)
        keychain.storeUsername(auth.user.username)

        // 5. 更新状态
        currentUser = auth.user
        isAuthenticated = true

        return auth.user
    }

    // MARK: - 注册

    func register(username: String, email: String, password: String, turnstileToken: String?) async throws -> UserModel {
        isLoading = true
        error = nil
        defer { isLoading = false }

        let passwordHash = CryptoService.hashPasswordForTransport(password)

        let request = RegisterRequest(
            username: username,
            email: email,
            passwordHash: passwordHash,
            turnstileToken: turnstileToken,
            deviceInfo: DeviceInfo.current()
        )

        let auth: AuthResponse = try await api.post("/api/auth/register", body: request)

        keychain.storeAuthToken(auth.token)
        if let refresh = auth.refreshToken {
            keychain.storeRefreshToken(refresh)
        }
        keychain.storeUserId(auth.user.id)
        keychain.storeUsername(auth.user.username)

        currentUser = auth.user
        isAuthenticated = true

        return auth.user
    }

    // MARK: - 登出

    func logout() async {
        // 通知后端登出
        if let token = keychain.readAuthToken() {
            struct LogoutBody: Encodable {
                let token: String
            }
            let _: EmptyBody? = try? await api.post("/api/auth/logout", body: LogoutBody(token: token))
        }

        // 清除本地凭据
        keychain.clearAll()
        currentUser = nil
        isAuthenticated = false
    }

    // MARK: - Token 验证

    func validateToken(_ token: String) async throws -> UserModel {
        struct TokenValidationBody: Encodable {
            let token: String
        }
        let user: UserModel = try await api.post("/api/auth/validate", body: TokenValidationBody(token: token))
        return user
    }

    // MARK: - 会话恢复

    private func restoreSession() async {
        guard let token = keychain.readAuthToken(), !token.isEmpty else {
            isAuthenticated = false
            return
        }

        do {
            let user = try await validateToken(token)
            currentUser = user
            isAuthenticated = true
        } catch {
            // Token 过期，尝试刷新
            if let refreshToken = keychain.readRefreshToken() {
                do {
                    let body = RefreshTokenRequest(refreshToken: refreshToken)
                    let auth: AuthResponse = try await api.post("/api/auth/refresh", body: body)
                    keychain.updateTokens(
                        accessToken: auth.token,
                        refreshToken: auth.refreshToken,
                        userId: auth.user.id
                    )
                    currentUser = auth.user
                    isAuthenticated = true
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

    func fetchUserProfile() async throws -> UserModel {
        let user: UserModel = try await api.get("/api/user/profile")
        currentUser = user
        return user
    }

    /// 更新用户头像
    func updateAvatar(imageData: Data) async throws -> UserModel {
        let response: Data = try await api.upload(
            "/api/user/avatar",
            fileData: imageData,
            filename: "avatar.jpg",
            mimeType: "image/jpeg"
        )
        let decoded = try JSONDecoder().decode(ApiResponse<UserModel>.self, from: response)
        guard let user = decoded.data else { throw ApiError.serverError(decoded.code, decoded.message) }
        currentUser = user
        return user
    }
}
