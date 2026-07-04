import FileProvider
import os.log

// MARK: - File Provider Enumerator

/// 文件枚举器，负责遍历指定容器（目录）中的子项
///
/// 系统通过此枚举器获取目录内容列表，支持分页和增量变更。
/// 枚举器分为两种模式：
/// - **初始枚举**：`enumerateItems(for:startingAt:)` 返回完整目录内容（首次打开目录时触发）
/// - **增量变更**：`enumerateChanges(for:from:)` 返回自上次同步锚点以来的变更
///
/// 与 iCloud Drive 的工作方式一致，系统会：
/// 1. 首次枚举所有项目
/// 2. 保存当前同步锚点
/// 3. 后续定期请求增量变更
/// 4. 用户手动刷新时触发重新枚举
final class FileProviderEnumerator: NSObject, NSFileProviderEnumerator {

    // MARK: - 日志

    private let logger = Logger(subsystem: "com.privateclouddisk.fileprovider", category: "Enumerator")

    // MARK: - 属性

    /// 被枚举的容器标识符
    private let enumeratedItemIdentifier: NSFileProviderItemIdentifier

    /// 对 FileProviderExtension 的弱引用（避免循环引用）
    private weak var fileProviderExtension: FileProviderExtension?

    // MARK: - 初始化

    init(
        enumeratedItemIdentifier: NSFileProviderItemIdentifier,
        fileProviderExtension: FileProviderExtension
    ) {
        self.enumeratedItemIdentifier = enumeratedItemIdentifier
        self.fileProviderExtension = fileProviderExtension
        super.init()
        logger.debug("FileProviderEnumerator 初始化: \(enumeratedItemIdentifier.rawValue, privacy: .public)")
    }

    // MARK: - 生命周期

    func invalidate() {
        logger.debug("FileProviderEnumerator invalidate: \(self.enumeratedItemIdentifier.rawValue, privacy: .public)")
    }

    // MARK: - 初始枚举

    /// 枚举指定容器中的所有项目
    ///
    /// 系统在以下情况调用此方法：
    /// - 首次打开 File Provider 域
    /// - 用户展开 Finder 中的文件夹
    /// - 用户手动刷新（Cmd+R）
    /// - 应用调用 `signalEnumerator(for:)` 触发
    ///
    /// - Parameters:
    ///   - observer: 枚举观察者，用于报告枚举进度和结果
    ///   - page: 分页标记（用于分批返回大量结果）
    func enumerateItems(
        for observer: NSFileProviderEnumerationObserver,
        startingAt page: NSFileProviderPage
    ) {
        logger.info("开始枚举: \(self.enumeratedItemIdentifier.rawValue, privacy: .public), page: \(page.rawValue)")

        guard let ext = fileProviderExtension else {
            logger.error("枚举失败: FileProviderExtension 已释放")
            observer.finishEnumerating(upTo: nil)
            return
        }

        Task { @MainActor in
            do {
                let items = try await ext.enumerateItems(for: enumeratedItemIdentifier)
                observer.didEnumerate(items)
                observer.finishEnumerating(upTo: nil)
                logger.info("枚举完成: \(items.count) 个项目")
            } catch {
                logger.error("枚举失败: \(error.localizedDescription, privacy: .public)")
                observer.finishEnumeratingWithError(error)
            }
        }
    }

    // MARK: - 增量变更

    /// 枚举自上次同步锚点以来的变更
    ///
    /// 系统定期调用此方法，获取增量变更。实现增量同步可以：
    /// - 减少网络请求
    /// - 提升同步效率
    /// - 支持实时更新
    ///
    /// 当前实现返回完整同步（moreComing: false），
    /// 后续可基于服务器时间戳或变更日志实现真正的增量同步。
    ///
    /// - Parameters:
    ///   - observer: 变更观察者，用于报告变更项目
    ///   - anchor: 上次同步锚点
    func enumerateChanges(
        for observer: NSFileProviderChangeObserver,
        from anchor: NSFileProviderSyncAnchor
    ) {
        logger.debug("查询增量变更, anchor: \(anchor.rawValue)")

        guard let ext = fileProviderExtension else {
            logger.error("增量变更枚举失败: FileProviderExtension 已释放")
            observer.finishEnumeratingChanges(upTo: anchor, moreComing: false)
            return
        }

        Task { @MainActor in
            do {
                let items = try await ext.enumerateItems(for: enumeratedItemIdentifier)

                // 构建更新和删除列表
                var updatedItems: [FileProviderItem] = []
                for item in items {
                    let cached = ext.getCachedItem(item.itemIdentifier)
                    if cached == nil || cached?.contentModificationDate != item.contentModificationDate {
                        updatedItems.append(item)
                    }
                }

                if !updatedItems.isEmpty {
                    observer.didUpdate(updatedItems)
                }

                // 生成新的同步锚点
                let newAnchorData = "\(Date().timeIntervalSince1970)".data(using: .utf8)!
                let newAnchor = NSFileProviderSyncAnchor(newAnchorData)

                observer.finishEnumeratingChanges(upTo: newAnchor, moreComing: false)
                logger.info("增量变更枚举完成: \(updatedItems.count) 个更新")
            } catch {
                logger.error("增量变更枚举失败: \(error.localizedDescription, privacy: .public)")
                observer.finishEnumeratingChanges(upTo: anchor, moreComing: false)
            }
        }
    }

    // MARK: - 同步锚点

    /// 获取当前同步锚点
    ///
    /// 系统在完成初始枚举后调用此方法，保存当前同步状态。
    /// 后续 `enumerateChanges(for:from:)` 会使用此锚点作为增量同步的起点。
    ///
    /// - Parameter completionHandler: 完成回调，返回当前同步锚点
    func currentSyncAnchor(completionHandler: @escaping (NSFileProviderSyncAnchor?) -> Void) {
        let anchorData = "\(Date().timeIntervalSince1970)".data(using: .utf8)!
        let anchor = NSFileProviderSyncAnchor(anchorData)
        logger.debug("生成同步锚点: \(anchor.rawValue)")
        completionHandler(anchor)
    }
}