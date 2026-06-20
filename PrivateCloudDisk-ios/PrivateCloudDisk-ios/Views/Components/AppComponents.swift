//
//  AppComponents.swift
//  PrivateCloudDisk-ios
//
//  企业级可复用组件库
//  包含：按钮、卡片、徽章、文件图标、空状态、搜索栏、进度条等
//

import SwiftUI

// MARK: - 主按钮

struct AppPrimaryButton: View {
    let title: String
    let icon: String?
    let isLoading: Bool
    let action: () -> Void

    init(
        _ title: String,
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
                        .tint(.white)
                        .scaleEffect(0.8)
                } else if let icon = icon {
                    Image(systemName: icon)
                        .font(.subheadline.weight(.medium))
                }
                Text(title)
                    .font(AppTypography.headline)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 15)
            .background(AppColors.primary)
            .foregroundColor(.white)
            .clipShape(RoundedRectangle(cornerRadius: AppRadius.md))
            .shadow(color: AppColors.primary.opacity(0.25), radius: 8, y: 4)
        }
        .disabled(isLoading)
    }
}

// MARK: - 次级按钮

struct AppSecondaryButton: View {
    let title: String
    let icon: String?
    let action: () -> Void

    init(_ title: String, icon: String? = nil, action: @escaping () -> Void) {
        self.title = title
        self.icon = icon
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let icon = icon {
                    Image(systemName: icon)
                        .font(.subheadline.weight(.medium))
                }
                Text(title)
                    .font(AppTypography.subheadline.weight(.medium))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(AppColors.primaryBg)
            .foregroundColor(AppColors.primary)
            .clipShape(RoundedRectangle(cornerRadius: AppRadius.md))
            .overlay(
                RoundedRectangle(cornerRadius: AppRadius.md)
                    .stroke(AppColors.primary.opacity(0.2), lineWidth: 1)
            )
        }
    }
}

// MARK: - 危险按钮

struct AppDestructiveButton: View {
    let title: String
    let icon: String?
    let action: () -> Void

    init(_ title: String, icon: String? = nil, action: @escaping () -> Void) {
        self.title = title
        self.icon = icon
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let icon = icon {
                    Image(systemName: icon)
                        .font(.subheadline.weight(.medium))
                }
                Text(title)
                    .font(AppTypography.subheadline.weight(.medium))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(AppColors.dangerBg)
            .foregroundColor(AppColors.danger)
            .clipShape(RoundedRectangle(cornerRadius: AppRadius.md))
            .overlay(
                RoundedRectangle(cornerRadius: AppRadius.md)
                    .stroke(AppColors.danger.opacity(0.2), lineWidth: 1)
            )
        }
    }
}

// MARK: - 图标按钮（小）

struct AppIconButton: View {
    let icon: String
    let color: Color
    let action: () -> Void

    init(_ icon: String, color: Color = AppColors.textSecondary, action: @escaping () -> Void) {
        self.icon = icon
        self.color = color
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.subheadline)
                .foregroundColor(color)
                .frame(width: 36, height: 36)
                .background(color.opacity(0.08))
                .clipShape(Circle())
        }
    }
}

// MARK: - 状态徽章

struct AppBadge: View {
    enum Style {
        case success, warning, danger, info, neutral
        case custom(bg: Color, fg: Color)

        var bgColor: Color {
            switch self {
            case .success: return AppColors.successBg
            case .warning: return AppColors.warningBg
            case .danger: return AppColors.dangerBg
            case .info: return AppColors.infoBg
            case .neutral: return AppColors.surfaceSecondary
            case .custom(let bg, _): return bg
            }
        }
        var fgColor: Color {
            switch self {
            case .success: return AppColors.success
            case .warning: return AppColors.warning
            case .danger: return AppColors.danger
            case .info: return AppColors.info
            case .neutral: return AppColors.textSecondary
            case .custom(_, let fg): return fg
            }
        }
    }

    let text: String
    let style: Style

    var body: some View {
        Text(text)
            .font(AppTypography.caption1.weight(.medium))
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(style.bgColor)
            .foregroundColor(style.fgColor)
            .clipShape(Capsule())
    }
}

// MARK: - 文件图标

struct FileIcon: View {
    let node: FileNode
    var size: CGFloat = 44

