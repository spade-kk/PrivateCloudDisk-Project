import SwiftUI

// MARK: - 设置页面（企业级设计 v2）

/// 设置页面
///
/// 参考百度网盘 macOS 客户端设置设计：
/// - 左侧导航 + 右侧内容区
/// - 分组卡片式布局
/// - 外观/主题切换
/// - 网络/同步/通知/关于 等分组
/// - 品牌色点缀 + 清晰信息层级
struct SettingsView: View {
    @EnvironmentObject var settingsVM: SettingsViewModel
    @EnvironmentObject var authService: AuthService

    @State private var selectedSection: SettingsSection = .general
    @State private var selectedAppearance: AppearanceMode = .system

    private let brandBlue = Color(red: 0.24, green: 0.47, blue: 0.96)

    enum SettingsSection: String, CaseIterable, Identifiable {
        case general = "通用"
        case appearance = "外观"
        case sync = "同步"
        case network = "网络"
        case notifications = "通知"
        case storage = "存储"
        case about = "关于"

        var id: String { rawValue }

        var icon: String {
            switch self {
            case .general: return "gearshape.fill"
            case .appearance: return "paintpalette.fill"
            case .sync: return "arrow.triangle.2.circlepath"
            case .network: return "network"
            case .notifications: return "bell.fill"
            case .storage: return "externaldrive.fill"
            case .about: return "info.circle.fill"
            }
        }

        var color: Color {
            switch self {
            case .general: return .gray
            case .appearance: return .purple
            case .sync: return .blue
            case .network: return .green
            case .notifications: return .orange
            case .storage: return .teal
            case .about: return .pink
            }
        }
    }

    enum AppearanceMode: String, CaseIterable {
        case light = "浅色"
        case dark = "深色"
        case system = "跟随系统"
    }

    var body: some View {
        HStack(spacing: 0) {
            // ── 左侧导航 ──
            settingsNav
                .frame(width: 200)

            Divider()
                .opacity(0.3)

            // ── 右侧内容 ──
            ScrollView(showsIndicators: false) {
                settingsContent
                    .padding(32)
                    .frame(maxWidth: 680, alignment: .leading)
            }
            .background(Color(nsColor: .controlBackgroundColor))
        }
        .background(Color(nsColor: .windowBackgroundColor))
        .onDisappear {
            settingsVM.saveAll()
        }
    }

    // MARK: - 左侧导航

    private var settingsNav: some View {
        VStack(spacing: 0) {
            HStack {
                Text("设置")
                    .font(.system(size: 18, weight: .bold, design: .rounded))
                    .foregroundColor(.primary)
                Spacer()
            }
            .padding(.horizontal, 20)
            .padding(.top, 24)
            .padding(.bottom, 16)

            Divider()
                .opacity(0.3)
                .padding(.horizontal, 12)

            ScrollView(showsIndicators: false) {
                VStack(spacing: 2) {
                    ForEach(SettingsSection.allCases) { section in
                        navItem(section)
                    }
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 8)
            }
        }
        .background(
            VisualEffectView(
                material: .sidebar,
                blendingMode: .behindWindow
            )
        )
    }

