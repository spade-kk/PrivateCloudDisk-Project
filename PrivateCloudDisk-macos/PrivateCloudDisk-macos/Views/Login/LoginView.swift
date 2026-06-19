import SwiftUI

// MARK: - 登录视图（企业级设计 v2）

/// 登录/注册页面
///
/// 参考百度网盘、夸克网盘 macOS 客户端设计：
/// - 左侧品牌展示区 + 右侧登录表单
/// - 毛玻璃材质卡片 + 渐变按钮
/// - 忘记密码 / 服务器配置 / 社交登录
/// - 优雅的深浅色模式适配
struct LoginView: View {
    @StateObject private var viewModel = LoginViewModel()
    @FocusState private var focusedField: Field?
    @State private var cardScale: CGFloat = 0.95
    @State private var cardOpacity: Double = 0
    @State private var showForgotPassword = false
    @State private var showServerConfig = false
    @State private var serverURL = ""

    enum Field {
        case username, email, password, server
    }

    // MARK: - 品牌色

    private let brandBlue = Color(red: 0.24, green: 0.47, blue: 0.96)
    private let brandPurple = Color(red: 0.45, green: 0.35, blue: 0.95)
    private let brandGradient = LinearGradient(
        colors: [
            Color(red: 0.24, green: 0.47, blue: 0.96),
            Color(red: 0.45, green: 0.35, blue: 0.95),
            Color(red: 0.65, green: 0.28, blue: 0.92),
        ],
        startPoint: .leading,
        endPoint: .trailing
    )

