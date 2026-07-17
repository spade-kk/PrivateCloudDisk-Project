import SwiftUI

// MARK: - 登录视图（企业级紧凑窗口 — 参考 QQ / 微信 / 百度网盘 / 网易云音乐）

/// 紧凑型登录窗口
///
/// 设计参考：
/// - QQ Mac 登录：380×400，白色背景，Logo + 头像 + 输入框 + 登录按钮
/// - 百度网盘 Mac：400×440，白色背景，Logo + 手机号 + 密码 + 登录按钮
/// - 网易云音乐 Mac：380×480，红色主题，手机号 + 密码 + 登录按钮
/// - 微信 Mac：350×420，绿色主题，二维码 / 手机号登录
///
/// 本窗口尺寸：400×520（略高以容纳 Turnstile 无感验证状态指示器）
///
/// Turnstile 人机验证集成：
/// - 用户名：手机号（11位1[3-9]）/ 邮箱 / 账号（4-16位字母数字下划线）
/// - 密码框：用户名合法后才可输入
/// - Turnstile：首次聚焦密码框时 execute() 触发无感验证
/// - 登录按钮：需获取 token 后才可点击
/// - token 过期/错误时自动重置
struct LoginView: View {
    @EnvironmentObject var authService: AuthService

    // MARK: - 输入状态

    @State private var username = ""
    @State private var password = ""
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var isPasswordVisible = false

    // MARK: - Turnstile 状态

    @State private var turnstileToken: String?
    @State private var turnstileLoading = false
    @State private var turnstileError: String?
    @State private var turnstileTriggered = false
    @State private var triggerTurnstileExecute = false
    @State private var triggerTurnstileReset = false
    @FocusState private var isPasswordFocused: Bool

    private let brandBlue = AppColors.primary

    // MARK: - 用户名验证

