//
//  ContentView.swift
//  PrivateCloudDisk-ios
//
//  主容器 — 企业级底部导航
//  采用悬浮式 TabBar 设计，简约专业
//

import SwiftUI

struct ContentView: View {
    @StateObject private var authVM = AuthViewModel()

    var body: some View {
        Group {
            if authVM.isLoggedIn {
                MainTabView()
                    .environmentObject(authVM)
            } else {
                LoginView()
                    .environmentObject(authVM)
            }
        }
    }
}

// MARK: - 主 Tab 视图

struct MainTabView: View {
    @EnvironmentObject var authVM: AuthViewModel
    @State private var selectedTab: Tab = .files
    @State private var previousTab: Tab = .files

    enum Tab: String, CaseIterable {
        case files, starred, shares, messages, profile

        var icon: String {
            switch self {
            case .files: return "folder"
            case .starred: return "star"
            case .shares: return "link"
            case .messages: return "message"
            case .profile: return "person"
            }
        }

        var selectedIcon: String {
            switch self {
            case .files: return "folder.fill"
            case .starred: return "star.fill"
            case .shares: return "link"
            case .messages: return "message.fill"
            case .profile: return "person.fill"
            }
        }

        var title: String {
            switch self {
            case .files: return "文件"
            case .starred: return "收藏"
            case .shares: return "分享"
            case .messages: return "消息"
            case .profile: return "我的"
            }
        }
    }

    var body: some View {
        ZStack {
            // 内容区
            TabView(selection: $selectedTab) {
                FileBrowserView()
                    .tag(Tab.files)
                StarredView()
                    .tag(Tab.starred)
                SharesView()
                    .tag(Tab.shares)
                MessagesView()
                    .tag(Tab.messages)
                ProfileView(authVM: authVM)
                    .tag(Tab.profile)
            }
            // 隐藏原生 TabBar，使用自定义悬浮 TabBar
            .toolbar(.hidden, for: .tabBar)

            // 悬浮 TabBar
            VStack {
                Spacer()
                floatingTabBar
            }
            .ignoresSafeArea(.keyboard)
        }
        .background(AppColors.background.ignoresSafeArea())
        .onChange(of: selectedTab) { _, newTab in
            if previousTab == newTab {
                // 重复点击同一 tab 可触发刷新
                NotificationCenter.default.post(
                    name: .tabDoubleTapped,
                    object: newTab
                )
            }
            previousTab = newTab
        }
    }

    // MARK: - 悬浮 TabBar

    private var floatingTabBar: some View {
        HStack(spacing: 0) {
            ForEach(Tab.allCases, id: \.self) { tab in
                tabBarButton(tab)
            }
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 6)
        .background(
            Capsule()
                .fill(AppColors.tabBarBg)
                .shadow(
                    color: .black.opacity(0.08),
                    radius: 16,
                    x: 0,
                    y: 4
                )
        )
        .padding(.horizontal, AppSpacing.xl)
        .padding(.bottom, 4)
    }

