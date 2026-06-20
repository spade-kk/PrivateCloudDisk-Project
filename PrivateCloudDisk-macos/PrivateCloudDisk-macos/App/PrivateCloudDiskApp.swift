import SwiftUI
import AppKit

// MARK: - 应用入口（企业级 SwiftUI App）

/// PrivateCloudDisk macOS 原生应用入口
///
/// 企业级 UI 设计：
/// - 无边框窗口 + 隐藏标题栏，自定义 Traffic Light 按钮（品牌色设计）
/// - 全屏品牌启动页，隐藏系统红绿灯
/// - 窗口圆角 + 自定义拖拽区域
/// - 参考百度网盘、夸克网盘等企业级 macOS 应用设计
/// - 毛玻璃侧边栏 + 现代卡片式布局
@main
struct PrivateCloudDiskApp: App {

    // MARK: - AppDelegate 桥接

    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    // MARK: - 全局状态

    @StateObject private var authService = AuthService.shared
    @StateObject private var virtualDiskManager = VirtualDiskManager.shared

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(authService)
                .environmentObject(virtualDiskManager)
                .frame(minWidth: 960, minHeight: 640)
                .onAppear {
                    configureWindow()
                }
        }
        .windowStyle(.hiddenTitleBar)
        .windowToolbarStyle(.unifiedCompact)
        .defaultSize(width: 1280, height: 820)
        .windowResizability(.contentMinSize)
        .commands {
            CommandGroup(replacing: .newItem) { }
            CommandGroup(replacing: .toolbar) { }
        }
    }

    // MARK: - 窗口配置

    private func configureWindow() {
        DispatchQueue.main.async {
            guard let window = NSApp.windows.first(where: { $0.isKeyWindow || $0.identifier?.rawValue == "main" })
                ?? NSApp.windows.first
            else { return }

            // 窗口标识
            window.identifier = NSUserInterfaceItemIdentifier("main")

            // ── 标题栏配置 ──
            window.titlebarAppearsTransparent = true
            window.title = ""
            window.titlebarSeparatorStyle = .none

            // ── 窗口外观 ──
            window.backgroundColor = .windowBackgroundColor
            window.appearance = NSApp.effectiveAppearance

            // ── 窗口行为 ──
            window.collectionBehavior = [.fullScreenPrimary, .fullScreenAllowsTiling]
            window.level = .normal
            window.minSize = NSSize(width: 960, height: 640)

            // ── 窗口圆角（macOS 15+ 风格） ──
            if #available(macOS 15.0, *) {
                // macOS 15 默认已有圆角，无需额外设置
            }

            // ── 自定义 Traffic Light 按钮 ──
            window.configureTrafficLightButtons()

            // ── 居中显示 ──
            window.center()
            window.makeKeyAndOrderFront(nil)
        }
    }
}

// MARK: - 窗口扩展：自定义 Traffic Light 按钮

extension NSWindow {

    /// 隐藏窗口的交通灯按钮（关闭/最小化/全屏）
    func hideTrafficLightButtons() {
        standardWindowButton(.closeButton)?.isHidden = true
        standardWindowButton(.miniaturizeButton)?.isHidden = true
        standardWindowButton(.zoomButton)?.isHidden = true
    }

    /// 显示窗口的交通灯按钮
    func showTrafficLightButtons() {
        standardWindowButton(.closeButton)?.isHidden = false
        standardWindowButton(.miniaturizeButton)?.isHidden = false
        standardWindowButton(.zoomButton)?.isHidden = false
    }

    /// 自定义交通灯按钮位置和样式
    /// 参考百度网盘 macOS 客户端：紧凑排列，垂直居中，与侧边栏标题对齐
    func configureTrafficLightButtons() {
        let buttons: [(NSWindow.ButtonType, String)] = [
            (.closeButton, "close"),
            (.miniaturizeButton, "miniaturize"),
            (.zoomButton, "zoom"),
        ]

        for (index, (type, _)) in buttons.enumerated() {
            guard let button = standardWindowButton(type) else { continue }

            // 获取父视图高度以垂直居中
            let superviewHeight = button.superview?.bounds.height ?? 44

            // 垂直居中（与侧边栏头部对齐）
            button.frame.origin.y = (superviewHeight / 2) - (button.frame.height / 2) - 1

            // 水平间距：紧凑排列，左侧留白与侧边栏 padding 对齐
            button.frame.origin.x = 12 + CGFloat(index) * 20
        }
    }
}

// MARK: - 自定义 Traffic Light 按钮视图（SwiftUI 版本）

/// 品牌色自定义窗口控制按钮
/// 用于替换原生红绿灯，提供更统一的设计语言
struct CustomTrafficLightButtons: View {
    let onClose: () -> Void
    let onMinimize: () -> Void
    let onZoom: () -> Void

    @State private var hoveredButton: ButtonType?

    enum ButtonType {
        case close, minimize, zoom
    }

    private let brandBlue = AppColors.primary

    var body: some View {
        HStack(spacing: 8) {
            // 关闭按钮
            trafficLightButton(
                type: .close,
                baseColor: Color(red: 1.0, green: 0.38, blue: 0.35),
                hoverColor: Color(red: 1.0, green: 0.35, blue: 0.32),
                symbol: "xmark",
                action: onClose
            )

            // 最小化按钮
            trafficLightButton(
                type: .minimize,
                baseColor: Color(red: 1.0, green: 0.75, blue: 0.18),
                hoverColor: Color(red: 1.0, green: 0.72, blue: 0.15),
                symbol: "minus",
                action: onMinimize
            )

            // 全屏按钮
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