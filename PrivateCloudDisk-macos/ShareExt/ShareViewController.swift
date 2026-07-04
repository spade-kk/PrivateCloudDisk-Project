import Cocoa
import UniformTypeIdentifiers

// MARK: - Share Extension

/// PrivateCloudDisk 分享扩展
///
/// 允许用户从任意 macOS 应用（Finder、Safari、Photos 等）
/// 直接将文件、图片、链接等内容分享到 PrivateCloudDisk。
///
/// 核心功能：
/// - 文件分享到云盘（从任意应用）
/// - 图片分享到云盘
/// - 链接分享到云盘
/// - 文本分享到云盘
/// - 多文件批量分享（最多 10 个）
/// - 目标文件夹选择
/// - 上传进度显示
///
/// 使用纯代码构建 UI（不使用 XIB），采用 NSStackView 布局。
/// 与主应用通信通过 App Group UserDefaults 和 URL Scheme。
@MainActor
final class ShareViewController: NSViewController {

    // MARK: - UI 组件

    private var stackView: NSStackView!
    private var titleLabel: NSTextField!
    private var fileListScrollView: NSScrollView!
    private var fileListTableView: NSTableView!
    private var destinationPopUp: NSPopUpButton!
    private var progressIndicator: NSProgressIndicator!
    private var statusLabel: NSTextField!
    private var cancelButton: NSButton!
    private var shareButton: NSButton!

    // MARK: - 数据

    private var sharedItems: [SharedItem] = []
    private var availableFolders: [FolderOption] = []
    private var selectedFolderId: String?
    private var isUploading = false

    /// 共享 UserDefaults
    private lazy var sharedDefaults: UserDefaults? = {
        UserDefaults(suiteName: "group.com.privateclouddisk.app")
    }()

    private var authToken: String? {
        sharedDefaults?.string(forKey: "fp.token")
    }

    private var apiBaseURL: String {
        sharedDefaults?.string(forKey: "fp.apiBaseUrl") ?? "http://localhost:8000"
    }

    // MARK: - 模型

    struct SharedItem {
        let name: String
        let type: SharedItemType
        let data: Data?
        let url: URL?
        let text: String?
        let size: Int64

        enum SharedItemType: String {
            case file, image, video, text, url
        }

        var icon: NSImage? {
            let symbol: String = switch type {
            case .file:  "doc"
            case .image: "photo"
            case .video: "film"
            case .text:  "doc.text"
            case .url:   "link"
            }
            return NSImage(systemSymbolName: symbol, accessibilityDescription: nil)
        }

        var formattedSize: String {
            ByteCountFormatter.string(fromByteCount: size, countStyle: .file)
        }
    }

    struct FolderOption {
        let id: String
        let name: String
        let isRoot: Bool
    }

    // MARK: - 生命周期

    override func loadView() {
        self.view = NSView(frame: NSRect(x: 0, y: 0, width: 480, height: 500))
        setupUI()
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        preferredContentSize = NSSize(width: 480, height: 500)
        loadSharedItems()
        loadFolders()
    }

    override func viewDidAppear() {
        super.viewDidAppear()
        view.window?.title = "分享到 PrivateCloudDisk"
    }

    // MARK: - UI 构建

