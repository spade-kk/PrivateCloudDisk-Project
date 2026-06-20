//
//  LoginView.swift
//  PrivateCloudDisk-ios
//
//  登录页 — 企业级简约风格
//  支持手机号+密码登录、注册、生物识别快速登录
//

import SwiftUI

struct LoginView: View {
    @StateObject private var authVM = AuthViewModel()
    @State private var showRegister = false
    @State private var showServerConfig = false
    @State private var isPasswordVisible = false
    @FocusState private var focusedField: Field?

    enum Field { case phone, password }

    var body: some View {
        ZStack {
            // 背景
            AppColors.background.ignoresSafeArea()

            VStack(spacing: 0) {
                Spacer()

                // 品牌区
                brandSection
                    .padding(.bottom, AppSpacing.xxxl)

                // 表单区
                formSection
                    .padding(.horizontal, AppSpacing.xxl)

                // 登录按钮
                VStack(spacing: AppSpacing.lg) {
                    AppPrimaryButton(
                        "登录",
                        isLoading: authVM.isLoading
                    ) {
                        Task { await authVM.login() }
                    }
                    .padding(.horizontal, AppSpacing.xxl)

                    // 错误提示
                    if let error = authVM.errorMessage {
                        Text(error)
                            .font(AppTypography.footnote)
                            .foregroundColor(AppColors.danger)
                            .padding(.horizontal, AppSpacing.xxl)
                            .transition(.opacity.combined(with: .move(edge: .top)))
                    }
                }
                .padding(.top, AppSpacing.xxl)

                // 底部操作区
                bottomActions
                    .padding(.top, AppSpacing.xxxl)

                Spacer()
            }
        }
        .animation(.easeInOut(duration: 0.3), value: authVM.errorMessage)
        .sheet(isPresented: $showRegister) {
            RegisterView(authVM: authVM)
        }
        .sheet(isPresented: $showServerConfig) {
            ServerConfigView(authVM: authVM)
        }
    }

    // MARK: - 品牌区

    private var brandSection: some View {
        VStack(spacing: AppSpacing.lg) {
            // Logo
            ZStack {
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [AppColors.primary, AppColors.primaryLight],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 80, height: 80)
                    .shadow(color: AppColors.primary.opacity(0.3), radius: 16, y: 8)

                Image(systemName: "cloud.fill")
                    .font(.system(size: 36))
                    .foregroundColor(.white)
            }

            VStack(spacing: AppSpacing.xs) {
                Text("私有云盘")
                    .font(AppTypography.title1)
                    .foregroundColor(AppColors.textPrimary)

                Text("安全可靠的个人云存储")
                    .font(AppTypography.subheadline)
                    .foregroundColor(AppColors.textSecondary)
            }
        }
    }

    // MARK: - 表单区

    private var formSection: some View {
        VStack(spacing: AppSpacing.md) {
            // 手机号
            phoneField

            // 密码
            passwordField
        }
    }

    private var phoneField: some View {
        HStack(spacing: AppSpacing.md) {
            Image(systemName: "phone.fill")
                .font(.subheadline)
                .foregroundColor(focusedField == .phone ? AppColors.primary : AppColors.textTertiary)
                .frame(width: 22)

            TextField("手机号", text: $authVM.phoneNumber)
                .font(AppTypography.body)
                .keyboardType(.phonePad)
                .textContentType(.telephoneNumber)
                .focused($focusedField, equals: .phone)
                .foregroundColor(AppColors.textPrimary)
        }
        .appInputField()
        .overlay(
            RoundedRectangle(cornerRadius: AppRadius.md)
                .stroke(
                    focusedField == .phone ? AppColors.primary : Color.clear,
                    lineWidth: 1.5
                )
        )
        .animation(.easeInOut(duration: 0.2), value: focusedField)
    }

    private var passwordField: some View {
        HStack(spacing: AppSpacing.md) {
            Image(systemName: "lock.fill")
                .font(.subheadline)
                .foregroundColor(focusedField == .password ? AppColors.primary : AppColors.textTertiary)
                .frame(width: 22)

            if isPasswordVisible {
                TextField("密码", text: $authVM.password)
                    .font(AppTypography.body)
                    .textContentType(.password)
                    .focused($focusedField, equals: .password)
                    .foregroundColor(AppColors.textPrimary)
            } else {
                SecureField("密码", text: $authVM.password)
                    .font(AppTypography.body)
                    .textContentType(.password)
                    .focused($focusedField, equals: .password)
                    .foregroundColor(AppColors.textPrimary)
            }

            Button(action: {
                withAnimation(.easeInOut(duration: 0.15)) {
                    isPasswordVisible.toggle()
                }
            }) {
                Image(systemName: isPasswordVisible ? "eye.slash.fill" : "eye.fill")
                    .font(.subheadline)
                    .foregroundColor(AppColors.textTertiary)
            }
        }
        .appInputField()
        .overlay(
            RoundedRectangle(cornerRadius: AppRadius.md)
                .stroke(
                    focusedField == .password ? AppColors.primary : Color.clear,
                    lineWidth: 1.5
                )
        )
        .animation(.easeInOut(duration: 0.2), value: focusedField)
    }

    // MARK: - 底部操作

    private var bottomActions: some View {
        VStack(spacing: AppSpacing.lg) {
            // 注册
            Button(action: { showRegister = true }) {
                Text("没有账号？")
                    .foregroundColor(AppColors.textSecondary)
                    +
                Text("立即注册")
                    .foregroundColor(AppColors.primary)
                    .fontWeight(.semibold)
            }
            .font(AppTypography.subheadline)

            // 生物识别
            if BiometricAuthManager.shared.isBiometricAvailable {
                biometricButton
            }

            // 服务器配置
            Button(action: { showServerConfig = true }) {
                Label("服务器设置", systemImage: "gearshape.fill")
                    .font(AppTypography.footnote)
                    .foregroundColor(AppColors.textTertiary)
            }
        }
    }

    private var biometricButton: some View {
        Button(action: {
            Task { await authVM.biometricLogin() }
        }) {
            HStack(spacing: AppSpacing.sm) {
                Image(systemName: BiometricAuthManager.shared.biometricIconName)
                    .font(.title3)
                Text("使用\(BiometricAuthManager.shared.biometricTypeName)登录")
                    .font(AppTypography.subheadline.weight(.medium))
            }
            .foregroundColor(AppColors.primary)
            .padding(.horizontal, AppSpacing.xxl)
            .padding(.vertical, AppSpacing.md)
            .background(AppColors.primaryBg)
            .clipShape(Capsule())
        }
    }
}