    private var iconName: String {
        if node.isFolder { return "folder.fill" }
        if node.isVideo { return "play.rectangle.fill" }
        if node.isImage { return "photo.fill" }
        if node.isAudio { return "music.note.list" }
        if node.isPDF { return "doc.richtext.fill" }
        if node.isDocument { return "doc.text.fill" }
        if node.isArchive { return "archivebox.fill" }
        if node.isCode { return "chevron.left.forwardslash.chevron.right" }
        return "doc.fill"
    }

    private var iconColor: Color {
        if node.isFolder { return AppColors.fileFolder }
        if node.isVideo { return AppColors.fileVideo }
        if node.isImage { return AppColors.fileImage }
        if node.isAudio { return AppColors.fileAudio }
        if node.isPDF { return AppColors.filePDF }
        if node.isDocument { return AppColors.fileDocument }
        if node.isArchive { return AppColors.fileArchive }
        if node.isCode { return AppColors.fileCode }
        return AppColors.fileUnknown
    }

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.22)
                .fill(iconColor.opacity(0.1))
                .frame(width: size, height: size)

            Image(systemName: iconName)
                .font(.system(size: size * 0.45))
                .foregroundColor(iconColor)
        }
    }
}

// MARK: - 空状态视图

struct AppEmptyState: View {
    let icon: String
    let title: String
    let message: String
    let actionTitle: String?
    let action: (() -> Void)?

    init(
        icon: String,
        title: String,
        message: String,
        actionTitle: String? = nil,
        action: (() -> Void)? = nil
    ) {
        self.icon = icon
        self.title = title
        self.message = message
        self.actionTitle = actionTitle
        self.action = action
    }

    var body: some View {
        VStack(spacing: AppSpacing.lg) {
            Spacer()

            Image(systemName: icon)
                .font(.system(size: 56))
                .foregroundColor(AppColors.textTertiary)

            VStack(spacing: AppSpacing.sm) {
                Text(title)
                    .font(AppTypography.headline)
                    .foregroundColor(AppColors.textPrimary)

                Text(message)
                    .font(AppTypography.subheadline)
                    .foregroundColor(AppColors.textSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 40)
            }

            if let actionTitle = actionTitle, let action = action {
                AppSecondaryButton(actionTitle) {
                    action()
                }
                .padding(.horizontal, 60)
                .padding(.top, AppSpacing.sm)
            }

            Spacer()
        }
    }
}

// MARK: - 搜索栏

struct AppSearchBar: View {
    @Binding var text: String
    var placeholder: String = "搜索"

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .font(.subheadline)
                .foregroundColor(AppColors.textTertiary)

            TextField(placeholder, text: $text)
                .font(AppTypography.subheadline)
                .foregroundColor(AppColors.textPrimary)

            if !text.isEmpty {
                Button(action: {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        text = ""
                    }
                }) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.subheadline)
                        .foregroundColor(AppColors.textTertiary)
                }
            }
        }
        .padding(.horizontal, AppSpacing.md)
        .padding(.vertical, 10)
        .background(AppColors.surfaceSecondary)
        .clipShape(RoundedRectangle(cornerRadius: AppRadius.md))
    }
}

// MARK: - 进度条

struct AppProgressBar: View {
    let progress: Double
    var height: CGFloat = 4
    var tint: Color = AppColors.primary

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                RoundedRectangle(cornerRadius: height / 2)
                    .fill(AppColors.surfaceTertiary)
                    .frame(height: height)

                RoundedRectangle(cornerRadius: height / 2)
                    .fill(tint)
                    .frame(width: geo.size.width * min(max(progress, 0), 1), height: height)
                    .animation(.easeInOut(duration: 0.3), value: progress)
            }
        }
        .frame(height: height)
    }
}

// MARK: - 信息行

struct AppInfoRow: View {
    let icon: String
    let label: String
    let value: String
    var iconColor: Color = AppColors.textSecondary

    var body: some View {
        HStack(spacing: AppSpacing.md) {
            Image(systemName: icon)
                .font(.subheadline)
                .foregroundColor(iconColor)
                .frame(width: 22)

            Text(label)
                .font(AppTypography.subheadline)
                .foregroundColor(AppColors.textSecondary)

            Spacer()

            Text(value)
                .font(AppTypography.subheadline)
                .foregroundColor(AppColors.textPrimary)
                .lineLimit(1)
                .multilineTextAlignment(.trailing)
        }
        .padding(.horizontal, AppSpacing.lg)
        .padding(.vertical, 13)
    }
}

