import Foundation
import Combine

// MARK: - 下载管理器

/// 文件下载管理器
///
/// 支持：
/// - 断点续传（HTTP Range 请求）
/// - 下载进度回调
/// - 暂停/恢复/取消
/// - 后台下载（使用 URLSession background configuration）
/// - 下载队列管理
///
/// macOS 特有优势：
/// - 使用 URLSessionDownloadTask 后台下载
/// - 下载完成自动通知 Finder 扩展
/// - 与 NSBackgroundActivityScheduler 配合
@MainActor
final class DownloadManager: NSObject, ObservableObject {

    static let shared = DownloadManager()

    // MARK: - 发布属性

    @Published var activeTasks: [DownloadTask] = []
    @Published var overallProgress: Double = 0

    private var backgroundSession: URLSession!
    private var tasks: [String: (task: DownloadTask, downloadTask: URLSessionDownloadTask?)] = [:]
    private let api = APIClient.shared
    private let downloadQueue = DispatchQueue(label: "com.pcd.download", qos: .utility)

    private override init() {
        super.init()

        let config = URLSessionConfiguration.background(
            withIdentifier: "com.privateclouddisk.download"
        )
        config.sessionSendsLaunchEvents = true
        config.isDiscretionary = false
        config.timeoutIntervalForResource = 86400 // 24小时
        backgroundSession = URLSession(
            configuration: config,
            delegate: self,
            delegateQueue: nil
        )
    }

    // MARK: - 开始下载

    /// 开始下载文件
    func downloadFile(
        nodeId: String,
        filename: String,
        remotePath: String? = nil
    ) async throws -> DownloadTask {
        let downloadDir = FileManager.default.urls(
            for: .downloadsDirectory, in: .userDomainMask
        ).first!
        let destURL = downloadDir.appendingPathComponent(filename)

        let task = DownloadTask(
            nodeId: nodeId,
            filename: filename,
            remotePath: remotePath ?? "/api/files/\(nodeId)/download",
            localPath: destURL.path,
            totalBytes: 0
        )

        // 获取文件大小
        let fileNode: FileNode = try await api.get("/api/files/\(nodeId)")
        let totalBytes = fileNode.size

        var updatedTask = task
        updatedTask.totalBytes = totalBytes
        tasks[nodeId] = (updatedTask, nil)
        activeTasks.append(updatedTask)

        // 开始下载
        let downloadTask = try await startDownload(task: updatedTask, destURL: destURL)
        tasks[nodeId]?.downloadTask = downloadTask

        return updatedTask
    }

    private func startDownload(task: DownloadTask, destURL: URL) async throws -> URLSessionDownloadTask {
        let urlString = "\(api_baseURL)\(task.remotePath)"
        var request = URLRequest(url: URL(string: urlString)!)
        if let token = KeychainManager.shared.readAuthToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        // 支持断点续传
        if let resumeData = task.resumeData {
            let downloadTask = backgroundSession.downloadTask(withResumeData: resumeData)
            downloadTask.resume()
            return downloadTask
        } else {
            let downloadTask = backgroundSession.downloadTask(with: request)
            downloadTask.resume()
            return downloadTask
        }
    }

    // MARK: - 任务控制

    func pauseDownload(_ nodeId: String) {
        guard let (task, downloadTask) = tasks[nodeId], let dt = downloadTask else { return }
        dt.cancel { resumeData in
            var updatedTask = task
            updatedTask.resumeData = resumeData
            updatedTask.status = .paused
            DispatchQueue.main.async {
                self.tasks[nodeId] = (updatedTask, nil)
                self.updateTaskInList(updatedTask)
            }
        }
    }

    func resumeDownload(_ nodeId: String) async {
        guard let (task, _) = tasks[nodeId], let resumeData = task.resumeData else { return }
        var updatedTask = task
        updatedTask.status = .downloading
        tasks[nodeId] = (updatedTask, nil)
        updateTaskInList(updatedTask)

        let dt = backgroundSession.downloadTask(withResumeData: resumeData)
        dt.resume()
        tasks[nodeId] = (updatedTask, dt)
    }

