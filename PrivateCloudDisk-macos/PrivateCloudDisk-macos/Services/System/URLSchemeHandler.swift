import Foundation
import AppKit

// MARK: - URL Scheme 处理器

/// 处理 pcd:// 自定义 URL Scheme
///
/// 支持的 URL 格式：
/// - pcd://open        — 打开主窗口
/// - pcd://share/{id}  — 打开分享链接
/// - pcd://file/{id}   — 打开文件详情
/// - pcd://mount       — 挂载虚拟磁盘
/// - pcd://unmount     — 卸载虚拟磁盘
final class URLSchemeHandler {

    func handle(url: URL) {
        guard let host = url.host else { return }

        DispatchQueue.main.async {
            switch host {
            case "open":
                self.openMainWindow()

            case "share":
                let shareId = url.pathComponents.dropFirst().first
                if let id = shareId {
                    self.openShare(id: id)
                }

            case "file":
                let fileId = url.pathComponents.dropFirst().first
                if let id = fileId {
                    self.openFileDetail(nodeId: id)
                }

            case "mount":
                Task { @MainActor in
                    try? await VirtualDiskManager.shared.mount()
                }

            case "unmount":
                VirtualDiskManager.shared.unmount()

            default:
                self.openMainWindow()
            }
        }
    }

    private func openMainWindow() {
        NSApp.activate(ignoringOtherApps: true)
    }

    private func openShare(id: String) {
        openMainWindow()
        // 通知 ContentView 导航到分享页面
        NotificationCenter.default.post(
            name: NSNotification.Name("NavigateToShare"),
            object: nil,
            userInfo: ["shareId": id]
        )
    }

    private func openFileDetail(nodeId: String) {
        openMainWindow()
        NotificationCenter.default.post(
            name: NSNotification.Name("NavigateToFile"),
            object: nil,
            userInfo: ["nodeId": nodeId]
        )
    }
}