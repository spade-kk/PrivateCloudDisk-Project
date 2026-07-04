//
//  DesignSystem.swift
//  PrivateCloudDisk-macos
//
//  企业级设计系统 —— 与 Web 前端（PrivateCloudDisk-web）完全统一
//  主色调：白 + 蓝 #165DFF（与 Web 前端 Tailwind 配置 100% 对齐）
//  参考：百度网盘 macOS 客户端 + Trae 桌面客户端设计风格
//

import SwiftUI

// MARK: - 颜色系统（与 Web 前端 Tailwind 完全对齐）

struct AppColors {
    // ── 品牌主色 ──
    // 与 Web 前端 tailwind.config.ts 中 primary: '#165DFF' 完全一致
    static let primary       = Color(hex: "#165DFF")   // 主色
    static let primaryLight  = Color(hex: "#4080FF")   // 浅主色（hover 态）
    static let primaryDark   = Color(hex: "#0E42D2")   // 深主色（active 态）
    static let primaryBg     = Color(hex: "#E8F0FE")   // 主色背景（10% opacity 等效）

    // ── 辅助色 ──
    // 与 Web 前端 secondary: '#36CFC9' 对齐
    static let secondary     = Color(hex: "#36CFC9")

    // ── 功能色 ──
    // 与 Web 前端 success: '#52C41A', warning: '#FAAD14', danger: '#FF4D4F' 对齐
    static let success       = Color(hex: "#52C41A")
    static let warning       = Color(hex: "#FAAD14")
    static let danger        = Color(hex: "#FF4D4F")
    static let info          = Color(hex: "#165DFF")   // 信息色与主色一致

    static let successBg     = Color(hex: "#F0FBE5")
    static let warningBg     = Color(hex: "#FFFBE6")
    static let dangerBg      = Color(hex: "#FFF0F0")
    static let infoBg        = Color(hex: "#E8F0FE")

    // ── 中性色 ──
    // 与 Web 前端 neutral 色阶对齐
    // neutral-100: #F5F7FA, 200: #E4E7ED, 300: #C0C6CF, 400: #909399, 500: #606266, 600: #303133, 700: #1E1E1E
    static let neutral100    = Color(hex: "#F5F7FA")
    static let neutral200    = Color(hex: "#E4E7ED")
    static let neutral300    = Color(hex: "#C0C6CF")
    static let neutral400    = Color(hex: "#909399")
    static let neutral500    = Color(hex: "#606266")
    static let neutral600    = Color(hex: "#303133")
    static let neutral700    = Color(hex: "#1E1E1E")

    // 文本色（映射到 neutral 色阶）
    static let textPrimary   = Color(hex: "#303133")   // neutral-600
    static let textSecondary = Color(hex: "#606266")   // neutral-500
    static let textTertiary  = Color(hex: "#909399")   // neutral-400
    static let textPlaceholder = Color(hex: "#C0C6CF") // neutral-300

    // ── 背景色 ──
    static let background    = Color(hex: "#F5F7FA")   // 与 Web 前端 bg-neutral-100 一致
    static let surface       = Color.white             // 与 Web 前端 bg-white 一致
    static let surfaceSecondary = Color(hex: "#F5F7FA") // neutral-100

    // ── 分隔线 ──
    static let divider       = Color(hex: "#E4E7ED")   // neutral-200
    static let dividerLight  = Color(hex: "#F5F7FA")   // neutral-100

    // ── 文件类型色 ──
    static let fileFolder    = Color(hex: "#165DFF")   // 蓝色 - 文件夹
    static let fileImage     = Color(hex: "#52C41A")   // 绿色 - 图片
    static let fileVideo     = Color(hex: "#722ED1")   // 紫色 - 视频
    static let fileAudio     = Color(hex: "#FA8C16")   // 橙色 - 音频
    static let fileDocument  = Color(hex: "#165DFF")   // 蓝色 - 文档
    static let filePDF       = Color(hex: "#FF4D4F")   // 红色 - PDF
    static let fileArchive   = Color(hex: "#EB2F96")   // 粉色 - 压缩包
    static let fileCode      = Color(hex: "#13C2C2")   // 青色 - 代码
    static let fileUnknown   = Color(hex: "#909399")   // 灰色 - 未知

    // ── 启动页深色 ──
    static let splashBg1     = Color(hex: "#0A1628")
    static let splashBg2     = Color(hex: "#061020")
    static let splashBg3     = Color(hex: "#040C18")
}

// MARK: - 渐变系统

struct AppGradients {
    /// 品牌主渐变（蓝 → 浅蓝）
    static let primary = LinearGradient(
        colors: [Color(hex: "#165DFF"), Color(hex: "#4080FF")],
        startPoint: .leading,
        endPoint: .trailing
    )

    /// 品牌扩展渐变（用于启动页等）
    static let primaryExtended = LinearGradient(
        colors: [Color(hex: "#165DFF"), Color(hex: "#4080FF"), Color(hex: "#69B1FF")],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )

    /// 头像渐变
    static let avatar = LinearGradient(
        colors: [Color(hex: "#165DFF"), Color(hex: "#4080FF")],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )

    /// 启动页暗色背景
    static let splashBackground = LinearGradient(
        colors: [
            Color(hex: "#0A1628"),
            Color(hex: "#061020"),
            Color(hex: "#040C18"),
        ],
        startPoint: .top,
        endPoint: .bottom
    )
}

// MARK: - 字体系统

struct AppTypography {
    // 标题
    static let largeTitle  = Font.system(size: 28, weight: .bold, design: .default)
    static let title1      = Font.system(size: 22, weight: .bold, design: .default)
    static let title2      = Font.system(size: 18, weight: .semibold, design: .default)
    static let title3      = Font.system(size: 16, weight: .semibold, design: .default)

    // 正文
    static let headline    = Font.system(size: 15, weight: .semibold, design: .default)
    static let body        = Font.system(size: 14, weight: .regular, design: .default)
    static let callout     = Font.system(size: 13, weight: .regular, design: .default)
    static let subheadline = Font.system(size: 12, weight: .regular, design: .default)

    // 辅助
    static let footnote    = Font.system(size: 11, weight: .regular, design: .default)
    static let caption     = Font.system(size: 10, weight: .regular, design: .default)

    // 等宽数字
    static let monospacedBody = Font.system(size: 14, weight: .regular, design: .monospaced)
}

// MARK: - 间距系统

struct AppSpacing {
    static let xs: CGFloat   = 4
    static let sm: CGFloat   = 8
    static let md: CGFloat   = 12
    static let lg: CGFloat   = 16
    static let xl: CGFloat   = 20
    static let xxl: CGFloat  = 24
    static let xxxl: CGFloat = 32
}

// MARK: - 圆角系统（与 Web 前端统一）

struct AppRadius {
    static let sm: CGFloat   = 6
    static let md: CGFloat   = 8
    static let lg: CGFloat   = 12
    static let xl: CGFloat   = 16
    static let xxl: CGFloat  = 20
    static let full: CGFloat = 9999
}

// MARK: - 阴影系统（与 Web 前端 boxShadow 对齐）

struct AppShadow {
    /// 卡片阴影 - 对应 Web 的 shadow-card: '0 2px 12px 0 rgba(0, 0, 0, 0.08)'
    static let card = ShadowConfig(color: .black.opacity(0.08), radius: 12, y: 2)

    /// 悬浮阴影 - 对应 Web 的 shadow-hover: '0 4px 16px 0 rgba(22, 93, 255, 0.15)'
    static let hover = ShadowConfig(color: Color(hex: "#165DFF").opacity(0.15), radius: 16, y: 4)

    /// 轻阴影
    static let sm = ShadowConfig(color: .black.opacity(0.04), radius: 4, y: 1)
    /// 中阴影
    static let md = ShadowConfig(color: .black.opacity(0.06), radius: 8, y: 2)
    /// 重阴影
    static let lg = ShadowConfig(color: .black.opacity(0.10), radius: 16, y: 4)
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
    static let `default` = Animation.easeInOut(duration: 0.2)
    static let spring    = Animation.spring(response: 0.35, dampingFraction: 0.75)
    static let snappy    = Animation.spring(response: 0.25, dampingFraction: 0.85)
    static let slow      = Animation.easeInOut(duration: 0.4)
    static let fast      = Animation.easeInOut(duration: 0.12)
}

// MARK: - 滚动条样式（与 Web 前端统一）

struct AppScrollBarModifier: ViewModifier {
    func body(content: Content) -> some View {
        if #available(macOS 14.0, *) {
            content.scrollIndicators(.hidden)
        } else {
            content
        }
    }
}

extension View {
    func hideScrollIndicators() -> some View {
        modifier(AppScrollBarModifier())
    }
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
    /// 卡片样式（与 Web 前端 shadow-card 对齐）
    func appCard(
        padding: CGFloat = AppSpacing.lg,
        radius: CGFloat = AppRadius.lg
    ) -> some View {
        self
            .padding(padding)
            .background(AppColors.surface)
            .clipShape(RoundedRectangle(cornerRadius: radius))
            .shadow(color: AppShadow.card.color, radius: AppShadow.card.radius, x: AppShadow.card.x, y: AppShadow.card.y)
    }

    /// 悬浮卡片（hover 时增强阴影，与 Web shadow-hover 对齐）
    func appCardHoverable(isHovered: Bool) -> some View {
        self.shadow(
            color: isHovered ? AppShadow.hover.color : AppShadow.card.color,
            radius: isHovered ? AppShadow.hover.radius : AppShadow.card.radius,
            x: 0,
            y: isHovered ? AppShadow.hover.y : AppShadow.card.y
        )
    }
}