    private func setupUI() {
        // 标题
        titleLabel = NSTextField(labelWithString: "分享到 PrivateCloudDisk")
        titleLabel.font = NSFont.systemFont(ofSize: 16, weight: .bold)
        titleLabel.alignment = .center
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        // 文件列表
        fileListTableView = NSTableView()
        fileListTableView.headerView = nil
        fileListTableView.addTableColumn(NSTableColumn(identifier: NSUserInterfaceItemIdentifier("items")))
        fileListTableView.dataSource = self
        fileListTableView.rowHeight = 40

        fileListScrollView = NSScrollView()
        fileListScrollView.documentView = fileListTableView
        fileListScrollView.hasVerticalScroller = true
        fileListScrollView.translatesAutoresizingMaskIntoConstraints = false
        fileListScrollView.heightAnchor.constraint(greaterThanOrEqualToConstant: 120).isActive = true

        // 目标文件夹
        let destLabel = NSTextField(labelWithString: "保存到:")
        destLabel.font = NSFont.systemFont(ofSize: 12)

        destinationPopUp = NSPopUpButton()
        destinationPopUp.addItem(withTitle: "加载中...")
        destinationPopUp.target = self
        destinationPopUp.action = #selector(folderSelected(_:))

        let destStack = NSStackView(views: [destLabel, destinationPopUp])
        destStack.orientation = .horizontal
        destStack.spacing = 8
        destStack.alignment = .centerY
        destStack.translatesAutoresizingMaskIntoConstraints = false

        // 进度条
        progressIndicator = NSProgressIndicator()
        progressIndicator.style = .bar
        progressIndicator.isIndeterminate = false
        progressIndicator.minValue = 0
        progressIndicator.maxValue = 100
        progressIndicator.doubleValue = 0
        progressIndicator.isHidden = true
        progressIndicator.translatesAutoresizingMaskIntoConstraints = false

        // 状态标签
        statusLabel = NSTextField(labelWithString: "")
        statusLabel.font = NSFont.systemFont(ofSize: 11)
        statusLabel.textColor = .secondaryLabelColor
        statusLabel.alignment = .center
        statusLabel.translatesAutoresizingMaskIntoConstraints = false

        // 按钮
        cancelButton = NSButton(title: "取消", target: self, action: #selector(cancelShare(_:)))
        cancelButton.bezelStyle = .rounded

        shareButton = NSButton(title: "分享到 PrivateCloudDisk", target: self, action: #selector(performShare(_:)))
        shareButton.bezelStyle = .rounded
        shareButton.keyEquivalent = "\r"

        let buttonStack = NSStackView(views: [cancelButton, shareButton])
        buttonStack.orientation = .horizontal
        buttonStack.spacing = 12
        buttonStack.distribution = .fillEqually
        buttonStack.translatesAutoresizingMaskIntoConstraints = false

        // 主布局
        stackView = NSStackView(views: [
            titleLabel,
            fileListScrollView,
            destStack,
            progressIndicator,
            statusLabel,
            buttonStack
        ])
        stackView.orientation = .vertical
        stackView.spacing = 16
        stackView.edgeInsets = NSEdgeInsets(top: 20, left: 20, bottom: 20, right: 20)
        stackView.translatesAutoresizingMaskIntoConstraints = false

        view.addSubview(stackView)

        NSLayoutConstraint.activate([
            stackView.topAnchor.constraint(equalTo: view.topAnchor),
            stackView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            stackView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            stackView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
    }

    // MARK: - 数据加载

    private func loadSharedItems() {
        guard let extensionContext = extensionContext,
              let inputItems = extensionContext.inputItems as? [NSExtensionItem] else { return }

        for inputItem in inputItems {
            guard let attachments = inputItem.attachments else { continue }

            for attachment in attachments {
                if attachment.hasItemConformingToTypeIdentifier(UTType.movie.identifier) {
                    loadAttachment(attachment, type: .video, typeIdentifier: UTType.movie.identifier)
                } else if attachment.hasItemConformingToTypeIdentifier(UTType.image.identifier) {
                    loadAttachment(attachment, type: .image, typeIdentifier: UTType.image.identifier)
                } else if attachment.hasItemConformingToTypeIdentifier(UTType.data.identifier) {
                    loadAttachment(attachment, type: .file, typeIdentifier: UTType.data.identifier)
                } else if attachment.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) {
                    loadTextAttachment(attachment)
                } else if attachment.hasItemConformingToTypeIdentifier(UTType.url.identifier) {
                    loadURLAttachment(attachment)
                }
            }
        }

        fileListTableView.reloadData()
        updateTitle()
    }

    private func loadAttachment(_ attachment: NSItemProvider, type: SharedItem.SharedItemType, typeIdentifier: String) {
        attachment.loadFileRepresentation(forTypeIdentifier: typeIdentifier) { [weak self] url, error in
            guard let self = self, let url = url else { return }
            let size = (try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize).map(Int64.init) ?? 0
            let data = try? Data(contentsOf: url)

            DispatchQueue.main.async {
                self.sharedItems.append(SharedItem(
                    name: url.lastPathComponent,
                    type: type,
                    data: data,
                    url: url,
                    text: nil,
                    size: size
                ))
                self.fileListTableView.reloadData()
                self.updateTitle()
            }
        }
    }

    private func loadTextAttachment(_ attachment: NSItemProvider) {
        attachment.loadItem(forTypeIdentifier: UTType.plainText.identifier, options: nil) { [weak self] item, error in
            guard let self = self else { return }
            let text = item as? String ?? ""
            let data = text.data(using: .utf8)

            DispatchQueue.main.async {
                self.sharedItems.append(SharedItem(
                    name: "Shared Text.txt",
                    type: .text,
                    data: data,
                    url: nil,
                    text: text,
                    size: Int64(data?.count ?? 0)
                ))
                self.fileListTableView.reloadData()
                self.updateTitle()
            }
        }
    }

    private func loadURLAttachment(_ attachment: NSItemProvider) {
        attachment.loadItem(forTypeIdentifier: UTType.url.identifier, options: nil) { [weak self] item, error in
            guard let self = self else { return }
            let url = item as? URL
            let urlString = url?.absoluteString ?? ""
            let content = "[InternetShortcut]\nURL=\(urlString)\n"
            let data = content.data(using: .utf8)
            let filename = (url?.host ?? "link") + ".url"

            DispatchQueue.main.async {
                self.sharedItems.append(SharedItem(
                    name: filename,
                    type: .url,
                    data: data,
                    url: url,
                    text: urlString,
                    size: Int64(data?.count ?? 0)
                ))
                self.fileListTableView.reloadData()
                self.updateTitle()
            }
        }
    }

    private func updateTitle() {
        let count = sharedItems.count
        titleLabel.stringValue = "分享 \(count) 个项目到 PrivateCloudDisk"
    }

    // MARK: - 文件夹加载

    private func loadFolders() {
        Task {
            do {
                let folders = try await fetchFolderList()
                availableFolders = folders

                destinationPopUp.removeAllItems()
                if folders.isEmpty {
                    destinationPopUp.addItem(withTitle: "根目录")
                    selectedFolderId = nil
                } else {
                    for folder in folders {
                        destinationPopUp.addItem(withTitle: folder.name)
                        destinationPopUp.lastItem?.representedObject = folder.id
                    }
                    selectedFolderId = folders.first?.id
                }
            } catch {
                destinationPopUp.removeAllItems()
                destinationPopUp.addItem(withTitle: "根目录")
                selectedFolderId = nil
            }
        }
    }

    private func fetchFolderList() async throws -> [FolderOption] {
        guard let token = authToken else { return [] }

        var request = URLRequest(url: URL(string: "\(apiBaseURL)/api/folders/list")!)
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.timeoutInterval = 10

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse,
              (200...299).contains(httpResponse.statusCode) else {
            return []
        }

        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        let apiResponse = try decoder.decode(ApiResponse<[FolderNode]>.self, from: data)

        let nodes = apiResponse.data ?? []
        return nodes.map { FolderOption(id: $0.id, name: $0.name, isRoot: false) }
    }

    // MARK: - 动作

    @objc private func folderSelected(_ sender: NSPopUpButton) {
        selectedFolderId = sender.selectedItem?.representedObject as? String
    }

    @objc private func cancelShare(_ sender: NSButton) {
        let error = NSError(domain: NSCocoaErrorDomain, code: NSUserCancelledError, userInfo: nil)
        extensionContext?.cancelRequest(withError: error)
    }

    @objc private func performShare(_ sender: NSButton) {
        guard !isUploading else { return }
        isUploading = true
        shareButton.isEnabled = false
        cancelButton.isEnabled = false
        progressIndicator.isHidden = false
        progressIndicator.doubleValue = 0

        let total = sharedItems.count
        guard total > 0 else {
            extensionContext?.completeRequest(returningItems: [], completionHandler: nil)
            return
        }

        Task {
            var completed = 0
            for item in sharedItems {
                do {
                    try await uploadItem(item)
                    completed += 1
                    progressIndicator.doubleValue = Double(completed) / Double(total) * 100
                    statusLabel.stringValue = "已完成 \(completed)/\(total)"
                } catch {
                    statusLabel.stringValue = "上传失败: \(item.name)"
                    progressIndicator.doubleValue = 0
                    isUploading = false
                    shareButton.isEnabled = true
                    cancelButton.isEnabled = true
                    return
                }
            }

            statusLabel.stringValue = "上传完成!"
            progressIndicator.doubleValue = 100

            // 通知主应用刷新
            notifyMainAppRefresh()

            // 延迟关闭，让用户看到完成状态
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) { [weak self] in
                self?.extensionContext?.completeRequest(returningItems: [], completionHandler: nil)
            }
        }
    }

    // MARK: - 上传

    private func uploadItem(_ item: SharedItem) async throws {
        guard let token = authToken else {
            throw NSError(domain: "ShareExt", code: -1,
                userInfo: [NSLocalizedDescriptionKey: "未登录"])
        }

        let dataToUpload: Data
        let filename: String

        if let data = item.data, !data.isEmpty {
            dataToUpload = data
            filename = item.name
        } else if let text = item.text, !text.isEmpty {
            dataToUpload = text.data(using: .utf8) ?? Data()
            filename = item.name
        } else {
            throw NSError(domain: "ShareExt", code: -2,
                userInfo: [NSLocalizedDescriptionKey: "无数据可上传"])
        }

        let boundary = UUID().uuidString
        var request = URLRequest(url: URL(string: "\(apiBaseURL)/api/files/upload")!)
        request.httpMethod = "POST"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = 120

        var bodyData = Data()

        if let folderId = selectedFolderId {
            bodyData.append("--\(boundary)\r\n".data(using: .utf8)!)
            bodyData.append("Content-Disposition: form-data; name=\"parent_id\"\r\n\r\n".data(using: .utf8)!)
            bodyData.append("\(folderId)\r\n".data(using: .utf8)!)
        }

        let mimeType = UTType(filenameExtension: (filename as NSString).pathExtension)?.preferredMIMEType ?? "application/octet-stream"
        bodyData.append("--\(boundary)\r\n".data(using: .utf8)!)
        bodyData.append("Content-Disposition: form-data; name=\"file\"; filename=\"\(filename)\"\r\n".data(using: .utf8)!)
        bodyData.append("Content-Type: \(mimeType)\r\n\r\n".data(using: .utf8)!)
        bodyData.append(dataToUpload)
        bodyData.append("\r\n".data(using: .utf8)!)
        bodyData.append("--\(boundary)--\r\n".data(using: .utf8)!)

        request.httpBody = bodyData

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse,
              (200...299).contains(httpResponse.statusCode) else {
            throw NSError(domain: "ShareExt", code: -3,
                userInfo: [NSLocalizedDescriptionKey: "上传失败"])
        }
    }

    private func notifyMainAppRefresh() {
        DistributedNotificationCenter.default().postNotificationName(
            NSNotification.Name("com.privateclouddisk.share.completed"),
            object: nil,
            userInfo: nil,
            deliverImmediately: true
        )
    }
}

// MARK: - NSTableViewDataSource

extension ShareViewController: NSTableViewDataSource {
    func numberOfRows(in tableView: NSTableView) -> Int {
        sharedItems.count
    }

    func tableView(_ tableView: NSTableView, objectValueFor tableColumn: NSTableColumn?, row: Int) -> Any? {
        guard row < sharedItems.count else { return nil }
        return sharedItems[row]
    }

    func tableView(_ tableView: NSTableView, viewFor tableColumn: NSTableColumn?, row: Int) -> NSView? {
        guard row < sharedItems.count else { return nil }
        let item = sharedItems[row]

        let cellView = NSTableCellView()
        let imageView = NSImageView()
        imageView.image = item.icon
        imageView.frame = NSRect(x: 4, y: 4, width: 32, height: 32)
        imageView.imageScaling = .scaleProportionallyUpOrDown
        cellView.addSubview(imageView)

        let nameLabel = NSTextField(labelWithString: item.name)
        nameLabel.font = NSFont.systemFont(ofSize: 12)
        nameLabel.frame = NSRect(x: 42, y: 12, width: 300, height: 16)
        cellView.addSubview(nameLabel)

        let sizeLabel = NSTextField(labelWithString: item.formattedSize)
        sizeLabel.font = NSFont.systemFont(ofSize: 10)
        sizeLabel.textColor = .secondaryLabelColor
        sizeLabel.frame = NSRect(x: 42, y: 0, width: 200, height: 14)
        cellView.addSubview(sizeLabel)

        return cellView
    }
}

// MARK: - API 响应模型

struct ApiResponse<T: Decodable>: Decodable {
    let code: Int
    let message: String
    let data: T?
}

struct FolderNode: Codable {
    let id: String
    let name: String
    let parentId: String?
    let children: [FolderNode]?

    enum CodingKeys: String, CodingKey {
        case id, name, children
        case parentId = "parent_id"
    }
}