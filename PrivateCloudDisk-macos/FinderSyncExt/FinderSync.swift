import Cocoa
import FinderSync
import os.log

// MARK: - Finder Sync Extension

/// PrivateCloudDisk Finder Sync 扩展
///
/// 在 Finder 中为云盘文件提供同步状态图标和右键菜单操作，
/// 与 iCloud、OneDrive 等云服务同级别的集成体验。
///
/// ## 沙盒环境下的路径处理（关键）
///
/// 作为 App Extension，FinderSync 运行在沙盒容器中。
/// `NSHomeDirectory()` 在沙盒中返回容器路径：
///   ~/Library/Containers/<bundle-id>/Data
///
/// 而 FileProvider 域的实际目录在真实 HOME 下的 CloudStorage 中：
///   ~/Library/CloudStorage/<displayName>-<suffix>
///
/// 因此必须使用 POSIX `getpwuid(getuid())` 获取真实 HOME 目录，
/// 不能使用 `NSHomeDirectory()` 或 `NSString.expandingTildeInPath`。
///
/// ## 监控策略
///
/// 1. 优先从共享 UserDefaults 读取 CloudStorage 路径（主应用写入）
/// 2. 扫描真实 ~/Library/CloudStorage/ 查找匹配目录
/// 3. 回退到真实 ~/PrivateCloudDisk 目录
/// 4. 收到挂载通知后动态更新监控目录
final class FinderSync: FIFinderSync {

    // MARK: - 日志

    private let logger = Logger(subsystem: "com.privateclouddisk.findersync", category: "Extension")

    /// CloudStorage 基础路径（系统管理 FileProvider 域的目录）
    private static var cloudStorageBase: String {
        "~/Library/CloudStorage"
    }

    /// 本地回退目录（域未挂载时使用）
    private static var fallbackMountPoint: String {
        "~/PrivateCloudDisk"
    }

    // MARK: - 属性

    /// 共享 UserDefaults（通过 App Group 与主应用通信）
    private lazy var sharedDefaults: UserDefaults? = {
        UserDefaults(suiteName: "group.com.privateclouddisk.app")
    }()

    /// 文件同步状态缓存 [relativePath: SyncStatus]
    private var syncStatusCache: [String: SyncStatus] = [:]
    private let cacheLock = NSLock()

    /// 当前实际监控的目录路径（CloudStorage 中的路径或回退路径）
    private var currentMonitoredPath: String = ""

    /// 当前挂载点目录（优先 CloudStorage 路径，回退到本地路径）
    ///
    /// 注意：在沙盒中，FileManager.fileExists 可能无法访问容器外的路径，
    /// 因此对共享 UserDefaults 中的路径不进行存在性检查，直接信任。
    private var mountPoint: String {
        // 1. 优先从共享 UserDefaults 读取（主应用挂载后写入，不检查存在性）
        if let cloudPath = sharedDefaults?.string(forKey: "fp.mountPoint"),
           !cloudPath.isEmpty {
            logger.debug("mountPoint 来自共享 UserDefaults: \(cloudPath, privacy: .public)")
            return cloudPath
        }

        // 2. 扫描真实 CloudStorage 查找匹配的目录
        if let foundPath = Self.findCloudStorageDirectory() {
            logger.debug("mountPoint 来自 CloudStorage 扫描: \(foundPath, privacy: .public)")
            return foundPath
        }

        // 3. 回退到本地目录
        logger.debug("mountPoint 回退到: \(Self.fallbackMountPoint, privacy: .public)")
        return Self.fallbackMountPoint
    }

    /// 是否已挂载虚拟磁盘
    private var isMounted: Bool {
        sharedDefaults?.bool(forKey: "VirtualDisk.IsMounted") ?? false
    }

    // MARK: - 同步状态枚举