    private var isUsernameValid: Bool {
        let trimmed = username.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return false }
        return isValidPhone(trimmed) || isValidEmail(trimmed) || isValidAccount(trimmed)
    }

    private func isValidPhone(_ value: String) -> Bool {
        value.range(of: #"^1[3-9]\d{9}$"#, options: .regularExpression) != nil
    }

    private func isValidEmail(_ value: String) -> Bool {
        value.range(of: #"^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$"#, options: .regularExpression) != nil
    }

    private func isValidAccount(_ value: String) -> Bool {
        value.range(of: #"^[a-zA-Z0-9_]{4,16}$"#, options: .regularExpression) != nil
    }

    private var isLoginDisabled: Bool {
        isLoading || !isUsernameValid || password.isEmpty || turnstileToken == nil
    }

    // MARK: - Body

    var body: some View {
        VStack(spacing: 0) {
            // ── 品牌 Logo ──
            brandLogo
                .padding(.top, 50)

            // ── 标题 ──
            Text("PrivateCloudDisk")
                .font(.system(size: 16, weight: .bold, design: .default))
                .foregroundColor(AppColors.textPrimary)
                .padding(.top, 16)

            Text("企业级私有云存储")
                .font(.system(size: 11, design: .default))
                .foregroundColor(AppColors.textTertiary)
                .padding(.top, 6)

            // ── 表单区域 ──
            VStack(spacing: 12) {
                // 错误提示
                errorBanner

                // 用户名
                usernameInput

                // 密码
                passwordInput

                // 记住密码
                rememberRow

                // Turnstile 状态
                turnstileStatus

                // 登录按钮
                loginButton

                // 注册链接
                registerLink
            }
            .padding(.horizontal, 44)
            .padding(.top, 28)

            Spacer()
        }
        .frame(width: 400, height: 520)
        .background(Color.white)
        .onSubmit { handleLogin() }
        .onChange(of: isPasswordFocused) { focused in
            if focused && !turnstileTriggered && isUsernameValid {
                triggerTurnstile()
            }
        }
        .onDisappear {
            resetTurnstile()
        }
    }

    // MARK: - 品牌 Logo

    private var brandLogo: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 12)
                .fill(
                    LinearGradient(
                        colors: [brandBlue, brandBlue.opacity(0.75)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .frame(width: 44, height: 44)

            Image(systemName: "cloud.fill")
                .font(.system(size: 20, weight: .medium))
                .foregroundColor(.white)
        }
        .shadow(color: brandBlue.opacity(0.25), radius: 8, y: 2)
    }

    // MARK: - 错误提示

    @ViewBuilder
    private var errorBanner: some View {
        if let error = errorMessage {
            HStack(spacing: 6) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 11))
                    .foregroundColor(AppColors.danger)
                Text(error)
                    .font(.system(size: 11, design: .default))
                    .foregroundColor(AppColors.danger)
                    .lineLimit(2)
            }
            .padding(.horizontal, 12).padding(.vertical, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 6)
                    .fill(AppColors.dangerBg)
            )
            .transition(.move(edge: .top).combined(with: .opacity))
        }
    }

    // MARK: - 用户名输入框

    private var usernameInput: some View {
        VStack(alignment: .leading, spacing: 4) {
            // 输入框
            HStack(spacing: 8) {
                Image(systemName: "person.fill")
                    .font(.system(size: 12))
                    .foregroundColor(AppColors.textTertiary)
                    .frame(width: 14)

                ZStack(alignment: .leading) {
                    if username.isEmpty {
                        Text("账号 / 手机号 / 邮箱")
                            .font(.system(size: 13, design: .default))
                            .foregroundColor(AppColors.textPlaceholder)
                            .allowsHitTesting(false)
                    }
                    TextField("", text: $username)
                        .textFieldStyle(.plain)
                        .font(.system(size: 13, design: .default))
                        .foregroundColor(AppColors.textPrimary)
                        .disableAutocorrection(true)
                }

                if !username.isEmpty {
                    Button(action: {
                        username = ""
                        clearAllErrors()
                    }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 10))
                            .foregroundColor(AppColors.neutral300)
                    }
                    .buttonStyle(.plain)
                }

                // 验证状态图标
                if !username.trimmingCharacters(in: .whitespaces).isEmpty {
                    Image(systemName: isUsernameValid ? "checkmark.circle.fill" : "xmark.circle.fill")
                        .font(.system(size: 11))
                        .foregroundColor(isUsernameValid ? AppColors.success : AppColors.neutral300)
                }
            }
            .padding(.horizontal, 12).padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 6)
                    .strokeBorder(
                        isUsernameValid && !username.isEmpty
                            ? AppColors.success.opacity(0.35)
                            : AppColors.neutral200,
                        lineWidth: 1
                    )
                    .background(
                        RoundedRectangle(cornerRadius: 6)
                            .fill(Color.white)
                    )
            )

            // 验证提示
            if !username.trimmingCharacters(in: .whitespaces).isEmpty && !isUsernameValid {
                Text("请输入正确的手机号、邮箱或账号（4-16位字母数字下划线）")
                    .font(.system(size: 10, design: .default))
                    .foregroundColor(AppColors.textTertiary)
                    .lineLimit(1)
                    .transition(.opacity)
            }
        }
        .onChange(of: username) { _ in
            if !username.isEmpty { clearAllErrors() }
            resetTurnstileState()
        }
    }

    // MARK: - 密码输入框

    private var passwordInput: some View {
        HStack(spacing: 8) {
            Image(systemName: "lock.fill")
                .font(.system(size: 12))
                .foregroundColor(AppColors.textTertiary)
                .frame(width: 14)

            ZStack(alignment: .leading) {
                if password.isEmpty {
                    Text("密码")
                        .font(.system(size: 13, design: .default))
                        .foregroundColor(AppColors.textPlaceholder)
                        .allowsHitTesting(false)
                }
                Group {
                    if isPasswordVisible {
                        TextField("", text: $password)
                            .textFieldStyle(.plain)
                            .font(.system(size: 13, design: .default))
                            .foregroundColor(AppColors.textPrimary)
                    } else {
                        SecureField("", text: $password)
                            .textFieldStyle(.plain)
                            .font(.system(size: 13, design: .default))
                            .foregroundColor(AppColors.textPrimary)
                    }
                }
                .focused($isPasswordFocused)
                .disabled(!isUsernameValid)
                .opacity(isUsernameValid ? 1 : 0.35)
            }

            Button(action: { isPasswordVisible.toggle() }) {
                Image(systemName: isPasswordVisible ? "eye.slash.fill" : "eye.fill")
                    .font(.system(size: 11))
                    .foregroundColor(AppColors.textTertiary)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 6)
                .strokeBorder(AppColors.neutral200, lineWidth: 1)
                .background(
                    RoundedRectangle(cornerRadius: 6)
                        .fill(isUsernameValid ? Color.white : Color(hex: "#F5F5F5"))
                )
        )
        .overlay(alignment: .bottom) {
            if !isUsernameValid {
                Text("请先输入合法的用户名")
                    .font(.system(size: 10, design: .default))
                    .foregroundColor(AppColors.textTertiary)
                    .offset(y: 16)
            }
        }
        .padding(.bottom, 4)
    }

    // MARK: - 记住密码行

    private var rememberRow: some View {
        HStack {
            Toggle("记住密码", isOn: .constant(false))
                .toggleStyle(.checkbox)
                .font(.system(size: 11, design: .default))
                .foregroundColor(AppColors.textSecondary)

            Spacer()

            Button("忘记密码?") {}
                .buttonStyle(.plain)
                .font(.system(size: 11, design: .default))
                .foregroundColor(brandBlue)
        }
    }

    // MARK: - Turnstile 状态指示器

    @ViewBuilder
    private var turnstileStatus: some View {
        // Turnstile 错误
        if let captchaError = turnstileError {
            HStack(spacing: 4) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 10))
                    .foregroundColor(AppColors.warning)
                Text(captchaError)
                    .font(.system(size: 10, design: .default))
                    .foregroundColor(AppColors.warning)
                    .lineLimit(1)
            }
            .transition(.opacity)
        } else if turnstileLoading {
            HStack(spacing: 4) {
                ProgressView()
                    .scaleEffect(0.5)
                    .tint(AppColors.textTertiary)
                Text("正在进行安全验证...")
                    .font(.system(size: 10, design: .default))
                    .foregroundColor(AppColors.textTertiary)
            }
        } else if turnstileToken != nil {
            HStack(spacing: 4) {
                Image(systemName: "checkmark.shield.fill")
                    .font(.system(size: 10))
                    .foregroundColor(AppColors.success)
                Text("安全验证通过")
                    .font(.system(size: 10, design: .default))
                    .foregroundColor(AppColors.success)
            }
            .transition(.opacity)
        }

        // 隐藏的 Turnstile WebView
        TurnstileWebView(
            triggerExecute: $triggerTurnstileExecute,
            triggerReset: $triggerTurnstileReset,
            onTokenReceived: { handleTurnstileToken($0) },
            onTokenExpired: { handleTurnstileExpired() },
            onError: { handleTurnstileError($0) }
        )
        .frame(width: 300, height: 70)
        .opacity(1)
        .allowsHitTesting(true)
    }

    // MARK: - 登录按钮

    private var loginButton: some View {
        Button(action: handleLogin) {
            HStack(spacing: 6) {
                if isLoading {
                    ProgressView()
                        .scaleEffect(0.6)
                        .tint(.white)
                }
                Text(isLoading ? "登录中..." : "登 录")
                    .font(.system(size: 14, weight: .semibold, design: .default))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 6)
                    .fill(
                        LinearGradient(
                            colors: [brandBlue, brandBlue.opacity(0.88)],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
            )
            .foregroundColor(.white)
        }
        .buttonStyle(.plain)
        .disabled(isLoginDisabled)
        .opacity(isLoginDisabled ? 0.45 : 1)
        .padding(.top, 2)
    }

    // MARK: - 注册链接

    private var registerLink: some View {
        HStack(spacing: 4) {
            Text("没有账号?")
                .font(.system(size: 11, design: .default))
                .foregroundColor(AppColors.textTertiary)

            Button("立即注册") {}
                .buttonStyle(.plain)
                .font(.system(size: 11, weight: .medium, design: .default))
                .foregroundColor(brandBlue)
        }
        .padding(.top, 8)
    }

    // MARK: - Turnstile 操作

    private func triggerTurnstile() {
        guard !turnstileTriggered else { return }
        turnstileTriggered = true
        turnstileLoading = true
        turnstileError = nil
        triggerTurnstileExecute = true
    }

    private func handleTurnstileToken(_ token: String) {
        turnstileToken = token
        turnstileLoading = false
        turnstileError = nil
    }

    private func handleTurnstileExpired() {
        turnstileToken = nil
        turnstileError = "安全验证已过期，请重新聚焦密码框完成验证"
        turnstileTriggered = false
    }

    private func handleTurnstileError(_ errorMsg: String) {
        turnstileToken = nil
        turnstileLoading = false
        turnstileError = errorMsg
        turnstileTriggered = false
    }

    private func resetTurnstileState() {
        turnstileTriggered = false
        turnstileLoading = false
        turnstileError = nil
    }

    private func resetTurnstile() {
        turnstileToken = nil
        turnstileTriggered = false
        turnstileLoading = false
        turnstileError = nil
        triggerTurnstileReset = true
    }

    private func clearAllErrors() {
        errorMessage = nil
        turnstileError = nil
    }

    // MARK: - 登录操作

    private func handleLogin() {
        guard !isLoginDisabled, let token = turnstileToken else { return }

        isLoading = true
        errorMessage = nil

        Task {
            defer {
                DispatchQueue.main.async { isLoading = false }
            }
            do {
                try await authService.login(
                    phoneNumber: username.trimmingCharacters(in: .whitespaces),
                    password: password,
                    captchaToken: token,
                    captchaAction: "login"
                )
            } catch {
                DispatchQueue.main.async {
                    errorMessage = error.localizedDescription
                    resetTurnstile()
                }
            }
        }
    }
}

// MARK: - Preview

#if DEBUG
struct LoginView_Previews: PreviewProvider {
    static var previews: some View {
        LoginView()
            .environmentObject(AuthService.shared)
    }
}
#endif