import SwiftUI

// MARK: - 通用组件库（企业级设计 v2）
//
// 提供可复用的 UI 组件：
// - Tooltip：悬停提示
// - Badge：状态徽章
// - SkeletonLoading：骨架屏加载
// - ContextMenuBuilder：右键菜单构建器
// - GlassCard：毛玻璃卡片
// - GradientButton：渐变按钮
// - EmptyState：空状态视图
// - StatusIndicator：状态指示器

// MARK: - Tooltip（悬停提示）

struct TooltipView: View {
    let text: String
    let position: TooltipPosition

    enum TooltipPosition {
        case top, bottom, left, right
    }

    var body: some View {
        Text(text)
            .font(.system(size: 11, design: .rounded))
            .foregroundColor(.white)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(
                RoundedRectangle(cornerRadius: 6)
                    .fill(Color.black.opacity(0.8))
            )
            .shadow(color: .black.opacity(0.15), radius: 4, x: 0, y: 2)
    }
}

extension View {
    /// 添加 Tooltip
    func tooltip(_ text: String, isPresented: Bool) -> some View {
        overlay(
            TooltipView(text: text, position: .top)
                .opacity(isPresented ? 1 : 0)
                .animation(.easeInOut(duration: 0.15), value: isPresented),
            alignment: .top
        )
    }
}

// MARK: - Badge（状态徽章）

struct Badge: View {
    let text: String
    let style: BadgeStyle

    enum BadgeStyle {
        case brand, success, warning, error, info, neutral
    }

    private let brandBlue = AppColors.primary

    var body: some View {
        Text(text)
            .font(.system(size: 10, weight: .semibold, design: .rounded))
            .foregroundColor(foregroundColor)
            .padding(.horizontal, 7)
            .padding(.vertical, 3)
            .background(
                RoundedRectangle(cornerRadius: 5)
                    .fill(backgroundColor)
            )
    }

    private var foregroundColor: Color {
        switch style {
        case .brand: return brandBlue
        case .success: return .green
        case .warning: return .orange
        case .error: return .red
        case .info: return .secondary
        case .neutral: return .secondary
        }
    }

    private var backgroundColor: Color {
        switch style {
        case .brand: return brandBlue.opacity(0.1)
        case .success: return .green.opacity(0.1)
        case .warning: return .orange.opacity(0.1)
        case .error: return .red.opacity(0.1)
        case .info: return .secondary.opacity(0.1)
        case .neutral: return Color(nsColor: .quaternaryLabelColor).opacity(0.5)
        }
    }
}

// MARK: - 渐变徽章

struct GradientBadge: View {
    let text: String

    private let gradient = AppGradients.primary

    var body: some View {
        Text(text)
            .font(.system(size: 10, weight: .semibold, design: .rounded))
            .foregroundColor(.white)
            .padding(.horizontal, 7)
            .padding(.vertical, 3)
            .background(
                RoundedRectangle(cornerRadius: 5)
                    .fill(gradient)
            )
    }
}

// MARK: - Skeleton Loading（骨架屏）

struct SkeletonLoading: View {
    let count: Int

    var body: some View {
        LazyVStack(spacing: 0) {
            ForEach(0..<count, id: \.self) { _ in
                SkeletonRow()
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)

                Divider()
                    .padding(.leading, 48)
                    .opacity(0.3)
            }
        }
    }
}

struct SkeletonRow: View {
    @State private var isAnimating = false

    private let shimmerGradient = LinearGradient(
        colors: [
            Color.gray.opacity(0.1),
            Color.gray.opacity(0.2),
            Color.gray.opacity(0.1),
        ],
        startPoint: .leading,
        endPoint: .trailing
    )