    /// 文件同步状态
    enum SyncStatus: String, CaseIterable {
        case synced = "synced"
        case syncing = "syncing"
        case notSynced = "not_synced"
        case cloudOnly = "cloud_only"
        case error = "error"
        case paused = "paused"

        var contextMenuTitle: String {
            switch self {
            case .synced:    return "已同步"
            case .syncing:   return "同步中..."
            case .notSynced: return "未同步"
            case .cloudOnly: return "仅云端"
            case .error:     return "同步错误"
            case .paused:    return "已暂停"
            }
        }
    }

    // MARK: - 初始化

    override init() {
        super.init()
        logger.info("FinderSync 扩展初始化开始")
        logger.info("CloudStorage 基础路径: \(Self.cloudStorageBase, privacy: .public)")

        // 确定监控目录
        currentMonitoredPath = mountPoint
        logger.info("目标监控目录: \(self.currentMonitoredPath, privacy: .public)")

        // 确保回退目录存在
        ensureFallbackDirectoryExists()

        // 设置监控目录
        setupMonitoring()

        // 监听跨进程通知
        observeDistributedNotifications()

        logger.info("FinderSync 扩展初始化完成，监控: \(self.currentMonitoredPath, privacy: .public)")
    }

    deinit {
        DistributedNotificationCenter.default().removeObserver(self)
    }

    // MARK: - 目录工具

    /// 扫描真实 ~/Library/CloudStorage/ 查找 PrivateCloudDisk 域目录
    private static func findCloudStorageDirectory() -> String? {
        let cloudURL = URL(fileURLWithPath: cloudStorageBase)

        guard let contents = try? FileManager.default.contentsOfDirectory(
            at: cloudURL,
            includingPropertiesForKeys: [.nameKey],
            options: [.skipsHiddenFiles]
        ) else {
            return nil
        }

        // 查找以 "PrivateCloudDisk" 开头的目录
        if let match = contents.first(where: { $0.lastPathComponent.hasPrefix("PrivateCloudDisk") }) {
            return match.path
        }

        return nil
    }

    /// 确保回退目录存在
    private func ensureFallbackDirectoryExists() {
        let fallbackURL = URL(fileURLWithPath: Self.fallbackMountPoint)
        if !FileManager.default.fileExists(atPath: Self.fallbackMountPoint) {
            try? FileManager.default.createDirectory(
                at: fallbackURL,
                withIntermediateDirectories: true
            )
            logger.info("创建回退目录: \(Self.fallbackMountPoint, privacy: .public)")
        }
    }

    // MARK: - 监控设置

    /// 设置 Finder 监控目录
    ///
    /// 同时监控 CloudStorage 路径（如果存在）和本地回退路径，
    /// 确保工具栏图标在两种场景下都能显示。
    ///
    /// 注意：FIFinderSyncController.default().directoryURLs 会授予
    /// 扩展访问这些目录的权限，因此即使路径在沙盒外部，扩展也能监控。
    private func setupMonitoring() {
        var monitoredURLs: [URL] = []

        // 1. 添加从共享 UserDefaults 读取的 CloudStorage 路径（主应用写入）
        if let sharedPath = sharedDefaults?.string(forKey: "fp.mountPoint"),
           !sharedPath.isEmpty {
            let sharedURL = URL(fileURLWithPath: sharedPath)
            monitoredURLs.append(sharedURL)
            logger.info("添加共享 UserDefaults 路径: \(sharedPath, privacy: .public)")
        }

        // 2. 扫描真实 CloudStorage 查找匹配目录
        if let cloudPath = Self.findCloudStorageDirectory() {
            let cloudURL = URL(fileURLWithPath: cloudPath)
            if !monitoredURLs.contains(cloudURL) {
                monitoredURLs.append(cloudURL)
                logger.info("添加 CloudStorage 扫描路径: \(cloudPath, privacy: .public)")
            }
        }

        // 3. 始终添加回退目录
        let fallbackURL = URL(fileURLWithPath: Self.fallbackMountPoint)
        if !monitoredURLs.contains(fallbackURL) {
            monitoredURLs.append(fallbackURL)
        }

        FIFinderSyncController.default().directoryURLs = Set(monitoredURLs)

        logger.info("监控目录设置完成: \(monitoredURLs.count) 个目录")
        for url in monitoredURLs {
            logger.debug("  - \(url.path, privacy: .public)")
        }
    }

