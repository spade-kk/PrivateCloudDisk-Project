import SwiftUI

// MARK: - 主界面（企业级 v4 — 与 Web 前端 Layout.vue 完全统一）

/// 主界面布局：侧边栏 + 顶部导航（console-header）+ 内容区 + 底部
///
/// 与 Web 前端 Layout.vue + Sidebar.vue 完全统一：
/// - 白色侧边栏（bg-white），带阴影（shadow-card），可折叠
/// - 顶部导航栏（console-header），含面包屑、搜索、用户操作
/// - 内容区使用 bg-neutral-100 (#F5F7FA) 背景
/// - 导航项分组（主菜单、文件管理、协作、安全、其他）
/// - 参考百度网盘 macOS 客户端 + Trae 桌面客户端布局
struct MainView: View {
    @StateObject private var contentVM = ContentViewModel()
    @StateObject private var fileListVM = FileListViewModel()
    @StateObject private var virtualDiskVM = VirtualDiskViewModel()
    @StateObject private var uploadVM = UploadViewModel()
    @StateObject private var favoritesTrashVM = FavoritesTrashViewModel()
    @StateObject private var settingsVM = SettingsViewModel()

    @State private var sidebarCollapsed = false
    @State private var showUserPopover = false

    private let sidebarExpandedWidth: CGFloat = 230
    private let sidebarCollapsedWidth: CGFloat = 72

    var body: some View {
        HStack(spacing: 0) {
            // ── 侧边栏 ──
            SidebarView(
                selectedTab: $contentVM.selectedTab,
                collapsed: $sidebarCollapsed
            )
            .frame(width: sidebarCollapsed ? sidebarCollapsedWidth : sidebarExpandedWidth)
            .animation(AppAnimation.snappy, value: sidebarCollapsed)

            // ── 右侧内容区 ──
            ZStack(alignment: .topLeading) {
                contentArea
                    .padding(.top, 68) // 为 header 留空间
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(AppColors.background)
        .environmentObject(contentVM)
        .environmentObject(fileListVM)
        .environmentObject(virtualDiskVM)
        .environmentObject(uploadVM)
        .environmentObject(favoritesTrashVM)
        .environmentObject(settingsVM)
        .onAppear {
            Task { await fileListVM.loadFiles() }
        }
    }

    // MARK: - 内容区（与 Web 前端 Layout.vue 的 app-content 统一）

    private var contentArea: some View {
        VStack(spacing: 0) {
            // ── 顶部导航栏（console-header）──
            consoleHeader

            // ── 主内容 ──
            mainContent

            // ── 底部 ──
            consoleFooter
        }
        .background(AppColors.background)
    }

    // MARK: - 顶部导航栏（与 Web 前端 console-header 统一）

    private var consoleHeader: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                // 左侧：面包屑导航 + 页面标题
                HStack(spacing: 12) {
                    // 面包屑
                    breadcrumbNav

                    // 分隔线
                    if !contentVM.currentPath.isEmpty {
                        Rectangle()
                            .fill(AppColors.divider)
                            .frame(width: 1, height: 20)
                    }

                    // 页面标题
                    currentPageTitle
                }

                Spacer()

                // 右侧：操作区
                HStack(spacing: 8) {
                    // 搜索（在文件页显示）
                    if contentVM.selectedTab == .home {
                        searchButton
                    }

                    // 存储空间指示器
                    storageInfoCompact

                    // 传输面板按钮
                    transferButton

                    // 通知按钮
                    notificationButton

                    // 用户下拉
                    userDropdown
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)

            // 底部分隔线
            Divider().foregroundColor(AppColors.divider)
        }
        .background(AppColors.surface)
        .frame(height: 68)
        .shadow(color: Color.black.opacity(0.03), radius: 4, x: 0, y: 2)
        .zIndex(20)
    }

    // MARK: - 面包屑导航

