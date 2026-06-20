//
//  DesignSystem.swift
//  PrivateCloudDisk-macos
//
//  企业级设计系统（与 iOS 端统一）
//  统一管理颜色、字体、间距、圆角、阴影、渐变等设计令牌
//  灵感来源：百度网盘／夸克网盘，风格：简约现代
//  主色调：深邃蓝 #2563EB（与 iOS 客户端完全统一）
//

import SwiftUI

// MARK: - 颜色系统

struct AppColors {
    // 品牌主色 - 深邃蓝，专业稳重（与 iOS 端完全一致）
    static let primary = Color(hex: "#2563EB")       // 主色
    static let primaryLight = Color(hex: "#3B82F6")  // 浅主色
    static let primaryDark = Color(hex: "#1D4ED8")   // 深主色
    static let primaryBg = Color(hex: "#EFF6FF")     // 主色背景

    // 功能色
    static let success = Color(hex: "#10B981")
    static let warning = Color(hex: "#F59E0B")
    static let danger = Color(hex: "#EF4444")
    static let info = Color(hex: "#6366F1")

    static let successBg = Color(hex: "#ECFDF5")
    static let warningBg = Color(hex: "#FFFBEB")
    static let dangerBg = Color(hex: "#FEF2F2")
    static let infoBg = Color(hex: "#EEF2FF")

    // 中性色
    static let textPrimary = Color(hex: "#111827")
    static let textSecondary = Color(hex: "#6B7280")
    static let textTertiary = Color(hex: "#9CA3AF")
    static let textPlaceholder = Color(hex: "#D1D5DB")

    // 背景色
    static let background = Color(hex: "#F9FAFB")
    static let surface = Color.white
    static let surfaceSecondary = Color(hex: "#F3F4F6")
    static let surfaceTertiary = Color(hex: "#E5E7EB")

    // 分隔线
    static let divider = Color(hex: "#E5E7EB")
    static let dividerLight = Color(hex: "#F3F4F6")

    // 文件类型色
    static let fileFolder = Color(hex: "#3B82F6")
    static let fileVideo = Color(hex: "#8B5CF6")
    static let fileImage = Color(hex: "#10B981")
    static let fileAudio = Color(hex: "#F59E0B")
    static let fileDocument = Color(hex: "#6366F1")
    static let filePDF = Color(hex: "#EF4444")
    static let fileArchive = Color(hex: "#EC4899")
    static let fileCode = Color(hex: "#14B8A6")
    static let fileUnknown = Color(hex: "#9CA3AF")

    // 启动页专用深色
    static let splashBg1 = Color(hex: "#0A0A1A")
    static let splashBg2 = Color(hex: "#060612")
    static let splashBg3 = Color(hex: "#040410")
}

// MARK: - 渐变系统

struct AppGradients {
    /// 品牌主渐变（蓝→紫）
    static let primary = LinearGradient(
        colors: [
            Color(hex: "#2563EB"),
            Color(hex: "#6366F1"),
        ],
        startPoint: .leading,
        endPoint: .trailing
    )

    /// 品牌扩展渐变（蓝→紫→品红，用于启动页等大块面）
    static let primaryExtended = LinearGradient(
        colors: [
            Color(hex: "#2563EB"),
            Color(hex: "#6366F1"),
            Color(hex: "#A855F7"),
        ],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )

    /// 头像渐变
    static let avatar = LinearGradient(
        colors: [
            Color(hex: "#2563EB"),
            Color(hex: "#8B5CF6"),
        ],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )

    /// 启动页暗色背景渐变
    static let splashBackground = LinearGradient(
        colors: [
            Color(hex: "#0A0A1A"),
            Color(hex: "#060612"),
            Color(hex: "#040410"),
        ],
        startPoint: .top,
        endPoint: .bottom
    )
}

// MARK: - 字体系统

