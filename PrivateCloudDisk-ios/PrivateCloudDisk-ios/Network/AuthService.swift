//
//  AuthService.swift
//  PrivateCloudDisk-ios
//
//  认证网络服务 — 登录、注册、用户信息
//

import Foundation

actor AuthService {
    static let shared = AuthService()
    private let client = APIClient.shared

    private init() {}

    // MARK: - 登录

    struct LoginResult {
        let token: String
        let user: UserProfile
    }

    func login(phoneNumber: String, password: String, captchaToken: String = "") async throws -> LoginResult {
        let hashedPwd = PasswordHasher.hashForTransport(password)

        struct LoginBody: Encodable {
            let phoneNumber: String; let password: String
            let captchaToken: String; let captchaAction: String
            enum CodingKeys: String, CodingKey {
                case phoneNumber = "phone_number"; case password
                case captchaToken = "captcha_token"; case captchaAction = "captcha_action"
            }
        }

        // 登录接口返回 token 字符串
        let resp: APIResponse<String> = try await client.request(
            .post, path: "/business/users/login",
            body: LoginBody(phoneNumber: phoneNumber, password: hashedPwd, captchaToken: captchaToken, captchaAction: "login")
        )

        guard let token = resp.data else { throw APIError.serverError(code: resp.code, message: resp.message ?? "登录失败") }

        await client.setToken(token)

        // 获取用户信息
        let user = try await getUserInfo()
        return LoginResult(token: token, user: user)
    }

    // MARK: - 注册

    func register(phoneNumber: String, password: String, code: String, username: String, captchaToken: String = "") async throws {
        let hashedPwd = PasswordHasher.hashForTransport(password)

        struct RegisterBody: Encodable {
            let phoneNumber: String; let password: String
            let code: String; let name: String
            let captchaToken: String; let captchaAction: String
            enum CodingKeys: String, CodingKey {
                case phoneNumber = "phone_number"; case password; case code; case name
                case captchaToken = "captcha_token"; case captchaAction = "captcha_action"
            }
        }

        let _: APIEmptyResponse = try await client.request(
            .post, path: "/business/users/",
            body: RegisterBody(phoneNumber: phoneNumber, password: hashedPwd, code: code, name: username, captchaToken: captchaToken, captchaAction: "register")
        )
    }

    // MARK: - 验证码

    func sendVerificationCode(target: String, captchaToken: String, captchaAction: String = "send_code", purpose: String = "register") async throws -> String {
        struct CodeBody: Encodable {
            let target: String; let captchaToken: String
            let captchaAction: String; let purpose: String
            enum CodingKeys: String, CodingKey {
                case target; case captchaToken = "captcha_token"
                case captchaAction = "captcha_action"; case purpose
            }
        }
        struct CodeResp: Codable {
            let resendToken: String
            enum CodingKeys: String, CodingKey { case resendToken = "resend_token" }
        }
        let resp: APIResponse<CodeResp> = try await client.request(
            .post, path: "/business/users/verification-code",
            body: CodeBody(target: target, captchaToken: captchaToken, captchaAction: captchaAction, purpose: purpose)
        )
        return resp.data?.resendToken ?? ""
    }

    func resendVerificationCode(target: String, resendToken: String) async throws {
        struct ResendBody: Encodable {
            let target: String; let resendToken: String
            enum CodingKeys: String, CodingKey {
                case target; case resendToken = "resend_token"
            }
        }
        let _: APIEmptyResponse = try await client.request(
            .post, path: "/business/users/verification-code/resend",
            body: ResendBody(target: target, resendToken: resendToken)
        )
    }

    // MARK: - 用户信息

    func getUserInfo() async throws -> UserProfile {
        let resp: APIResponse<UserProfile> = try await client.request(.get, path: "/business/users/me")
        guard let data = resp.data else { throw APIError.noData }
        return data
    }

    func updateProfile(name: String? = nil, email: String? = nil) async throws -> UserProfile {
        struct UpdateBody: Encodable {
            let name: String?; let email: String?
        }
        let resp: APIResponse<UserProfile> = try await client.request(
            .patch, path: "/business/users/me",
            body: UpdateBody(name: name, email: email)
        )
        guard let data = resp.data else { throw APIError.noData }
        return data
    }

    func changePassword(oldPassword: String, newPassword: String) async throws {
        let oldHashed = PasswordHasher.hashForTransport(oldPassword)
        let newHashed = PasswordHasher.hashForTransport(newPassword)

        struct PwdBody: Encodable {
            let oldPassword: String; let newPassword: String
            enum CodingKeys: String, CodingKey {
                case oldPassword = "old_password"; case newPassword = "new_password"
            }
        }
        let _: APIEmptyResponse = try await client.request(
            .patch, path: "/business/users/me/password",
            body: PwdBody(oldPassword: oldHashed, newPassword: newHashed)
        )
    }

    // MARK: - 设备管理

    func getDevices() async throws -> [String: Any] {
        // 简化实现
        let resp: APIResponse<[String: String]> = try await client.request(.get, path: "/business/users/me/devices")
        return ["data": resp.data as Any]
    }

    func revokeDevice(deviceId: String) async throws {
        let _: APIEmptyResponse = try await client.request(.delete, path: "/business/users/me/devices/\(deviceId)")
    }

    // MARK: - 退出登录

    func logout() async {
        do {
            let _: APIEmptyResponse? = try await client.request(.post, path: "/business/users/logout", body: Optional<String>.none)
        } catch { /* 忽略退出登录错误 */ }
        await client.setToken(nil)
        KeychainManager.shared.clearAll()
    }
}