    /// 更新监控目录（挂载点变化时调用）
    private func updateMonitoredFolder() {
        logger.info("更新监控目录...")
        setupMonitoring()
        currentMonitoredPath = mountPoint
    }

    // MARK: - 分布式通知监听

    private func observeDistributedNotifications() {
        let center = DistributedNotificationCenter.default()

        // 虚拟磁盘挂载
        center.addObserver(
            self,
            selector: #selector(handleVirtualDiskMounted(_:)),
            name: NSNotification.Name("com.privateclouddisk.virtualdisk.mounted"),
            object: nil,
            suspensionBehavior: .deliverImmediately
        )

        // 虚拟磁盘卸载
        center.addObserver(
            self,
            selector: #selector(handleVirtualDiskUnmounted(_:)),
            name: NSNotification.Name("com.privateclouddisk.virtualdisk.unmounted"),
            object: nil,
            suspensionBehavior: .deliverImmediately
        )

        // 同步状态变更
        center.addObserver(
            self,
            selector: #selector(handleSyncStatusChanged(_:)),
            name: NSNotification.Name("com.privateclouddisk.virtualdisk.syncStatusChanged"),
            object: nil,
            suspensionBehavior: .deliverImmediately
        )

        // 批量同步状态变更
        center.addObserver(
            self,
            selector: #selector(handleBatchSyncStatusChanged(_:)),
            name: NSNotification.Name("com.privateclouddisk.virtualdisk.batchSyncStatusChanged"),
            object: nil,
            suspensionBehavior: .deliverImmediately
        )
    }

    @objc private func handleVirtualDiskMounted(_ notification: Notification) {
        logger.info("收到挂载通知")

        if let mountPoint = notification.userInfo?["mountPoint"] as? String {
            logger.info("挂载点更新为: \(mountPoint, privacy: .public)")
        }

        DispatchQueue.main.async { [weak self] in
            self?.updateMonitoredFolder()
        }
    }

    @objc private func handleVirtualDiskUnmounted(_ notification: Notification) {
        logger.info("收到卸载通知 - 清理状态缓存并更新监控目录")

        cacheLock.lock()
        syncStatusCache.removeAll()
        cacheLock.unlock()

        DispatchQueue.main.async { [weak self] in
            self?.updateMonitoredFolder()
        }
    }

    @objc private func handleSyncStatusChanged(_ notification: Notification) {
        guard let userInfo = notification.userInfo,
              let path = userInfo["path"] as? String,
              let statusRaw = userInfo["status"] as? String,
              let status = SyncStatus(rawValue: statusRaw) else {
            logger.warning("收到无效的同步状态通知")
            return
        }

        cacheLock.lock()
        syncStatusCache[path] = status
        cacheLock.unlock()
    }

    @objc private func handleBatchSyncStatusChanged(_ notification: Notification) {
        guard let userInfo = notification.userInfo,
              let statusMap = userInfo["statuses"] as? [String: String] else {
            logger.warning("收到无效的批量同步状态通知")
            return
        }

        cacheLock.lock()
        for (path, statusRaw) in statusMap {
            if let status = SyncStatus(rawValue: statusRaw) {
                syncStatusCache[path] = status
            }
        }
        cacheLock.unlock()
    }

    // MARK: - FIFinderSync: 目录观察

    override func beginObservingDirectory(at url: URL) {
        logger.debug("开始观察目录: \(url.path, privacy: .private)")
    }

    override func endObservingDirectory(at url: URL) {
        logger.debug("停止观察目录: \(url.path, privacy: .private)")
    }

