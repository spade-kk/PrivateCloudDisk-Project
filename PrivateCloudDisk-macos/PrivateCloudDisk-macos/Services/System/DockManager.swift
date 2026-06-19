import Cocoa

// MARK: - Dock 管理器

/// Dock 图标管理器
///
/// macOS Dock 特有功能（Windows 无直接对应）：
/// - 动态 Dock 图标（进度条叠加）
/// - 角标（Badge）显示未读消息数
/// - Dock 菜单（右键菜单）
/// - 弹跳通知
/// - 最近项目列表
/// - 快速操作菜单
final class DockManager {

    static let shared = DockManager()

    private init() {}

    // MARK: - 角标

    /// 设置 Dock 角标文本
    func setBadge(_ text: String?) {
        NSApp.dockTile.badgeLabel = text
    }

    /// 清除角标
    func clearBadge() {
        NSApp.dockTile.badgeLabel = nil
    }

    // MARK: - 进度指示

    /// 在 Dock 图标上显示进度条
    func showProgress(_ progress: Double) {
        // macOS 通过 NSDockTile 的 contentView 实现
        // 或使用 NSProgressIndicator 嵌入 dock tile
        let dockTile = NSApp.dockTile
        let progressView = NSProgressIndicator(frame: NSRect(x: 0, y: 0, width: dockTile.size.width, height: 20))
        progressView.isIndeterminate = false
        progressView.minValue = 0
        progressView.maxValue = 1
        progressView.doubleValue = progress
        progressView.style = .bar

        dockTile.contentView = progressView
        dockTile.display()
    }

    /// 清除进度条
    func clearProgress() {
        NSApp.dockTile.contentView = nil
        NSApp.dockTile.display()
    }

    // MARK: - 弹跳通知

    /// 应用弹跳（引起用户注意）
    func bounce(_ type: NSApplication.RequestUserAttentionType = .informationalRequest) {
        NSApp.requestUserAttention(type)
    }

    // MARK: - Dock 菜单

    /// 构建 Dock 右键菜单
    func buildDockMenu() -> NSMenu {
        let menu = NSMenu()

        menu.addItem(NSMenuItem(
            title: "打开 PrivateCloudDisk",
            action: #selector(NSApplicationDelegate.applicationDidBecomeActive(_:)),
            keyEquivalent: ""
        ))

        menu.addItem(.separator())

        let mountItem = NSMenuItem(
            title: "挂载虚拟磁盘",
            action: #selector(toggleMount),
            keyEquivalent: ""
        )
        menu.addItem(mountItem)

        menu.addItem(NSMenuItem(
            title: "上传文件...",
            action: #selector(uploadFile),
            keyEquivalent: ""
        ))

        menu.addItem(.separator())

        menu.addItem(NSMenuItem(
            title: "最近文件",
            action: nil,
            keyEquivalent: ""
        ))

        return menu
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

    // MARK: - 重置

    func reset() {
        clearBadge()
        clearProgress()
    }
}