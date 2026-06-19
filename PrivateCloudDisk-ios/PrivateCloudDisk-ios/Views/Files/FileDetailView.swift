//
//  FileDetailView.swift
//  PrivateCloudDisk-ios
//
//  文件详情页面 — 显示文件元信息、操作按钮
//  支持快速预览、视频播放、收藏、分享、下载
//

import SwiftUI
import QuickLook

struct FileDetailView: View {
    let fileNode: FileNode
    @State private var fileDetail: FileDetail?
    @State private var isLoading = true
    @State private var showVideoPlayer = false
    @State private var showPreview = false
    @State private var isStarred = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    if isLoading {
                        ProgressView()
                            .padding(.top, 80)
                    } else {
                        // 文件图标
                        VStack(spacing: 12) {
                            Image(systemName: fileNode.systemIcon)
                                .font(.system(size: 64))
                                .foregroundStyle(iconColor)
                                .padding(24)
                                .background(iconColor.opacity(0.1))
                                .clipShape(RoundedRectangle(cornerRadius: 20))

                            Text(fileNode.nodeName)
                                .font(.title3.bold())
                                .multilineTextAlignment(.center)
                        }
                        .padding(.top, 20)

                        // 文件信息
                        infoSection

                        // 操作按钮
                        actionsSection
                    }
                }
                .padding()
            }
            .navigationTitle("文件详情")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
            }
            .task {
                await loadDetail()
            }
            .fullScreenCover(isPresented: $showVideoPlayer) {
                VideoPlayerView(fileId: fileNode.fileId ?? "", fileName: fileNode.nodeName)
            }
            .sheet(isPresented: $showPreview) {
                if let detail = fileDetail {
                    QuickLookPreview(url: cachedFileURL(for: detail.fileId))
                }
            }
        }
    }

    // MARK: - 文件信息

    private var infoSection: some View {
        VStack(spacing: 0) {
            infoRow(icon: "doc.text", label: "文件名", value: fileDetail?.fileName ?? fileNode.nodeName)
            Divider().padding(.leading, 40)
            infoRow(icon: "externaldrive", label: "文件大小", value: fileDetail?.formattedSize ?? fileNode.formattedSize)
            Divider().padding(.leading, 40)
            infoRow(icon: "tag", label: "文件类型", value: fileDetail?.fileType ?? fileNode.fileExtension)
            Divider().padding(.leading, 40)
            if let mime = fileDetail?.mimeType ?? fileNode.mimeType {
                infoRow(icon: "doc.text.magnifyingglass", label: "MIME", value: mime)
                Divider().padding(.leading, 40)
            }
            if let date = fileDetail?.createdAt ?? fileNode.createdAt {
                infoRow(icon: "calendar", label: "创建时间", value: formatDate(date))
                Divider().padding(.leading, 40)
            }
            if let date = fileDetail?.updatedAt ?? fileNode.updatedAt {
                infoRow(icon: "clock", label: "修改时间", value: formatDate(date))
            }
        }
        .background(Color(.systemGray6))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func infoRow(icon: String, label: String, value: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .frame(width: 24)
                .foregroundStyle(.secondary)
            Text(label)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Spacer()
            Text(value)
                .font(.subheadline)
                .lineLimit(1)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    // MARK: - 操作按钮

    private var actionsSection: some View {
        VStack(spacing: 12) {
            // 播放视频（仅视频文件）
            if fileNode.isVideo {
                Button(action: { showVideoPlayer = true }) {
                    Label("播放视频", systemImage: "play.fill")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
            }

            // 预览
            Button(action: { showPreview = true }) {
                Label("快速预览", systemImage: "eye")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)

            // 收藏
            Button(action: { toggleStar() }) {
                Label(isStarred ? "取消收藏" : "添加收藏", systemImage: isStarred ? "star.fill" : "star")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .tint(isStarred ? .orange : .blue)

            // 下载
            Button(action: { downloadFile() }) {
                Label("下载到本地", systemImage: "arrow.down.circle")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)

            // 分享
            if let detail = fileDetail {
                ShareLink(item: detail.fileName) {
                    Label("分享文件", systemImage: "square.and.arrow.up")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
            }

            // 创建分享链接
            Button(action: { createShareLink() }) {
                Label("创建分享链接", systemImage: "link")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
        }
    }

    // MARK: - Private

    private var iconColor: Color {
        if fileNode.isFolder { return .blue }
        if fileNode.isVideo { return .purple }
        if fileNode.isImage { return .green }
        if fileNode.isAudio { return .orange }
        return .gray
    }

    private func loadDetail() async {
        guard let fileId = fileNode.fileId else { return }
        do {
            let detail = try await FileService.shared.getFileInfo(fileId: fileId)
            fileDetail = detail
        } catch {
            // 文件信息加载失败
        }
        isLoading = false
    }

    private func toggleStar() {
        isStarred.toggle()
        Task {
            let targetType = "FILE"
            let targetId = fileNode.fileId ?? ""
            do {
                let result = try await StarService.shared.isStarred(targetType: targetType, targetId: targetId)
                if result {
                    // 取消收藏 - 需要 starId
                } else {
                    let request = CreateStarRequest(targetType: targetType, fileId: fileNode.fileId, nodeId: nil)
                    let _ = try await StarService.shared.addStar(request: request)
                }
            } catch {}
        }
    }

    private func downloadFile() {
        Task {
            guard let fileId = fileNode.fileId else { return }
            do {
                let token = try await FileService.shared.createOperationToken(fileId: fileId, operationType: "download")
                let data = try await FileService.shared.downloadFileContent(fileId: fileId, operationToken: token)
                // 保存到本地
                let fileName = fileNode.nodeName
                FileCacheManager.shared.cacheFile(fileId: fileId, data: data)
                // 保存到 Files App 可访问位置
                saveToFilesApp(data: data, fileName: fileName)
            } catch {}
        }
    }

    private func createShareLink() {
        // 导航到分享创建页面
    }

    private func saveToFilesApp(data: Data, fileName: String) {
        let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
        try? data.write(to: tempURL)

        // 触发分享 Sheet 让用户保存到 Files
        let activityVC = UIActivityViewController(activityItems: [tempURL], applicationActivities: nil)
        if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let rootVC = windowScene.windows.first?.rootViewController {
            rootVC.present(activityVC, animated: true)
        }
    }

    private func cachedFileURL(for fileId: String) -> URL {
        let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
        return caches.appendingPathComponent("FileCache/\(fileId)")
    }

    private func formatDate(_ dateStr: String) -> String {
        let formatter = ISO8601DateFormatter()
        if let date = formatter.date(from: dateStr) {
            let display = DateFormatter()
            display.dateStyle = .medium
            display.timeStyle = .short
            return display.string(from: date)
        }
        return dateStr
    }
}

// MARK: - QuickLook 预览

struct QuickLookPreview: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> QLPreviewController {
        let controller = QLPreviewController()
        controller.dataSource = context.coordinator
        return controller
    }

    func updateUIViewController(_ uiViewController: QLPreviewController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(url: url)
    }

    class Coordinator: NSObject, QLPreviewControllerDataSource {
        let url: URL
        init(url: URL) { self.url = url }

        func numberOfPreviewItems(in controller: QLPreviewController) -> Int { 1 }
        func previewController(_ controller: QLPreviewController, previewItemAt index: Int) -> QLPreviewItem {
            url as QLPreviewItem
        }
    }
}

#Preview {
    FileDetailView(fileNode: FileNode(
        nodeId: "1", nodeName: "test.mp4", nodeType: .file,
        nodeSize: 1024, createdAt: nil, updatedAt: nil,
        parentId: nil, fileId: "f1", fileType: "mp4", mimeType: "video/mp4", path: nil
    ))
}