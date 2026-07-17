import FileProvider
import Foundation
import UniformTypeIdentifiers
import os.log

// MARK: - File Provider Extension

/// PrivateCloudDisk File Provider 扩展
///
/// 实现 macOS 原生 File Provider 扩展，在 Finder 侧边栏中显示云盘内容，
/// 与 iCloud Drive 同级展示。支持按需下载、离线缓存、文件操作等功能。
///
/// 使用 NSFileProviderReplicatedExtension（macOS 26.5+ completion-handler API），
/// 这是 Apple 最新的 API，取代了早期的 async/await 版本。
///
/// 核心功能：
/// - 文件枚举（浏览目录结构）
/// - 按需下载（materialization）
/// - 文件上传（创建新文件，支持分块上传）
/// - 文件修改（重命名、移动、内容替换）
/// - 文件删除（软删除到回收站）
/// - 文件导入（从其他应用拖入）
/// - 本地缓存管理与自动清理
///
/// 通信方式：
/// - App Group UserDefaults（共享配置和认证信息）
/// - App Group Container（共享文件缓存）
/// - NSFileProviderManager（域管理和信号通知）
/// - DistributedNotificationCenter（跨进程事件通知）
final class FileProviderExtension: NSObject, NSFileProviderReplicatedExtension, NSFileProviderEnumerating {

    // MARK: - 日志

    private let logger = Logger(subsystem: "com.privateclouddisk.fileprovider", category: "Extension")

    // MARK: - 属性

    private let domain: NSFileProviderDomain
    private let manager: NSFileProviderManager?

    /// 共享 UserDefaults（通过 App Group 与主应用通信）
    private lazy var sharedDefaults: UserDefaults? = {
        UserDefaults(suiteName: AppGroup.identifier)
    }()

