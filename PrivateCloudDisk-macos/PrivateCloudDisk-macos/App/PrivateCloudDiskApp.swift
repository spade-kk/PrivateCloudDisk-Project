import SwiftUI
import AppKit

// MARK: - 应用入口（企业级 macOS 应用 — 双窗口模式）

/// PrivateCloudDisk macOS 原生应用
///
/// 窗口模式设计（参考 QQ 微信 百度网盘 网易云音乐）：
/// - **登录窗口**：紧凑固定尺寸 400×520，不可缩放，仅关闭按钮可见
/// - **主窗口**：1280×820，可缩放，完整窗口控制按钮
/// - 登录成功后窗口平滑切换（大小、位置、按钮状态）
///
/// 与 Web 前端 PrivateCloudDisk-web 统一：
/// - 品牌色 #165DFF（Tailwind blue-600）
/// - 白色背景 + 蓝色主题
/// - 毛玻璃侧边栏 + 卡片式内容区
@main
struct PrivateCloudDiskApp: App {

    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    @StateObject private var authService = AuthService.shared
    @StateObject private var virtualDiskManager = VirtualDiskManager.shared

    // MARK: - 窗口尺寸常量

    /// 登录窗口尺寸（参考 QQ Mac 380×400 / 百度网盘 400×440 / 网易云音乐 380×480）
    /// 取 400×520 以容纳 Turnstile 验证状态指示器
    static let loginWindowSize = NSSize(width: 400, height: 520)
    /// 主窗口尺寸
    static let mainWindowSize = NSSize(width: 1280, height: 820)
    /// 主窗口最小尺寸
    static let mainWindowMinSize = NSSize(width: 960, height: 640)

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(authService)
                .environmentObject(virtualDiskManager)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .windowStyle(.hiddenTitleBar)
        .defaultSize(width: Self.loginWindowSize.width, height: Self.loginWindowSize.height)
        .windowResizability(.automatic)
        .commands {
            CommandGroup(replacing: .newItem) { }
            CommandGroup(replacing: .toolbar) { }
        }
    }
}

// MARK: - NSWindow 扩展：窗口模式切换

extension NSWindow {

    /// 切换到主窗口模式（可缩放、完整控制按钮、自定义标题栏）
    func transitionToMainWindow() {
        // 启用缩放
        styleMask.insert(.resizable)

        // 显示所有控制按钮
        standardWindowButton(.closeButton)?.isHidden = false
        standardWindowButton(.miniaturizeButton)?.isHidden = false
        standardWindowButton(.zoomButton)?.isHidden = false

        // 更新尺寸约束
        minSize = PrivateCloudDiskApp.mainWindowMinSize
        maxSize = NSSize(
            width: CGFloat.greatestFiniteMagnitude,
            height: CGFloat.greatestFiniteMagnitude
        )

        // 调整到主窗口尺寸
        let newFrame = NSRect(
            x: frame.midX - PrivateCloudDiskApp.mainWindowSize.width / 2,
            y: frame.midY - PrivateCloudDiskApp.mainWindowSize.height / 2,
            width: PrivateCloudDiskApp.mainWindowSize.width,
            height: PrivateCloudDiskApp.mainWindowSize.height
        )
        setFrame(newFrame, display: true, animate: true)

        // 主窗口行为
        collectionBehavior = [.fullScreenPrimary, .fullScreenAllowsTiling]
        backgroundColor = NSColor(red: 0.96, green: 0.97, blue: 0.98, alpha: 1.0) // #F5F7FA
        isOpaque = false
        titlebarSeparatorStyle = .none

        // 自定义标题栏配置（与登录窗口一致）
        titlebarAppearsTransparent = true
        title = ""

        // 自定义 Traffic Light 按钮位置
        configureTrafficLightButtons()
    }