    private func navItem(_ section: SettingsSection) -> some View {
        let isSelected = selectedSection == section

        return Button(action: {
            withAnimation(.easeInOut(duration: 0.2)) {
                selectedSection = section
            }
        }) {
            HStack(spacing: 10) {
                ZStack {
                    RoundedRectangle(cornerRadius: 6)
                        .fill(isSelected ? section.color.opacity(0.15) : .clear)
                        .frame(width: 30, height: 30)

                    Image(systemName: section.icon)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(isSelected ? section.color : .secondary)
                }

                Text(section.rawValue)
                    .font(.system(size: 13, weight: isSelected ? .semibold : .regular, design: .rounded))
                    .foregroundColor(isSelected ? .primary : .secondary)

                Spacer()

                if isSelected {
                    RoundedRectangle(cornerRadius: 2)
                        .fill(section.color)
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
    }

    // MARK: - 右侧内容

    @ViewBuilder
    private var settingsContent: some View {
        switch selectedSection {
        case .general:
            generalSection
        case .appearance:
            appearanceSection
        case .sync:
            syncSection
        case .network:
            networkSection
        case .notifications:
            notificationsSection
        case .storage:
            storageSection
        case .about:
            aboutSection
        }
    }

    // MARK: - 通用设置

    private var generalSection: some View {
        VStack(alignment: .leading, spacing: 24) {
            sectionTitle("通用", icon: "gearshape.fill")

            settingsGroup("启动与登录") {
                ToggleRow(
                    title: "开机自动启动",
                    subtitle: "登录系统时自动启动 PrivateCloudDisk",
                    icon: "power",
                    isOn: $settingsVM.launchAtLogin
                )
            }

            settingsGroup("文件管理") {
                ToggleRow(
                    title: "显示隐藏文件",
                    subtitle: "显示以 . 开头的隐藏文件",
                    icon: "eye.slash",
                    isOn: $settingsVM.showHiddenFiles
                )
            }
        }
    }

    // MARK: - 外观设置

    private var appearanceSection: some View {
        VStack(alignment: .leading, spacing: 24) {
            sectionTitle("外观", icon: "paintpalette.fill")

            settingsGroup("主题") {
                HStack(spacing: 12) {
                    themeOption(
                        title: "浅色",
                        icon: "sun.max.fill",
                        color: .orange,
                        mode: .light
                    )

                    themeOption(
                        title: "深色",
                        icon: "moon.fill",
                        color: .indigo,
                        mode: .dark
                    )

                    themeOption(
                        title: "跟随系统",
                        icon: "circle.lefthalf.filled",
                        color: .gray,
                        mode: .system
                    )
                }
            }
        }
    }

    private func themeOption(
        title: String,
        icon: String,
        color: Color,
        mode: AppearanceMode
    ) -> some View {
        let isSelected = selectedAppearance == mode

        return Button(action: { selectedAppearance = mode }) {
            VStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 22, weight: .medium))
                    .foregroundColor(isSelected ? color : .secondary)

                Text(title)
                    .font(.system(size: 11, weight: isSelected ? .semibold : .regular, design: .rounded))
                    .foregroundColor(isSelected ? .primary : .secondary)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(isSelected ? color.opacity(0.06) : .clear)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .strokeBorder(
                        isSelected ? color.opacity(0.2) : Color.primary.opacity(0.06),
                        lineWidth: 1
                    )
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: - 同步设置

    private var syncSection: some View {
        VStack(alignment: .leading, spacing: 24) {
            sectionTitle("同步", icon: "arrow.triangle.2.circlepath")

            settingsGroup("自动同步") {
                ToggleRow(
                    title: "启用自动同步",
                    subtitle: "文件变更时自动同步到云端",
                    icon: "arrow.triangle.2.circlepath",
                    isOn: $settingsVM.autoSync
                )
            }

            settingsGroup("同步配置") {
                HStack {
                    InfoRow(label: "同步间隔", value: "\(Int(settingsVM.syncInterval)) 秒", icon: "clock")
                }
            }
        }
    }

    // MARK: - 网络设置

    private var networkSection: some View {
        VStack(alignment: .leading, spacing: 24) {
            sectionTitle("网络", icon: "network")

            settingsGroup("服务器配置") {
                VStack(alignment: .leading, spacing: 6) {
                    Text("API 服务器地址")
                        .font(.system(size: 11, design: .rounded))
                        .foregroundColor(.secondary)
                        .padding(.leading, 4)

                    HStack(spacing: 10) {
                        Image(systemName: "network")
                            .foregroundColor(.secondary)
                        TextField("http://localhost:8000", text: $settingsVM.apiBaseURL)
                            .textFieldStyle(.plain)
                            .font(.system(size: 13, design: .rounded))
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(
                        RoundedRectangle(cornerRadius: 8)
                            .fill(Color(nsColor: .quaternaryLabelColor).opacity(0.4))
                    )
                }

                Text("修改后需要重新登录生效")
                    .font(.system(size: 11, design: .rounded))
                    .foregroundColor(.secondary)
                    .padding(.top, 4)
            }
        }
    }

    // MARK: - 通知设置

    private var notificationsSection: some View {
        VStack(alignment: .leading, spacing: 24) {
            sectionTitle("通知", icon: "bell.fill")

            settingsGroup("通知类型") {
                ToggleRow(
                    title: "上传完成通知",
                    subtitle: "文件上传成功时显示通知",
                    icon: "arrow.up.circle",
                    isOn: $settingsVM.uploadNotification
                )

                Divider().opacity(0.3)

                ToggleRow(
                    title: "下载完成通知",
                    subtitle: "文件下载完成时显示通知",
                    icon: "arrow.down.circle",
                    isOn: $settingsVM.downloadNotification
                )

                Divider().opacity(0.3)

                ToggleRow(
                    title: "分享通知",
                    subtitle: "收到文件分享时显示通知",
                    icon: "square.and.arrow.up",
                    isOn: $settingsVM.shareNotification
                )

                Divider().opacity(0.3)

                ToggleRow(
                    title: "存储空间提醒",
                    subtitle: "存储空间不足时提醒",
                    icon: "exclamationmark.triangle",
                    isOn: $settingsVM.storageAlert
                )
            }
        }
    }

    // MARK: - 存储设置

    private var storageSection: some View {
        VStack(alignment: .leading, spacing: 24) {
            sectionTitle("存储", icon: "externaldrive.fill")

            settingsGroup("虚拟磁盘") {
                VStack(alignment: .leading, spacing: 6) {
                    Text("挂载路径")
                        .font(.system(size: 11, design: .rounded))
                        .foregroundColor(.secondary)
                        .padding(.leading, 4)

                    HStack(spacing: 10) {
                        Image(systemName: "externaldrive")
                            .foregroundColor(.secondary)
                        Text(settingsVM.mountPoint)
                            .font(.system(size: 12, design: .rounded))
                            .foregroundColor(.primary)
                            .lineLimit(1)
                        Spacer()
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(
                        RoundedRectangle(cornerRadius: 8)
                            .fill(Color(nsColor: .quaternaryLabelColor).opacity(0.4))
                    )
                }

                Divider().opacity(0.3)

                InfoRow(label: "缓存上限", value: "\(Int(settingsVM.cacheMaxSize)) GB", icon: "folder")
            }

            settingsGroup("安全") {
                ToggleRow(
                    title: "使用生物识别认证",
                    subtitle: "使用 Touch ID 或 Face ID 解锁",
                    icon: "faceid",
                    isOn: $settingsVM.useBiometricAuth
                )
            }
        }
    }

    // MARK: - 关于

    private var aboutSection: some View {
        VStack(alignment: .leading, spacing: 24) {
            sectionTitle("关于", icon: "info.circle.fill")

            settingsGroup("应用信息") {
                VStack(spacing: 16) {
                    HStack(spacing: 16) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 16)
                                .fill(
                                    LinearGradient(
                                        colors: [brandBlue, Color.purple],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )
                                )
                                .frame(width: 64, height: 64)

                            Image(systemName: "externaldrive.fill.badge.icloud")
                                .font(.system(size: 28, weight: .medium))
                                .foregroundColor(.white)
                        }

                        VStack(alignment: .leading, spacing: 4) {
                            Text("PrivateCloudDisk")
                                .font(.system(size: 18, weight: .bold, design: .rounded))
                            Text("版本 \(settingsVM.appVersion)")
                                .font(.system(size: 12, design: .rounded))
                                .foregroundColor(.secondary)
                        }
                    }

                    Divider().opacity(0.3)

                    InfoRow(label: "Build", value: settingsVM.buildNumber, icon: "hammer")
                    InfoRow(label: "许可证", value: "Business License", icon: "doc.text")
                }
            }

            settingsGroup("支持") {
                HStack {
                    Text("需要帮助？")
                        .font(.system(size: 13, design: .rounded))
                        .foregroundColor(.secondary)
                    Spacer()
                    Button("访问帮助中心") {}
                        .buttonStyle(.plain)
                        .font(.system(size: 12, weight: .medium, design: .rounded))
                        .foregroundColor(brandBlue)
                }

                Divider().opacity(0.3)

                HStack {
                    Text("检查更新")
                        .font(.system(size: 13, design: .rounded))
                        .foregroundColor(.secondary)
                    Spacer()
                    Button("检查") {}
                        .buttonStyle(.plain)
                        .font(.system(size: 12, weight: .medium, design: .rounded))
                        .foregroundColor(brandBlue)
                }
            }
        }
    }

    // MARK: - 公共组件

    private func sectionTitle(_ title: String, icon: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(brandBlue)

            Text(title)
                .font(.system(size: 20, weight: .bold, design: .rounded))
                .foregroundColor(.primary)
        }
    }

    private func settingsGroup<Content: View>(
        _ title: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title)
                .font(.system(size: 11, weight: .semibold, design: .rounded))
                .foregroundColor(.secondary)
                .textCase(.uppercase)
                .padding(.horizontal, 4)
                .padding(.bottom, 10)

            VStack(spacing: 0) {
                content()
                    .padding(16)
            }
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(.regularMaterial)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .strokeBorder(Color.primary.opacity(0.06), lineWidth: 0.5)
            )
        }
    }
}

#Preview {
    SettingsView()
        .environmentObject(SettingsViewModel())
        .environmentObject(AuthService.shared)
}