    // MARK: - FIFinderSync: 徽章

    override func requestBadgeIdentifier(for url: URL) {
        let relativePath = getRelativePath(for: url)
        let status = getSyncStatus(for: relativePath)

        switch status {
        case .synced:
            FIFinderSyncController.default().setBadgeIdentifier("", for: url)
        case .syncing:
            FIFinderSyncController.default().setBadgeIdentifier("syncing", for: url)
        case .notSynced:
            FIFinderSyncController.default().setBadgeIdentifier("notSynced", for: url)
        case .cloudOnly:
            FIFinderSyncController.default().setBadgeIdentifier("cloud", for: url)
        case .error:
            FIFinderSyncController.default().setBadgeIdentifier("error", for: url)
        case .paused:
            FIFinderSyncController.default().setBadgeIdentifier("paused", for: url)
        }
    }

    // MARK: - FIFinderSync: 工具栏

    override var toolbarItemName: String {
        "PrivateCloudDisk"
    }

    override var toolbarItemToolTip: String {
        isMounted ? "PrivateCloudDisk - 已挂载" : "PrivateCloudDisk - 未挂载"
    }

    override var toolbarItemImage: NSImage {
        if let image = NSImage(systemSymbolName: "externaldrive.fill.badge.icloud",
                                accessibilityDescription: "PrivateCloudDisk") {
            return image
        }
        return NSImage(named: NSImage.folderName)!
    }

    /// 工具栏菜单（点击 Finder 工具栏按钮时显示）
    override func menu(for menuKind: FIMenuKind) -> NSMenu? {
        switch menuKind {
        case .contextualMenuForItems, .toolbarItemMenu:
            return buildContextMenu()
        case .contextualMenuForContainer:
            return buildContainerMenu()
        case .contextualMenuForSidebar:
            return buildSidebarMenu()
        @unknown default:
            return nil
        }
    }

    // MARK: - 菜单构建

    private func buildContextMenu() -> NSMenu {
        let menu = NSMenu(title: "PrivateCloudDisk")
        let selectedItems = FIFinderSyncController.default().selectedItemURLs() ?? []

        if selectedItems.isEmpty {
            addGeneralMenuItems(to: menu)
            return menu
        }

        if selectedItems.count == 1, let itemURL = selectedItems.first {
            addSingleItemMenuItems(to: menu, for: itemURL)
        } else {
            addMultiItemMenuItems(to: menu, for: selectedItems)
        }

        menu.addItem(.separator())
        addGeneralMenuItems(to: menu)

        return menu
    }

    private func buildContainerMenu() -> NSMenu {
        let menu = NSMenu(title: "PrivateCloudDisk")
        addGeneralMenuItems(to: menu)
        return menu
    }

    private func buildSidebarMenu() -> NSMenu {
        let menu = NSMenu(title: "PrivateCloudDisk")
        addGeneralMenuItems(to: menu)
        return menu
    }

    // MARK: - 菜单构建：单个项目

