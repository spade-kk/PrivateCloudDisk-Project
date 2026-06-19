import Cocoa
import SwiftUI
import UserNotifications
import ServiceManagement

/// 应用生命周期代理
///
/// 通过 NSApplicationDelegateAdaptor 桥接到 SwiftUI @main App，
/// 负责：
/// - 应用生命周期管理（启动、退出、单实例锁）
/// - 菜单栏与 Dock 集成
/// - URL Scheme 处理（pcd://）
/// - 通知注册与处理
/// - 开机启动管理
/// - 文件提供者扩展协调
///
/// 窗口管理已迁移至 SwiftUI App 的 WindowGroup 处理。
final class AppDelegate: NSObject, NSApplicationDelegate, UNUserNotificationCenterDelegate {

    // MARK: - 全局服务单例

    private var menuBarManager: MenuBarManager!
    private var dockManager: DockManager!
    private var notificationManager: NotificationManager!
    private var loginItemManager: LoginItemManager!
    private var virtualDiskManager: VirtualDiskManager!
    private var authService: AuthService!
    private var urlSchemeHandler: URLSchemeHandler!

    // MARK: - NSApplicationDelegate

    func applicationDidFinishLaunching(_ notification: Notification) {
        // ── 1. 单实例检查 ──
        guard checkSingleInstance() else {
            NSApp.terminate(nil)
            return
        }

        // ── 2. 设置主菜单 ──
        NSApp.mainMenu = MainMenuBuilder.build()

        // ── 3. 初始化核心服务 ──
        initializeServices()

        // ── 4. 注册 URL Scheme 处理 ──
        NSAppleEventManager.shared().setEventHandler(
            self,
            andSelector: #selector(handleGetURLEvent(_:withReplyEvent:)),
            forEventClass: AEEventClass(kInternetEventClass),
            andEventID: AEEventID(kAEGetURL)
        )

        // ── 5. 注册通知 ──
        UNUserNotificationCenter.current().delegate = self
        notificationManager.requestAuthorization()
    }

    func applicationWillTerminate(_ notification: Notification) {
        // 清理资源
        virtualDiskManager.unmount()
        menuBarManager.remove()
        dockManager.reset()
        KeychainManager.shared.clearSessionSensitiveData()
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        // macOS 习惯：关闭窗口不退出应用（菜单栏图标保留）
        return false
    }

    func applicationShouldHandleReopen(_ sender: NSApplication, hasVisibleWindows flag: Bool) -> Bool {
        if !flag {
            // SwiftUI WindowGroup 会自动处理窗口重新打开
            // 这里只需激活应用
            NSApp.activate(ignoringOtherApps: true)
        }
        return true
    }

    // MARK: - 通知代理

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        // 应用在前台时也显示通知横幅
        completionHandler([.banner, .sound, .badge])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        // 用户点击通知 → 打开对应页面
        let userInfo = response.notification.request.content.userInfo
        handleNotificationAction(userInfo: userInfo)
        completionHandler()
    }

    // MARK: - URL Scheme 处理

    @objc private func handleGetURLEvent(_ event: NSAppleEventDescriptor, withReplyEvent: NSAppleEventDescriptor) {
        guard let urlString = event.paramDescriptor(forKeyword: keyDirectObject)?.stringValue,
              let url = URL(string: urlString) else { return }
        urlSchemeHandler.handle(url: url)
    }

    // MARK: - 私有方法

    private func checkSingleInstance() -> Bool {
        let bundleId = Bundle.main.bundleIdentifier ?? "com.privateclouddisk.app"
        let runningApps = NSRunningApplication.runningApplications(withBundleIdentifier: bundleId)
        if runningApps.count > 1 {
            // 激活已有实例
            runningApps.first { $0 != NSRunningApplication.current }?.activate(options: .activateIgnoringOtherApps)
            return false
        }
        return true
    }

    private func initializeServices() {
        // 网络层
        authService = AuthService.shared

        // 安全层
        _ = KeychainManager.shared

        // 系统集成
        menuBarManager = MenuBarManager()
        dockManager = DockManager.shared
        notificationManager = NotificationManager.shared
        loginItemManager = LoginItemManager()
        urlSchemeHandler = URLSchemeHandler()

        // 虚拟磁盘
        virtualDiskManager = VirtualDiskManager.shared
    }

    // MARK: - 通知动作处理

    private func handleNotificationAction(userInfo: [AnyHashable: Any]) {
        guard let action = userInfo["action"] as? String else { return }

        DispatchQueue.main.async {
            switch action {
            case "upload_complete":
                NSApp.activate(ignoringOtherApps: true)
            case "share_received":
                NSApp.activate(ignoringOtherApps: true)
                let urlStr = userInfo["share_url"] as? String
                if let url = urlStr, let shareURL = URL(string: url) {
                    self.urlSchemeHandler.handle(url: shareURL)
                }
            case "storage_alert":
                NSApp.activate(ignoringOtherApps: true)
            default:
                NSApp.activate(ignoringOtherApps: true)
            }
        }
    }
}