import Foundation
import Combine
import SwiftUI

// MARK: - 设置视图模型

@MainActor
final class SettingsViewModel: ObservableObject {

    // MARK: - 通用设置

    @Published var launchAtLogin = false
    @Published var apiBaseURL = "http://localhost:8000"
    @Published var showHiddenFiles = false

    // MARK: - 虚拟磁盘设置

    @Published var mountPoint = NSHomeDirectory() + "/PrivateCloudDisk"
    @Published var autoSync = true
    @Published var syncInterval: Double = 30
    @Published var cacheMaxSize: Double = 5 // GB

    // MARK: - 通知设置

    @Published var uploadNotification = true
    @Published var downloadNotification = true
    @Published var shareNotification = true
    @Published var storageAlert = true
    @Published var storageAlertThreshold: Double = 0.9 // 90%

    // MARK: - 安全设置

    @Published var useBiometricAuth = false
    @Published var autoLockTimeout: Int = 5 // 分钟，0 表示不自动锁定

    // MARK: - 关于

    @Published var appVersion = "1.0.0"
    @Published var buildNumber = "1"

    private let loginItemManager = LoginItemManager()

    // MARK: - 初始化

    init() {
        loadSettings()
    }

    func loadSettings() {
        let defaults = UserDefaults.standard

        launchAtLogin = defaults.bool(forKey: "LaunchAtLogin")
        apiBaseURL = defaults.string(forKey: "api_base_url") ?? "http://localhost:8000"
        showHiddenFiles = defaults.bool(forKey: "ShowHiddenFiles")

        mountPoint = defaults.string(forKey: "VirtualDisk.MountPoint") ?? NSHomeDirectory() + "/PrivateCloudDisk"
        autoSync = defaults.bool(forKey: "VirtualDisk.AutoSync")
        syncInterval = defaults.double(forKey: "VirtualDisk.SyncInterval")
        if syncInterval == 0 { syncInterval = 30 }
        cacheMaxSize = Double(defaults.integer(forKey: "VirtualDisk.CacheMaxSize")) / (1024 * 1024 * 1024)
        if cacheMaxSize == 0 { cacheMaxSize = 5 }

        uploadNotification = defaults.object(forKey: "Notification.Upload") as? Bool ?? true
        downloadNotification = defaults.object(forKey: "Notification.Download") as? Bool ?? true
        shareNotification = defaults.object(forKey: "Notification.Share") as? Bool ?? true
        storageAlert = defaults.object(forKey: "Notification.StorageAlert") as? Bool ?? true
        storageAlertThreshold = defaults.double(forKey: "Notification.StorageAlertThreshold")
        if storageAlertThreshold == 0 { storageAlertThreshold = 0.9 }

        useBiometricAuth = defaults.bool(forKey: "Security.UseBiometricAuth")
        autoLockTimeout = defaults.integer(forKey: "Security.AutoLockTimeout")

        appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
        buildNumber = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
    }

    // MARK: - 保存

    func saveAll() {
        let defaults = UserDefaults.standard

        defaults.set(launchAtLogin, forKey: "LaunchAtLogin")
        defaults.set(apiBaseURL, forKey: "api_base_url")
        defaults.set(showHiddenFiles, forKey: "ShowHiddenFiles")

        defaults.set(mountPoint, forKey: "VirtualDisk.MountPoint")
        defaults.set(autoSync, forKey: "VirtualDisk.AutoSync")
        defaults.set(syncInterval, forKey: "VirtualDisk.SyncInterval")
        defaults.set(Int(cacheMaxSize * 1024 * 1024 * 1024), forKey: "VirtualDisk.CacheMaxSize")

        defaults.set(uploadNotification, forKey: "Notification.Upload")
        defaults.set(downloadNotification, forKey: "Notification.Download")
        defaults.set(shareNotification, forKey: "Notification.Share")
        defaults.set(storageAlert, forKey: "Notification.StorageAlert")
        defaults.set(storageAlertThreshold, forKey: "Notification.StorageAlertThreshold")

        defaults.set(useBiometricAuth, forKey: "Security.UseBiometricAuth")
        defaults.set(autoLockTimeout, forKey: "Security.AutoLockTimeout")

        // 更新开机启动
        do {
            if launchAtLogin {
                try loginItemManager.enable()
            } else {
                try loginItemManager.disable()
            }
        } catch {
            print("[Settings] 更新开机启动失败: \(error)")
        }

        // 通知虚拟磁盘管理器更新配置
        let vdConfig = VirtualDiskConfig(
            mountPoint: mountPoint,
            displayName: "PrivateCloudDisk",
            apiBaseUrl: apiBaseURL,
            token: KeychainManager.shared.readAuthToken() ?? "",
            userId: KeychainManager.shared.readUserId() ?? "",
            quota: nil,
            cacheMaxSize: Int64(cacheMaxSize * 1024 * 1024 * 1024),
            autoSync: autoSync,
            syncInterval: syncInterval
        )
        VirtualDiskManager.shared.updateConfig(vdConfig)
    }

    // MARK: - 导出日志

    func exportLogs() -> URL? {
        // 收集日志文件并导出
        // 实际实现需要从 os_log 收集日志
        return nil
    }

    /// 清除所有本地数据
    func clearAllLocalData() {
        KeychainManager.shared.clearAll()
        let defaults = UserDefaults.standard
        let domain = Bundle.main.bundleIdentifier ?? "com.privateclouddisk.app"
        defaults.removePersistentDomain(forName: domain)
        defaults.synchronize()
    }
}