// MARK: - 注册页面

struct RegisterView: View {
    @ObservedObject var authVM: AuthViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var step: RegisterStep = .inputPhone
    @State private var isPasswordVisible = false

    enum RegisterStep {
        case inputPhone, verifyCode, setPassword
    }

    var body: some View {
        NavigationStack {
            ZStack {
                AppColors.background.ignoresSafeArea()

                VStack(spacing: AppSpacing.xxl) {
                    // 步骤指示器
                    stepIndicator
                        .padding(.top, AppSpacing.lg)

                    // 步骤内容
                    switch step {
                    case .inputPhone:
                        phoneInputStep
                    case .verifyCode:
                        verifyCodeStep
                    case .setPassword:
                        setPasswordStep
                    }
                }
                .padding(.horizontal, AppSpacing.xxl)
            }
            .navigationTitle("注册")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
            }
        }
    }

    // MARK: - 步骤指示器

    private var stepIndicator: some View {
        HStack(spacing: 0) {
            ForEach(0..<3) { i in
                stepDot(index: i)
                if i < 2 {
                    Rectangle()
                        .fill(stepColor(i + 1).opacity(0.3))
                        .frame(height: 2)
                        .frame(maxWidth: 40)
                }
            }
        }
    }

    private func stepDot(index: Int) -> some View {
        let currentStepIndex: Int = {
            switch step {
            case .inputPhone: return 0
            case .verifyCode: return 1
            case .setPassword: return 2
            }
        }()

        return ZStack {
            Circle()
                .fill(stepColor(index))
                .frame(width: 28, height: 28)

            if index < currentStepIndex {
                Image(systemName: "checkmark")
                    .font(.caption2.weight(.bold))
                    .foregroundColor(.white)
            } else {
                Text("\(index + 1)")
                    .font(AppTypography.caption1.weight(.bold))
                    .foregroundColor(index == currentStepIndex ? .white : AppColors.textSecondary)
            }
        }
    }

    private func stepColor(_ index: Int) -> Color {
        let currentStepIndex: Int = {
            switch step {
            case .inputPhone: return 0
            case .verifyCode: return 1
            case .setPassword: return 2
            }
        }()
        if index < currentStepIndex { return AppColors.success }
        if index == currentStepIndex { return AppColors.primary }
        return AppColors.surfaceTertiary
    }

    // MARK: - 步骤 1: 输入手机号

    private var phoneInputStep: some View {
        VStack(spacing: AppSpacing.xl) {
            VStack(spacing: AppSpacing.sm) {
                Text("输入手机号")
                    .font(AppTypography.title3)
                    .foregroundColor(AppColors.textPrimary)
                Text("用于接收验证码和登录")
                    .font(AppTypography.subheadline)
                    .foregroundColor(AppColors.textSecondary)
            }

            VStack(spacing: AppSpacing.md) {
                phoneInputField(icon: "phone.fill", text: $authVM.phoneNumber, placeholder: "手机号")
                phoneInputField(icon: "person.fill", text: $authVM.registerUsername, placeholder: "用户名")
            }

            AppPrimaryButton("下一步") {
                withAnimation(.easeInOut(duration: 0.3)) {
                    step = .verifyCode
                }
            }
            .disabled(authVM.phoneNumber.isEmpty || authVM.registerUsername.isEmpty)
        }
        .transition(.asymmetric(
            insertion: .move(edge: .trailing).combined(with: .opacity),
            removal: .move(edge: .leading).combined(with: .opacity)
        ))
    }

    // MARK: - 步骤 2: 验证码

    private var verifyCodeStep: some View {
        VStack(spacing: AppSpacing.xl) {
            VStack(spacing: AppSpacing.sm) {
                Text("验证手机号")
                    .font(AppTypography.title3)
                    .foregroundColor(AppColors.textPrimary)
                Text("已发送验证码到 \(authVM.phoneNumber)")
                    .font(AppTypography.subheadline)
                    .foregroundColor(AppColors.textSecondary)
            }

            phoneInputField(icon: "envelope.fill", text: $authVM.registerCode, placeholder: "验证码")
                .keyboardType(.numberPad)

            AppSecondaryButton("重新发送验证码") {
                Task { await authVM.sendVerificationCode() }
            }
            .disabled(authVM.isLoading)

            AppPrimaryButton("下一步") {
                withAnimation(.easeInOut(duration: 0.3)) {
                    step = .setPassword
                }
            }
            .disabled(authVM.registerCode.isEmpty)
        }
        .transition(.asymmetric(
            insertion: .move(edge: .trailing).combined(with: .opacity),
            removal: .move(edge: .leading).combined(with: .opacity)
        ))
    }

    // MARK: - 步骤 3: 设置密码

    private var setPasswordStep: some View {
        VStack(spacing: AppSpacing.xl) {
            VStack(spacing: AppSpacing.sm) {
                Text("设置密码")
                    .font(AppTypography.title3)
                    .foregroundColor(AppColors.textPrimary)
                Text("用于登录你的账号")
                    .font(AppTypography.subheadline)
                    .foregroundColor(AppColors.textSecondary)
            }

            HStack(spacing: AppSpacing.md) {
                Image(systemName: "lock.fill")
                    .font(.subheadline)
                    .foregroundColor(AppColors.textTertiary)
                    .frame(width: 22)

                if isPasswordVisible {
                    TextField("密码", text: $authVM.password)
                } else {
                    SecureField("密码", text: $authVM.password)
                }

                Button(action: {
                    withAnimation(.easeInOut(duration: 0.15)) {
                        isPasswordVisible.toggle()
                    }
                }) {
                    Image(systemName: isPasswordVisible ? "eye.slash.fill" : "eye.fill")
                        .font(.subheadline)
                        .foregroundColor(AppColors.textTertiary)
                }
            }
            .appInputField()

            if let error = authVM.errorMessage {
                Text(error)
                    .font(AppTypography.footnote)
                    .foregroundColor(AppColors.danger)
            }

            AppPrimaryButton("完成注册", isLoading: authVM.isLoading) {
                Task {
                    await authVM.register()
                    if authVM.isLoggedIn { dismiss() }
                }
            }
            .disabled(authVM.password.isEmpty)
        }
        .transition(.asymmetric(
            insertion: .move(edge: .trailing).combined(with: .opacity),
            removal: .move(edge: .leading).combined(with: .opacity)
        ))
    }

    private func phoneInputField(icon: String, text: Binding<String>, placeholder: String) -> some View {
        HStack(spacing: AppSpacing.md) {
            Image(systemName: icon)
                .font(.subheadline)
                .foregroundColor(AppColors.textTertiary)
                .frame(width: 22)
            TextField(placeholder, text: text)
                .font(AppTypography.body)
                .foregroundColor(AppColors.textPrimary)
        }
        .appInputField()
    }
}

// MARK: - 服务器配置

struct ServerConfigView: View {
    @ObservedObject var authVM: AuthViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var showSavedToast = false

    var body: some View {
        NavigationStack {
            ZStack {
                AppColors.background.ignoresSafeArea()

                VStack(spacing: AppSpacing.xl) {
                    VStack(alignment: .leading, spacing: AppSpacing.sm) {
                        Text("服务器地址")
                            .font(AppTypography.subheadline.weight(.medium))
                            .foregroundColor(AppColors.textPrimary)

                        TextField("https://api.example.com", text: $authVM.serverURL)
                            .font(AppTypography.body)
                            .keyboardType(.URL)
                            .autocapitalization(.none)
                            .appInputField()
                    }

                    AppPrimaryButton("保存") {
                        KeychainManager.shared.saveServerURL(authVM.serverURL)
                        showSavedToast = true
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                            dismiss()
                        }
                    }
                }
                .padding(AppSpacing.xxl)
            }
            .navigationTitle("服务器设置")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
            }
            .appToast(isPresented: $showSavedToast, message: "已保存")
        }
    }
}

#Preview {
    LoginView()
}