    var body: some View {
        GeometryReader { geo in
            HStack(spacing: 0) {
                // ── 左侧品牌展示区 ──
                brandPanel
                    .frame(width: max(380, geo.size.width * 0.42))

                // ── 右侧登录表单 ──
                loginPanel
                    .frame(maxWidth: .infinity)
            }
            .background(
                ZStack {
                    Color(nsColor: .windowBackgroundColor)

                    LinearGradient(
                        colors: [
                            Color(red: 0.12, green: 0.10, blue: 0.22),
                            Color(nsColor: .windowBackgroundColor),
                        ],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                    .opacity(0.3)
                }
            )
        }
        .frame(minWidth: 800, minHeight: 560)
        .onAppear {
            withAnimation(.spring(response: 0.6, dampingFraction: 0.8)) {
                cardScale = 1
                cardOpacity = 1
            }
        }
    }

    // MARK: - 左侧品牌展示

    private var brandPanel: some View {
        ZStack {
            LinearGradient(
                colors: [
                    Color(red: 0.10, green: 0.08, blue: 0.20),
                    Color(red: 0.06, green: 0.05, blue: 0.16),
                    Color(red: 0.08, green: 0.06, blue: 0.18),
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )

            // 装饰光晕
            Circle()
                .fill(
                    RadialGradient(
                        colors: [brandBlue.opacity(0.15), .clear],
                        center: .center,
                        startRadius: 0,
                        endRadius: 250
                    )
                )
                .frame(width: 350, height: 350)
                .offset(x: -50, y: -100)
                .blur(radius: 30)

            GridPatternView()
                .opacity(0.05)

            VStack(spacing: 32) {
                Spacer()

                // Logo
                ZStack {
                    RoundedRectangle(cornerRadius: 24)
                        .fill(brandGradient)
                        .frame(width: 80, height: 80)
                        .shadow(
                            color: brandBlue.opacity(0.5),
                            radius: 30,
                            x: 0,
                            y: 8
                        )

                    Image(systemName: "externaldrive.fill.badge.icloud")
                        .font(.system(size: 36, weight: .medium))
                        .foregroundColor(.white)
                }

                // 品牌名称
                VStack(spacing: 8) {
                    HStack(spacing: 0) {
                        Text("Private")
                            .font(.system(size: 28, weight: .bold, design: .rounded))
                            .foregroundColor(.white)
                        Text("Cloud")
                            .font(.system(size: 28, weight: .bold, design: .rounded))
                            .foregroundColor(brandBlue)
                        Text("Disk")
                            .font(.system(size: 28, weight: .bold, design: .rounded))
                            .foregroundColor(.white)
                    }

                    Text("企业级私有云存储解决方案")
                        .font(.system(size: 13, design: .rounded))
                        .foregroundColor(.white.opacity(0.5))
                        .tracking(2)
                }

                Spacer()

                // 特性列表
                VStack(alignment: .leading, spacing: 16) {
                    FeatureItem(
                        icon: "lock.shield.fill",
                        title: "端到端加密",
                        description: "AES-256 军事级加密保护"
                    )
                    FeatureItem(
                        icon: "bolt.fill",
                        title: "极速传输",
                        description: "P2P 加速 + 断点续传"
                    )
                    FeatureItem(
                        icon: "externaldrive.fill",
                        title: "虚拟磁盘",
                        description: "像本地磁盘一样使用云端空间"
                    )
                    FeatureItem(
                        icon: "device.phone.rtl",
                        title: "多端同步",
                        description: "Windows / macOS / Android / iOS"
                    )
                }
                .padding(.horizontal, 40)

                Spacer()
                Spacer()
            }
        }
    }

    // MARK: - 右侧登录面板

    private var loginPanel: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 0) {
                Spacer(minLength: 40)

                // 登录卡片
                VStack(spacing: 28) {
                    // 欢迎标题
                    VStack(spacing: 6) {
                        Text(viewModel.isLoginMode ? "欢迎回来" : "创建账户")
                            .font(.system(size: 26, weight: .bold, design: .rounded))
                            .foregroundColor(.primary)

                        Text(viewModel.isLoginMode
                            ? "登录您的私有云存储账户"
                            : "注册一个新的私有云存储账户"
                        )
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                    }

                    // 表单
                    VStack(spacing: 18) {
                        loginFormFields
                    }

                    // 登录按钮
                    loginButton
                        .padding(.top, 4)

                    // 忘记密码
                    if viewModel.isLoginMode {
                        forgotPasswordLink
                    }

                    // 分隔线
                    socialDivider

                    // 社交登录
                    socialLoginButtons

                    // 切换模式
                    modeToggle
                }
                .padding(40)
                .frame(width: 420)
                .background(
                    RoundedRectangle(cornerRadius: 20)
                        .fill(.regularMaterial)
                        .shadow(color: .black.opacity(0.06), radius: 20, x: 0, y: 4)
                )
                .scaleEffect(cardScale)
                .opacity(cardOpacity)

                Spacer(minLength: 40)
            }
            .frame(maxWidth: .infinity)
        }
    }

    // MARK: - 表单字段

    private var loginFormFields: some View {
        Group {
            // 用户名
            FormField(
                icon: "person.fill",
                label: "用户名",
                placeholder: "请输入用户名",
                text: $viewModel.username,
                focused: $focusedField,
                field: .username,
                onSubmit: { focusedField = .password }
            )

            // 邮箱（仅注册模式）
            if !viewModel.isLoginMode {
                FormField(
                    icon: "envelope.fill",
                    label: "邮箱",
                    placeholder: "请输入邮箱地址",
                    text: $viewModel.email,
                    focused: $focusedField,
                    field: .email,
                    onSubmit: { focusedField = .password }
                )
                .transition(
                    .asymmetric(
                        insertion: .opacity.combined(with: .move(edge: .top)).combined(with: .scale(scale: 0.95)),
                        removal: .opacity.combined(with: .move(edge: .top)).combined(with: .scale(scale: 0.95))
                    )
                )
            }

            // 密码
            FormField(
                icon: "lock.fill",
                label: "密码",
                placeholder: "请输入密码",
                text: $viewModel.password,
                focused: $focusedField,
                field: .password,
                isSecure: true,
                onSubmit: {
                    Task {
                        if viewModel.isLoginMode {
                            await viewModel.login()
                        } else {
                            await viewModel.register()
                        }
                    }
                }
            )

            // 错误提示
            if viewModel.showError, let error = viewModel.errorMessage {
                HStack(spacing: 8) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundColor(.red)
                        .font(.caption)
                    Text(error)
                        .font(.caption)
                        .foregroundColor(.red)
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 10)
                        .fill(.red.opacity(0.08))
                )
                .transition(.opacity.combined(with: .scale(scale: 0.98)))
            }
        }
        .animation(.easeInOut(duration: 0.3), value: viewModel.isLoginMode)
    }

    // MARK: - 登录按钮

    private var loginButton: some View {
        Button(action: {
            Task {
                if viewModel.isLoginMode {
                    await viewModel.login()
                } else {
                    await viewModel.register()
                }
            }
        }) {
            HStack(spacing: 8) {
                if viewModel.isLoading {
                    ProgressView()
                        .scaleEffect(0.7)
                        .tint(.white)
                }
                Text(viewModel.isLoginMode ? "登录" : "创建账户")
                    .font(.system(size: 16, weight: .semibold, design: .rounded))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(
                        isFormValid
                            ? AnyShapeStyle(brandGradient)
                            : AnyShapeStyle(Color.gray.opacity(0.3))
                    )
            )
            .foregroundColor(isFormValid ? .white : .gray)
            .shadow(
                color: isFormValid ? brandBlue.opacity(0.3) : .clear,
                radius: 12,
                x: 0,
                y: 4
            )
        }
        .buttonStyle(.plain)
        .disabled(viewModel.isLoading || !isFormValid)
        .scaleEffect(viewModel.isLoading ? 0.98 : 1)
        .animation(.easeInOut(duration: 0.2), value: viewModel.isLoading)
    }

    // MARK: - 忘记密码

    private var forgotPasswordLink: some View {
        HStack(spacing: 4) {
            Spacer()
            Button("忘记密码？") {
                showForgotPassword = true
            }
            .buttonStyle(.plain)
            .font(.system(size: 12, design: .rounded))
            .foregroundColor(brandBlue)
        }
        .sheet(isPresented: $showForgotPassword) {
            forgotPasswordSheet
        }
    }

    private var forgotPasswordSheet: some View {
        VStack(spacing: 24) {
            Text("重置密码")
                .font(.system(size: 16, weight: .bold, design: .rounded))

            VStack(alignment: .leading, spacing: 6) {
                Text("邮箱地址")
                    .font(.caption.weight(.medium))
                    .foregroundColor(.secondary)
                HStack(spacing: 10) {
                    Image(systemName: "envelope.fill")
                        .foregroundColor(.secondary)
                    TextField("请输入注册邮箱", text: $viewModel.email)
                        .textFieldStyle(.plain)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(
                    RoundedRectangle(cornerRadius: 10)
                        .fill(.quaternary.opacity(0.4))
                )
            }

            HStack(spacing: 12) {
                Button("取消") { showForgotPassword = false }
                    .buttonStyle(.plain)
                    .foregroundColor(.secondary)

                Button("发送重置链接") {
                    // TODO: 实现密码重置 API
                    showForgotPassword = false
                }
                .buttonStyle(.borderedProminent)
                .tint(brandBlue)
            }
        }
        .padding(30)
        .frame(width: 380, height: 240)
    }

    // MARK: - 社交登录分隔线

    private var socialDivider: some View {
        HStack(spacing: 12) {
            Rectangle()
                .fill(.quaternary)
                .frame(height: 1)

            Text("或")
                .font(.system(size: 11, design: .rounded))
                .foregroundStyle(.tertiary)

            Rectangle()
                .fill(.quaternary)
                .frame(height: 1)
        }
    }

    // MARK: - 社交登录按钮

    private var socialLoginButtons: some View {
        HStack(spacing: 16) {
            // Apple ID
            socialLoginButton(
                icon: "apple.logo",
                label: "Apple",
                color: .primary
            ) {
                // Apple Sign In
            }

            // 服务器配置
            socialLoginButton(
                icon: "server.rack",
                label: "服务器",
                color: brandBlue
            ) {
                showServerConfig = true
            }
        }
        .sheet(isPresented: $showServerConfig) {
            serverConfigSheet
        }
    }

    private func socialLoginButton(
        icon: String,
        label: String,
        color: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 14, weight: .medium))
                Text(label)
                    .font(.system(size: 12, weight: .medium, design: .rounded))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(.quaternary.opacity(0.5))
            )
            .foregroundColor(color)
        }
        .buttonStyle(.plain)
    }

    // MARK: - 服务器配置 Sheet

    private var serverConfigSheet: some View {
        VStack(spacing: 24) {
            Text("服务器配置")
                .font(.system(size: 16, weight: .bold, design: .rounded))

            VStack(alignment: .leading, spacing: 6) {
                Text("API 服务器地址")
                    .font(.caption.weight(.medium))
                    .foregroundColor(.secondary)
                HStack(spacing: 10) {
                    Image(systemName: "network")
                        .foregroundColor(.secondary)
                    TextField("http://localhost:8000", text: $serverURL)
                        .textFieldStyle(.plain)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(
                    RoundedRectangle(cornerRadius: 10)
                        .fill(.quaternary.opacity(0.4))
                )
                Text("修改后需要重新登录")
                    .font(.system(size: 11, design: .rounded))
                    .foregroundColor(.secondary)
                    .padding(.leading, 4)
            }

            HStack(spacing: 12) {
                Button("取消") {
                    showServerConfig = false
                }
                .buttonStyle(.plain)
                .foregroundColor(.secondary)

                Button("保存") {
                    // TODO: 保存服务器配置
                    showServerConfig = false
                }
                .buttonStyle(.borderedProminent)
                .tint(brandBlue)
            }
        }
        .padding(30)
        .frame(width: 420, height: 240)
    }

    // MARK: - 模式切换

    private var modeToggle: some View {
        HStack(spacing: 6) {
            Text(viewModel.isLoginMode ? "还没有账户？" : "已有账户？")
                .font(.caption)
                .foregroundColor(.secondary)

            Button(viewModel.isLoginMode ? "立即注册" : "立即登录") {
                withAnimation(.easeInOut(duration: 0.3)) {
                    viewModel.toggleMode()
                }
            }
            .font(.caption.weight(.semibold))
            .buttonStyle(.plain)
            .foregroundColor(brandBlue)
        }
    }

    // MARK: - 表单验证

    private var isFormValid: Bool {
        guard !viewModel.username.isEmpty, !viewModel.password.isEmpty else {
            return false
        }
        if !viewModel.isLoginMode {
            guard !viewModel.email.isEmpty, viewModel.email.contains("@") else {
                return false
            }
        }
        return true
    }
}

