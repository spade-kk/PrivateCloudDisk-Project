//
//  FriendProfileView.swift
//  PrivateCloudDisk-ios
//
//  好友个人资料页
//  扫码识别到好友二维码时跳转此页面
//  显示用户头像、昵称、ID，支持添加好友
//  风格类似微信添加好友页面，简约企业级
//

import SwiftUI

// MARK: - 好友资料视图

struct FriendProfileView: View {
    let userId: String
    let nickname: String?
    let profileURL: String

    @Environment(\.dismiss) private var dismiss
    @State private var isAdding = false
    @State private var showResult = false
    @State private var addSuccess = false
    @State private var verifyMessage = ""

    var onAddFriend: (() -> Void)?

    /// 显示的昵称（fallback 到 userId 前缀）
    private var displayName: String {
        if let name = nickname, !name.isEmpty {
            return name
        }
        return "用户 " + String(userId.prefix(8))
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                // 头像区
                avatarSection
                    .padding(.top, AppSpacing.xxxl + AppSpacing.xl)

                // 用户信息区
                userInfoSection
                    .padding(.top, AppSpacing.xxl)

                // 验证消息输入
                verifyMessageSection
                    .padding(.top, AppSpacing.xxxl)
                    .padding(.horizontal, AppSpacing.xl)

                // 操作按钮
                actionButtons
                    .padding(.top, AppSpacing.xl)
                    .padding(.horizontal, AppSpacing.xl)
            }
            .padding(.bottom, AppSpacing.xxxl)
        }
        .background(AppColors.background)
        .navigationTitle("详细资料")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(isAdding)
        .alert(addSuccess ? "已发送" : "添加失败", isPresented: $showResult) {
            if addSuccess {
                Button("完成") {
                    onAddFriend?()
                    dismiss()
                }
            } else {
                Button("重试", role: .cancel) {}
                Button("返回") { dismiss() }
            }
        } message: {
            Text(addSuccess
                ? "好友请求已发送，等待对方验证通过"
                : "添加好友请求失败，请检查网络后重试"
            )
        }
    }

    // MARK: - 头像区

    private var avatarSection: some View {
        VStack(spacing: AppSpacing.md) {
            ZStack {
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [Color(hex: "#667EEA"), Color(hex: "#764BA2")],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 88, height: 88)

                Text(String(displayName.prefix(1)).uppercased())
                    .font(.system(size: 36, weight: .semibold))
                    .foregroundColor(.white)
            }

            Text(displayName)
                .font(AppTypography.title2)
                .foregroundColor(AppColors.textPrimary)

            Text("CloudDrive 用户")
                .font(AppTypography.subheadline)
                .foregroundColor(AppColors.textSecondary)
        }
    }

    // MARK: - 用户信息区

    private var userInfoSection: some View {
        VStack(spacing: 0) {
            infoRow(icon: "person.text.rectangle", title: "用户 ID", value: userId)
            Divider().padding(.leading, 48)
            infoRow(icon: "globe", title: "来源", value: "通过二维码扫描")
        }
        .appCard(padding: 0)
        .padding(.horizontal, AppSpacing.xl)
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
                .font(AppTypography.subheadline.monospaced())
                .foregroundColor(AppColors.textPrimary)
                .lineLimit(1)
        }
        .padding(.horizontal, AppSpacing.lg)
        .padding(.vertical, 14)
    }

    // MARK: - 验证消息

    private var verifyMessageSection: some View {
        VStack(alignment: .leading, spacing: AppSpacing.sm) {
            Text("发送验证消息")
                .font(AppTypography.headline)
                .foregroundColor(AppColors.textPrimary)

            Text("对方验证通过后，即可成为好友")
                .font(AppTypography.footnote)
                .foregroundColor(AppColors.textTertiary)
                .padding(.bottom, AppSpacing.xs)

            TextField("我是 \(displayName)", text: $verifyMessage)
                .appInputField()
        }
    }

    // MARK: - 操作按钮

    private var actionButtons: some View {
        VStack(spacing: AppSpacing.md) {
            AppPrimaryButton(isAdding ? "发送中..." : "添加到通讯录", isLoading: isAdding) {
                addFriend()
            }

            if !isAdding {
                Button("取消") {
                    dismiss()
                }
                .font(AppTypography.subheadline)
                .foregroundColor(AppColors.textSecondary)
                .padding(.vertical, AppSpacing.sm)
            }
        }
    }

    // MARK: - 添加好友逻辑

    private func addFriend() {
        isAdding = true
        // 模拟请求（实际项目中替换为真实 API）
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
            isAdding = false
            addSuccess = true
            showResult = true
        }
    }
}

// MARK: - 预览

#Preview("好友资料页") {
    NavigationStack {
        FriendProfileView(
            userId: "u_abc123def456",
            nickname: "张晓明",
            profileURL: "https://clouddrive.example.com/user/u_abc123def456"
        )
    }
}

#Preview("好友资料 - 无昵称") {
    NavigationStack {
        FriendProfileView(
            userId: "u_xyz789ghi012",
            nickname: nil,
            profileURL: "clouddrive://user/u_xyz789ghi012"
        )
    }
}

#Preview("好友资料 - 暗色模式") {
    NavigationStack {
        FriendProfileView(
            userId: "u_test001",
            nickname: "李四",
            profileURL: "https://clouddrive.example.com/user/u_test001"
        )
    }
    .preferredColorScheme(.dark)
}