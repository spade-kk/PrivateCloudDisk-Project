//
//  LoginView.swift
//  PrivateCloudDisk-ios
//
//  登录页面 — 支持手机号+密码登录、注册、Face ID/Touch ID 快速登录
//

import SwiftUI

struct LoginView: View {
    @StateObject private var authVM = AuthViewModel()
    @State private var showRegister = false
    @State private var showServerConfig = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    // Logo
                    VStack(spacing: 12) {
                        Image(systemName: "cloud.fill")
                            .font(.system(size: 60))
                            .foregroundStyle(.blue.gradient)

                        Text("私有云盘")
                            .font(.largeTitle.bold())

                        Text("安全可靠的个人云存储")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.top, 60)
                    .padding(.bottom, 20)

                    // 登录表单
                    VStack(spacing: 16) {
                        // 手机号
                        HStack {
                            Image(systemName: "phone.fill")
                                .foregroundStyle(.secondary)
                            TextField("手机号", text: $authVM.phoneNumber)
                                .keyboardType(.phonePad)
                                .textContentType(.telephoneNumber)
                        }
                        .padding()
                        .background(Color(.systemGray6))
                        .clipShape(RoundedRectangle(cornerRadius: 12))

                        // 密码
                        HStack {
                            Image(systemName: "lock.fill")
                                .foregroundStyle(.secondary)
                            SecureField("密码", text: $authVM.password)
                                .textContentType(.password)
                        }
                        .padding()
                        .background(Color(.systemGray6))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }

                    // 错误提示
                    if let error = authVM.errorMessage {
                        Text(error)
                            .font(.caption)
                            .foregroundStyle(.red)
                            .padding(.horizontal)
                    }

                    // 登录按钮
                    Button(action: {
                        Task { await authVM.login() }
                    }) {
                        HStack {
                            if authVM.isLoading {
                                ProgressView()
                                    .tint(.white)
                            }
                            Text("登录")
                                .fontWeight(.semibold)
                        }
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.blue)
                        .foregroundColor(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    .disabled(authVM.isLoading)

                    // 注册按钮
                    Button("没有账号？立即注册") {
                        showRegister = true
                    }
                    .font(.subheadline)

                    // Biometric 登录
                    if BiometricAuthManager.shared.isBiometricAvailable {
                        Divider()
                            .padding(.horizontal, 40)

                        Button(action: {
                            Task { await authVM.biometricLogin() }
                        }) {
                            Label(
                                "使用\(BiometricAuthManager.shared.biometricTypeName)登录",
                                systemImage: BiometricAuthManager.shared.biometricIconName
                            )
                            .font(.subheadline)
                        }
                    }

                    // 服务器配置
                    Button("服务器设置") {
                        showServerConfig = true
                    }
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }
                .padding(.horizontal, 24)
            }
            .sheet(isPresented: $showRegister) {
                RegisterView(authVM: authVM)
            }
            .sheet(isPresented: $showServerConfig) {
                ServerConfigView(authVM: authVM)
            }
        }
    }
}

// MARK: - 注册页面

struct RegisterView: View {
    @ObservedObject var authVM: AuthViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var step: RegisterStep = .inputPhone

    enum RegisterStep {
        case inputPhone
        case verifyCode
        case setPassword
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                switch step {
                case .inputPhone:
                    phoneInputStep
                case .verifyCode:
                    verifyCodeStep
                case .setPassword:
                    setPasswordStep
                }
            }
            .padding()
            .navigationTitle("注册")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
            }
        }
    }

    private var phoneInputStep: some View {
        VStack(spacing: 16) {
            Text("输入手机号")
                .font(.title2.bold())

            HStack {
                Image(systemName: "phone.fill")
                TextField("手机号", text: $authVM.phoneNumber)
                    .keyboardType(.phonePad)
            }
            .padding()
            .background(Color(.systemGray6))
            .clipShape(RoundedRectangle(cornerRadius: 12))

            HStack {
                Image(systemName: "person.fill")
                TextField("用户名", text: $authVM.registerUsername)
            }
            .padding()
            .background(Color(.systemGray6))
            .clipShape(RoundedRectangle(cornerRadius: 12))

            Button("下一步") {
                step = .verifyCode
            }
            .buttonStyle(.borderedProminent)
            .disabled(authVM.phoneNumber.isEmpty || authVM.registerUsername.isEmpty)
        }
    }

    private var verifyCodeStep: some View {
        VStack(spacing: 16) {
            Text("验证手机号")
                .font(.title2.bold())

            HStack {
                Image(systemName: "envelope.fill")
                TextField("验证码", text: $authVM.registerCode)
                    .keyboardType(.numberPad)
            }
            .padding()
            .background(Color(.systemGray6))
            .clipShape(RoundedRectangle(cornerRadius: 12))

            Button("发送验证码") {
                Task { await authVM.sendVerificationCode() }
            }
            .disabled(authVM.isLoading)

            Button("下一步") {
                step = .setPassword
            }
            .buttonStyle(.borderedProminent)
            .disabled(authVM.registerCode.isEmpty)
        }
    }

    private var setPasswordStep: some View {
        VStack(spacing: 16) {
            Text("设置密码")
                .font(.title2.bold())

            SecureField("密码", text: $authVM.password)
                .padding()
                .background(Color(.systemGray6))
                .clipShape(RoundedRectangle(cornerRadius: 12))

            if let error = authVM.errorMessage {
                Text(error).foregroundColor(.red).font(.caption)
            }

            Button("完成注册") {
                Task {
                    await authVM.register()
                    if authVM.isLoggedIn { dismiss() }
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(authVM.password.isEmpty)
        }
    }
}

// MARK: - 服务器配置

struct ServerConfigView: View {
    @ObservedObject var authVM: AuthViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("服务器地址") {
                    TextField("https://api.example.com", text: $authVM.serverURL)
                        .keyboardType(.URL)
                        .autocapitalization(.none)
                }

                Section {
                    Button("保存") {
                        KeychainManager.shared.saveServerURL(authVM.serverURL)
                        dismiss()
                    }
                }
            }
            .navigationTitle("服务器设置")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

#Preview {
    LoginView()
}