    private func addSingleItemMenuItems(to menu: NSMenu, for url: URL) {
        let relativePath = getRelativePath(for: url)
        let status = getSyncStatus(for: relativePath)

        let statusItem = NSMenuItem(
            title: "状态: \(status.contextMenuTitle)",
            action: nil,
            keyEquivalent: ""
        )
        statusItem.isEnabled = false
        statusItem.image = statusIcon(for: status)
        menu.addItem(statusItem)
        menu.addItem(.separator())

        let syncItem = NSMenuItem(
            title: "立即同步",
            action: #selector(syncSelectedItem(_:)),
            keyEquivalent: ""
        )
        syncItem.target = self
        syncItem.representedObject = url
        syncItem.image = NSImage(systemSymbolName: "arrow.triangle.2.circlepath",
                                  accessibilityDescription: nil)
        menu.addItem(syncItem)

        let offlineItem = NSMenuItem(
            title: "设为离线可用",
            action: #selector(makeAvailableOffline(_:)),
            keyEquivalent: ""
        )
        offlineItem.target = self
        offlineItem.representedObject = url
        offlineItem.image = NSImage(systemSymbolName: "arrow.down.to.line",
                                     accessibilityDescription: nil)
        menu.addItem(offlineItem)

        let freeSpaceItem = NSMenuItem(
            title: "释放本地空间",
            action: #selector(freeUpSpace(_:)),
            keyEquivalent: ""
        )
        freeSpaceItem.target = self
        freeSpaceItem.representedObject = url
        freeSpaceItem.image = NSImage(systemSymbolName: "trash",
                                       accessibilityDescription: nil)
        menu.addItem(freeSpaceItem)

        menu.addItem(.separator())

        let shareLinkItem = NSMenuItem(
            title: "复制分享链接",
            action: #selector(copyShareLink(_:)),
            keyEquivalent: ""
        )
        shareLinkItem.target = self
        shareLinkItem.representedObject = url
        shareLinkItem.image = NSImage(systemSymbolName: "link",
                                       accessibilityDescription: nil)
        menu.addItem(shareLinkItem)

        let createShareItem = NSMenuItem(
            title: "创建分享链接...",
            action: #selector(createShareLink(_:)),
            keyEquivalent: ""
        )
        createShareItem.target = self
        createShareItem.representedObject = url
        createShareItem.image = NSImage(systemSymbolName: "square.and.arrow.up",
                                         accessibilityDescription: nil)
        menu.addItem(createShareItem)

        menu.addItem(.separator())

        let infoItem = NSMenuItem(
            title: "获取文件信息",
            action: #selector(getFileInfo(_:)),
            keyEquivalent: ""
        )
        infoItem.target = self
        infoItem.representedObject = url
        infoItem.image = NSImage(systemSymbolName: "info.circle",
                                  accessibilityDescription: nil)
        menu.addItem(infoItem)

        let revealItem = NSMenuItem(
            title: "在 Finder 中显示",
            action: #selector(revealInFinder(_:)),
            keyEquivalent: ""
        )
        revealItem.target = self
        revealItem.representedObject = url
        revealItem.image = NSImage(systemSymbolName: "eye",
                                    accessibilityDescription: nil)
        menu.addItem(revealItem)
    }

    // MARK: - 菜单构建：多个项目

    private func addMultiItemMenuItems(to menu: NSMenu, for urls: [URL]) {
        let countItem = NSMenuItem(
            title: "已选择 \(urls.count) 个项目",
            action: nil,
            keyEquivalent: ""
        )
        countItem.isEnabled = false
        menu.addItem(countItem)
        menu.addItem(.separator())

        let syncItem = NSMenuItem(
            title: "同步所选项目",
            action: #selector(syncSelectedItems(_:)),
            keyEquivalent: ""
        )
        syncItem.target = self
        syncItem.representedObject = urls
        syncItem.image = NSImage(systemSymbolName: "arrow.triangle.2.circlepath",
                                  accessibilityDescription: nil)
        menu.addItem(syncItem)

        let downloadItem = NSMenuItem(
            title: "下载所选项目",
            action: #selector(downloadSelectedItems(_:)),
            keyEquivalent: ""
        )
        downloadItem.target = self
        downloadItem.representedObject = urls
        downloadItem.image = NSImage(systemSymbolName: "arrow.down.to.line",
                                      accessibilityDescription: nil)
        menu.addItem(downloadItem)

        let freeSpaceItem = NSMenuItem(
            title: "释放所选项目本地空间",
            action: #selector(freeUpSelectedItems(_:)),
            keyEquivalent: ""
        )
        freeSpaceItem.target = self
        freeSpaceItem.representedObject = urls
        freeSpaceItem.image = NSImage(systemSymbolName: "trash",
                                       accessibilityDescription: nil)
        menu.addItem(freeSpaceItem)
    }