    private var breadcrumbNav: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 4) {
                // 根目录
                Button(action: {
                    contentVM.currentPath = []
                    Task { await fileListVM.loadFiles() }
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: "house.fill")
                            .font(.system(size: 11))
                        Text("我的网盘")
                            .font(.system(size: 12, weight: .medium, design: .default))
                    }
                    .foregroundColor(AppColors.primary)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 5)
                    .background(
                        RoundedRectangle(cornerRadius: 6)
                            .fill(AppColors.primaryBg)
                    )
                }
                .buttonStyle(.plain)

                // 路径层级
                ForEach(Array(contentVM.currentPath.enumerated()), id: \.offset) { index, node in
                    Image(systemName: "chevron.right")
                        .font(.system(size: 8, weight: .bold))
                        .foregroundColor(AppColors.textTertiary)

                    Button(action: {
                        contentVM.currentPath = Array(contentVM.currentPath.prefix(index + 1))
                        Task { await fileListVM.loadFiles(parentId: node.id) }
                    }) {
                        Text(node.name)
                            .font(.system(size: 12, weight: .medium, design: .default))
                            .foregroundColor(index == contentVM.currentPath.count - 1
                                ? AppColors.textPrimary : AppColors.textSecondary)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 5)
                            .background(
                                RoundedRectangle(cornerRadius: 6)
                                    .fill(index == contentVM.currentPath.count - 1
                                        ? AppColors.background : Color.clear)
                            )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 2)
        }
    }

    // MARK: - 页面标题

    private var currentPageTitle: some View {
        let title: String = {
            switch contentVM.selectedTab {
            case .home: return "我的文件"
            case .favorites: return "收藏夹"
            case .trash: return "回收站"
            case .virtualDisk: return "虚拟磁盘"
            case .settings: return "系统设置"
            }
        }()

        return Text(title)
            .font(.system(size: 16, weight: .semibold, design: .default))
            .foregroundColor(AppColors.textPrimary)
    }

    // MARK: - 搜索按钮

    private var searchButton: some View {
        Button(action: {}) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(AppColors.textSecondary)
                .frame(width: 36, height: 36)
                .background(
                    RoundedRectangle(cornerRadius: 10)
                        .fill(AppColors.background)
                )
        }
        .buttonStyle(.plain)
    }

    // MARK: - 存储空间（紧凑版）

    private var storageInfoCompact: some View {
        HStack(spacing: 6) {
            ZStack {
                RoundedRectangle(cornerRadius: 3)
                    .fill(AppColors.divider)
                    .frame(width: 40, height: 6)

                RoundedRectangle(cornerRadius: 3)
                    .fill(AppColors.primary)
                    .frame(width: 40 * 0.105, height: 6)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            Text("10.5%")
                .font(.system(size: 10, weight: .medium, design: .default))
                .foregroundColor(AppColors.textTertiary)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(AppColors.background)
        )
    }

    // MARK: - 传输按钮

    private var transferButton: some View {
        Button(action: {}) {
            ZStack {
                Image(systemName: "arrow.up.arrow.down.circle")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(AppColors.textSecondary)

                // 传输计数徽章
                if !uploadVM.activeTasks.isEmpty {
                    Text("\(uploadVM.activeTasks.count)")
                        .font(.system(size: 8, weight: .bold, design: .default))
                        .foregroundColor(.white)
                        .padding(.horizontal, 4)
                        .padding(.vertical, 1)
                        .background(
                            Capsule()
                                .fill(AppColors.primary)
                        )
                        .offset(x: 10, y: -8)
                }
            }
            .frame(width: 36, height: 36)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(AppColors.background)
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: - 通知按钮

    private var notificationButton: some View {
        Button(action: {}) {
            Image(systemName: "bell")
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(AppColors.textSecondary)
                .frame(width: 36, height: 36)
                .background(
                    RoundedRectangle(cornerRadius: 10)
                        .fill(AppColors.background)
                )
        }
        .buttonStyle(.plain)
    }

    // MARK: - 用户下拉

    @EnvironmentObject var authService: AuthService

    private var userDropdown: some View {
        Button(action: { showUserPopover.toggle() }) {
            HStack(spacing: 8) {
                // 头像
                ZStack {
                    Circle()
                        .fill(AppGradients.avatar)
                        .frame(width: 30, height: 30)
                    Text(userInitials)
                        .font(.system(size: 11, weight: .bold, design: .default))
                        .foregroundColor(.white)
                }

                // 用户名
                VStack(alignment: .leading, spacing: 0) {
                    Text(authService.currentUser?.nickname ?? authService.currentUser?.username ?? "用户")
                        .font(.system(size: 12, weight: .medium, design: .default))
                        .foregroundColor(AppColors.textPrimary)
                        .lineLimit(1)
                    Text("免费版")
                        .font(.system(size: 10, design: .default))
                        .foregroundColor(AppColors.textTertiary)
                }

                Image(systemName: "chevron.down")
                    .font(.system(size: 8, weight: .bold))
                    .foregroundColor(AppColors.textTertiary)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(AppColors.background)
            )
        }
        .buttonStyle(.plain)
        .popover(isPresented: $showUserPopover, arrowEdge: .bottom) {
            userDropdownPopover
        }
    }

    private var userDropdownPopover: some View {
        VStack(alignment: .leading, spacing: 0) {
            // 用户信息
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .fill(AppGradients.avatar)
                        .frame(width: 40, height: 40)
                    Text(userInitials)
                        .font(.system(size: 16, weight: .bold, design: .default))
                        .foregroundColor(.white)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(authService.currentUser?.nickname ?? authService.currentUser?.username ?? "用户")
                        .font(.system(size: 14, weight: .semibold, design: .default))
                    Text(authService.currentUser?.email ?? "")
                        .font(.system(size: 11, design: .default))
                        .foregroundColor(AppColors.textSecondary)
                }
            }
            .padding(16)

            Divider().foregroundColor(AppColors.divider)

            // 菜单项
            menuItem(icon: "person.circle", title: "个人中心") {
                showUserPopover = false
                contentVM.selectedTab = .settings
            }
            menuItem(icon: "creditcard", title: "套餐管理") {
                showUserPopover = false
            }
            menuItem(icon: "questionmark.circle", title: "帮助中心") {
                showUserPopover = false
            }

            Divider().foregroundColor(AppColors.divider)

            menuItem(icon: "rectangle.portrait.and.arrow.right", title: "退出登录", isDestructive: true) {
                showUserPopover = false
                Task { await authService.logout() }
            }
        }
        .frame(width: 220)
    }

    private func menuItem(icon: String, title: String, isDestructive: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Image(systemName: icon)
                    .font(.system(size: 13))
                    .foregroundColor(isDestructive ? AppColors.danger : AppColors.textSecondary)
                    .frame(width: 18)
                Text(title)
                    .font(.system(size: 13, design: .default))
                    .foregroundColor(isDestructive ? AppColors.danger : AppColors.textPrimary)
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
    }

    private var userInitials: String {
        let name = authService.currentUser?.nickname
            ?? authService.currentUser?.username
            ?? "用户"
        return String(name.prefix(1)).uppercased()
    }

    // MARK: - 主内容区

    private var mainContent: some View {
        Group {
            switch contentVM.selectedTab {
            case .home:
                HomeView()
            case .favorites:
                FavoritesView()
            case .trash:
                TrashView()
            case .virtualDisk:
                VirtualDiskView()
            case .settings:
                SettingsView()
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - 底部（与 Web 前端 footer 统一）

    private var consoleFooter: some View {
        VStack(spacing: 0) {
            Divider().foregroundColor(AppColors.divider)

            HStack {
                Text("© 2025 CloudDrive 私有云网盘管理系统")
                    .font(.system(size: 11, design: .default))
                    .foregroundColor(AppColors.textTertiary)

                Spacer()

                // 上传进度指示器
                if !uploadVM.activeTasks.isEmpty {
                    HStack(spacing: 6) {
                        ProgressView()
                            .scaleEffect(0.6)
                            .tint(AppColors.primary)
                        Text("\(uploadVM.activeTasks.count) 个文件上传中")
                            .font(.system(size: 11, design: .default))
                            .foregroundColor(AppColors.textSecondary)
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 10)
        }
        .background(AppColors.surface)
    }
}

// MARK: - 侧边栏（企业级 v4 — 与 Web Sidebar.vue 完全统一）

struct SidebarView: View {
    @Binding var selectedTab: ContentViewModel.NavigationTab
    @Binding var collapsed: Bool
    @EnvironmentObject var authService: AuthService
    @EnvironmentObject var uploadVM: UploadViewModel
    @EnvironmentObject var fileListVM: FileListViewModel

    @State private var hoveredTab: ContentViewModel.NavigationTab?
    @State private var showProfilePopover = false

    private let brandBlue = AppColors.primary

    var body: some View {
        ZStack(alignment: .trailing) {
            VStack(spacing: 0) {
                // ── Logo 区域 ──
                logoArea

                Divider()
                    .foregroundColor(AppColors.divider)

                // ── 导航菜单（分组，与 Web 端完全一致） ──
                ScrollView(showsIndicators: false) {
                    VStack(spacing: 0) {
                        // 主菜单
                        navGroup(label: "主菜单") {
                            navItem(tab: .home, icon: "folder.fill", title: "我的文件", color: brandBlue)
                        }

                        // 文件管理
                        navGroup(label: "文件管理") {
                            navItem(tab: .favorites, icon: "star.fill", title: "收藏夹", color: Color(hex: "#FAAD14"))
                            navItem(tab: .trash, icon: "trash.fill", title: "回收站", color: AppColors.textTertiary)
                        }

                        // 工具
                        navGroup(label: "工具") {
                            navItem(tab: .virtualDisk, icon: "externaldrive.fill", title: "虚拟磁盘", color: AppColors.success)
                        }
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 8)
                }

                Spacer()

                // ── 存储空间指示器 ──
                if !collapsed {
                    storageIndicator
                }

                // ── 底部用户区域 ──
                bottomSection
            }
            .frame(width: collapsed ? 72 : 230)
            .background(AppColors.surface)

            // ── 折叠/展开按钮（与 Web 端一致，右侧边缘突出） ──
            collapseButton
        }
        .shadow(color: AppShadow.card.color, radius: AppShadow.card.radius, x: AppShadow.card.x, y: AppShadow.card.y)
        .zIndex(30)
    }

    // MARK: - 折叠按钮（与 Web 端 sidebar 的折叠按钮一致）

    private var collapseButton: some View {
        Button(action: {
            withAnimation(AppAnimation.snappy) {
                collapsed.toggle()
            }
        }) {
            ZStack {
                Circle()
                    .fill(AppColors.surface)
                    .frame(width: 22, height: 22)
                    .shadow(color: Color.black.opacity(0.08), radius: 4, x: 0, y: 1)

                Circle()
                    .strokeBorder(AppColors.divider, lineWidth: 1)
                    .frame(width: 22, height: 22)

                Image(systemName: collapsed ? "chevron.right" : "chevron.left")
                    .font(.system(size: 8, weight: .bold))
                    .foregroundColor(AppColors.textSecondary)
            }
        }
        .buttonStyle(.plain)
        .offset(x: 11)
    }

    // MARK: - Logo 区域

    private var logoArea: some View {
        HStack(spacing: 10) {
            ZStack {
                RoundedRectangle(cornerRadius: 8)
                    .fill(LinearGradient(
                        colors: [brandBlue, AppColors.primaryLight],
                        startPoint: .topLeading, endPoint: .bottomTrailing))
                    .frame(width: 32, height: 32)

                Image(systemName: "cloud.fill")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundColor(.white)
            }

            if !collapsed {
                VStack(alignment: .leading, spacing: 0) {
                    Text("私有云")
                        .font(.system(size: 13, weight: .bold, design: .default))
                        .foregroundColor(AppColors.textPrimary)
                    Text("控制台")
                        .font(.system(size: 10, weight: .medium, design: .default))
                        .foregroundColor(brandBlue)
                }

                Spacer()
            }
        }
        .padding(.horizontal, collapsed ? 20 : 14)
        .padding(.vertical, 12)
        .background(AppColors.surface)
    }

    // MARK: - 导航分组

    private func navGroup(label: String, @ViewBuilder items: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            if !collapsed {
                Text(label)
                    .font(.system(size: 10, weight: .semibold, design: .default))
                    .foregroundColor(AppColors.textTertiary)
                    .tracking(1)
                    .textCase(.uppercase)
                    .padding(.horizontal, 14)
                    .padding(.top, 16)
                    .padding(.bottom, 6)
            }

            items()
        }
    }

    // MARK: - 导航项

    private func navItem(
        tab: ContentViewModel.NavigationTab,
        icon: String,
        title: String,
        color: Color
    ) -> some View {
        let isSelected = selectedTab == tab

        return Button(action: {
            withAnimation(AppAnimation.snappy) {
                selectedTab = tab
            }
        }) {
            HStack(spacing: 10) {
                Image(systemName: icon)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(isSelected ? color : AppColors.textTertiary)
                    .frame(width: 20)

                if !collapsed {
                    Text(title)
                        .font(.system(size: 13, weight: isSelected ? .semibold : .regular, design: .default))
                        .foregroundColor(isSelected ? AppColors.textPrimary : AppColors.textSecondary)

                    Spacer()
                }
            }
            .padding(.horizontal, collapsed ? 14 : 12)
            .padding(.vertical, 9)
            .frame(maxWidth: .infinity, alignment: collapsed ? .center : .leading)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .fill(isSelected ? brandBlue.opacity(0.08) : .clear)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .onHover { hovering in
            withAnimation(AppAnimation.fast) {
                hoveredTab = hovering ? tab : nil
            }
        }
    }

    // MARK: - 存储空间指示器

    private var storageIndicator: some View {
        VStack(spacing: 0) {
            Divider().foregroundColor(AppColors.divider)

            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text("存储空间")
                        .font(.system(size: 10, design: .default))
                        .foregroundColor(AppColors.textTertiary)
                    Spacer()
                    Text("10.5 GB / 100 GB")
                        .font(.system(size: 10, weight: .medium, design: .default))
                        .foregroundColor(AppColors.textSecondary)
                }

                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        RoundedRectangle(cornerRadius: 2)
                            .fill(AppColors.divider)
                            .frame(height: 4)

                        RoundedRectangle(cornerRadius: 2)
                            .fill(LinearGradient(
                                colors: [brandBlue, brandBlue.opacity(0.7)],
                                startPoint: .leading, endPoint: .trailing))
                            .frame(width: geo.size.width * 0.105, height: 4)
                    }
                }
                .frame(height: 4)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
    }

    // MARK: - 底部

    private var bottomSection: some View {
        VStack(spacing: 0) {
            Divider().foregroundColor(AppColors.divider)

            if collapsed {
                // 折叠时仅显示头像
                Button(action: { showProfilePopover.toggle() }) {
                    ZStack {
                        Circle()
                            .fill(AppGradients.avatar)
                            .frame(width: 28, height: 28)
                        Text(userInitials)
                            .font(.system(size: 12, weight: .bold, design: .default))
                            .foregroundColor(.white)
                    }
                    .padding(.vertical, 10)
                }
                .buttonStyle(.plain)
                .popover(isPresented: $showProfilePopover, arrowEdge: .leading) {
                    userProfilePopover
                }
            } else {
                // 展开时显示完整用户信息
                userProfileSection
            }
        }
        .padding(.bottom, 8)
    }

    // MARK: - 用户信息（展开状态）

    private var userProfileSection: some View {
        Button(action: { showProfilePopover.toggle() }) {
            HStack(spacing: 10) {
                ZStack {
                    Circle()
                        .fill(AppGradients.avatar)
                        .frame(width: 28, height: 28)
                    Text(userInitials)
                        .font(.system(size: 12, weight: .bold, design: .default))
                        .foregroundColor(.white)
                }

                VStack(alignment: .leading, spacing: 1) {
                    Text(authService.currentUser?.nickname ?? authService.currentUser?.username ?? "用户")
                        .font(.system(size: 12, weight: .medium, design: .default))
                        .foregroundColor(AppColors.textPrimary)
                        .lineLimit(1)
                    Text("免费版")
                        .font(.system(size: 10, design: .default))
                        .foregroundColor(AppColors.textTertiary)
                }

                Spacer()

                Image(systemName: "ellipsis")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(AppColors.textTertiary)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
        .popover(isPresented: $showProfilePopover, arrowEdge: .leading) {
            userProfilePopover
        }
    }

    private var userProfilePopover: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .fill(AppGradients.avatar)
                        .frame(width: 40, height: 40)
                    Text(userInitials)
                        .font(.system(size: 16, weight: .bold, design: .default))
                        .foregroundColor(.white)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(authService.currentUser?.nickname ?? authService.currentUser?.username ?? "用户")
                        .font(.system(size: 14, weight: .semibold, design: .default))
                    Text(authService.currentUser?.email ?? "")
                        .font(.system(size: 11, design: .default))
                        .foregroundColor(AppColors.textSecondary)
                }
            }
            .padding(16)

            Divider().foregroundColor(AppColors.divider)

            Button(action: {
                showProfilePopover = false
                selectedTab = .settings
            }) {
                HStack {
                    Image(systemName: "person.circle")
                    Text("账户管理")
                    Spacer()
                }
                .padding(.horizontal, 16).padding(.vertical, 10)
            }
            .buttonStyle(.plain)

            Button(action: {
                showProfilePopover = false
                Task { await authService.logout() }
            }) {
                HStack {
                    Image(systemName: "rectangle.portrait.and.arrow.right")
                    Text("退出登录")
                    Spacer()
                }
                .padding(.horizontal, 16).padding(.vertical, 10)
            }
            .buttonStyle(.plain)
        }
        .frame(width: 200)
    }

    private var userInitials: String {
        let name = authService.currentUser?.nickname
            ?? authService.currentUser?.username
            ?? "用户"
        return String(name.prefix(1)).uppercased()
    }
}

// MARK: - Toast 通知

struct ToastView: View {
    let message: String
    let type: ContentViewModel.ToastType

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: iconName)
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(iconColor)

            Text(message)
                .font(.system(size: 13, design: .default))
                .foregroundColor(AppColors.textPrimary)

            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .frame(maxWidth: 360)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(AppColors.surface)
                .shadow(color: AppShadow.lg.color, radius: AppShadow.lg.radius, x: AppShadow.lg.x, y: AppShadow.lg.y)
        )
        .padding(.horizontal, 24)
    }

    private var iconName: String {
        switch type {
        case .success: return "checkmark.circle.fill"
        case .error: return "xmark.circle.fill"
        case .info: return "info.circle.fill"
        case .warning: return "exclamationmark.triangle.fill"
        }
    }

    private var iconColor: Color {
        switch type {
        case .success: return AppColors.success
        case .error: return AppColors.danger
        case .info: return AppColors.primary
        case .warning: return AppColors.warning
        }
    }
}

#Preview {
    MainView()
        .environmentObject(ContentViewModel())
        .environmentObject(FileListViewModel())
        .environmentObject(VirtualDiskViewModel())
        .environmentObject(UploadViewModel())
        .environmentObject(FavoritesTrashViewModel())
        .environmentObject(SettingsViewModel())
}