    func cancelDownload(_ nodeId: String) {
        guard let (_, downloadTask) = tasks[nodeId] else { return }
        downloadTask?.cancel()
        tasks.removeValue(forKey: nodeId)
        activeTasks.removeAll { $0.nodeId == nodeId }
        updateOverallProgress()
    }

    // MARK: - 私有方法

    private func updateTaskInList(_ task: DownloadTask) {
        if let index = activeTasks.firstIndex(where: { $0.nodeId == task.nodeId }) {
            activeTasks[index] = task
        }
        updateOverallProgress()
    }

    private func updateOverallProgress() {
        let allTasks = activeTasks
        guard !allTasks.isEmpty else {
            overallProgress = 0
            return
        }
        let totalBytes = allTasks.reduce(0) { $0 + $1.totalBytes }
        let downloadedBytes = allTasks.reduce(0) { $0 + $1.downloadedBytes }
        overallProgress = totalBytes > 0 ? Double(downloadedBytes) / Double(totalBytes) : 0
    }

    private var api_baseURL: String {
        UserDefaults.standard.string(forKey: "api_base_url") ?? "http://localhost:8000"
    }
}

// MARK: - URLSessionDownloadDelegate

extension DownloadManager: URLSessionDownloadDelegate {

    func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didFinishDownloadingTo location: URL
    ) {
        // 找到对应的任务
        guard let taskId = downloadTask.taskDescription,
              let (task, _) = tasks[taskId] else { return }

        let destURL = URL(fileURLWithPath: task.localPath)

        do {
            if FileManager.default.fileExists(atPath: destURL.path) {
                try FileManager.default.removeItem(at: destURL)
            }
            try FileManager.default.moveItem(at: location, to: destURL)
        } catch {
            print("[DownloadManager] 文件移动失败: \(error)")
            return
        }

        DispatchQueue.main.async {
            var updatedTask = task
            updatedTask.status = .completed
            updatedTask.downloadedBytes = task.totalBytes
            self.updateTaskInList(updatedTask)
            self.tasks.removeValue(forKey: taskId)

            // 发送下载完成通知
            NotificationCenter.default.post(
                name: .downloadCompleted,
                object: nil,
                userInfo: ["nodeId": taskId, "localPath": destURL.path]
            )

            // 3秒后从列表移除
            DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
                self.activeTasks.removeAll { $0.nodeId == taskId }
            }
        }
    }

    func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didWriteData bytesWritten: Int64,
        totalBytesWritten: Int64,
        totalBytesExpectedToWrite: Int64
    ) {
        guard let taskId = downloadTask.taskDescription,
              let (task, _) = tasks[taskId] else { return }

        DispatchQueue.main.async {
            var updatedTask = task
            updatedTask.downloadedBytes = totalBytesWritten
            if totalBytesExpectedToWrite > 0 {
                updatedTask.totalBytes = totalBytesExpectedToWrite
            }
            updatedTask.status = .downloading
            self.tasks[taskId] = (updatedTask, self.tasks[taskId]?.downloadTask)
            self.updateTaskInList(updatedTask)
        }
    }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didCompleteWithError error: Error?
    ) {
        if let error = error {
            print("[DownloadManager] 下载错误: \(error.localizedDescription)")
            if let taskId = task.taskDescription {
                DispatchQueue.main.async {
                    var updatedTask = self.tasks[taskId]?.task
                    updatedTask?.status = .failed
                    if let t = updatedTask {
                        self.updateTaskInList(t)
                    }
                }
            }
        }
    }
}


// MARK: - 通知

extension Notification.Name {
    static let downloadCompleted = Notification.Name("com.privateclouddisk.download.completed")
    static let uploadCompleted = Notification.Name("com.privateclouddisk.upload.completed")
    static let virtualDiskStatusChanged = Notification.Name("com.privateclouddisk.virtualdisk.statusChanged")
    static let fileSyncEvent = Notification.Name("com.privateclouddisk.file.syncEvent")
}