    var body: some View {
        HStack(spacing: 12) {
            // 图标占位
            RoundedRectangle(cornerRadius: 6)
                .fill(Color.gray.opacity(0.15))
                .frame(width: 24, height: 24)

            // 名称占位
            RoundedRectangle(cornerRadius: 4)
                .fill(Color.gray.opacity(0.15))
                .frame(width: CGFloat.random(in: 120...220), height: 14)

            Spacer()

            // 大小占位
            RoundedRectangle(cornerRadius: 4)
                .fill(Color.gray.opacity(0.15))
                .frame(width: 60, height: 14)

            // 时间占位
            RoundedRectangle(cornerRadius: 4)
                .fill(Color.gray.opacity(0.15))
                .frame(width: 100, height: 14)
        }
        .overlay(
            shimmerGradient
                .mask(
                    Rectangle()
                        .fill(
                            LinearGradient(
                                colors: [.clear, .white.opacity(0.3), .clear],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .offset(x: isAnimating ? 300 : -300)
                )
        )
        .onAppear {
            withAnimation(.linear(duration: 1.5).repeatForever(autoreverses: false)) {
                isAnimating = true
            }
        }
    }
}

// MARK: - 卡片骨架屏

struct CardSkeleton: View {
    @State private var isAnimating = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            RoundedRectangle(cornerRadius: 6)
                .fill(Color.gray.opacity(0.15))
                .frame(height: 16)

            RoundedRectangle(cornerRadius: 6)
                .fill(Color.gray.opacity(0.15))
                .frame(width: 120, height: 32)

            VStack(spacing: 8) {
                ForEach(0..<3, id: \.self) { _ in
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Color.gray.opacity(0.15))
                        .frame(height: 12)
                }
            }
        }
        .padding(20)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(.regularMaterial)
        )
        .overlay(
            LinearGradient(
                colors: [.clear, .white.opacity(0.3), .clear],
                startPoint: .leading,
                endPoint: .trailing
            )
            .mask(
                Rectangle()
                    .offset(x: isAnimating ? 300 : -300)
            )
        )
        .onAppear {
            withAnimation(.linear(duration: 1.5).repeatForever(autoreverses: false)) {
                isAnimating = true
            }
        }
    }
}

// MARK: - ContextMenuBuilder（右键菜单构建器）

struct FileContextMenu: View {
    let file: FileNode
    let onOpen: () -> Void
    let onDownload: () -> Void
    let onShare: () -> Void
    let onRename: () -> Void
    let onCopy: () -> Void
    let onMove: () -> Void
    let onToggleFavorite: () -> Void
    let onDelete: () -> Void
    let onViewDetails: () -> Void

    var body: some View {
        Button("打开") { onOpen() }

        Divider()

        Button("下载") { onDownload() }
        Button("分享...") { onShare() }

        Divider()

        Button("重命名") { onRename() }
        Button("复制") { onCopy() }
        Button("移动") { onMove() }

        Divider()

        Button(file.isStarred == true ? "取消收藏" : "添加收藏") {
            onToggleFavorite()
        }

        Divider()

        Button("删除", role: .destructive) { onDelete() }

        Button("查看详情") { onViewDetails() }
    }
}

// MARK: - GlassCard（毛玻璃卡片）

struct GlassCard<Content: View>: View {
    let content: Content
    var cornerRadius: CGFloat = 16
    var padding: CGFloat = 20
    var shadow: Bool = true

    init(
        cornerRadius: CGFloat = 16,
        padding: CGFloat = 20,
        shadow: Bool = true,
        @ViewBuilder content: () -> Content
    ) {
        self.cornerRadius = cornerRadius
        self.padding = padding
        self.shadow = shadow
        self.content = content()
    }

    var body: some View {
        content
            .padding(padding)
            .background(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .fill(.regularMaterial)
            )
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .strokeBorder(Color.primary.opacity(0.06), lineWidth: 0.5)
            )
            .shadow(
                color: shadow ? .black.opacity(0.04) : .clear,
                radius: 12,
                x: 0,
                y: 4
            )
    }
}

// MARK: - GradientButton（渐变按钮）

struct GradientButton: View {
    let title: String
    let icon: String?
    let isLoading: Bool
    let action: () -> Void

    private let brandGradient = AppGradients.primary

