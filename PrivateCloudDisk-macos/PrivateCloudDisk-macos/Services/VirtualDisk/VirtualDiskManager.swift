import Foundation
import Combine
import FileProvider
import os.log

// MARK: - 虚拟磁盘管理器

/// macOS 虚拟磁盘管理器
///
/// 对应 Windows 的 VirtualDiskService（基于 Cloud Files API + WinFsp）
///
/// macOS 实现策略：
/// 1. NSFileProviderExtension — 苹果官方文件提供者 API（macOS 11+）
///    - 在 Finder 侧边栏显示，与 iCloud Drive 同级
///    - 按需下载（materialization）
///    - 离线文件支持
///    - 系统级集成，无需额外安装
/// 2. FUSE (macFUSE) — 备用方案，类似 WinFsp（可选）
///    - 挂载为独立卷
///    - 第三方依赖（需要用户安装 macFUSE）
///
/// 选择方案 1 作为主方案：NSFileProviderExtension 是 Apple 推荐的
/// 云存储集成方式，与系统深度集成，用户体验最佳。
///
/// macOS 特有优势：
/// - NSFileProviderManager 自动管理文件状态
/// - NSFileProviderService 支持自定义操作
/// - NSFileProviderDomain 支持多账户
/// - 与 Spotlight 搜索集成
/// - 与 Time Machine 兼容
/// - 文件协调（NSFileCoordinator）保证数据一致性
@MainActor
final class VirtualDiskManager: ObservableObject {

    static let shared = VirtualDiskManager()

    // MARK: - 发布属性

    @Published var status: VirtualDiskStatus = .disconnected
    @Published var config = VirtualDiskConfig.default
    @Published var syncEvents: [SyncEvent] = []
    @Published var isMounted = false

    private let logger = Logger(subsystem: "com.privateclouddisk.app", category: "VirtualDisk")
    private var syncTimer: Timer?
    private var fileProviderDomain: NSFileProviderDomain?
    private let fileProviderIdentifier = "com.privateclouddisk.fileprovider"

    private init() {
        loadConfig()
    }

    // MARK: - 挂载

    /// 挂载虚拟磁盘（通过 NSFileProviderExtension）
    func mount() async throws {
        guard !isMounted else { return }

        status = .connecting
        logger.info("开始挂载虚拟磁盘...")

        // 1. 确保挂载目录存在
        let mountURL = URL(fileURLWithPath: config.mountPoint)
        try FileManager.default.createDirectory(
            at: mountURL,
            withIntermediateDirectories: true,
            attributes: nil
        )

        // 2. 注册 File Provider 域
        let domain = NSFileProviderDomain(
            identifier: NSFileProviderDomainIdentifier(rawValue: "\(fileProviderIdentifier).\(config.userId)"),
            displayName: config.displayName
        )

        // 3. 添加域（实际挂载由 FileProviderExt 处理）
        _ = NSFileProviderManager(for: domain)
        try await NSFileProviderManager.add(domain)
        fileProviderDomain = domain

        // 4. 更新状态
        isMounted = true
        status = .connected
        saveConfig(isMounted: true)

        // 5. 启动同步
        if config.autoSync {
            startSyncTimer()
        }

        // 6. 通知 Finder 扩展
        postDistributedNotification(.virtualDiskMounted, userInfo: [
            "mountPoint": config.mountPoint,
            "displayName": config.displayName
        ])

        logger.info("虚拟磁盘挂载成功: \(self.config.mountPoint)")
    }

    /// 卸载虚拟磁盘
    func unmount() {
        guard isMounted, let domain = fileProviderDomain else { return }

        status = .unmounting
        logger.info("正在卸载虚拟磁盘...")

        stopSyncTimer()

        _ = NSFileProviderManager(for: domain)
        Task {
            do {
                try await NSFileProviderManager.remove(domain)
                await MainActor.run {
                    self.isMounted = false
                    self.status = .disconnected
                    self.fileProviderDomain = nil
                    self.saveConfig(isMounted: false)
                }
                postDistributedNotification(.virtualDiskUnmounted, userInfo: nil)
                logger.info("虚拟磁盘卸载成功")
            } catch {
                await MainActor.run {
                    self.status = .error
                }
                logger.error("虚拟磁盘卸载失败: \(error.localizedDescription)")
            }
        }
    }

    /// 恢复挂载（应用启动时）
    func restoreMount() async throws {
        guard UserDefaults.standard.bool(forKey: "VirtualDisk.IsMounted") else { return }

        // 尝试检查域是否仍然存在，如果不存在则重新挂载
        let domainIdentifier = NSFileProviderDomainIdentifier(
            rawValue: "\(fileProviderIdentifier).\(config.userId)"
        )

        let domain = NSFileProviderDomain(
            identifier: domainIdentifier,
            displayName: config.displayName
        )

        do {
            // 尝试获取域的用户可见 URL 来验证域是否存在
            if let manager = NSFileProviderManager(for: domain) {
                // 使用 workingSet 枚举器来验证域是否可用
                try await manager.signalEnumerator(for: .workingSet)
                logger.info("域已存在，恢复挂载状态")
                isMounted = true
                status = .connected
                fileProviderDomain = domain
                if config.autoSync {
                    startSyncTimer()
                }
            }
        } catch {
            // 域不存在，重新挂载
            logger.info("域不存在，重新挂载: \(error.localizedDescription)")
            try await mount()
        }
    }

