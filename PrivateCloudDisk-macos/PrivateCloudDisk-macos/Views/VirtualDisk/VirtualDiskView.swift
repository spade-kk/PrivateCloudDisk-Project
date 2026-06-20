import SwiftUI

// MARK: - 虚拟磁盘视图（企业级设计）

/// 虚拟磁盘管理页面
///
/// 参考百度网盘 macOS 客户端设计：
/// - 状态卡片 + 脉动动画
/// - 同步事件时间线
/// - 卡片式配置分组
/// - 品牌色点缀
struct VirtualDiskView: View {
    @EnvironmentObject var viewModel: VirtualDiskViewModel

    @State private var showMountPicker = false
    @State private var showClearCacheAlert = false
    @State private var statusPulse: Bool = false

    private let brandBlue = AppColors.primary

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 24) {
                // 状态卡片
                statusCard

                // 同步事件
                syncEventsSection

                // 配置
                configSection

                // 缓存管理
                cacheSection
            }
            .padding(24)
            .padding(.top, 10)
        }
        .background(.ultraThinMaterial)
        .alert("确认清除缓存？", isPresented: $showClearCacheAlert) {
            Button("取消", role: .cancel) {}
            Button("清除", role: .destructive) {
                Task { await viewModel.clearCache() }
            }
        } message: {
            Text("清除缓存后，未同步的文件将丢失。已同步的文件不受影响。")
        }
    }

    // MARK: - 状态卡片

    private var statusCard: some View {
        HStack(spacing: 16) {
            // 状态图标（带脉动动画）
            ZStack {
                Circle()
                    .fill(statusColor.opacity(0.12))
                    .frame(width: 68, height: 68)

                if viewModel.status == .syncing || viewModel.status == .connecting {
                    Circle()
                        .stroke(statusColor.opacity(0.3), lineWidth: 2)
                        .frame(width: 68, height: 68)
                        .scaleEffect(statusPulse ? 1.1 : 1.0)
                        .opacity(statusPulse ? 0 : 0.5)
                        .animation(.easeInOut(duration: 1.5).repeatForever(autoreverses: false), value: statusPulse)
                        .onAppear { statusPulse = true }
                }

                Image(systemName: viewModel.status.sfSymbolName)
                    .font(.system(size: 28, weight: .medium))
                    .foregroundColor(statusColor)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text("虚拟磁盘")
                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                    .foregroundColor(.secondary)
                Text(viewModel.status.displayName)
                    .font(.system(size: 18, weight: .bold, design: .rounded))
                    .foregroundColor(statusColor)
                if viewModel.isMounted {
                    Text(viewModel.config.mountPoint)
                        .font(.system(size: 11, design: .rounded))
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
            }

            Spacer()

            // 挂载/卸载按钮
            Button(action: {
                if viewModel.isMounted {
                    viewModel.unmount()
                } else {
                    Task { await viewModel.mount() }
                }
            }) {
                HStack(spacing: 6) {
                    Image(systemName: viewModel.isMounted ? "eject.fill" : "externaldrive.badge.plus")
                        .font(.system(size: 12))
                    Text(viewModel.isMounted ? "卸载" : "挂载")
                        .font(.system(size: 13, weight: .semibold, design: .rounded))
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(
                    RoundedRectangle(cornerRadius: 10)
                        .fill(viewModel.isMounted ? Color.red.opacity(0.1) : brandBlue)
                )
                .foregroundColor(viewModel.isMounted ? .red : .white)
            }
            .buttonStyle(.plain)
            .disabled(viewModel.isLoading)
            .shadow(
                color: viewModel.isMounted ? .clear : brandBlue.opacity(0.25),
                radius: 10, x: 0, y: 4
            )
        }
        .padding(20)
        .background(
            RoundedRectangle(cornerRadius: 18)
                .fill(.regularMaterial)
                .shadow(color: .black.opacity(0.05), radius: 10, x: 0, y: 3)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(statusColor.opacity(0.15), lineWidth: 1)
        )
    }

    // MARK: - 同步事件

    private var syncEventsSection: some View {
        VStack(alignment: .leading, spacing: 0) {
            sectionHeader(title: "同步事件", icon: "arrow.triangle.2.circlepath")

            if viewModel.syncEvents.isEmpty {
                emptyState(icon: "clock.badge.checkmark", text: "暂无同步事件")
                    .padding(.top, 16)
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(viewModel.syncEvents.suffix(10).enumerated()), id: \.element.timestamp) { index, event in
                        HStack(spacing: 12) {
                            // 时间线圆点
                            ZStack {
                                Circle()
                                    .fill(event.type.color.opacity(0.15))
                                    .frame(width: 28, height: 28)

                                Image(systemName: event.type.iconName)
                                    .font(.system(size: 11, weight: .medium))
                                    .foregroundColor(event.type.color)
                            }

                            VStack(alignment: .leading, spacing: 2) {
                                Text(event.type.displayName)
                                    .font(.system(size: 13, design: .rounded))
                                if let details = event.details {
                                    Text(details)
                                        .font(.system(size: 11, design: .rounded))
                                        .foregroundColor(.secondary)
                                }
                            }

                            Spacer()

                            Text(event.timestamp, style: .time)
                                .font(.system(size: 11, design: .rounded))
                                .foregroundStyle(.tertiary)
                        }
                        .padding(.vertical, 8)

                        if index < viewModel.syncEvents.suffix(10).count - 1 {
                            Divider()
                                .opacity(0.3)
                                .padding(.leading, 40)
                        }
                    }
                }
                .padding(.top, 12)
            }
        }
        .padding(20)
        .background(
            RoundedRectangle(cornerRadius: 18)
                .fill(.regularMaterial)
                .shadow(color: .black.opacity(0.04), radius: 8, x: 0, y: 2)
        )
    }

    // MARK: - 配置

    private var configSection: some View {
        VStack(alignment: .leading, spacing: 0) {
            sectionHeader(title: "配置", icon: "gearshape")

            VStack(spacing: 0) {
                // 挂载点
                HStack {
                    Text("挂载点")
                        .font(.system(size: 13, design: .rounded))
                        .foregroundColor(.secondary)
                        .frame(width: 80, alignment: .leading)

                    Text(viewModel.config.mountPoint)
                        .font(.system(size: 13, design: .rounded))
                        .lineLimit(1)

                    Spacer()

                    Button("选择...") {
                        showMountPicker = true
                    }
                    .buttonStyle(.plain)
                    .font(.system(size: 12, design: .rounded))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 5)
                    .background(
                        RoundedRectangle(cornerRadius: 6)
                            .fill(brandBlue.opacity(0.1))
                    )
                    .foregroundColor(brandBlue)
                }
                .padding(.vertical, 10)

                ConfigDivider()

                // 自动同步
                Toggle(isOn: Binding(
                    get: { viewModel.config.autoSync },
                    set: { viewModel.updateConfig(autoSync: $0) }
                )) {
                    Text("自动同步")
                        .font(.system(size: 13, design: .rounded))
                }
                .toggleStyle(.switch)
                .tint(brandBlue)
                .padding(.vertical, 10)

                if viewModel.config.autoSync {
                    ConfigDivider()
                    HStack {
                        Text("同步间隔")
                            .font(.system(size: 13, design: .rounded))
                            .foregroundColor(.secondary)
                        Spacer()
                        Picker("", selection: Binding(
                            get: { viewModel.config.syncInterval },
                            set: { viewModel.updateConfig(syncInterval: $0) }
                        )) {
                            Text("15秒").tag(15.0)
                            Text("30秒").tag(30.0)
                            Text("1分钟").tag(60.0)
                            Text("5分钟").tag(300.0)
                        }
                        .frame(width: 120)
                    }
                    .padding(.vertical, 10)
                }
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

    // MARK: - 缓存

    private var cacheSection: some View {
        VStack(alignment: .leading, spacing: 0) {
            sectionHeader(title: "缓存管理", icon: "internaldrive")

            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("缓存大小")
                        .font(.system(size: 13, design: .rounded))
                        .foregroundColor(.secondary)
                    Text(ByteCountFormatter.string(fromByteCount: viewModel.cacheSize, countStyle: .file))
                        .font(.system(size: 16, weight: .semibold, design: .rounded))
                }

                Spacer()

                Button("清除缓存") {
                    showClearCacheAlert = true
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 16)
                .padding(.vertical, 9)
                .background(
                    RoundedRectangle(cornerRadius: 8)
                        .fill(.orange.opacity(0.1))
                )
                .foregroundColor(.orange)
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

    // MARK: - 辅助组件

    private func sectionHeader(title: String, icon: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(brandBlue)
            Text(title)
                .font(.system(size: 14, weight: .semibold, design: .rounded))
        }
    }

    private func emptyState(icon: String, text: String) -> some View {
        HStack {
            Spacer()
            VStack(spacing: 10) {
                ZStack {
                    Circle()
                        .fill(.quaternary.opacity(0.5))
                        .frame(width: 48, height: 48)
                    Image(systemName: icon)
                        .font(.system(size: 18, weight: .light))
                        .foregroundColor(.secondary.opacity(0.5))
                }
                Text(text)
                    .font(.system(size: 13, design: .rounded))
                    .foregroundColor(.secondary)
            }
            Spacer()
        }
        .padding(.vertical, 20)
    }

    private var statusColor: Color {
        switch viewModel.status {
        case .disconnected: return .secondary
        case .connecting: return brandBlue
        case .connected: return .green
        case .syncing: return brandBlue
        case .paused: return .orange
        case .error: return .red
        case .unmounting: return .orange
        }
    }
}

struct ConfigDivider: View {
    var body: some View {
        Divider()
            .opacity(0.3)
    }
}

// MARK: - 同步事件类型扩展

extension SyncEvent.SyncEventType {
    var iconName: String {
        switch self {
        case .fileCreated: return "plus.circle.fill"
        case .fileModified: return "pencil.circle.fill"
        case .fileDeleted: return "trash.circle.fill"
        case .fileRenamed: return "pencil.circle.fill"
        case .fileMoved: return "arrow.right.circle.fill"
        case .syncStarted: return "arrow.triangle.2.circlepath"
        case .syncCompleted: return "checkmark.circle.fill"
        case .syncFailed: return "exclamationmark.circle.fill"
        case .conflictDetected: return "exclamationmark.triangle.fill"
        case .cacheEvicted: return "trash.slash"
        }
    }

    var displayName: String {
        switch self {
        case .fileCreated: return "文件创建"
        case .fileModified: return "文件修改"
        case .fileDeleted: return "文件删除"
        case .fileRenamed: return "文件重命名"
        case .fileMoved: return "文件移动"
        case .syncStarted: return "开始同步"
        case .syncCompleted: return "同步完成"
        case .syncFailed: return "同步失败"
        case .conflictDetected: return "冲突检测"
        case .cacheEvicted: return "缓存清理"
        }
    }

    var color: Color {
        let brandBlue = AppColors.primary
        switch self {
        case .fileCreated: return .green
        case .fileModified: return brandBlue
        case .fileDeleted: return .red
        case .fileRenamed: return .orange
        case .fileMoved: return .purple
        case .syncStarted: return brandBlue
        case .syncCompleted: return .green
        case .syncFailed: return .red
        case .conflictDetected: return .orange
        case .cacheEvicted: return .secondary
        }
    }
}

#Preview {
    VirtualDiskView()
        .environmentObject(VirtualDiskViewModel())
}
