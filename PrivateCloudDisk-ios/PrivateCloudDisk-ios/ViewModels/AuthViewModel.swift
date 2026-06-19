//
//  AuthViewModel.swift
//  PrivateCloudDisk-ios
//
//  认证 ViewModel — 管理登录、注册、用户状态
//

import Foundation
import SwiftUI
import Combine

@MainActor
class AuthViewModel: ObservableObject {
    @Published var isLoggedIn = false
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var user: UserProfile?

    // 登录表单
    @Published var phoneNumber = ""
    @Published var password = ""
    @Published var captchaToken = ""

    // 注册表单
    @Published var registerUsername = ""
    @Published var registerCode = ""
    @Published var resendToken = ""

    // 服务器地址
    @Published var serverURL = "https://api.cloud.example.com"

    private let authService = AuthService.shared

    init() {
        checkExistingSession()
    }

    // MARK: - 会话检查

    func checkExistingSession() {
        if let token = KeychainManager.shared.getToken(), !token.isEmpty {
            isLoggedIn = true
            Task {
                await loadUserInfo()
            }
        }
    }

    func loadUserInfo() async {
        do {
            let userInfo = try await authService.getUserInfo()
            user = userInfo
            KeychainManager.shared.saveUserId(userInfo.idOrEmpty)
        } catch {
            print("Failed to load user info: \(error)")
        }
    }

    // MARK: - 登录

    func login() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let result = try await authService.login(
                phoneNumber: phoneNumber,
                password: password,
                captchaToken: captchaToken
            )
            user = result.user
            KeychainManager.shared.saveUserId(result.user.idOrEmpty)
            isLoggedIn = true
        } catch let error as APIError {
            errorMessage = error.localizedDescription
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    // MARK: - 注册

    func sendVerificationCode() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let token = try await authService.sendVerificationCode(
                target: phoneNumber,
                captchaToken: captchaToken,
                captchaAction: "register",
                purpose: "register"
            )
            resendToken = token
        } catch let error as APIError {
            errorMessage = error.localizedDescription
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func register() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            try await authService.register(
                phoneNumber: phoneNumber,
                password: password,
                code: registerCode,
                username: registerUsername,
                captchaToken: captchaToken
            )
            // 注册成功后自动登录
            await login()
        } catch let error as APIError {
            errorMessage = error.localizedDescription
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    // MARK: - Biometric 登录

    func biometricLogin() async {
        let storedToken = KeychainManager.shared.getToken()
        guard let _ = storedToken else { return }

        do {
            let success = try await BiometricAuthManager.shared.authenticate(purpose: .unlockApp)
            if success {
                isLoggedIn = true
                await loadUserInfo()
            }
        } catch {
            errorMessage = "生物识别验证失败"
        }
    }

    // MARK: - 退出

    func logout() async {
        await authService.logout()
        isLoggedIn = false
        user = nil
        phoneNumber = ""
        password = ""
    }
}