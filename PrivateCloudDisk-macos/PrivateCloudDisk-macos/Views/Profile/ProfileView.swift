import SwiftUI

// MARK: - 用户资料视图（企业级设计）

/// 用户资料和账户管理页面
///
/// 参考百度网盘 macOS 客户端设计：
/// - 渐变头像圈
/// - 卡片式信息分组
/// - 品牌色点缀
/// - 优雅的登出按钮
struct ProfileView: View {
    @EnvironmentObject var authService: AuthService

    @State private var showLogoutAlert = false
    @State private var showAvatarPicker = false
    @State private var nickname = ""
    @State private var showSaveSuccess = false
    @State private var avatarScale: CGFloat = 0.8

    private let brandBlue = AppColors.primary

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 24) {
                // 头像区域
                avatarSection
                    .scaleEffect(avatarScale)
                    .onAppear {
                        withAnimation(.spring(response: 0.6, dampingFraction: 0.7)) {
                            avatarScale = 1.0
                        }
                    }

                // 账户信息
                accountInfoSection

                // 配额信息
                quotaSection

                // 存储详情
                storageDetailSection

                // 登出
                logoutSection
            }
            .padding(24)
            .padding(.top, 10)
        }
        .background(.ultraThinMaterial)
        .alert("确认退出登录？", isPresented: $showLogoutAlert) {
            Button("取消", role: .cancel) {}
            Button("退出", role: .destructive) {
                Task { await authService.logout() }
            }
        } message: {
            Text("退出后需要重新登录才能访问您的文件。")
        }
    }

    // MARK: - 头像

    private var avatarSection: some View {
        VStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [brandBlue, AppColors.info.opacity(0.6)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 88, height: 88)
                    .shadow(color: brandBlue.opacity(0.3), radius: 16, x: 0, y: 8)

                Text(avatarInitials)
                    .font(.system(size: 36, weight: .bold, design: .rounded))
                    .foregroundColor(.white)
            }
            .onTapGesture {
                showAvatarPicker = true
            }
            .overlay(
                // 编辑覆盖层
                Circle()
                    .fill(.white.opacity(0.2))
                    .frame(width: 88, height: 88)
                    .overlay(
                        Image(systemName: "camera.fill")
                            .font(.system(size: 14))
                            .foregroundColor(.white.opacity(0.8))
                    )
                    .opacity(0)
            )

            VStack(spacing: 4) {
                Text(authService.currentUser?.nickname ?? authService.currentUser?.username ?? "用户")
                    .font(.system(size: 20, weight: .bold, design: .rounded))

                Text(authService.currentUser?.email ?? "")
                    .font(.system(size: 13, design: .rounded))
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 20)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 18)
                .fill(.regularMaterial)
                .shadow(color: .black.opacity(0.04), radius: 8, x: 0, y: 2)
        )
    }

    // MARK: - 账户信息

    private var accountInfoSection: some View {
        VStack(alignment: .leading, spacing: 0) {
            sectionHeader(title: "账户信息", icon: "person.text.rectangle")

            VStack(spacing: 0) {
                ProfileInfoRow(label: "用户名", value: authService.currentUser?.username ?? "-")
                ProfileDivider()
                ProfileInfoRow(label: "邮箱", value: authService.currentUser?.email ?? "-")
                ProfileDivider()
                ProfileInfoRow(label: "手机", value: authService.currentUser?.phone ?? "未绑定")
                ProfileDivider()
                ProfileInfoRow(label: "注册时间", value: authService.currentUser?.createdAt ?? "-")
            }
        }
        .padding(20)
        .background(
            RoundedRectangle(cornerRadius: 18)
                .fill(.regularMaterial)
                .shadow(color: .black.opacity(0.04), radius: 8, x: 0, y: 2)
        )
    }

    // MARK: - 配额

    private var quotaSection: some View {
        VStack(alignment: .leading, spacing: 0) {
            sectionHeader(title: "存储空间", icon: "internaldrive")

            VStack(spacing: 16) {
                // 容量进度条
                VStack(spacing: 8) {
                    HStack {
                        Text("已使用")
                            .font(.system(size: 13, design: .rounded))
                            .foregroundColor(.secondary)
                        Spacer()
                        Text("10.5 GB / 100 GB")
                            .font(.system(size: 13, weight: .semibold, design: .rounded))
                    }

                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            RoundedRectangle(cornerRadius: 4)
                                .fill(.quaternary)
                                .frame(height: 8)

                            RoundedRectangle(cornerRadius: 4)
                                .fill(
                                    LinearGradient(
                                        colors: [brandBlue, brandBlue.opacity(0.7)],
                                        startPoint: .leading,
                                        endPoint: .trailing
                                    )
                                )
                                .frame(width: geo.size.width * 0.105, height: 8)
                        }
                    }
                    .frame(height: 8)

                    HStack {
                        Circle()
                            .fill(brandBlue)
                            .frame(width: 8, height: 8)
                        Text("已使用")
                            .font(.system(size: 11, design: .rounded))
                            .foregroundColor(.secondary)

                        Circle()
                            .fill(.quaternary)
                            .frame(width: 8, height: 8)
                        Text("可用空间")
                            .font(.system(size: 11, design: .rounded))
                            .foregroundColor(.secondary)
                        Spacer()
                    }
                }

                ProfileDivider()

                ProfileInfoRow(label: "文件数", value: "1,234")
            }
            .padding(.top, 12)
        }
        .padding(20)
        .background(
            RoundedRectangle(cornerRadius: 18)
                .fill(.regularMaterial)
                .shadow(color: .black.opacity(0.04), radius: 8, x: 0, y: 2)
        )
    }

    // MARK: - 存储详情

    private var storageDetailSection: some View {
        VStack(alignment: .leading, spacing: 0) {
            sectionHeader(title: "存储详情", icon: "chart.pie")

            HStack(spacing: 20) {
                storageTypeItem(
                    icon: "doc.fill",
                    color: brandBlue,
                    label: "文档",
                    size: "3.2 GB",
                    count: "856"
                )
                storageTypeItem(
                    icon: "photo.fill",
                    color: .purple,
                    label: "图片",
                    size: "4.1 GB",
                    count: "234"
                )
                storageTypeItem(
                    icon: "video.fill",
                    color: .pink,
                    label: "视频",
                    size: "2.8 GB",
                    count: "45"
                )
                storageTypeItem(
                    icon: "archivebox.fill",
                    color: .orange,
                    label: "其他",
                    size: "0.4 GB",
                    count: "99"
                )
            }
            .padding(.top, 12)
        }
        .padding(20)
        .background(
            RoundedRectangle(cornerRadius: 18)
                .fill(.regularMaterial)
                .shadow(color: .black.opacity(0.04), radius: 8, x: 0, y: 2)
        )
    }

    private func storageTypeItem(icon: String, color: Color, label: String, size: String, count: String) -> some View {
        VStack(spacing: 8) {
            ZStack {
                RoundedRectangle(cornerRadius: 10)
                    .fill(color.opacity(0.12))
                    .frame(width: 48, height: 48)

                Image(systemName: icon)
                    .font(.system(size: 20))
                    .foregroundColor(color)
            }

            Text(label)
                .font(.system(size: 12, weight: .medium, design: .rounded))
            Text(size)
                .font(.system(size: 11, design: .rounded))
                .foregroundColor(.secondary)
            Text("\(count) 个")
                .font(.system(size: 10, design: .rounded))
                .foregroundColor(.secondary)
        }
    }

    // MARK: - 登出

    private var logoutSection: some View {
        Button(action: { showLogoutAlert = true }) {
            HStack(spacing: 8) {
                Image(systemName: "rectangle.portrait.and.arrow.right")
                    .font(.system(size: 14, weight: .medium))
                Text("退出登录")
                    .font(.system(size: 13, weight: .medium, design: .rounded))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(.red.opacity(0.08))
            )
            .foregroundColor(.red)
        }
        .buttonStyle(.plain)
    }

    // MARK: - 区块标题辅助

    private func sectionHeader(title: String, icon: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(brandBlue)
            Text(title)
                .font(.system(size: 14, weight: .semibold, design: .rounded))
        }
        .padding(.bottom, 4)
    }

    private var avatarInitials: String {
        let name = authService.currentUser?.nickname ?? authService.currentUser?.username ?? "?"
        return String(name.prefix(1)).uppercased()
    }
}

// MARK: - 辅助组件

struct ProfileInfoRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label)
                .font(.system(size: 13, design: .rounded))
                .foregroundColor(.secondary)
                .frame(width: 80, alignment: .leading)
            Text(value)
                .font(.system(size: 13, design: .rounded))
            Spacer()
        }
        .padding(.vertical, 10)
    }
}

struct ProfileDivider: View {
    var body: some View {
        Divider()
            .opacity(0.3)
    }
}

#Preview {
    ProfileView()
}
