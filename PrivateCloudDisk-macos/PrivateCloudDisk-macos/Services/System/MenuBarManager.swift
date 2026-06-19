import Cocoa
import Combine

// MARK: - 菜单栏管理器

/// 菜单栏管理器
///
/// 对应 Windows 的 SystemTrayService，在 macOS 菜单栏右侧显示应用图标
///
/// macOS 菜单栏（NSStatusBar）对比 Windows 系统托盘的优势：
/// - 始终可见，无需点击展开箭头
/// - 支持拖拽排序
/// - 支持 NSMenu 全部功能（子菜单、快捷键、图片、状态指示）
/// - 支持 NSStatusBarButton 自定义视图
/// - 与系统深色/浅色模式自动适配
final class MenuBarManager: ObservableObject {

    private var statusItem: NSStatusItem!
    private var menu: NSMenu!

    // MARK: - 初始化

    init() {
        setupStatusItem()
        buildMenu()
    }

    private func setupStatusItem() {
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)

        if let button = statusItem.button {
            // 使用 SF Symbol（macOS 原生图标系统）
            button.image = NSImage(
                systemSymbolName: "externaldrive.fill.badge.icloud",
                accessibilityDescription: "PrivateCloudDisk"
            )
            button.image?.isTemplate = true // 自动适配深色模式
            button.toolTip = "PrivateCloudDisk"
            button.imagePosition = .imageLeading
        }
    }

    private func buildMenu() {
        menu = NSMenu()

        // ── 状态区域 ──
        let statusItem = NSMenuItem(
            title: "PrivateCloudDisk",
            action: nil,
            keyEquivalent: ""
        )
        statusItem.isEnabled = false
        statusItem.image = NSImage(
            systemSymbolName: "externaldrive.fill.badge.icloud",
            accessibilityDescription: nil
        )
        menu.addItem(statusItem)

        let storageItem = NSMenuItem(
            title: "存储空间: 正在加载...",
            action: nil,
            keyEquivalent: ""
        )
        storageItem.isEnabled = false
        storageItem.identifier = NSUserInterfaceItemIdentifier("storageStatus")
        menu.addItem(storageItem)

        menu.addItem(.separator())

        // ── 快捷操作 ──
        menu.addItem(NSMenuItem(
            title: "打开 PrivateCloudDisk",
            action: #selector(openMainWindow),
            keyEquivalent: ""
        ))

        menu.addItem(.separator())

        // ── 虚拟磁盘 ──
        let mountItem = NSMenuItem(
            title: "挂载虚拟磁盘",
            action: #selector(toggleMount),
            keyEquivalent: ""
        )
        mountItem.identifier = NSUserInterfaceItemIdentifier("mountItem")
        menu.addItem(mountItem)

        menu.addItem(.separator())

        // ── 上传 ──
        menu.addItem(NSMenuItem(
            title: "上传文件...",
            action: #selector(uploadFile),
            keyEquivalent: ""
        ))

        menu.addItem(.separator())

        // ── 同步状态 ──
        let syncItem = NSMenuItem(
            title: "同步状态: 未挂载",
            action: nil,
            keyEquivalent: ""
        )
        syncItem.isEnabled = false
        syncItem.identifier = NSUserInterfaceItemIdentifier("syncStatus")
        menu.addItem(syncItem)

        menu.addItem(.separator())

        // ── 偏好设置和退出 ──
        menu.addItem(NSMenuItem(
            title: "偏好设置...",
            action: #selector(openPreferences),
            keyEquivalent: ","
        ))

        menu.addItem(NSMenuItem(
            title: "退出 PrivateCloudDisk",
            action: #selector(quitApp),
            keyEquivalent: "q"
        ))

        statusItem.menu = menu
    }

    // MARK: - 更新方法

    func updateStorageStatus(used: Int64, total: Int64) {
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        let usedStr = formatter.string(fromByteCount: used)
        let totalStr = formatter.string(fromByteCount: total)
        if let item = menu.item(withTitle: NSUserInterfaceItemIdentifier("storageStatus").rawValue) {
            item.title = "存储空间: \(usedStr) / \(totalStr)"
        }
    }

    func updateSyncStatus(text: String, icon: String? = nil) {
        if let item = menu.item(withTitle: NSUserInterfaceItemIdentifier("syncStatus").rawValue) {
            item.title = "同步状态: \(text)"
            if let iconName = icon {
                item.image = NSImage(systemSymbolName: iconName, accessibilityDescription: nil)
            }
        }
    }

    func updateMountStatus(isMounted: Bool) {
        if let item = menu.item(withTitle: NSUserInterfaceItemIdentifier("mountItem").rawValue) {
            item.title = isMounted ? "卸载虚拟磁盘" : "挂载虚拟磁盘"
        }
        updateSyncStatus(
            text: isMounted ? "已连接" : "未挂载",
            icon: isMounted ? "externaldrive.fill" : "externaldrive.badge.timemachine"
        )
    }

    // MARK: - 动作

    @objc private func openMainWindow() {
        NSApp.activate(ignoringOtherApps: true)
    }

    @objc private func toggleMount() {
        Task { @MainActor in
            let manager = VirtualDiskManager.shared
            if manager.isMounted {
                manager.unmount()
            } else {
                try? await manager.mount()
            }
        }
    }

    @objc private func uploadFile() {
        let panel = NSOpenPanel()
        panel.canChooseFiles = true
        panel.canChooseDirectories = false
        panel.allowsMultipleSelection = true
        panel.begin { response in
            guard response == .OK else { return }
            for url in panel.urls {
                Task {
                    try? await UploadManager.shared.uploadFile(
                        localURL: url,
                        parentId: nil
                    )
                }
            }
        }
    }

    @objc private func openPreferences() {
        NSApp.activate(ignoringOtherApps: true)
        // 打开偏好设置窗口
        NotificationCenter.default.post(
            name: NSNotification.Name("OpenPreferences"),
            object: nil
        )
    }

    @objc private func quitApp() {
        NSApp.terminate(nil)
    }

    // MARK: - 清理

    func remove() {
        if let statusItem = statusItem {
            NSStatusBar.system.removeStatusItem(statusItem)
        }
    }
}
