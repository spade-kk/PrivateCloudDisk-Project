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
///
/// ## FinderSync 集成架构
///
/// FinderSync 扩展需要监控 FileProvider 域的实际目录（位于
/// ~/Library/CloudStorage/ 下），而非本地挂载点。挂载成功后，
/// 本管理器会：
/// 1. 扫描 CloudStorage 找到实际的域目录
/// 2. 将路径写入共享 UserDefaults（App Group）
/// 3. 通过 DistributedNotificationCenter 通知 FinderSync 更新监控目录
///
/// 同时监听 FinderSync 发来的操作请求（挂载/卸载/同步等）。
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

    /// 共享 UserDefaults（通过 App Group 与扩展通信）
    private let sharedDefaults = UserDefaults(suiteName: "group.com.privateclouddisk.app")

    /// CloudStorage 基础路径
    private static let cloudStorageBase = NSHomeDirectory() + "/Library/CloudStorage"

    private init() {
        loadConfig()
        setupFinderSyncObserver()
    }

    // MARK: - 挂载

    /// 挂载虚拟磁盘（通过 NSFileProviderExtension）
    ///
    /// 对于 NSFileProviderReplicatedExtension，系统会自动管理挂载位置
    /// （在 ~/Library/CloudStorage/ 下），不需要手动创建目录。
    /// 挂载后会在 Finder 侧边栏「位置」中与 iCloud Drive 同级显示。
    func mount() async throws {
        guard !isMounted else { return }

        status = .connecting
        logger.info("开始挂载虚拟磁盘...")

        // 1. 注册 File Provider 域（在 Finder 侧边栏与 iCloud 同级显示）
        let domain = NSFileProviderDomain(
            identifier: NSFileProviderDomainIdentifier(rawValue: "\(fileProviderIdentifier).\(config.userId)"),
            displayName: config.displayName
        )
        // 关键：确保在 Finder 侧边栏中可见（与 iCloud、OneDrive 同级）
        domain.isHidden = false

        // 2. 添加域到系统（由 FileProviderExt 处理实际文件操作）
        do {
            try await NSFileProviderManager.add(domain)
            logger.info("FileProvider 域注册成功: \(self.config.displayName)")
        } catch let error as NSError {
            // 如果域已存在（相同标识符），尝试更新
            if error.code == NSFileWriteFileExistsError {
                logger.info("域已存在，尝试更新显示名称和可见性")
                try await NSFileProviderManager.remove(domain)
                try await NSFileProviderManager.add(domain)
            } else {
                throw error
            }
        }

        fileProviderDomain = domain

        // 3. 发现 CloudStorage 中的实际目录路径（系统异步创建，需等待）
        if let cloudPath = await discoverCloudStoragePath() {
            config.mountPoint = cloudPath
            logger.info("发现 CloudStorage 路径: \(cloudPath, privacy: .public)")
        }

        // 4. 更新状态并持久化
        isMounted = true
        status = .connected
        saveConfig(isMounted: true)

        // 5. 启动同步
        if config.autoSync {
            startSyncTimer()
        }

        // 6. 通知 FinderSync 扩展更新监控目录（传递实际 CloudStorage 路径）
        postDistributedNotification(.virtualDiskMounted, userInfo: [
            "mountPoint": config.mountPoint,
            "displayName": config.displayName
        ])

        logger.info("虚拟磁盘挂载成功: \(self.config.mountPoint, privacy: .public)")
    }

    /// 卸载虚拟磁盘
    func unmount() {
        guard isMounted, let domain = fileProviderDomain else { return }

        status = .unmounting
        logger.info("正在卸载虚拟磁盘...")

        stopSyncTimer()

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
    ///
    /// 应用重启时，域可能仍然存在于系统中。需要恢复挂载状态，
    /// 并重新发现 CloudStorage 路径通知 FinderSync。
    func restoreMount() async throws {
        guard UserDefaults.standard.bool(forKey: "VirtualDisk.IsMounted") else { return }

        let domainIdentifier = NSFileProviderDomainIdentifier(
            rawValue: "\(fileProviderIdentifier).\(config.userId)"
        )

        let domain = NSFileProviderDomain(
            identifier: domainIdentifier,
            displayName: config.displayName
        )
        domain.isHidden = false

        do {
            if let manager = NSFileProviderManager(for: domain) {
                try await manager.signalEnumerator(for: .workingSet)
                logger.info("域已存在，恢复挂载状态")

                fileProviderDomain = domain
                isMounted = true
                status = .connected

                // 重新发现 CloudStorage 路径
                if let cloudPath = await discoverCloudStoragePath() {
                    config.mountPoint = cloudPath
                    saveConfig(isMounted: true)
                }

                if config.autoSync {
                    startSyncTimer()
                }

                // 通知 FinderSync 当前挂载状态
                postDistributedNotification(.virtualDiskMounted, userInfo: [
                    "mountPoint": config.mountPoint,
                    "displayName": config.displayName
                ])
            }
        } catch {
            // 域不存在或不可用，重新挂载
            logger.info("域不存在，重新挂载: \(error.localizedDescription)")
            try await mount()
        }
    }

    // MARK: - CloudStorage 路径发现

    /// 扫描 ~/Library/CloudStorage/ 查找 FileProvider 域的实际目录
    ///
    /// 系统注册域后会在 CloudStorage 下创建目录，命名模式为：
    /// `<displayName>-<domain_identifier_suffix>`
    ///
    /// 由于系统异步创建目录，此方法会等待最多 5 秒并重试。
    private func discoverCloudStoragePath() async -> String? {
        let cloudStorageURL = URL(fileURLWithPath: Self.cloudStorageBase)

        // 最多重试 10 次，每次间隔 0.5 秒
        for attempt in 0..<10 {
            guard let contents = try? FileManager.default.contentsOfDirectory(
                at: cloudStorageURL,
                includingPropertiesForKeys: [.nameKey],
                options: [.skipsHiddenFiles]
            ) else {
                logger.warning("无法读取 CloudStorage 目录 (尝试 \(attempt + 1)/10)")
                try? await Task.sleep(nanoseconds: 500_000_000)
                continue
            }

            // 查找匹配的目录：以 displayName 开头
            let match = contents.first { url in
                url.lastPathComponent.hasPrefix(config.displayName)
            }

            if let found = match {
                logger.info("找到 CloudStorage 目录: \(found.path, privacy: .public)")
                return found.path
            }

            logger.debug("CloudStorage 目录尚未创建，等待中... (尝试 \(attempt + 1)/10)")
            try? await Task.sleep(nanoseconds: 500_000_000)
        }

        logger.warning("未在 CloudStorage 中找到目录，使用默认路径")
        // 回退：构造预期路径
        return "\(Self.cloudStorageBase)/\(config.displayName)-\(config.userId)"
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
            let rootNode = try await FileService.shared.getRootNode()
            let remoteFiles = try await FileService.shared.getNodeChildren(nodeId: rootNode.nodeId)

            // 通知 FileProvider 重新枚举根容器和工作集
            // 这样 Finder 中的共享目录会刷新文件列表
            if let domain = fileProviderDomain {
                let manager = NSFileProviderManager(for: domain)
                try await manager?.signalEnumerator(for: .workingSet)
                try await manager?.signalEnumerator(for: .rootContainer)
            }

            status = .connected
            logger.info("同步完成: \(remoteFiles.count) 个文件")
        } catch {
            status = .error
            logger.error("同步失败: \(error.localizedDescription)")
        }
    }

    // MARK: - 缓存管理

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
        config.token = defaults.string(forKey: "VirtualDisk.Token") ?? VirtualDiskConfig.default.token
        config.userId = defaults.string(forKey: "VirtualDisk.UserId") ?? VirtualDiskConfig.default.userId
        config.cacheMaxSize = Int64(defaults.integer(forKey: "VirtualDisk.CacheMaxSize"))
        if config.cacheMaxSize == 0 { config.cacheMaxSize = VirtualDiskConfig.default.cacheMaxSize }
        config.autoSync = defaults.bool(forKey: "VirtualDisk.AutoSync")
        config.syncInterval = defaults.double(forKey: "VirtualDisk.SyncInterval")
        if config.syncInterval == 0 { config.syncInterval = VirtualDiskConfig.default.syncInterval }
    }

    private func saveConfig(isMounted: Bool) {
        // 1. 写入应用自身 UserDefaults
        let defaults = UserDefaults.standard
        defaults.set(isMounted, forKey: "VirtualDisk.IsMounted")
        defaults.set(config.mountPoint, forKey: "VirtualDisk.MountPoint")
        defaults.set(config.displayName, forKey: "VirtualDisk.DisplayName")
        defaults.set(config.apiBaseUrl, forKey: "VirtualDisk.ApiBaseUrl")
        defaults.set(config.token, forKey: "VirtualDisk.Token")
        defaults.set(config.userId, forKey: "VirtualDisk.UserId")
        defaults.set(config.cacheMaxSize, forKey: "VirtualDisk.CacheMaxSize")
        defaults.set(config.autoSync, forKey: "VirtualDisk.AutoSync")
        defaults.set(config.syncInterval, forKey: "VirtualDisk.SyncInterval")

        // 2. 同步写入共享 UserDefaults（App Group），供扩展读取
        //    FinderSync 和 FileProvider 扩展使用 UserDefaults(suiteName: "group.com.privateclouddisk.app")
        sharedDefaults?.set(isMounted, forKey: "VirtualDisk.IsMounted")
        sharedDefaults?.set(config.mountPoint, forKey: "fp.mountPoint")
        sharedDefaults?.set(config.displayName, forKey: "fp.displayName")
        sharedDefaults?.set(config.userId, forKey: "fp.userId")

        // 关键：同步 auth token 和 API URL，FileProvider 扩展需要这些信息才能调用后端 API
        let authToken = KeychainManager.shared.readAuthToken() ?? ""
        let apiBaseURL = "http://localhost:8080/api/v1/"
        sharedDefaults?.set(authToken, forKey: "fp.token")
        sharedDefaults?.set(apiBaseURL, forKey: "fp.apiBaseUrl")
        sharedDefaults?.synchronize()
    }

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

    // MARK: - 监听 FinderSync 操作请求

    /// 监听来自 FinderSync 扩展的操作请求
    ///
    /// FinderSync 通过 DistributedNotificationCenter 发送操作请求，
    /// 包括挂载、卸载、同步、分享等操作。
    /// 通知名称: com.privateclouddisk.finder.action
    private func setupFinderSyncObserver() {
        DistributedNotificationCenter.default().addObserver(
            self,
            selector: #selector(handleFinderSyncAction(_:)),
            name: NSNotification.Name("com.privateclouddisk.finder.action"),
            object: nil,
            suspensionBehavior: .deliverImmediately
        )
    }

    @objc private func handleFinderSyncAction(_ notification: Notification) {
        guard let userInfo = notification.userInfo as? [String: Any],
              let action = userInfo["action"] as? String else {
            logger.warning("收到无效的 FinderSync 操作通知")
            return
        }

        logger.info("收到 FinderSync 操作: \(action, privacy: .public)")

        Task { @MainActor in
            switch action {
            case "mount":
                do {
                    try await self.mount()
                } catch {
                    logger.error("FinderSync 请求挂载失败: \(error.localizedDescription)")
                }

            case "unmount":
                self.unmount()

            case "sync":
                if let path = userInfo["path"] as? String {
                    await self.performSync()
                } else if let paths = userInfo["paths"] as? [String] {
                    await self.performSync()
                } else {
                    await self.performSync()
                }

            case "refreshStatus":
                // 通知 FinderSync 当前状态
                self.postDistributedNotification(.virtualDiskMounted, userInfo: [
                    "mountPoint": self.config.mountPoint,
                    "displayName": self.config.displayName
                ])

            default:
                logger.debug("未处理的 FinderSync 操作: \(action, privacy: .public)")
            }
        }
    }
}

// MARK: - 分布式通知名称

extension Notification.Name {
    static let virtualDiskMounted = Notification.Name("com.privateclouddisk.virtualdisk.mounted")
    static let virtualDiskUnmounted = Notification.Name("com.privateclouddisk.virtualdisk.unmounted")
    static let virtualDiskSyncStarted = Notification.Name("com.privateclouddisk.virtualdisk.syncStarted")
    static let virtualDiskSyncCompleted = Notification.Name("com.privateclouddisk.virtualdisk.syncCompleted")
}