    /// 切换到登录窗口模式（固定尺寸、不可缩放、仅关闭按钮）
    func transitionToLoginWindow() {
        // 禁用缩放
        styleMask.remove(.resizable)

        // 隐藏最小化和缩放按钮，仅保留关闭按钮
        standardWindowButton(.closeButton)?.isHidden = false
        standardWindowButton(.miniaturizeButton)?.isHidden = true
        standardWindowButton(.zoomButton)?.isHidden = true

        // 固定尺寸
        minSize = PrivateCloudDiskApp.loginWindowSize
        maxSize = PrivateCloudDiskApp.loginWindowSize

        // 调整到登录窗口尺寸
        let newFrame = NSRect(
            x: frame.midX - PrivateCloudDiskApp.loginWindowSize.width / 2,
            y: frame.midY - PrivateCloudDiskApp.loginWindowSize.height / 2,
            width: PrivateCloudDiskApp.loginWindowSize.width,
            height: PrivateCloudDiskApp.loginWindowSize.height
        )
        setFrame(newFrame, display: true, animate: true)

        // 登录窗口行为
        collectionBehavior = []
        backgroundColor = .white
        isOpaque = true
        titlebarSeparatorStyle = .none
    }

    /// 隐藏红绿灯按钮
    func hideTrafficLightButtons() {
        standardWindowButton(.closeButton)?.isHidden = true
        standardWindowButton(.miniaturizeButton)?.isHidden = true
        standardWindowButton(.zoomButton)?.isHidden = true
    }

    /// 显示红绿灯按钮
    func showTrafficLightButtons() {
        standardWindowButton(.closeButton)?.isHidden = false
        standardWindowButton(.miniaturizeButton)?.isHidden = false
        standardWindowButton(.zoomButton)?.isHidden = false
    }

    /// 自定义红绿灯按钮位置（垂直居中于侧边栏头部区域，与品牌 Logo 对齐）
    func configureTrafficLightButtons() {
        let buttons: [NSWindow.ButtonType] = [.closeButton, .miniaturizeButton, .zoomButton]
        for (index, type) in buttons.enumerated() {
            guard let button = standardWindowButton(type) else { continue }
            let superviewHeight = button.superview?.bounds.height ?? 44
            button.frame.origin.y = (superviewHeight / 2) - (button.frame.height / 2) - 1
            button.frame.origin.x = 14 + CGFloat(index) * 20
        }
    }
}

// MARK: - 自定义 Traffic Light 按钮（SwiftUI 品牌色设计）

struct CustomTrafficLightButtons: View {
    let onClose: () -> Void
    let onMinimize: () -> Void
    let onZoom: () -> Void

    @State private var hoveredButton: ButtonType?

    enum ButtonType { case close, minimize, zoom }

    var body: some View {
        HStack(spacing: 8) {
            trafficLightButton(
                type: .close,
                baseColor: Color(red: 1.0, green: 0.38, blue: 0.35),
                hoverColor: Color(red: 1.0, green: 0.35, blue: 0.32),
                symbol: "xmark",
                action: onClose
            )
            trafficLightButton(
                type: .minimize,
                baseColor: Color(red: 1.0, green: 0.75, blue: 0.18),
                hoverColor: Color(red: 1.0, green: 0.72, blue: 0.15),
                symbol: "minus",
                action: onMinimize
            )
            trafficLightButton(
                type: .zoom,
                baseColor: Color(red: 0.15, green: 0.80, blue: 0.35),
                hoverColor: Color(red: 0.12, green: 0.77, blue: 0.32),
                symbol: "arrow.up.left.and.arrow.down.right",
                action: onZoom
            )
        }
    }

    private func trafficLightButton(
        type: ButtonType,
        baseColor: Color,
        hoverColor: Color,
        symbol: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            ZStack {
                Circle()
                    .fill(hoveredButton == type ? hoverColor : baseColor)
                    .frame(width: 12, height: 12)
                    .shadow(color: .black.opacity(0.1), radius: 1, x: 0, y: 0.5)
                if hoveredButton == type {
                    Image(systemName: symbol)
                        .font(.system(size: 5.5, weight: .black))
                        .foregroundColor(.black.opacity(0.4))
                }
            }
        }
        .buttonStyle(.plain)
        .onHover { hovering in
            withAnimation(.easeInOut(duration: 0.15)) {
                hoveredButton = hovering ? type : nil
            }
        }
    }
}