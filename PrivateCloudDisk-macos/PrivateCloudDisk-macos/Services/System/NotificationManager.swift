import Foundation
import UserNotifications
import Combine

// MARK: - 通知管理器

/// 推送通知管理器
///
/// macOS 通知特性：
/// - UNUserNotificationCenter 统一通知接口
/// - 通知分组（threadIdentifier）
/// - 通知操作按钮（UNNotificationAction）
/// - 通知摘要（macOS 12+）
/// - 时间敏感通知（timeSensitive）
/// - 与 Focus 模式集成
final class NotificationManager: ObservableObject {

    static let shared = NotificationManager()

    private let center = UNUserNotificationCenter.current()

    private init() {}

    // MARK: - 授权

    func requestAuthorization() {
        center.requestAuthorization(options: [.alert, .sound, .badge, .provisional]) { granted, error in
            if granted {
                print("[NotificationManager] 通知权限已获取")
                // 注册通知类别
                self.registerNotificationCategories()
            }
            if let error = error {
                print("[NotificationManager] 通知权限请求失败: \(error.localizedDescription)")
            }
        }
    }

    // MARK: - 通知类别

    private func registerNotificationCategories() {
        // 上传完成
        let viewAction = UNNotificationAction(
            identifier: "view_file",
            title: "查看文件",
            options: .foreground
        )
        let uploadCategory = UNNotificationCategory(
            identifier: "upload_complete",
            actions: [viewAction],
            intentIdentifiers: [],
            options: []
        )

        // 下载完成
        let openAction = UNNotificationAction(
            identifier: "open_file",
            title: "打开文件",
            options: .foreground
        )
        let downloadCategory = UNNotificationCategory(
            identifier: "download_complete",
            actions: [openAction],
            intentIdentifiers: [],
            options: []
        )

        // 分享通知
        let acceptAction = UNNotificationAction(
            identifier: "accept_share",
            title: "接受",
            options: .foreground
        )
        let shareCategory = UNNotificationCategory(
            identifier: "share_received",
            actions: [acceptAction],
            intentIdentifiers: [],
            options: []
        )

        center.setNotificationCategories([
            uploadCategory, downloadCategory, shareCategory
        ])
    }

    // MARK: - 发送通知

    /// 发送本地通知
    func sendNotification(
        title: String,
        body: String,
        categoryIdentifier: String? = nil,
        userInfo: [String: Any] = [:],
        threadIdentifier: String? = nil,
        sound: UNNotificationSound? = .default
    ) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = sound
        content.userInfo = userInfo
        if let category = categoryIdentifier {
            content.categoryIdentifier = category
        }
        if let thread = threadIdentifier {
            content.threadIdentifier = thread
        }

        let request = UNNotificationRequest(
            identifier: UUID().uuidString,
            content: content,
            trigger: nil // 立即发送
        )

        center.add(request) { error in
            if let error = error {
                print("[NotificationManager] 发送通知失败: \(error.localizedDescription)")
            }
        }
    }

    /// 发送上传完成通知
    func notifyUploadComplete(filename: String, nodeId: String) {
        sendNotification(
            title: "上传完成",
            body: "\(filename) 已上传成功",
            categoryIdentifier: "upload_complete",
            userInfo: ["action": "upload_complete", "node_id": nodeId],
            threadIdentifier: "upload"
        )
    }

    /// 发送下载完成通知
    func notifyDownloadComplete(filename: String, localPath: String) {
        sendNotification(
            title: "下载完成",
            body: "\(filename) 已下载到本地",
            categoryIdentifier: "download_complete",
            userInfo: ["action": "download_complete", "local_path": localPath],
            threadIdentifier: "download"
        )
    }

    /// 发送分享通知
    func notifyShareReceived(from username: String, filename: String, shareUrl: String) {
        sendNotification(
            title: "收到分享",
            body: "\(username) 分享了文件 \"\(filename)\"",
            categoryIdentifier: "share_received",
            userInfo: [
                "action": "share_received",
                "share_url": shareUrl
            ],
            threadIdentifier: "share"
        )
    }

    /// 发送存储空间警告
    func notifyStorageAlert(usedPercentage: Double) {
        sendNotification(
            title: "存储空间不足",
            body: "已使用 \(Int(usedPercentage * 100))% 的存储空间，请及时清理",
            categoryIdentifier: "storage_alert",
            userInfo: ["action": "storage_alert"],
            threadIdentifier: "storage"
        )
    }

    /// 发送同步完成通知
    func notifySyncComplete(fileCount: Int) {
        sendNotification(
            title: "同步完成",
            body: "已完成 \(fileCount) 个文件的同步",
            threadIdentifier: "sync"
        )
    }
}