    init(
        title: String,
        icon: String? = nil,
        isLoading: Bool = false,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.icon = icon
        self.isLoading = isLoading
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if isLoading {
                    ProgressView()
                        .scaleEffect(0.7)
                        .tint(.white)
                } else if let icon = icon {
                    Image(systemName: icon)
                        .font(.system(size: 14, weight: .medium))
                }

                Text(title)
                    .font(.system(size: 14, weight: .semibold, design: .rounded))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(brandGradient)
            )
            .foregroundColor(.white)
            .shadow(
                color: AppColors.primary.opacity(0.3),
                radius: 8,
                x: 0,
                y: 3
            )
        }
        .buttonStyle(.plain)
        .disabled(isLoading)
        .scaleEffect(isLoading ? 0.98 : 1)
        .animation(.easeInOut(duration: 0.2), value: isLoading)
    }
}

// MARK: - EmptyState（空状态视图）

struct EmptyState: View {
    let icon: String
    let title: String
    let subtitle: String
    let actionTitle: String?
    let action: (() -> Void)?

    private let brandBlue = AppColors.primary

    init(
        icon: String,
        title: String,
        subtitle: String,
        actionTitle: String? = nil,
        action: (() -> Void)? = nil
    ) {
        self.icon = icon
        self.title = title
        self.subtitle = subtitle
        self.actionTitle = actionTitle
        self.action = action
    }

    var body: some View {
        VStack(spacing: 16) {
            Spacer()

            ZStack {
                RoundedRectangle(cornerRadius: 20)
                    .fill(brandBlue.opacity(0.06))
                    .frame(width: 80, height: 80)

                Image(systemName: icon)
                    .font(.system(size: 32, weight: .light))
                    .foregroundColor(brandBlue.opacity(0.3))
            }

            VStack(spacing: 4) {
                Text(title)
                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                    .foregroundColor(.secondary)

                Text(subtitle)
                    .font(.system(size: 12, design: .rounded))
                    .foregroundColor(.secondary.opacity(0.6))
            }

            if let actionTitle = actionTitle, let action = action {
                Button(action: action) {
                    Text(actionTitle)
                        .font(.system(size: 13, weight: .medium, design: .rounded))
                        .foregroundColor(brandBlue)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 8)
                        .background(
                            RoundedRectangle(cornerRadius: 8)
                                .fill(brandBlue.opacity(0.08))
                        )
                }
                .buttonStyle(.plain)
                .padding(.top, 4)
            }

            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - StatusIndicator（状态指示器）

struct StatusIndicator: View {
    let status: FileStatus
    let showLabel: Bool

    enum FileStatus {
        case synced, syncing, pending, error, offline

        var color: Color {
            switch self {
            case .synced: return .green
            case .syncing: return .blue
            case .pending: return .orange
            case .error: return .red
            case .offline: return .gray
            }
        }

        var label: String {
            switch self {
            case .synced: return "已同步"
            case .syncing: return "同步中"
            case .pending: return "待同步"
            case .error: return "同步失败"
            case .offline: return "离线"
            }
        }
    }

    init(_ status: FileStatus, showLabel: Bool = false) {
        self.status = status
        self.showLabel = showLabel
    }

    var body: some View {
        HStack(spacing: 6) {
            Circle()
                .fill(status.color)
                .frame(width: 6, height: 6)
                .overlay(
                    Circle()
                        .fill(status.color.opacity(0.3))
                        .frame(width: 10, height: 10)
                )

            if showLabel {
                Text(status.label)
                    .font(.system(size: 11, design: .rounded))
                    .foregroundColor(.secondary)
            }
        }
    }
}

// MARK: - SectionHeader（分组标题）

struct SectionHeader: View {
    let title: String
    let subtitle: String?

    init(_ title: String, subtitle: String? = nil) {
        self.title = title
        self.subtitle = subtitle
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.system(size: 11, weight: .semibold, design: .rounded))
                .foregroundColor(.secondary)
                .textCase(.uppercase)

            if let subtitle = subtitle {
                Text(subtitle)
                    .font(.system(size: 11, design: .rounded))
                    .foregroundColor(.secondary.opacity(0.6))
            }
        }
        .padding(.horizontal, 4)
    }
}