    private func tabBarButton(_ tab: Tab) -> some View {
        let isSelected = selectedTab == tab

        return Button(action: {
            withAnimation(.spring(response: 0.35, dampingFraction: 0.7)) {
                selectedTab = tab
            }
        }) {
            VStack(spacing: 3) {
                Image(systemName: isSelected ? tab.selectedIcon : tab.icon)
                    .font(.system(size: 20, weight: .medium))
                    .symbolEffect(.bounce, value: isSelected)

                Text(tab.title)
                    .font(.system(size: 10, weight: isSelected ? .semibold : .medium))
            }
            .foregroundColor(isSelected ? AppColors.tabActive : AppColors.tabInactive)
            .frame(maxWidth: .infinity)
            .frame(height: 50)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - 我的页面

struct ProfileView: View {
    @ObservedObject var authVM: AuthViewModel
    @State private var showLogoutConfirm = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: AppSpacing.xl) {
                    // 用户头像区
                    userHeader
                        .padding(.top, AppSpacing.lg)

                    // 存储信息卡片
                    storageCard
                        .padding(.horizontal, AppSpacing.lg)

                    // 功能列表
                    VStack(spacing: 1) {
                        menuRow(icon: "arrow.up.circle.fill", title: "上传记录", color: AppColors.primary)
                        Divider().padding(.leading, 52)
                        menuRow(icon: "arrow.down.circle.fill", title: "下载记录", color: AppColors.success)
                        Divider().padding(.leading, 52)
                        menuRow(icon: "trash.fill", title: "回收站", color: AppColors.danger)
                        Divider().padding(.leading, 52)
                        menuRow(icon: "gearshape.fill", title: "设置", color: AppColors.textSecondary)
                    }
                    .background(AppColors.surface)
                    .clipShape(RoundedRectangle(cornerRadius: AppRadius.lg))
                    .shadow(color: .black.opacity(0.03), radius: 8, y: 2)
                    .padding(.horizontal, AppSpacing.lg)

                    // 退出登录
                    AppDestructiveButton("退出登录", icon: "rectangle.portrait.and.arrow.right") {
                        showLogoutConfirm = true
                    }
                    .padding(.horizontal, AppSpacing.lg)
                }
            }
            .background(AppColors.background)
            .navigationTitle("我的")
            .alert("确认退出", isPresented: $showLogoutConfirm) {
                Button("取消", role: .cancel) {}
                Button("退出", role: .destructive) {
                    Task { await authVM.logout() }
                }
            } message: {
                Text("退出后需要重新登录")
            }
        }
    }

    private var userHeader: some View {
        VStack(spacing: AppSpacing.md) {
            // 头像
            ZStack {
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [AppColors.primary, AppColors.primaryLight],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 72, height: 72)

                Text(authVM.user?.name.prefix(1).uppercased() ?? "U")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(.white)
            }
            .shadow(color: AppColors.primary.opacity(0.25), radius: 10, y: 4)

            VStack(spacing: AppSpacing.xs) {
                Text(authVM.user?.name ?? "用户")
                    .font(AppTypography.title3)
                    .foregroundColor(AppColors.textPrimary)
                Text(authVM.user?.phoneNumber ?? "")
                    .font(AppTypography.subheadline)
                    .foregroundColor(AppColors.textSecondary)
            }
        }
    }

    private var storageCard: some View {
        VStack(spacing: AppSpacing.md) {
            HStack {
                Label("存储空间", systemImage: "internaldrive.fill")
                    .font(AppTypography.subheadline.weight(.medium))
                    .foregroundColor(AppColors.textPrimary)
                Spacer()
                Text("42.5 GB / 100 GB")
                    .font(AppTypography.footnote)
                    .foregroundColor(AppColors.textSecondary)
            }

            AppProgressBar(progress: 0.425)
        }
        .padding(AppSpacing.lg)
        .background(AppColors.surface)
        .clipShape(RoundedRectangle(cornerRadius: AppRadius.lg))
        .shadow(color: .black.opacity(0.03), radius: 8, y: 2)
    }

    private func menuRow(icon: String, title: String, color: Color) -> some View {
        Button(action: {}) {
            HStack(spacing: AppSpacing.md) {
                Image(systemName: icon)
                    .font(.subheadline)
                    .foregroundColor(color)
                    .frame(width: 28)

                Text(title)
                    .font(AppTypography.subheadline)
                    .foregroundColor(AppColors.textPrimary)

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.caption)
                    .foregroundColor(AppColors.textTertiary)
            }
            .padding(.horizontal, AppSpacing.lg)
            .padding(.vertical, 14)
        }
    }
}

// MARK: - 通知名称

extension Notification.Name {
    static let tabDoubleTapped = Notification.Name("tabDoubleTapped")
}

#Preview {
    ContentView()
}