// MARK: - 表单字段组件

struct FormField: View {
    let icon: String
    let label: String
    let placeholder: String
    @Binding var text: String
    var focused: FocusState<LoginView.Field?>.Binding
    let field: LoginView.Field
    var isSecure: Bool = false
    var onSubmit: (() -> Void)?

    @State private var isHovered = false
    @State private var showPassword = false

    private let brandBlue = Color(red: 0.24, green: 0.47, blue: 0.96)

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.caption.weight(.medium))
                .foregroundColor(.secondary)
                .padding(.leading, 4)

            HStack(spacing: 10) {
                Image(systemName: icon)
                    .font(.system(size: 14))
                    .foregroundColor(
                        focused.wrappedValue == field
                            ? brandBlue
                            : .secondary.opacity(0.6)
                    )
                    .frame(width: 18)
                    .animation(.easeInOut(duration: 0.2), value: focused.wrappedValue)

                Group {
                    if isSecure && !showPassword {
                        SecureField(placeholder, text: $text)
                    } else {
                        TextField(placeholder, text: $text)
                    }
                }
                .textFieldStyle(.plain)
                .font(.system(size: 15, design: .rounded))
                .focused(focused, equals: field)
                .onSubmit { onSubmit?() }

                if isSecure {
                    Button(action: { showPassword.toggle() }) {
                        Image(systemName: showPassword ? "eye.slash.fill" : "eye.fill")
                            .font(.caption)
                            .foregroundColor(.secondary.opacity(0.6))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(
                        focused.wrappedValue == field
                            ? Color(nsColor: .controlBackgroundColor)
                            : Color(nsColor: .quaternaryLabelColor).opacity(0.3)
                    )
            )
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .strokeBorder(
                        focused.wrappedValue == field
                            ? brandBlue.opacity(0.5)
                            : Color.clear,
                        lineWidth: 1.5
                    )
            )
            .shadow(
                color: focused.wrappedValue == field
                    ? brandBlue.opacity(0.08)
                    : .clear,
                radius: 6,
                x: 0,
                y: 2
            )
            .animation(.easeInOut(duration: 0.2), value: focused.wrappedValue)
        }
    }
}

// MARK: - 特性项组件

struct FeatureItem: View {
    let icon: String
    let title: String
    let description: String

    private let brandBlue = Color(red: 0.24, green: 0.47, blue: 0.96)

    var body: some View {
        HStack(spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 8)
                    .fill(brandBlue.opacity(0.15))
                    .frame(width: 36, height: 36)

                Image(systemName: icon)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundColor(brandBlue)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                    .foregroundColor(.white.opacity(0.9))
                Text(description)
                    .font(.system(size: 11, design: .rounded))
                    .foregroundColor(.white.opacity(0.45))
            }
        }
    }
}