    /// 本地缓存目录（位于 App Group Container 中，主应用和扩展共享）
    private lazy var cacheDirectory: URL = {
        guard let containerURL = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: AppGroup.identifier
        ) else {
            // 降级：使用临时目录
            let fallback = FileManager.default.temporaryDirectory
                .appendingPathComponent("FileProviderCache", isDirectory: true)
            try? FileManager.default.createDirectory(at: fallback, withIntermediateDirectories: true)
            return fallback
        }
        let cacheURL = containerURL
            .appendingPathComponent("Library", isDirectory: true)
            .appendingPathComponent("Caches", isDirectory: true)
            .appendingPathComponent("FileProvider", isDirectory: true)
        try? FileManager.default.createDirectory(at: cacheURL, withIntermediateDirectories: true)
        return cacheURL
    }()

    /// 内存缓存：itemIdentifier → FileProviderItem
    private var itemCache: [NSFileProviderItemIdentifier: FileProviderItem] = [:]
    private let cacheLock = NSLock()

    /// 根节点 ID 缓存（避免每次枚举都请求根节点）
    private var cachedRootNodeId: String?

    /// 当前认证 Token
    private var authToken: String? {
        sharedDefaults?.string(forKey: AppGroup.Keys.authToken)
    }

    /// API 基础 URL（与主应用一致：http://localhost:8080/api/v1/）
    private var apiBaseURL: String {
        let url = sharedDefaults?.string(forKey: AppGroup.Keys.apiBaseURL) ?? "http://localhost:8080/api/v1/"
        // 确保以 / 结尾
        return url.hasSuffix("/") ? url : url + "/"
    }

    /// 当前用户 ID
    private var currentUserId: String? {
        sharedDefaults?.string(forKey: AppGroup.Keys.userId)
    }

    /// URLSession（针对文件传输优化）
    private lazy var urlSession: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 60
        config.timeoutIntervalForResource = 600
        config.httpMaximumConnectionsPerHost = 4
        config.waitsForConnectivity = true
        config.urlCache = nil // 禁用 URL 缓存，使用自定义缓存
        return URLSession(configuration: config)
    }()

    /// 上传专用的 URLSession（更长的超时）
    private lazy var uploadSession: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 120
        config.timeoutIntervalForResource = 3600
        config.httpMaximumConnectionsPerHost = 2
        config.waitsForConnectivity = true
        config.urlCache = nil
        return URLSession(configuration: config)
    }()

    // MARK: - 初始化

    required init(domain: NSFileProviderDomain) {
        self.domain = domain
        self.manager = NSFileProviderManager(for: domain)
        super.init()
        logger.info("FileProviderExtension 初始化完成, domain: \(domain.identifier.rawValue, privacy: .public)")
    }

    // MARK: - 生命周期

    func invalidate() {
        logger.info("FileProviderExtension invalidate - 清理缓存")
        cacheLock.lock()
        itemCache.removeAll()
        cacheLock.unlock()
    }

    // MARK: - NSFileProviderEnumerating

    func enumerator(
        for containerItemIdentifier: NSFileProviderItemIdentifier,
        request: NSFileProviderRequest
    ) throws -> any NSFileProviderEnumerator {
        logger.debug("请求枚举器: \(containerItemIdentifier.rawValue, privacy: .public)")
        return FileProviderEnumerator(
            enumeratedItemIdentifier: containerItemIdentifier,
            fileProviderExtension: self
        )
    }

    // MARK: - NSFileProviderReplicatedExtension: 项目查询

    func item(
        for identifier: NSFileProviderItemIdentifier,
        request: NSFileProviderRequest,
        completionHandler: @escaping (NSFileProviderItem?, (any Error)?) -> Void
    ) -> Progress {
        logger.debug("查询项目: \(identifier.rawValue, privacy: .public)")

        let progress = Progress(totalUnitCount: 1)

        // 根容器
        if identifier == .rootContainer {
            completionHandler(FileProviderItem.rootContainer(), nil)
            return progress
        }

        // 回收站容器
        if identifier == .trashContainer {
            completionHandler(FileProviderItem.trashContainer(), nil)
            return progress
        }

        // 工作集
        if identifier == .workingSet {
            completionHandler(FileProviderItem.workingSet(), nil)
            return progress
        }

        // 从缓存获取
        if let cached = getCachedItem(identifier) {
            completionHandler(cached, nil)
            return progress
        }

        // 从服务器获取
        Task {
            do {
                let item = try await fetchItemFromServer(itemIdentifier: identifier)
                completionHandler(item, nil)
            } catch {
                logger.error("查询项目失败: \(error.localizedDescription, privacy: .public)")
                completionHandler(nil, error)
            }
        }

        return progress
    }

    // MARK: - NSFileProviderReplicatedExtension: 获取内容（下载）

    func fetchContents(
        for itemIdentifier: NSFileProviderItemIdentifier,
        version requestedVersion: NSFileProviderItemVersion?,
        request: NSFileProviderRequest,
        completionHandler: @escaping (URL?, NSFileProviderItem?, (any Error)?) -> Void
    ) -> Progress {
        logger.info("获取文件内容: \(itemIdentifier.rawValue, privacy: .public)")

        let progress = Progress(totalUnitCount: 100)

        let cachedItem = getCachedItem(itemIdentifier)

        // 检查本地缓存
        let localURL = cacheDirectory.appendingPathComponent(itemIdentifier.rawValue)
        if FileManager.default.fileExists(atPath: localURL.path) {
            logger.debug("使用本地缓存: \(localURL.path, privacy: .private)")
            let updatedItem = cachedItem.map { item in
                FileProviderItem(
                    itemIdentifier: item.itemIdentifier,
                    parentItemIdentifier: item.parentItemIdentifier,
                    filename: item.filename,
                    contentType: item.contentType,
                    documentSize: item.documentSize,
                    creationDate: item.creationDate,
                    contentModificationDate: item.contentModificationDate,
                    isUploaded: true,
                    isDownloaded: true,
                    isMostRecentVersionDownloaded: true,
                    fileNode: item.fileNode
                )
            }
            completionHandler(localURL, updatedItem, nil)
            return progress
        }

        // 从服务器下载
        Task {
            do {
                let (fileURL, item) = try await downloadFile(
                    itemIdentifier: itemIdentifier,
                    cachedItem: cachedItem
                )
                completionHandler(fileURL, item, nil)
            } catch {
                logger.error("下载失败: \(error.localizedDescription, privacy: .public)")
                completionHandler(nil, nil, error)
            }
        }

        return progress
    }

    // MARK: - NSFileProviderReplicatedExtension: 创建项目

    func createItem(
        basedOn itemTemplate: NSFileProviderItem,
        fields: NSFileProviderItemFields,
        contents url: URL?,
        options: NSFileProviderCreateItemOptions = [],
        request: NSFileProviderRequest,
        completionHandler: @escaping (NSFileProviderItem?, NSFileProviderItemFields, Bool, (any Error)?) -> Void
    ) -> Progress {
        logger.info("创建项目: \(itemTemplate.filename, privacy: .public), 父级: \(itemTemplate.parentItemIdentifier.rawValue, privacy: .public)")

        let progress = Progress(totalUnitCount: 100)

        Task {
            do {
                // 创建文件夹
                if itemTemplate.contentType == .folder {
                    let item = try await createFolder(
                        name: itemTemplate.filename,
                        parentIdentifier: itemTemplate.parentItemIdentifier
                    )
                    completionHandler(item, [], false, nil)
                    return
                }

                // 上传文件
                guard let contentsURL = url else {
                    logger.error("创建文件失败: 缺少文件内容 URL")
                    completionHandler(nil, [], false, NSFileProviderError(.noSuchItem))
                    return
                }

                let item = try await uploadFile(
                    from: contentsURL,
                    filename: itemTemplate.filename,
                    parentIdentifier: itemTemplate.parentItemIdentifier
                )
                completionHandler(item, [], false, nil)
            } catch {
                logger.error("创建项目失败: \(error.localizedDescription, privacy: .public)")
                completionHandler(nil, [], false, error)
            }
        }

        return progress
    }

    // MARK: - NSFileProviderReplicatedExtension: 修改项目

    func modifyItem(
        _ item: NSFileProviderItem,
        baseVersion version: NSFileProviderItemVersion,
        changedFields: NSFileProviderItemFields,
        contents newContents: URL?,
        options: NSFileProviderModifyItemOptions = [],
        request: NSFileProviderRequest,
        completionHandler: @escaping (NSFileProviderItem?, NSFileProviderItemFields, Bool, (any Error)?) -> Void
    ) -> Progress {
        logger.info("修改项目: \(item.filename, privacy: .public), changedFields: \(changedFields.rawValue, privacy: .public)")

        let progress = Progress(totalUnitCount: 100)

        Task {
            do {
                var modifiedItem = item

                // 处理重命名
                if changedFields.contains(.filename) {
                    modifiedItem = try await renameItem(
                        itemIdentifier: item.itemIdentifier,
                        newName: item.filename
                    )
                }

                // 处理移动（更改父目录）
                if changedFields.contains(.parentItemIdentifier) {
                    modifiedItem = try await moveItem(
                        itemIdentifier: item.itemIdentifier,
                        newParentIdentifier: item.parentItemIdentifier
                    )
                }

                // 处理内容修改
                if let contentsURL = newContents {
                    modifiedItem = try await replaceFileContent(
                        itemIdentifier: item.itemIdentifier,
                        contentsURL: contentsURL
                    )
                }

                completionHandler(modifiedItem, [], false, nil)
            } catch {
                logger.error("修改项目失败: \(error.localizedDescription, privacy: .public)")
                completionHandler(nil, [], false, error)
            }
        }

        return progress
    }

    // MARK: - NSFileProviderReplicatedExtension: 删除项目

    func deleteItem(
        identifier: NSFileProviderItemIdentifier,
        baseVersion version: NSFileProviderItemVersion,
        options: NSFileProviderDeleteItemOptions = [],
        request: NSFileProviderRequest,
        completionHandler: @escaping ((any Error)?) -> Void
    ) -> Progress {
        logger.info("删除项目: \(identifier.rawValue, privacy: .public)")

        let progress = Progress(totalUnitCount: 100)

        Task {
            do {
                try await deleteItemOnServer(itemIdentifier: identifier)
                // 清理本地缓存
                clearLocalCache(for: identifier)
                completionHandler(nil)
            } catch {
                logger.error("删除项目失败: \(error.localizedDescription, privacy: .public)")
                completionHandler(error)
            }
        }

        return progress
    }

    // MARK: - NSFileProviderReplicatedExtension: 导入文档

    func importDocument(
        at fileURL: URL,
        toParentItemIdentifier parentItemIdentifier: NSFileProviderItemIdentifier,
        completionHandler: @escaping (NSFileProviderItem?, (any Error)?) -> Void
    ) -> Progress {
        logger.info("导入文档: \(fileURL.lastPathComponent, privacy: .public)")

        let progress = Progress(totalUnitCount: 100)

        Task {
            do {
                let item = try await uploadFile(
                    from: fileURL,
                    filename: fileURL.lastPathComponent,
                    parentIdentifier: parentItemIdentifier
                )
                completionHandler(item, nil)
            } catch {
                logger.error("导入文档失败: \(error.localizedDescription, privacy: .public)")
                completionHandler(nil, error)
            }
        }

        return progress
    }

    // MARK: - 枚举子项（供 FileProviderEnumerator 调用）

    func enumerateItems(
        for identifier: NSFileProviderItemIdentifier
    ) async throws -> [FileProviderItem] {
        logger.info("枚举子项: \(identifier.rawValue, privacy: .public)")

        let nodes: [FileNode]

        // 根容器：先获取根节点 ID，再获取子节点
        if identifier == .rootContainer {
            // 优先使用缓存的根节点 ID
            if let cachedRootId = cachedRootNodeId {
                nodes = try await fetchChildren(nodeId: cachedRootId)
            } else {
                // 获取根节点
                let rootNode: FolderNode = try await fetchRootNode()
                cachedRootNodeId = rootNode.nodeId
                nodes = try await fetchChildren(nodeId: rootNode.nodeId)
            }
        } else {
            // 非根容器：直接获取子节点
            nodes = try await fetchChildren(nodeId: identifier.rawValue)
        }

        let parentIdentifier = identifier
        let providerItems = nodes.map { node in
            FileProviderItem(from: node, parentIdentifier: parentIdentifier)
        }

        // 更新缓存
        cacheLock.lock()
        for item in providerItems {
            itemCache[item.itemIdentifier] = item
        }
        cacheLock.unlock()

        logger.info("枚举完成: \(providerItems.count) 个项目")
        return providerItems
    }

    // MARK: - 缓存管理

    func getCachedItem(_ identifier: NSFileProviderItemIdentifier) -> FileProviderItem? {
        cacheLock.lock()
        defer { cacheLock.unlock() }
        return itemCache[identifier]
    }

    private func clearLocalCache(for identifier: NSFileProviderItemIdentifier) {
        cacheLock.lock()
        itemCache.removeValue(forKey: identifier)
        cacheLock.unlock()

        // 删除本地缓存文件
        let localURL = cacheDirectory.appendingPathComponent(identifier.rawValue)
        if FileManager.default.fileExists(atPath: localURL.path) {
            try? FileManager.default.removeItem(at: localURL)
            logger.debug("已删除本地缓存: \(localURL.path, privacy: .private)")
        }
    }

    // MARK: - API 通信

    /// 构建标准 API 请求
    ///
    /// 路径参数是相对于 apiBaseURL 的路径，例如 "business/nodes/root"
    private func makeRequest(
        path: String,
        method: String = "GET",
        body: Data? = nil,
        contentType: String = "application/json"
    ) -> URLRequest {
        let fullPath = apiBaseURL + path
        guard let url = URL(string: fullPath) else {
            logger.error("无效的 API URL: \(fullPath, privacy: .public)")
            return URLRequest(url: URL(string: "http://localhost/invalid")!)
        }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue(contentType, forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let token = authToken, !token.isEmpty {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.setValue("macOS-FileProvider/1.0", forHTTPHeaderField: "X-Client-Type")
        request.httpBody = body
        request.timeoutInterval = 30
        return request
    }

    /// 通用 API 调用（泛型）
    ///
    /// 统一使用 .convertFromSnakeCase 解码，模型中不需要手动 CodingKeys
    private func apiCall<T: Decodable>(_ request: URLRequest) async throws -> T {
        let (data, response) = try await urlSession.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            logger.error("API 调用失败: 无效的响应类型")
            throw NSFileProviderError(.serverUnreachable)
        }

        // 认证失败
        if httpResponse.statusCode == 401 {
            logger.error("API 调用失败: 认证失败 (401)")
            // 通知主应用重新登录
            notifyMainApp(event: "authExpired", userInfo: nil)
            throw NSFileProviderError(.notAuthenticated)
        }

        // 权限不足
        if httpResponse.statusCode == 403 {
            logger.error("API 调用失败: 权限不足 (403)")
            throw NSFileProviderError(.insufficientQuota)
        }

        // 服务器错误
        guard (200...299).contains(httpResponse.statusCode) else {
            logger.error("API 调用失败: HTTP \(httpResponse.statusCode)")
            throw NSFileProviderError(.serverUnreachable)
        }

        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase

        let apiResponse: ApiResponse<T>
        do {
            apiResponse = try decoder.decode(ApiResponse<T>.self, from: data)
        } catch {
            logger.error("API 响应解码失败: \(error.localizedDescription, privacy: .public)")
            throw NSFileProviderError(.serverUnreachable)
        }

        guard let result = apiResponse.data, apiResponse.code == 200 else {
            logger.error("API 业务错误: code=\(apiResponse.code), message=\(apiResponse.message ?? "nil", privacy: .public)")
            throw NSFileProviderError(.serverUnreachable)
        }

        return result
    }

    // MARK: - 服务器操作

    /// 获取根节点
    private func fetchRootNode() async throws -> FolderNode {
        let request = makeRequest(path: "business/nodes/root")
        return try await apiCall(request)
    }

    /// 获取节点的子节点列表
    private func fetchChildren(nodeId: String) async throws -> [FileNode] {
        let request = makeRequest(path: "business/nodes/\(nodeId)/children")
        return try await apiCall(request)
    }

    /// 获取文件列表（兼容旧调用，内部使用 fetchChildren）
    private func fetchFileList(parentId: String?) async throws -> [FileNode] {
        if let parentId = parentId {
            return try await fetchChildren(nodeId: parentId)
        } else {
            // 根目录：先获取根节点，再获取子节点
            let rootNode: FolderNode = try await fetchRootNode()
            cachedRootNodeId = rootNode.nodeId
            return try await fetchChildren(nodeId: rootNode.nodeId)
        }
    }

    /// 获取单个文件元数据
    private func fetchItemFromServer(itemIdentifier: NSFileProviderItemIdentifier) async throws -> FileProviderItem {
        // 使用节点路径接口获取节点信息
        let request = makeRequest(path: "business/nodes/\(itemIdentifier.rawValue)/path")
        let pathInfo: PathInfo = try await apiCall(request)
        let node = pathInfo.node

        let parentId = node.parentId ?? NSFileProviderItemIdentifier.rootContainer.rawValue
        let item = FileProviderItem(from: node, parentIdentifier: NSFileProviderItemIdentifier(rawValue: parentId))

        // 更新缓存
        cacheLock.lock()
        itemCache[itemIdentifier] = item
        cacheLock.unlock()

        return item
    }

    /// 下载文件到本地缓存
    private func downloadFile(
        itemIdentifier: NSFileProviderItemIdentifier,
        cachedItem: FileProviderItem?
    ) async throws -> (URL, FileProviderItem) {
        let item: FileProviderItem
        if let cached = cachedItem {
            item = cached
        } else {
            item = try await fetchItemFromServer(itemIdentifier: itemIdentifier)
        }

        // 创建下载授权令牌
        let grantBody = try JSONSerialization.data(withJSONObject: ["file_id": itemIdentifier.rawValue])
        let grantRequest = makeRequest(path: "files/download-grants", method: "POST", body: grantBody)
        let downloadInfo: DownloadInfo = try await apiCall(grantRequest)

        guard let downloadURL = URL(string: downloadInfo.downloadUrl) else {
            logger.error("下载 URL 无效: \(downloadInfo.downloadUrl, privacy: .private)")
            throw NSFileProviderError(.serverUnreachable)
        }

        // 使用 download task 下载（支持断点续传）
        var downloadRequest = URLRequest(url: downloadURL)
        downloadRequest.httpMethod = "GET"
        if let token = authToken, !token.isEmpty {
            downloadRequest.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        downloadRequest.setValue(downloadInfo.downloadGrant, forHTTPHeaderField: "X-Download-Grant")

        let (tempURL, response) = try await urlSession.download(for: downloadRequest)

        guard let httpResponse = response as? HTTPURLResponse,
              (200...299).contains(httpResponse.statusCode) else {
            logger.error("下载失败: HTTP 状态码异常")
            throw NSFileProviderError(.serverUnreachable)
        }

        // 移动到缓存目录
        let localURL = cacheDirectory.appendingPathComponent(itemIdentifier.rawValue)
        try? FileManager.default.removeItem(at: localURL)
        try FileManager.default.moveItem(at: tempURL, to: localURL)

        logger.info("下载完成: \(localURL.path, privacy: .private)")

        // 更新缓存中的项目状态
        let downloadedItem = FileProviderItem(
            itemIdentifier: item.itemIdentifier,
            parentItemIdentifier: item.parentItemIdentifier,
            filename: item.filename,
            contentType: item.contentType,
            documentSize: item.documentSize,
            creationDate: item.creationDate,
            contentModificationDate: item.contentModificationDate,
            isUploaded: true,
            isDownloaded: true,
            isMostRecentVersionDownloaded: true,
            fileNode: item.fileNode
        )

        cacheLock.lock()
        itemCache[itemIdentifier] = downloadedItem
        cacheLock.unlock()

        return (localURL, downloadedItem)
    }

    /// 创建文件夹
    private func createFolder(
        name: String,
        parentIdentifier: NSFileProviderItemIdentifier
    ) async throws -> FileProviderItem {
        let parentId: String? = parentIdentifier == .rootContainer ? nil : parentIdentifier.rawValue

        let body: [String: Any] = [
            "node_id": parentId as Any,
            "folder_name": name
        ]
        let bodyData = try JSONSerialization.data(withJSONObject: body)

        // 创建文件夹接口返回空响应，不需要解析
        let request = makeRequest(path: "business/nodes/", method: "POST", body: bodyData)
        let _: EmptyResponse = try await apiCall(request)

        // 创建后重新枚举父目录找到新文件夹
        let children: [FileNode] = try await fetchChildren(nodeId: parentIdentifier == .rootContainer ? (cachedRootNodeId ?? "") : parentIdentifier.rawValue)

        // 查找刚创建的文件夹
        if let newNode = children.first(where: { $0.nodeName == name }) {
            let item = FileProviderItem(from: newNode, parentIdentifier: parentIdentifier)
            cacheLock.lock()
            itemCache[item.itemIdentifier] = item
            cacheLock.unlock()
            logger.info("文件夹创建成功: \(name, privacy: .public)")
            return item
        }

        // 如果没找到（可能服务器延迟），创建一个临时 item
        let tempNode = FileNode(
            nodeId: UUID().uuidString,
            nodeType: "FOLDER",
            nodeName: name,
            nodeSize: nil,
            parentId: parentId,
            createTime: nil,
            updateTime: nil,
            children: nil
        )
        let item = FileProviderItem(from: tempNode, parentIdentifier: parentIdentifier)
        logger.info("文件夹创建成功（临时）: \(name, privacy: .public)")
        return item
    }

    /// 上传文件（分块上传）
    private func uploadFile(
        from localURL: URL,
        filename: String,
        parentIdentifier: NSFileProviderItemIdentifier
    ) async throws -> FileProviderItem {
        let fileSize = (try? localURL.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
        let mimeType = mimeTypeFor(filename: filename)
        let parentId: String? = parentIdentifier == .rootContainer ? nil : parentIdentifier.rawValue

        logger.info("开始上传: \(filename, privacy: .public), 大小: \(fileSize) bytes")

        // 小文件直接上传（< 5MB），大文件分块上传
        if fileSize < 5 * 1024 * 1024 {
            return try await uploadSmallFile(
                from: localURL,
                filename: filename,
                parentId: parentId,
                mimeType: mimeType,
                parentIdentifier: parentIdentifier
            )
        } else {
            return try await uploadLargeFile(
                from: localURL,
                filename: filename,
                parentId: parentId,
                mimeType: mimeType,
                fileSize: fileSize,
                parentIdentifier: parentIdentifier
            )
        }
    }

    /// 小文件直接上传（multipart/form-data）
    private func uploadSmallFile(
        from localURL: URL,
        filename: String,
        parentId: String?,
        mimeType: String,
        parentIdentifier: NSFileProviderItemIdentifier
    ) async throws -> FileProviderItem {
        let fileData = try Data(contentsOf: localURL)
        let boundary = UUID().uuidString

        var request = makeRequest(path: "files/upload", method: "POST")
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = 120

        var bodyData = Data()

        // 添加 parent_id
        if let pid = parentId {
            appendFormField(to: &bodyData, name: "parent_id", value: pid, boundary: boundary)
        }

        // 添加文件
        appendFormFile(
            to: &bodyData,
            fieldName: "file",
            filename: filename,
            mimeType: mimeType,
            fileData: fileData,
            boundary: boundary
        )

        bodyData.append("--\(boundary)--\r\n".data(using: .utf8)!)
        request.httpBody = bodyData

        let node: FileNode = try await apiCall(request)
        let item = FileProviderItem(from: node, parentIdentifier: parentIdentifier)

        cacheLock.lock()
        itemCache[item.itemIdentifier] = item
        cacheLock.unlock()

        logger.info("小文件上传完成: \(filename, privacy: .public)")
        return item
    }

    /// 大文件分块上传（init → chunk × N → complete）
    private func uploadLargeFile(
        from localURL: URL,
        filename: String,
        parentId: String?,
        mimeType: String,
        fileSize: Int,
        parentIdentifier: NSFileProviderItemIdentifier
    ) async throws -> FileProviderItem {
        let chunkSize = 5 * 1024 * 1024 // 5MB 每块

        // 1. 初始化上传会话
        let initBody: [String: Any] = [
            "filename": filename,
            "file_size": fileSize,
            "mime_type": mimeType,
            "parent_id": parentId as Any,
            "chunk_size": chunkSize
        ]
        let initBodyData = try JSONSerialization.data(withJSONObject: initBody)
        let initRequest = makeRequest(path: "files/upload/init", method: "POST", body: initBodyData)
        let uploadInit: UploadInitResponse = try await apiCall(initRequest)

        logger.info("分块上传初始化: uploadId=\(uploadInit.uploadId, privacy: .public), 总块数=\(uploadInit.totalChunks)")

        // 2. 分块上传
        let fileData = try Data(contentsOf: localURL)
        let totalChunks = uploadInit.totalChunks
        let uploadedChunks = Set(uploadInit.uploadedChunks ?? [])

        for chunkIndex in 0..<totalChunks {
            if uploadedChunks.contains(chunkIndex) {
                logger.debug("跳过已上传块: \(chunkIndex)/\(totalChunks)")
                continue
            }

            let start = chunkIndex * chunkSize
            let end = min(start + chunkSize, fileData.count)
            let chunk = fileData.subdata(in: start..<end)

            var chunkRequest = makeRequest(
                path: "files/upload/\(uploadInit.uploadId)/chunk/\(chunkIndex)",
                method: "POST",
                body: chunk,
                contentType: "application/octet-stream"
            )
            chunkRequest.timeoutInterval = 120

            let maxRetries = 3
            var lastError: Error?
            for retry in 0..<maxRetries {
                do {
                    let (_, response) = try await uploadSession.data(for: chunkRequest)
                    guard let httpResponse = response as? HTTPURLResponse,
                          (200...299).contains(httpResponse.statusCode) else {
                        throw NSError(domain: "FileProvider", code: -1,
                            userInfo: [NSLocalizedDescriptionKey: "分块上传失败: chunk \(chunkIndex)"])
                    }
                    lastError = nil
                    break
                } catch {
                    lastError = error
                    logger.warning("分块上传重试 \(retry + 1)/\(maxRetries): chunk \(chunkIndex)")
                    if retry < maxRetries - 1 {
                        try await Task.sleep(nanoseconds: UInt64(pow(2.0, Double(retry))) * 1_000_000_000)
                    }
                }
            }

            if let error = lastError {
                logger.error("分块上传最终失败: chunk \(chunkIndex), error: \(error.localizedDescription, privacy: .public)")
                // 取消上传会话
                try? await cancelUpload(uploadId: uploadInit.uploadId)
                throw error
            }
        }

        // 3. 完成上传
        let completeRequest = makeRequest(
            path: "files/upload/\(uploadInit.uploadId)/complete",
            method: "POST"
        )
        let node: FileNode = try await apiCall(completeRequest)

        let item = FileProviderItem(from: node, parentIdentifier: parentIdentifier)
        cacheLock.lock()
        itemCache[item.itemIdentifier] = item
        cacheLock.unlock()

        logger.info("大文件上传完成: \(filename, privacy: .public)")
        return item
    }

    /// 取消上传会话
    private func cancelUpload(uploadId: String) async throws {
        let request = makeRequest(
            path: "files/upload/\(uploadId)/cancel",
            method: "POST"
        )
        let _: EmptyResponse = try await apiCall(request)
        logger.info("上传会话已取消: \(uploadId, privacy: .public)")
    }

    /// 重命名文件/文件夹
    private func renameItem(
        itemIdentifier: NSFileProviderItemIdentifier,
        newName: String
    ) async throws -> FileProviderItem {
        let body = ["new_node_name": newName]
        let bodyData = try JSONSerialization.data(withJSONObject: body)
        let request = makeRequest(
            path: "business/nodes/\(itemIdentifier.rawValue)/name",
            method: "PATCH",
            body: bodyData
        )
        let _: EmptyResponse = try await apiCall(request)

        // 重新获取节点信息
        let pathRequest = makeRequest(path: "business/nodes/\(itemIdentifier.rawValue)/path")
        let pathInfo: PathInfo = try await apiCall(pathRequest)
        let node = pathInfo.node
        let parentId = node.parentId ?? NSFileProviderItemIdentifier.rootContainer.rawValue
        let item = FileProviderItem(from: node, parentIdentifier: NSFileProviderItemIdentifier(rawValue: parentId))

        cacheLock.lock()
        itemCache[itemIdentifier] = item
        cacheLock.unlock()

        logger.info("重命名成功: -> \(newName, privacy: .public)")
        return item
    }

    /// 移动文件/文件夹
    private func moveItem(
        itemIdentifier: NSFileProviderItemIdentifier,
        newParentIdentifier: NSFileProviderItemIdentifier
    ) async throws -> FileProviderItem {
        let targetParentId: String? = newParentIdentifier == .rootContainer ? nil : newParentIdentifier.rawValue
        let body: [String: Any] = [
            "target_position": targetParentId as Any
        ]
        let bodyData = try JSONSerialization.data(withJSONObject: body)
        let request = makeRequest(
            path: "business/nodes/\(itemIdentifier.rawValue)/position",
            method: "PATCH",
            body: bodyData
        )
        let _: EmptyResponse = try await apiCall(request)

        // 重新获取移动后的状态
        return try await fetchItemFromServer(itemIdentifier: itemIdentifier)
    }

    /// 替换文件内容
    private func replaceFileContent(
        itemIdentifier: NSFileProviderItemIdentifier,
        contentsURL: URL
    ) async throws -> FileProviderItem {
        let cachedItem = getCachedItem(itemIdentifier)
        let parentIdentifier = cachedItem?.parentItemIdentifier ?? .rootContainer

        // 删除旧版本（移到回收站）
        let request = makeRequest(
            path: "business/nodes/\(itemIdentifier.rawValue)",
            method: "DELETE"
        )
        let _: EmptyResponse = try await apiCall(request)

        // 上传新版本
        return try await uploadFile(
            from: contentsURL,
            filename: cachedItem?.filename ?? "file",
            parentIdentifier: parentIdentifier
        )
    }

    /// 删除服务端文件
    private func deleteItemOnServer(itemIdentifier: NSFileProviderItemIdentifier) async throws {
        let request = makeRequest(
            path: "business/nodes/\(itemIdentifier.rawValue)",
            method: "DELETE"
        )
        let _: EmptyResponse = try await apiCall(request)
        logger.info("服务端删除成功: \(itemIdentifier.rawValue, privacy: .public)")
    }

    // MARK: - 辅助方法

    /// 获取文件的 MIME 类型
    private func mimeTypeFor(filename: String) -> String {
        let ext = (filename as NSString).pathExtension.lowercased()
        if let uttype = UTType(filenameExtension: ext),
           let mime = uttype.preferredMIMEType {
            return mime
        }
        return "application/octet-stream"
    }

    /// 向 multipart body 添加表单字段
    private func appendFormField(to data: inout Data, name: String, value: String, boundary: String) {
        data.append("--\(boundary)\r\n".data(using: .utf8)!)
        data.append("Content-Disposition: form-data; name=\"\(name)\"\r\n\r\n".data(using: .utf8)!)
        data.append("\(value)\r\n".data(using: .utf8)!)
    }

    /// 向 multipart body 添加文件
    private func appendFormFile(
        to data: inout Data,
        fieldName: String,
        filename: String,
        mimeType: String,
        fileData: Data,
        boundary: String
    ) {
        data.append("--\(boundary)\r\n".data(using: .utf8)!)
        data.append("Content-Disposition: form-data; name=\"\(fieldName)\"; filename=\"\(filename)\"\r\n".data(using: .utf8)!)
        data.append("Content-Type: \(mimeType)\r\n\r\n".data(using: .utf8)!)
        data.append(fileData)
        data.append("\r\n".data(using: .utf8)!)
    }

    /// 通过 DistributedNotificationCenter 通知主应用
    private func notifyMainApp(event: String, userInfo: [String: Any]?) {
        DistributedNotificationCenter.default().postNotificationName(
            NSNotification.Name("com.privateclouddisk.fileprovider.event"),
            object: nil,
            userInfo: ["event": event, "data": userInfo as Any],
            deliverImmediately: true
        )
    }
}

// MARK: - App Group 常量

private enum AppGroup {
    static let identifier = "group.com.privateclouddisk.app"

    enum Keys {
        static let authToken = "fp.token"
        static let apiBaseURL = "fp.apiBaseUrl"
        static let userId = "fp.userId"
        static let mountPoint = "fp.mountPoint"
        static let displayName = "fp.displayName"
        static let domainIdentifier = "fp.domainIdentifier"
    }
}

// MARK: - API 响应模型

/// 统一 API 响应（与后端 ApiResponse 对齐）
///
/// 使用 .convertFromSnakeCase 自动解码，无需手动 CodingKeys
struct ApiResponse<T: Decodable>: Decodable {
    let code: Int
    let message: String?
    let data: T?
}

struct EmptyResponse: Decodable {}

/// 下载授权信息（与后端对齐，使用 .convertFromSnakeCase）
struct DownloadInfo: Decodable {
    let downloadUrl: String
    let downloadGrant: String
    let nodeId: String?
}

/// 上传初始化响应（与后端对齐，使用 .convertFromSnakeCase）
struct UploadInitResponse: Decodable {
    let uploadId: String
    let chunkSize: Int
    let totalChunks: Int
    let uploadedChunks: [Int]?
}

/// 节点路径信息（与后端 PathChildrenVO 对齐）
struct PathInfo: Decodable {
    let node: FileNode
    let children: [FileNode]?
}