// MARK: - IconButton（图标按钮）

struct IconButton: View {
    let icon: String
    let label: String
    let color: Color
    let action: () -> Void

    @State private var isHovered = false

    init(
        icon: String,
        label: String = "",
        color: Color = .secondary,
        action: @escaping () -> Void
    ) {
        self.icon = icon
        self.label = label
        self.color = color
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.system(size: 12, weight: .medium))

                if !label.isEmpty {
                    Text(label)
                        .font(.system(size: 12, design: .rounded))
                }
            }
            .foregroundColor(isHovered ? color : .secondary)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(
                RoundedRectangle(cornerRadius: 6)
                    .fill(isHovered ? color.opacity(0.08) : .clear)
            )
        }
        .buttonStyle(.plain)
        .onHover { hovering in
            withAnimation(.easeInOut(duration: 0.15)) {
                isHovered = hovering
            }
        }
    }
}

// MARK: - ToggleRow（开关行）

struct ToggleRow: View {
    let title: String
    let subtitle: String?
    let icon: String?
    @Binding var isOn: Bool

    init(
        title: String,
        subtitle: String? = nil,
        icon: String? = nil,
        isOn: Binding<Bool>
    ) {
        self.title = title
        self.subtitle = subtitle
        self.icon = icon
        self._isOn = isOn
    }

    var body: some View {
        HStack(spacing: 12) {
            if let icon = icon {
                Image(systemName: icon)
                    .font(.system(size: 14))
                    .foregroundColor(.secondary)
                    .frame(width: 20)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 13, design: .rounded))
                    .foregroundColor(.primary)

                if let subtitle = subtitle {
                    Text(subtitle)
                        .font(.system(size: 11, design: .rounded))
                        .foregroundColor(.secondary)
                }
            }

            Spacer()

            Toggle("", isOn: $isOn)
                .toggleStyle(.switch)
                .labelsHidden()
        }
    }
}

// MARK: - InfoRow（信息行）

struct InfoRow: View {
    let label: String
    let value: String
    let icon: String?

    init(label: String, value: String, icon: String? = nil) {
        self.label = label
        self.value = value
        self.icon = icon
    }

    var body: some View {
        HStack(spacing: 10) {
            if let icon = icon {
                Image(systemName: icon)
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
                    .frame(width: 16)
            }

            Text(label)
                .font(.system(size: 13, design: .rounded))
                .foregroundColor(.secondary)

            Spacer()

            Text(value)
                .font(.system(size: 13, weight: .medium, design: .rounded))
                .foregroundColor(.primary)
        }
    }
}

// MARK: - LoadingStateView（加载状态）

struct LoadingStateView: View {
    let message: String

    private let brandBlue = AppColors.primary

    var body: some View {
        VStack(spacing: 16) {
            Spacer()

            ProgressView()
                .scaleEffect(1.2)
                .tint(brandBlue)

            Text(message)
                .font(.system(size: 13, design: .rounded))
                .foregroundColor(.secondary)

            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - EmptyStateView（空状态）

struct EmptyStateView: View {
    let icon: String
    let title: String
    let subtitle: String

    private let brandBlue = AppColors.primary

    var body: some View {
        VStack(spacing: 16) {
            Spacer()

            ZStack {
                RoundedRectangle(cornerRadius: 20)
                    .fill(brandBlue.opacity(0.06))
                    .frame(width: 80, height: 80)

                Image(systemName: icon)
                    .font(.system(size: 32, weight: .light))
                    .foregroundColor(brandBlue.opacity(0.3))
            }

            VStack(spacing: 4) {
                Text(title)
                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                    .foregroundColor(.secondary)

                Text(subtitle)
                    .font(.system(size: 12, design: .rounded))
                    .foregroundColor(.secondary.opacity(0.6))
            }

            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

