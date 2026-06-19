import Cocoa
import SwiftUI

/// 应用主菜单栏构建器
///
/// 对应 Windows 的 SystemTrayService 右键菜单功能，
/// 在 macOS 上通过菜单栏提供完整的应用操作入口。
///
/// macOS 菜单栏对比 Windows 系统托盘的优势：
/// - 始终可见，无需点击展开
/// - 支持全局快捷键
/// - 支持 Services 集成
/// - 支持 Touch Bar（在支持的 Mac 上）
final class MainMenuBuilder {

    // MARK: - 构建主菜单

    static func build() -> NSMenu {
        let mainMenu = NSMenu()

        // ── 应用菜单 ──
        let appMenu = NSMenu()
        appMenu.addItem(NSMenuItem(
            title: "关于 PrivateCloudDisk",
            action: #selector(NSApplication.orderFrontStandardAboutPanel(_:)),
            keyEquivalent: ""
        ))
        appMenu.addItem(.separator())
        appMenu.addItem(NSMenuItem(
            title: "偏好设置...",
            action: #selector(NSApp.sendAction(_:to:from:)),
            keyEquivalent: ","
        ))
        appMenu.addItem(.separator())
        appMenu.addItem(NSMenuItem(
            title: "隐藏 PrivateCloudDisk",
            action: #selector(NSApplication.hide(_:)),
            keyEquivalent: "h"
        ))
        appMenu.addItem(NSMenuItem(
            title: "隐藏其他",
            action: #selector(NSApplication.hideOtherApplications(_:)),
            keyEquivalent: "h"
        ) { $0.keyEquivalentModifierMask = [.command, .option] })
        appMenu.addItem(NSMenuItem(
            title: "显示全部",
            action: #selector(NSApplication.unhideAllApplications(_:)),
            keyEquivalent: ""
        ))
        appMenu.addItem(.separator())
        appMenu.addItem(NSMenuItem(
            title: "退出 PrivateCloudDisk",
            action: #selector(NSApplication.terminate(_:)),
            keyEquivalent: "q"
        ))

        let appMenuItem = NSMenuItem()
        appMenuItem.submenu = appMenu
        mainMenu.addItem(appMenuItem)

        // ── 文件菜单 ──
        let fileMenu = NSMenu(title: "文件")
        fileMenu.addItem(NSMenuItem(
            title: "上传文件...",
            action: #selector(NSApp.sendAction(_:to:from:)),
            keyEquivalent: "u"
        ) { $0.keyEquivalentModifierMask = [.command, .shift] })
        fileMenu.addItem(NSMenuItem(
            title: "新建文件夹",
            action: #selector(NSApp.sendAction(_:to:from:)),
            keyEquivalent: "n"
        ) { $0.keyEquivalentModifierMask = [.command, .shift] })
        fileMenu.addItem(.separator())
        fileMenu.addItem(NSMenuItem(
            title: "挂载虚拟磁盘",
            action: #selector(NSApp.sendAction(_:to:from:)),
            keyEquivalent: "m"
        ) { $0.keyEquivalentModifierMask = [.command, .option] })
        fileMenu.addItem(NSMenuItem(
            title: "卸载虚拟磁盘",
            action: #selector(NSApp.sendAction(_:to:from:)),
            keyEquivalent: "m"
        ) { $0.keyEquivalentModifierMask = [.command, .option, .shift] })
        fileMenu.addItem(.separator())
        fileMenu.addItem(NSMenuItem(
            title: "关闭窗口",
            action: #selector(NSWindow.performClose(_:)),
            keyEquivalent: "w"
        ))

        let fileMenuItem = NSMenuItem()
        fileMenuItem.submenu = fileMenu
        mainMenu.addItem(fileMenuItem)

        // ── 编辑菜单 ──
        let editMenu = NSMenu(title: "编辑")
        editMenu.addItem(NSMenuItem(title: "撤销", action: Selector(("undo:")), keyEquivalent: "z"))
        editMenu.addItem(NSMenuItem(title: "重做", action: Selector(("redo:")), keyEquivalent: "Z"))
        editMenu.addItem(.separator())
        editMenu.addItem(NSMenuItem(title: "剪切", action: #selector(NSText.cut(_:)), keyEquivalent: "x"))
        editMenu.addItem(NSMenuItem(title: "拷贝", action: #selector(NSText.copy(_:)), keyEquivalent: "c"))
        editMenu.addItem(NSMenuItem(title: "粘贴", action: #selector(NSText.paste(_:)), keyEquivalent: "v"))
        editMenu.addItem(NSMenuItem(title: "全选", action: #selector(NSText.selectAll(_:)), keyEquivalent: "a"))

        let editMenuItem = NSMenuItem()
        editMenuItem.submenu = editMenu
        mainMenu.addItem(editMenuItem)

        // ── 视图菜单 ──
        let viewMenu = NSMenu(title: "视图")
        viewMenu.addItem(NSMenuItem(
            title: "显示为图标",
            action: #selector(NSApp.sendAction(_:to:from:)),
            keyEquivalent: "1"
        ) { $0.keyEquivalentModifierMask = [.command] })
        viewMenu.addItem(NSMenuItem(
            title: "显示为列表",
            action: #selector(NSApp.sendAction(_:to:from:)),
            keyEquivalent: "2"
        ) { $0.keyEquivalentModifierMask = [.command] })
        viewMenu.addItem(.separator())
        viewMenu.addItem(NSMenuItem(
            title: "显示侧边栏",
            action: #selector(NSApp.sendAction(_:to:from:)),
            keyEquivalent: "s"
        ) { $0.keyEquivalentModifierMask = [.command, .option] })
        viewMenu.addItem(.separator())
        viewMenu.addItem(NSMenuItem(
            title: "进入全屏幕",
            action: #selector(NSWindow.toggleFullScreen(_:)),
            keyEquivalent: "f"
        ) { $0.keyEquivalentModifierMask = [.command, .control] })

        let viewMenuItem = NSMenuItem()
        viewMenuItem.submenu = viewMenu
        mainMenu.addItem(viewMenuItem)

        // ── 窗口菜单 ──
        let windowMenu = NSMenu(title: "窗口")
        windowMenu.addItem(NSMenuItem(
            title: "最小化",
            action: #selector(NSWindow.performMiniaturize(_:)),
            keyEquivalent: "m"
        ))
        windowMenu.addItem(NSMenuItem(
            title: "缩放",
            action: #selector(NSWindow.performZoom(_:)),
            keyEquivalent: ""
        ))
        windowMenu.addItem(.separator())
        windowMenu.addItem(NSMenuItem(
            title: "全部置于最前",
            action: #selector(NSApplication.arrangeInFront(_:)),
            keyEquivalent: ""
        ))

        let windowMenuItem = NSMenuItem()
        windowMenuItem.submenu = windowMenu
        mainMenu.addItem(windowMenuItem)

        // ── 帮助菜单 ──
        let helpMenu = NSMenu(title: "帮助")
        helpMenu.addItem(NSMenuItem(
            title: "PrivateCloudDisk 帮助",
            action: #selector(NSApp.sendAction(_:to:from:)),
            keyEquivalent: "?"
        ))

        let helpMenuItem = NSMenuItem()
        helpMenuItem.submenu = helpMenu
        mainMenu.addItem(helpMenuItem)

        return mainMenu
    }
}

// MARK: - NSMenuItem 便捷构造

extension NSMenuItem {
    convenience init(
        title: String,
        action: Selector?,
        keyEquivalent: String,
        modifier: ((NSMenuItem) -> Void)? = nil
    ) {
        self.init(title: title, action: action, keyEquivalent: keyEquivalent)
        modifier?(self)
    }
}