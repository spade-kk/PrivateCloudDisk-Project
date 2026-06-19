import Foundation
import FileProvider

// MARK: - File Provider 辅助工具

/// File Provider 扩展的帮助类
/// 提供主应用和 File Provider 扩展之间的共享逻辑
final class FileProviderHelper {

    static let shared = FileProviderHelper()

    /// 共享的 UserDefaults（App Group）
    private var sharedDefaults: UserDefaults? {
        UserDefaults(suiteName: "group.com.privateclouddisk.app")
    }

    // MARK: - 共享状态

    /// 保存挂载配置到共享 UserDefaults
    func saveConfig(_ config: VirtualDiskConfig) {
        sharedDefaults?.set(config.mountPoint, forKey: "fp.mountPoint")
        sharedDefaults?.set(config.displayName, forKey: "fp.displayName")
        sharedDefaults?.set(config.apiBaseUrl, forKey: "fp.apiBaseUrl")
        sharedDefaults?.set(config.token, forKey: "fp.token")
        sharedDefaults?.set(config.userId, forKey: "fp.userId")
        sharedDefaults?.synchronize()
    }

    /// 从共享 UserDefaults 读取配置
    func loadConfig() -> VirtualDiskConfig {
        var config = VirtualDiskConfig.default
        config.mountPoint = sharedDefaults?.string(forKey: "fp.mountPoint") ?? config.mountPoint
        config.displayName = sharedDefaults?.string(forKey: "fp.displayName") ?? config.displayName
        config.apiBaseUrl = sharedDefaults?.string(forKey: "fp.apiBaseUrl") ?? config.apiBaseUrl
        config.token = sharedDefaults?.string(forKey: "fp.token") ?? config.token
        config.userId = sharedDefaults?.string(forKey: "fp.userId") ?? config.userId
        return config
    }

    /// 更新 File Provider 域
    func signalFileProviderChange() {
        guard let domainIdentifier = sharedDefaults?.string(forKey: "fp.domainIdentifier") else { return }
        let identifier = NSFileProviderDomainIdentifier(rawValue: domainIdentifier)
        let manager = NSFileProviderManager(for: NSFileProviderDomain(
            identifier: identifier,
            displayName: ""
        ))
        Task {
            try? await manager?.signalEnumerator(for: .workingSet)
        }
    }
}