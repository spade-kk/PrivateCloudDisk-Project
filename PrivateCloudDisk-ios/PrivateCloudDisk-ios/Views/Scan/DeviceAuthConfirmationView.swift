//
//  DeviceAuthConfirmationView.swift
//  PrivateCloudDisk-ios
//
//  设备授权确认页面
//  扫码后识别到设备授权链接时，直接跳转此页面
//  用户确认后授权设备登录
//

import SwiftUI

// MARK: - 设备授权确认视图

struct DeviceAuthConfirmationView: View {
    let userCode: String
    let deviceToken: String?
    let authURL: String

    @Environment(\.dismiss) private var dismiss
    @State private var isAuthorizing = false
    @State private var showResult = false
    @State private var authSuccess = false

    /// 授权成功回调
    var onAuthorized: (() -> Void)?

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                // 设备图标区
                deviceHeaderSection
                    .padding(.top, AppSpacing.xxxl + AppSpacing.xl)

                // 用户码展示区
                userCodeSection
                    .padding(.top, AppSpacing.xxxl)

                // 设备信息
                deviceInfoSection
                    .padding(.top, AppSpacing.xxl)

                // 操作按钮
                actionButtons
                    .padding(.top, AppSpacing.xxxl + AppSpacing.xl)
                    .padding(.horizontal, AppSpacing.xl)
            }
            .padding(.bottom, AppSpacing.xxxl)
        }
        .background(AppColors.background)
        .navigationTitle("设备授权")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(isAuthorizing)
        .alert(authSuccess ? "授权成功" : "授权失败", isPresented: $showResult) {
            if authSuccess {
                Button("完成") {
                    onAuthorized?()
                    dismiss()
                }
            } else {
                Button("重试", role: .cancel) {}
                Button("返回") { dismiss() }
            }
        } message: {
            Text(authSuccess
                ? "设备已成功授权，可在设备上继续操作"
                : "授权请求失败，请确认设备码是否正确或稍后重试"
            )
        }
    }

    // MARK: - 设备图标区

    private var deviceHeaderSection: some View {
        VStack(spacing: AppSpacing.lg) {
            ZStack {
                Circle()
                    .fill(AppColors.primaryBg)
                    .frame(width: 88, height: 88)

                Image(systemName: "desktopcomputer")
                    .font(.system(size: 36))
                    .foregroundColor(AppColors.primary)
            }

            Text("设备授权登录")
                .font(AppTypography.title2)
                .foregroundColor(AppColors.textPrimary)

            Text("确认授权后，设备将登录您的账号")
                .font(AppTypography.subheadline)
                .foregroundColor(AppColors.textSecondary)
                .multilineTextAlignment(.center)
        }
    }

    // MARK: - 用户码展示区

    private var userCodeSection: some View {
        VStack(spacing: AppSpacing.lg) {
            Text("请在设备上确认以下用户码")
                .font(AppTypography.footnote)
                .foregroundColor(AppColors.textTertiary)

            HStack(spacing: AppSpacing.md) {
                ForEach(Array(userCode.enumerated()), id: \.offset) { _, char in
                    if char == "-" {
                        Text("-")
                            .font(AppTypography.largeTitle)
                            .foregroundColor(AppColors.textTertiary)
                    } else {
                        Text(String(char))
                            .font(.system(size: 28, weight: .bold, design: .monospaced))
                            .foregroundColor(AppColors.primary)
                            .frame(width: 36, height: 48)
                            .background(
                                RoundedRectangle(cornerRadius: AppRadius.sm)
                                    .fill(AppColors.primaryBg)
                            )
                    }
                }
            }

            // 复制按钮
            Button(action: { UIPasteboard.general.string = userCode }) {
                HStack(spacing: 4) {
                    Image(systemName: "doc.on.doc")
                        .font(.caption2)
                    Text("复制用户码")
                        .font(AppTypography.footnote)
                }
                .foregroundColor(AppColors.primary)
            }
            .padding(.top, AppSpacing.xs)
        }
    }

    // MARK: - 设备信息

    private var deviceInfoSection: some View {
        VStack(alignment: .leading, spacing: AppSpacing.md) {
            Text("设备信息")
                .font(AppTypography.headline)
                .foregroundColor(AppColors.textPrimary)
                .padding(.horizontal, AppSpacing.xl)

            VStack(spacing: 0) {
                infoRow(icon: "link", title: "授权来源", value: formatHost(authURL))
                Divider().padding(.leading, 48)
                infoRow(icon: "key", title: "设备令牌", value: deviceToken.map { String($0.prefix(20)) + "..." } ?? "—")
                Divider().padding(.leading, 48)
                infoRow(icon: "clock", title: "有效期", value: "5 分钟")
            }
            .appCard(padding: 0)
            .padding(.horizontal, AppSpacing.xl)
        }
    }

    private func infoRow(icon: String, title: String, value: String) -> some View {
        HStack(spacing: AppSpacing.md) {
            Image(systemName: icon)
                .font(.subheadline)
                .foregroundColor(AppColors.textTertiary)
                .frame(width: 20)

            Text(title)
                .font(AppTypography.subheadline)
                .foregroundColor(AppColors.textSecondary)

            Spacer()

            Text(value)
                .font(AppTypography.subheadline)
                .foregroundColor(AppColors.textPrimary)
                .lineLimit(1)
        }
        .padding(.horizontal, AppSpacing.lg)
        .padding(.vertical, 14)
    }

    // MARK: - 操作按钮

    private var actionButtons: some View {
        VStack(spacing: AppSpacing.md) {
            AppPrimaryButton(isAuthorizing ? "授权中..." : "确认授权", isLoading: isAuthorizing) {
                authorize()
            }

            if !isAuthorizing {
                Button("取消") {
                    dismiss()
                }
                .font(AppTypography.subheadline)
                .foregroundColor(AppColors.textSecondary)
                .padding(.vertical, AppSpacing.sm)
            }
        }
    }

    // MARK: - 授权逻辑

    private func authorize() {
        isAuthorizing = true
        // 模拟授权请求（实际项目中替换为真实 API 调用）
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            isAuthorizing = false
            authSuccess = true
            showResult = true
        }
    }

    private func formatHost(_ urlString: String) -> String {
        guard let url = URL(string: urlString), let host = url.host else {
            return urlString
        }
        return host
    }
}

// MARK: - 预览

#Preview("设备授权确认页") {
    NavigationStack {
        DeviceAuthConfirmationView(
            userCode: "KD8X-2P9A",
            deviceToken: "eyJhbGciOiJSUzI1NiJ9.eyJkZXZpY2VfaWQiOiJkZXZfMDAxIn0.xxx",
            authURL: "https://clouddrive.example.com/device/authorize?user_code=KD8X-2P9A&device_token=xxx"
        )
    }
}

#Preview("设备授权 - 暗色模式") {
    NavigationStack {
        DeviceAuthConfirmationView(
            userCode: "AB3C-7F2K",
            deviceToken: nil,
            authURL: "https://drive.company.com/device/auth?user_code=AB3C-7F2K"
        )
    }
    .preferredColorScheme(.dark)
}