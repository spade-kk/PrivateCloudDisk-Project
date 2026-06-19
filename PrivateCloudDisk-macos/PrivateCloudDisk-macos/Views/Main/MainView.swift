import SwiftUI

// MARK: - 主界面（企业级设计 v2）

/// 主界面：侧边栏 + 内容区
///
/// 参考百度网盘、夸克网盘 macOS 客户端设计：
/// - 毛玻璃侧边栏，紧凑高效
/// - 存储空间使用指示器
/// - 用户头像 + 快捷操作
/// - 内容区自适应布局
/// - 品牌色点缀 + 流畅切换动画
struct MainView: View {
    @StateObject private var contentVM = ContentViewModel()
    @StateObject private var fileListVM = FileListViewModel()
    @StateObject private var virtualDiskVM = VirtualDiskViewModel()
    @StateObject private var uploadVM = UploadViewModel()
    @StateObject private var favoritesTrashVM = FavoritesTrashViewModel()
    @StateObject private var settingsVM = SettingsViewModel()

    @State private var columnVisibility = NavigationSplitViewVisibility.all

    private let brandBlue = Color(red: 0.24, green: 0.47, blue: 0.96)

    var body: some View {
        ZStack {
            NavigationSplitView(columnVisibility: $columnVisibility) {
                // ── 侧边栏 ──
                SidebarView(selectedTab: $contentVM.selectedTab)
                    .navigationSplitViewColumnWidth(min: 210, ideal: 230, max: 270)
            } detail: {
                // ── 内容区 ──
                contentArea
            }

            // ── Toast 通知 ──
            if let message = contentVM.toastMessage {
                VStack {
                    Spacer()
                    ToastView(message: message, type: contentVM.toastType)
                        .padding(.bottom, 24)
                        .transition(
                            .move(edge: .bottom)
                            .combined(with: .opacity)
                            .combined(with: .scale(scale: 0.95))
                        )
                }
                .animation(.spring(response: 0.4, dampingFraction: 0.7), value: contentVM.toastMessage)
            }
        }
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

    // MARK: - 内容区

    @ViewBuilder
    private var contentArea: some View {
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

// MARK: - 侧边栏（企业级设计 v2）

struct SidebarView: View {
    @Binding var selectedTab: ContentViewModel.NavigationTab
    @EnvironmentObject var authService: AuthService
    @EnvironmentObject var uploadVM: UploadViewModel
    @EnvironmentObject var fileListVM: FileListViewModel

    @State private var hoveredTab: ContentViewModel.NavigationTab?
    @State private var showProfilePopover = false

    private let brandBlue = Color(red: 0.24, green: 0.47, blue: 0.96)

    var body: some View {
        VStack(spacing: 0) {
            // ── 顶部 Logo 区域（含拖拽区） ──
            headerView

            Divider()
                .opacity(0.3)

            // ── 导航项 ──
            ScrollView(showsIndicators: false) {
                VStack(spacing: 2) {
                    // 我的文件
                    sidebarNavItem(
                        tab: .home,
                        icon: "folder.fill",
                        title: "我的文件",
                        color: brandBlue,
                        badge: fileListVM.files.count > 0 ? "\(fileListVM.files.count)" : nil
                    )

                    // 收藏
                    sidebarNavItem(
                        tab: .favorites,
                        icon: "star.fill",
                        title: "收藏",
                        color: .yellow
                    )

                    // 回收站
                    sidebarNavItem(
                        tab: .trash,
                        icon: "trash.fill",
                        title: "回收站",
                        color: .gray
                    )

                    Divider()
                        .opacity(0.3)
                        .padding(.vertical, 8)
                        .padding(.horizontal, 12)

                    // 虚拟磁盘
                    sidebarNavItem(
                        tab: .virtualDisk,
                        icon: "externaldrive.fill",
                        title: "虚拟磁盘",
                        color: .green
                    )
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 8)
            }

            Spacer()

            // ── 存储空间指示器 ──
            storageIndicator

            // ── 底部用户区域 ──
            bottomSection
        }
        .frame(minWidth: 210)
        .background(
            VisualEffectView(
                material: .sidebar,
                blendingMode: .behindWindow
            )
        )
    }

    // MARK: - 头部

    private var headerView: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                // 品牌图标
                ZStack {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(
                            LinearGradient(
                                colors: [brandBlue, Color.purple],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: 32, height: 32)

                    Image(systemName: "externaldrive.fill.badge.icloud")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.white)
                }

                VStack(alignment: .leading, spacing: 0) {
                    Text("PrivateCloudDisk")
                        .font(.system(size: 12, weight: .bold, design: .rounded))
                        .foregroundColor(.primary)

                    Text(authService.currentUser?.nickname ?? authService.currentUser?.username ?? "未登录")
                        .font(.system(size: 10, design: .rounded))
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }

                Spacer()
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
        }
        .background(.clear)
    }

    // MARK: - 导航项

    private func sidebarNavItem(
        tab: ContentViewModel.NavigationTab,
        icon: String,
        title: String,
        color: Color,
        badge: String? = nil
    ) -> some View {
        let isSelected = selectedTab == tab

        return Button(action: {
            withAnimation(.easeInOut(duration: 0.2)) {
                selectedTab = tab
            }
        }) {
            HStack(spacing: 10) {
                // 图标
                ZStack {
                    RoundedRectangle(cornerRadius: 6)
                        .fill(isSelected ? color.opacity(0.15) : .clear)
                        .frame(width: 30, height: 30)

                    Image(systemName: icon)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(isSelected ? color : .secondary)
                }

                // 标题
                Text(title)
                    .font(.system(size: 13, weight: isSelected ? .semibold : .regular, design: .rounded))
                    .foregroundColor(isSelected ? .primary : .secondary)

                Spacer()

                // Badge
                if let badge = badge {
                    Text(badge)
                        .font(.system(size: 10, weight: .semibold, design: .rounded))
                        .foregroundColor(.secondary)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(
                            RoundedRectangle(cornerRadius: 4)
                                .fill(.quaternary.opacity(0.5))
                        )
                }

                // 选中指示器
                if isSelected {
                    RoundedRectangle(cornerRadius: 2)
                        .fill(color)
                        .frame(width: 3, height: 16)
                        .transition(.scale(scale: 0, anchor: .trailing).combined(with: .opacity))
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .fill(isSelected ? Color.primary.opacity(0.06) : .clear)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .onHover { hovering in
            withAnimation(.easeInOut(duration: 0.15)) {
                hoveredTab = hovering ? tab : nil
            }
        }
    }

    // MARK: - 存储空间指示器

    private var storageIndicator: some View {
        VStack(spacing: 6) {
            Divider()
                .opacity(0.3)

            VStack(spacing: 6) {
                HStack {
                    Text("存储空间")
                        .font(.system(size: 10, design: .rounded))
                        .foregroundStyle(.tertiary)
                    Spacer()
                    Text("10.5 GB / 100 GB")
                        .font(.system(size: 10, weight: .medium, design: .rounded))
                        .foregroundColor(.secondary)
                }

                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        RoundedRectangle(cornerRadius: 2)
                            .fill(.quaternary.opacity(0.5))
                            .frame(height: 4)

                        RoundedRectangle(cornerRadius: 2)
                            .fill(
                                LinearGradient(
                                    colors: [brandBlue, brandBlue.opacity(0.7)],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .frame(width: geo.size.width * 0.105, height: 4)
                    }
                }
                .frame(height: 4)
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 8)
        }
    }

    // MARK: - 底部

    private var bottomSection: some View {
        VStack(spacing: 0) {
            Divider()
                .opacity(0.3)

            // 设置按钮
            Button(action: {
                withAnimation(.easeInOut(duration: 0.2)) {
                    selectedTab = .settings
                }
            }) {
                HStack(spacing: 10) {
                    Image(systemName: "gearshape.fill")
                        .font(.system(size: 13))
                        .foregroundColor(selectedTab == .settings ? brandBlue : .secondary)

                    Text("设置")
                        .font(.system(size: 13, design: .rounded))
                        .foregroundColor(selectedTab == .settings ? .primary : .secondary)

                    Spacer()
                }
                .padding(.horizontal, 18)
                .padding(.vertical, 10)
                .background(
                    RoundedRectangle(cornerRadius: 8)
                        .fill(selectedTab == .settings ? Color.primary.opacity(0.06) : .clear)
                        .padding(.horizontal, 8)
                )
            }
            .buttonStyle(.plain)
            .padding(.vertical, 2)

            // 上传进度指示
            if !uploadVM.activeTasks.isEmpty {
                HStack(spacing: 8) {
                    ProgressView()
                        .scaleEffect(0.6)
                        .tint(brandBlue)

                    Text("\(uploadVM.activeTasks.count) 个文件上传中")
                        .font(.system(size: 10, design: .rounded))
                        .foregroundColor(.secondary)
                }
                .padding(.horizontal, 18)
                .padding(.vertical, 8)
                .background(
                    RoundedRectangle(cornerRadius: 6)
                        .fill(brandBlue.opacity(0.06))
                        .padding(.horizontal, 8)
                )
            }

            // 用户信息
            Divider()
                .opacity(0.3)

            userProfileSection
        }
        .padding(.bottom, 8)
    }

    // MARK: - 用户信息区域

    private var userProfileSection: some View {
        Button(action: {
            showProfilePopover.toggle()
        }) {
            HStack(spacing: 10) {
                // 头像
                ZStack {
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [brandBlue, Color.purple.opacity(0.6)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: 28, height: 28)

                    Text(userInitials)
                        .font(.system(size: 12, weight: .bold, design: .rounded))
                        .foregroundColor(.white)
                }

                VStack(alignment: .leading, spacing: 1) {
                    Text(authService.currentUser?.nickname ?? authService.currentUser?.username ?? "用户")
                        .font(.system(size: 12, weight: .medium, design: .rounded))
                        .foregroundColor(.primary)
                        .lineLimit(1)

                    Text(authService.currentUser?.email ?? "")
                        .font(.system(size: 10, design: .rounded))
                        .foregroundStyle(.tertiary)
                        .lineLimit(1)
                }

                Spacer()

                Image(systemName: "ellipsis")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(.secondary.opacity(0.5))
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
        .popover(isPresented: $showProfilePopover, arrowEdge: .leading) {
            userProfilePopover
        }
    }

    private var userProfilePopover: some View {
        VStack(alignment: .leading, spacing: 0) {
            // 用户信息
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [brandBlue, Color.purple.opacity(0.6)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: 40, height: 40)

                    Text(userInitials)
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundColor(.white)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(authService.currentUser?.nickname ?? authService.currentUser?.username ?? "用户")
                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                    Text(authService.currentUser?.email ?? "")
                        .font(.system(size: 11, design: .rounded))
                        .foregroundColor(.secondary)
                }
            }
            .padding(16)

            Divider()
                .opacity(0.3)

            // 快捷操作
            Button(action: {
                showProfilePopover = false
                selectedTab = .settings
            }) {
                HStack {
                    Image(systemName: "person.circle")
                    Text("账户管理")
                    Spacer()
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
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
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
            }
            .buttonStyle(.plain)
        }
        .frame(width: 220)
    }

    private var userInitials: String {
        let name = authService.currentUser?.nickname ?? authService.currentUser?.username ?? "?"
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
                .foregroundColor(iconColor)
                .font(.system(size: 14, weight: .medium))

            Text(message)
                .font(.system(size: 13, design: .rounded))
                .foregroundColor(.primary)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 11)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(.regularMaterial)
                .shadow(color: .black.opacity(0.1), radius: 16, x: 0, y: 4)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .strokeBorder(Color.primary.opacity(0.08), lineWidth: 0.5)
        )
    }

    private var iconName: String {
        switch type {
        case .success: return "checkmark.circle.fill"
        case .error: return "exclamationmark.circle.fill"
        case .info: return "info.circle.fill"
        case .warning: return "exclamationmark.triangle.fill"
        }
    }

    private var iconColor: Color {
        switch type {
        case .success: return .green
        case .error: return .red
        case .info: return Color(red: 0.24, green: 0.47, blue: 0.96)
        case .warning: return .orange
        }
    }
}