    // MARK: - 菜单构建：通用

    private func addGeneralMenuItems(to menu: NSMenu) {
        let statusTitle = isMounted ? "状态: 已挂载" : "状态: 未挂载"
        let statusItem = NSMenuItem(
            title: statusTitle,
            action: nil,
            keyEquivalent: ""
        )
        statusItem.isEnabled = false
        statusItem.image = NSImage(
            systemSymbolName: isMounted ? "externaldrive.fill" : "externaldrive.badge.timemachine",
            accessibilityDescription: nil
        )
        menu.addItem(statusItem)
        menu.addItem(.separator())

        let openAppItem = NSMenuItem(
            title: "打开 PrivateCloudDisk",
            action: #selector(openMainApp(_:)),
            keyEquivalent: ""
        )
        openAppItem.target = self
        openAppItem.image = NSImage(systemSymbolName: "app.badge",
                                     accessibilityDescription: nil)
        menu.addItem(openAppItem)

        menu.addItem(.separator())

        let refreshItem = NSMenuItem(
            title: "刷新同步状态",
            action: #selector(refreshStatus(_:)),
            keyEquivalent: ""
        )
        refreshItem.target = self
        refreshItem.image = NSImage(systemSymbolName: "arrow.clockwise",
                                     accessibilityDescription: nil)
        menu.addItem(refreshItem)

        if isMounted {
            let unmountItem = NSMenuItem(
                title: "卸载 PrivateCloudDisk",
                action: #selector(unmountDisk(_:)),
                keyEquivalent: ""
            )
            unmountItem.target = self
            unmountItem.image = NSImage(systemSymbolName: "eject",
                                         accessibilityDescription: nil)
            menu.addItem(unmountItem)
        } else {
            let mountItem = NSMenuItem(
                title: "挂载 PrivateCloudDisk",
                action: #selector(mountDisk(_:)),
                keyEquivalent: ""
            )
            mountItem.target = self
            mountItem.image = NSImage(systemSymbolName: "externaldrive.badge.plus",
                                       accessibilityDescription: nil)
            menu.addItem(mountItem)
        }
    }

    // MARK: - 菜单动作：单个文件

    @objc private func syncSelectedItem(_ sender: NSMenuItem) {
        guard let url = sender.representedObject as? URL else { return }
        postToMainApp(action: "sync", path: getRelativePath(for: url))
    }

    @objc private func makeAvailableOffline(_ sender: NSMenuItem) {
        guard let url = sender.representedObject as? URL else { return }
        postToMainApp(action: "makeOffline", path: getRelativePath(for: url))
    }

    @objc private func freeUpSpace(_ sender: NSMenuItem) {
        guard let url = sender.representedObject as? URL else { return }
        postToMainApp(action: "freeSpace", path: getRelativePath(for: url))
    }

    @objc private func copyShareLink(_ sender: NSMenuItem) {
        guard let url = sender.representedObject as? URL else { return }
        postToMainApp(action: "copyShareLink", path: getRelativePath(for: url))
    }

    @objc private func createShareLink(_ sender: NSMenuItem) {
        guard let url = sender.representedObject as? URL else { return }
        postToMainApp(action: "createShareLink", path: getRelativePath(for: url))
    }

    @objc private func revealInFinder(_ sender: NSMenuItem) {
        guard let url = sender.representedObject as? URL else { return }
        NSWorkspace.shared.activateFileViewerSelecting([url])
    }

    @objc private func getFileInfo(_ sender: NSMenuItem) {
        guard let url = sender.representedObject as? URL else { return }
        postToMainApp(action: "fileInfo", path: getRelativePath(for: url))
    }

    // MARK: - 菜单动作：多个文件

    @objc private func syncSelectedItems(_ sender: NSMenuItem) {
        guard let urls = sender.representedObject as? [URL] else { return }
        let paths = urls.map { getRelativePath(for: $0) }
        postToMainApp(action: "sync", paths: paths)
    }

