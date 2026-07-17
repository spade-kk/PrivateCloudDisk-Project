import Foundation
import Combine
import SwiftUI

// MARK: - 登录视图模型

@MainActor
final class LoginViewModel: ObservableObject {

    @Published var username = ""
    @Published var password = ""
    @Published var email = ""

    @Published var isLoginMode = true
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var showError = false

    @Published var turnstileToken: String?

    private let authService = AuthService.shared

    // MARK: - 输入验证

    var isLoginFormValid: Bool {
        !username.trimmingCharacters(in: .whitespaces).isEmpty &&
        !password.isEmpty
    }

    var isRegisterFormValid: Bool {
        !username.trimmingCharacters(in: .whitespaces).isEmpty &&
        !email.trimmingCharacters(in: .whitespaces).isEmpty &&
        isEmailValid &&
        password.count >= 6
    }

    private var isEmailValid: Bool {
        let emailRegex = #"^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$"#
        return email.range(of: emailRegex, options: .regularExpression) != nil
    }

    // MARK: - 操作

    func login() async {
        guard isLoginFormValid else { return }

        isLoading = true
        errorMessage = nil

        do {
            let user = try await authService.login(
                phoneNumber: username,
                password: password,
                captchaToken: turnstileToken ?? "",
                captchaAction: "login"
            )
            print("[LoginViewModel] 登录成功: \(user.name)")
        } catch {
            errorMessage = error.localizedDescription
            showError = true
        }

        isLoading = false
    }

    func register() async {
        guard isRegisterFormValid else { return }

        isLoading = true
        errorMessage = nil

        do {
            let user = try await authService.register(
                phoneNumber: username,
                password: password,
                code: "",
                username: email,
                captchaToken: turnstileToken ?? "",
                captchaAction: "register"
            )
            print("[LoginViewModel] 注册成功: \(user.name)")
        } catch {
            errorMessage = error.localizedDescription
            showError = true
        }

        isLoading = false
    }

    func toggleMode() {
        withAnimation(.easeInOut(duration: 0.3)) {
            isLoginMode.toggle()
            errorMessage = nil
        }
    }
}