struct AppTypography {
    // 标题
    static let largeTitle = Font.system(size: 34, weight: .bold, design: .rounded)
    static let title1 = Font.system(size: 28, weight: .bold, design: .rounded)
    static let title2 = Font.system(size: 22, weight: .semibold, design: .rounded)
    static let title3 = Font.system(size: 20, weight: .semibold, design: .rounded)

    // 正文
    static let headline = Font.system(size: 17, weight: .semibold, design: .rounded)
    static let body = Font.system(size: 14, weight: .regular, design: .rounded)
    static let callout = Font.system(size: 13, weight: .regular, design: .rounded)
    static let subheadline = Font.system(size: 12, weight: .regular, design: .rounded)

    // 辅助文字
    static let footnote = Font.system(size: 11, weight: .regular, design: .rounded)
    static let caption1 = Font.system(size: 10, weight: .regular, design: .rounded)
    static let caption2 = Font.system(size: 9, weight: .regular, design: .rounded)

    // 等宽数字
    static let monospacedTitle = Font.system(size: 17, weight: .semibold, design: .monospaced)
    static let monospacedBody = Font.system(size: 14, weight: .regular, design: .monospaced)
    static let monospacedCaption = Font.system(size: 12, weight: .regular, design: .monospaced)
}

// MARK: - 间距系统

struct AppSpacing {
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 20
    static let xxl: CGFloat = 24
    static let xxxl: CGFloat = 32
}

// MARK: - 圆角系统

struct AppRadius {
    static let sm: CGFloat = 6
    static let md: CGFloat = 10
    static let lg: CGFloat = 14
    static let xl: CGFloat = 20
    static let xxl: CGFloat = 24
    static let full: CGFloat = 9999
}

// MARK: - 阴影系统

struct AppShadow {
    static let sm = ShadowConfig(color: .black.opacity(0.04), radius: 4, y: 2)
    static let md = ShadowConfig(color: .black.opacity(0.06), radius: 8, y: 4)
    static let lg = ShadowConfig(color: .black.opacity(0.08), radius: 16, y: 8)
    static let xl = ShadowConfig(color: .black.opacity(0.10), radius: 24, y: 12)
}

struct ShadowConfig {
    let color: Color
    let radius: CGFloat
    let y: CGFloat
    let x: CGFloat

    init(color: Color, radius: CGFloat, y: CGFloat, x: CGFloat = 0) {
        self.color = color
        self.radius = radius
        self.y = y
        self.x = x
    }
}

// MARK: - 动画系统

struct AppAnimation {
    static let `default` = Animation.easeInOut(duration: 0.25)
    static let spring = Animation.spring(response: 0.35, dampingFraction: 0.75)
    static let snappy = Animation.spring(response: 0.3, dampingFraction: 0.85)
    static let slow = Animation.easeInOut(duration: 0.4)
}

// MARK: - Color HEX 扩展

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 6:
            (a, r, g, b) = (255, (int >> 16) & 0xFF, (int >> 8) & 0xFF, int & 0xFF)
        case 8:
            (a, r, g, b) = ((int >> 24) & 0xFF, (int >> 16) & 0xFF, (int >> 8) & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (255, 0, 0, 0)
        }
        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue: Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}

// MARK: - View 扩展

extension View {
    /// 卡片样式
    func appCard(
        padding: CGFloat = AppSpacing.lg,
        radius: CGFloat = AppRadius.lg,
        shadow: ShadowConfig = AppShadow.sm
    ) -> some View {
        self
            .padding(padding)
            .background(AppColors.surface)
            .clipShape(RoundedRectangle(cornerRadius: radius))
            .shadow(color: shadow.color, radius: shadow.radius, x: shadow.x, y: shadow.y)
    }

    /// 内联卡片（无阴影）
    func appInlineCard(
        padding: CGFloat = AppSpacing.lg,
        radius: CGFloat = AppRadius.md
    ) -> some View {
        self
            .padding(padding)
            .background(AppColors.surfaceSecondary)
            .clipShape(RoundedRectangle(cornerRadius: radius))
    }
}