    // MARK: - 同步

    private func startSyncTimer() {
        syncTimer = Timer.scheduledTimer(
            withTimeInterval: config.syncInterval,
            repeats: true
        ) { [weak self] _ in
            Task { @MainActor in
                await self?.performSync()
            }
        }
    }

    private func stopSyncTimer() {
        syncTimer?.invalidate()
        syncTimer = nil
    }

    private func performSync() async {
        guard status == .connected else { return }

        status = .syncing
        logger.info("开始同步...")

        do {
            // 获取服务器文件列表
            let remoteFiles = try await FileService.shared.listFiles(pageSize: 1000)

            // 信号 File Provider 有变更
            if let domain = fileProviderDomain {
                let manager = NSFileProviderManager(for: domain)
                try await manager?.signalEnumerator(for: .workingSet)
            }

            status = .connected
            logger.info("同步完成: \(remoteFiles.items.count) 个文件")
        } catch {
            status = .error
            logger.error("同步失败: \(error.localizedDescription)")
        }
    }

    // MARK: - 缓存管理

    /// 获取缓存大小
    func getCacheSize() -> Int64 {
        let cacheURL = URL(fileURLWithPath: config.mountPoint)
            .appendingPathComponent(".pcd_cache")

        guard let enumerator = FileManager.default.enumerator(
            at: cacheURL,
            includingPropertiesForKeys: [.fileSizeKey],
            options: [.skipsHiddenFiles]
        ) else { return 0 }

        var totalSize: Int64 = 0
        for case let url as URL in enumerator {
            if let size = try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize {
                totalSize += Int64(size)
            }
        }
        return totalSize
    }

    /// 清理缓存
    func clearCache() async {
        let cacheURL = URL(fileURLWithPath: config.mountPoint)
            .appendingPathComponent(".pcd_cache")

        try? FileManager.default.removeItem(at: cacheURL)
        logger.info("缓存已清理")
    }

    // MARK: - 配置管理

    private func loadConfig() {
        let defaults = UserDefaults.standard
        config.mountPoint = defaults.string(forKey: "VirtualDisk.MountPoint") ?? VirtualDiskConfig.default.mountPoint
        config.displayName = defaults.string(forKey: "VirtualDisk.DisplayName") ?? VirtualDiskConfig.default.displayName
        config.apiBaseUrl = defaults.string(forKey: "VirtualDisk.ApiBaseUrl") ?? VirtualDiskConfig.default.apiBaseUrl
        config.cacheMaxSize = Int64(defaults.integer(forKey: "VirtualDisk.CacheMaxSize"))
        if config.cacheMaxSize == 0 { config.cacheMaxSize = VirtualDiskConfig.default.cacheMaxSize }
        config.autoSync = defaults.bool(forKey: "VirtualDisk.AutoSync")
        config.syncInterval = defaults.double(forKey: "VirtualDisk.SyncInterval")
        if config.syncInterval == 0 { config.syncInterval = VirtualDiskConfig.default.syncInterval }
    }

    private func saveConfig(isMounted: Bool) {
        let defaults = UserDefaults.standard
        defaults.set(isMounted, forKey: "VirtualDisk.IsMounted")
        defaults.set(config.mountPoint, forKey: "VirtualDisk.MountPoint")
        defaults.set(config.displayName, forKey: "VirtualDisk.DisplayName")
        defaults.set(config.apiBaseUrl, forKey: "VirtualDisk.ApiBaseUrl")
        defaults.set(config.cacheMaxSize, forKey: "VirtualDisk.CacheMaxSize")
        defaults.set(config.autoSync, forKey: "VirtualDisk.AutoSync")
        defaults.set(config.syncInterval, forKey: "VirtualDisk.SyncInterval")
    }

    /// 更新配置
    func updateConfig(_ newConfig: VirtualDiskConfig) {
        config = newConfig
        saveConfig(isMounted: isMounted)
    }

    // MARK: - 分布式通知（跨进程通信）

    private func postDistributedNotification(_ name: Notification.Name, userInfo: [String: Any]?) {
        DistributedNotificationCenter.default().postNotificationName(
            name,
            object: nil,
            userInfo: userInfo,
            deliverImmediately: true
        )
    }
}

// MARK: - 分布式通知名称

extension Notification.Name {
    static let virtualDiskMounted = Notification.Name("com.privateclouddisk.virtualdisk.mounted")
    static let virtualDiskUnmounted = Notification.Name("com.privateclouddisk.virtualdisk.unmounted")
    static let virtualDiskSyncStarted = Notification.Name("com.privateclouddisk.virtualdisk.syncStarted")
    static let virtualDiskSyncCompleted = Notification.Name("com.privateclouddisk.virtualdisk.syncCompleted")
}