    @objc private func downloadSelectedItems(_ sender: NSMenuItem) {
        guard let urls = sender.representedObject as? [URL] else { return }
        let paths = urls.map { getRelativePath(for: $0) }
        postToMainApp(action: "download", paths: paths)
    }

    @objc private func freeUpSelectedItems(_ sender: NSMenuItem) {
        guard let urls = sender.representedObject as? [URL] else { return }
        let paths = urls.map { getRelativePath(for: $0) }
        postToMainApp(action: "freeSpace", paths: paths)
    }

    // MARK: - 菜单动作：通用

    @objc private func openMainApp(_ sender: NSMenuItem) {
        let bundleId = "com.spadek.PrivateCloudDisk-macos"
        if let appURL = NSWorkspace.shared.urlForApplication(withBundleIdentifier: bundleId) {
            let config = NSWorkspace.OpenConfiguration()
            NSWorkspace.shared.openApplication(at: appURL, configuration: config)
        }
    }

    @objc private func refreshStatus(_ sender: NSMenuItem) {
        postToMainApp(action: "refreshStatus", path: nil)
    }

    @objc private func mountDisk(_ sender: NSMenuItem) {
        logger.info("FinderSync 请求挂载虚拟磁盘")
        postToMainApp(action: "mount", path: nil)
    }

    @objc private func unmountDisk(_ sender: NSMenuItem) {
        logger.info("FinderSync 请求卸载虚拟磁盘")
        postToMainApp(action: "unmount", path: nil)
    }

    // MARK: - 与主应用通信

    /// 向主应用发送操作指令（带单个路径）
    private func postToMainApp(action: String, path: String?) {
        var userInfo: [String: Any] = ["action": action]
        if let p = path {
            userInfo["path"] = p
        }

        DistributedNotificationCenter.default().postNotificationName(
            NSNotification.Name("com.privateclouddisk.finder.action"),
            object: nil,
            userInfo: userInfo,
            deliverImmediately: true
        )

        logger.debug("发送操作: \(action, privacy: .public), path: \(path ?? "nil", privacy: .public)")
    }

    /// 向主应用发送操作指令（带多个路径）
    private func postToMainApp(action: String, paths: [String]) {
        let userInfo: [String: Any] = ["action": action, "paths": paths]

        DistributedNotificationCenter.default().postNotificationName(
            NSNotification.Name("com.privateclouddisk.finder.action"),
            object: nil,
            userInfo: userInfo,
            deliverImmediately: true
        )

        logger.debug("发送批量操作: \(action, privacy: .public), count: \(paths.count)")
    }

    // MARK: - 辅助方法

    /// 获取文件相对于当前监控目录的路径
    private func getRelativePath(for url: URL) -> String {
        let mountURL = URL(fileURLWithPath: currentMonitoredPath)
        var relativePath = url.path.replacingOccurrences(of: mountURL.path, with: "")
        if relativePath.hasPrefix("/") {
            relativePath = String(relativePath.dropFirst())
        }
        return relativePath
    }

    /// 获取文件的同步状态
    private func getSyncStatus(for relativePath: String) -> SyncStatus {
        cacheLock.lock()
        defer { cacheLock.unlock() }
        return syncStatusCache[relativePath] ?? .synced
    }

    /// 同步状态对应的 SF Symbol 图标
    private func statusIcon(for status: SyncStatus) -> NSImage? {
        let symbolName: String = switch status {
        case .synced:    "checkmark.icloud"
        case .syncing:   "arrow.triangle.2.circlepath.icloud"
        case .notSynced: "icloud.slash"
        case .cloudOnly: "icloud"
        case .error:     "exclamationmark.icloud"
        case .paused:    "pause.icloud"
        }
        return NSImage(systemSymbolName: symbolName, accessibilityDescription: status.contextMenuTitle)
    }
}