// MARK: - 分段控制器

struct AppSegmentedPicker<T: Hashable & CaseIterable>: View where T: RawRepresentable, T.RawValue == String {
    @Binding var selection: T
    let cases: [T]

    var body: some View {
        HStack(spacing: 2) {
            ForEach(Array(cases.enumerated()), id: \.offset) { _, item in
                Button(action: {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        selection = item
                    }
                }) {
                    Text(item.rawValue)
                        .font(AppTypography.footnote.weight(.medium))
                        .padding(.horizontal, AppSpacing.lg)
                        .padding(.vertical, 8)
                        .frame(maxWidth: .infinity)
                        .background(selection == item ? AppColors.surface : Color.clear)
                        .foregroundColor(selection == item ? AppColors.primary : AppColors.textSecondary)
                        .clipShape(Capsule())
                }
            }
        }
        .padding(3)
        .background(AppColors.surfaceSecondary)
        .clipShape(Capsule())
    }
}

// MARK: - Toast 提示

struct AppToast: ViewModifier {
    @Binding var isPresented: Bool
    let message: String
    let duration: TimeInterval

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .top) {
                if isPresented {
                    Text(message)
                        .font(AppTypography.subheadline.weight(.medium))
                        .foregroundColor(.white)
                        .padding(.horizontal, AppSpacing.lg)
                        .padding(.vertical, AppSpacing.md)
                        .background(AppColors.textPrimary.opacity(0.85))
                        .clipShape(Capsule())
                        .shadow(color: .black.opacity(0.15), radius: 10, y: 4)
                        .padding(.top, 60)
                        .transition(.move(edge: .top).combined(with: .opacity))
                        .onAppear {
                            DispatchQueue.main.asyncAfter(deadline: .now() + duration) {
                                withAnimation(.easeInOut(duration: 0.3)) {
                                    isPresented = false
                                }
                            }
                        }
                }
            }
            .animation(.easeInOut(duration: 0.3), value: isPresented)
    }
}

extension View {
    func appToast(isPresented: Binding<Bool>, message: String, duration: TimeInterval = 2.0) -> some View {
        modifier(AppToast(isPresented: isPresented, message: message, duration: duration))
    }
}

// MARK: - Loading Overlay

struct AppLoadingOverlay: ViewModifier {
    let isLoading: Bool
    let message: String

    func body(content: Content) -> some View {
        content
            .overlay {
                if isLoading {
                    ZStack {
                        Color.black.opacity(0.15)
                            .ignoresSafeArea()

                        VStack(spacing: AppSpacing.md) {
                            ProgressView()
                                .scaleEffect(1.2)
                                .tint(AppColors.primary)

                            Text(message)
                                .font(AppTypography.subheadline)
                                .foregroundColor(AppColors.textSecondary)
                        }
                        .padding(AppSpacing.xxl)
                        .background(AppColors.surface)
                        .clipShape(RoundedRectangle(cornerRadius: AppRadius.xl))
                        .shadow(color: .black.opacity(0.1), radius: 20, y: 10)
                    }
                }
            }
            .animation(.easeInOut(duration: 0.25), value: isLoading)
    }
}

extension View {
    func appLoadingOverlay(isLoading: Bool, message: String = "加载中...") -> some View {
        modifier(AppLoadingOverlay(isLoading: isLoading, message: message))
    }
}

// MARK: - 预览

#Preview("Components") {
    ScrollView {
        VStack(spacing: 20) {
            AppPrimaryButton("主要按钮", icon: "icloud.and.arrow.up") {}
            AppSecondaryButton("次要按钮", icon: "folder") {}
            AppDestructiveButton("删除", icon: "trash") {}
            AppBadge(text: "已激活", style: .success)
            AppBadge(text: "已过期", style: .danger)
            AppBadge(text: "待处理", style: .warning)
            AppSearchBar(text: .constant(""))
            AppProgressBar(progress: 0.6)
            AppEmptyState(
                icon: "folder",
                title: "暂无文件",
                message: "点击右上角上传文件",
                actionTitle: "上传文件",
                action: {}
            )
        }
        .padding()
    }
    .background(AppColors.background)
}