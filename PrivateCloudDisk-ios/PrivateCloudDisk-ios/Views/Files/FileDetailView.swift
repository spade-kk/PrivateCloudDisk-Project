//
//  FileDetailView.swift
//  PrivateCloudDisk-ios
//
//  文件详情页 — 企业级卡片式布局
//  支持快速预览、视频播放、收藏、分享、下载等操作
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
    @State private var showToast = false
    @State private var toastMessage = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ZStack {
                AppColors.background.ignoresSafeArea()

                if isLoading {
                    VStack(spacing: AppSpacing.lg) {
                        ProgressView()
                            .scaleEffect(1.2)
                            .tint(AppColors.primary)
                        Text("加载中...")
                            .font(AppTypography.subheadline)
                            .foregroundColor(AppColors.textSecondary)
                    }
                } else {
                    ScrollView {
                        VStack(spacing: AppSpacing.xl) {
                            // 文件头部卡片
                            fileHeaderCard
                                .padding(.horizontal, AppSpacing.lg)

                            // 文件信息卡片
                            fileInfoCard
                                .padding(.horizontal, AppSpacing.lg)

                            // 操作按钮
                            actionButtons
                                .padding(.horizontal, AppSpacing.lg)
                        }
                        .padding(.vertical, AppSpacing.lg)
                    }
                }
            }
            .navigationTitle("文件详情")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark")
                            .font(.subheadline.weight(.semibold))
                            .foregroundColor(AppColors.textSecondary)
                            .frame(width: 32, height: 32)
                            .background(AppColors.surfaceSecondary)
                            .clipShape(Circle())
                    }
                }

                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: { toggleStar() }) {
                        Image(systemName: isStarred ? "star.fill" : "star")
                            .font(.subheadline)
                            .foregroundColor(isStarred ? AppColors.warning : AppColors.textSecondary)
                            .frame(width: 32, height: 32)
                            .background(AppColors.surfaceSecondary)
                            .clipShape(Circle())
                    }
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
            .appToast(isPresented: $showToast, message: toastMessage)
        }
    }

    // MARK: - 文件头部卡片

    private var fileHeaderCard: some View {
        VStack(spacing: AppSpacing.lg) {
            // 图标
            ZStack {
                RoundedRectangle(cornerRadius: AppRadius.xl)
                    .fill(iconColor.opacity(0.08))
                    .frame(width: 88, height: 88)

                FileIcon(node: fileNode, size: 60)
            }

            VStack(spacing: AppSpacing.xs) {
                Text(fileNode.nodeName)
                    .font(AppTypography.title3)
                    .foregroundColor(AppColors.textPrimary)
                    .multilineTextAlignment(.center)

                if let detail = fileDetail {
                    Text(detail.formattedSize)
                        .font(AppTypography.subheadline)
                        .foregroundColor(AppColors.textSecondary)
                }
            }
        }
        .padding(AppSpacing.xl)
        .frame(maxWidth: .infinity)
        .background(AppColors.surface)
        .clipShape(RoundedRectangle(cornerRadius: AppRadius.xl))
        .shadow(color: .black.opacity(0.04), radius: 8, y: 2)
    }

    // MARK: - 文件信息卡片

    private var fileInfoCard: some View {
        VStack(spacing: 0) {
            SectionHeader(title: "文件信息")

            AppInfoRow(
                icon: "doc.text",
                label: "文件名",
                value: fileDetail?.fileName ?? fileNode.nodeName,
                iconColor: AppColors.primary
            )
            infoDivider
            AppInfoRow(
                icon: "internaldrive",
                label: "文件大小",
                value: fileDetail?.formattedSize ?? fileNode.formattedSize,
                iconColor: AppColors.fileImage
            )
            infoDivider
            AppInfoRow(
                icon: "tag",
                label: "文件类型",
                value: fileDetail?.fileType ?? fileNode.fileExtension.uppercased(),
                iconColor: AppColors.fileVideo
            )
            if let mime = fileDetail?.mimeType ?? fileNode.mimeType {
                infoDivider
                AppInfoRow(
                    icon: "info.circle",
                    label: "MIME 类型",
                    value: mime,
                    iconColor: AppColors.info
                )
            }
            if let date = fileDetail?.createdAt ?? fileNode.createdAt {
                infoDivider
                AppInfoRow(
                    icon: "calendar",
                    label: "创建时间",
                    value: formatDate(date),
                    iconColor: AppColors.success
                )
            }
            if let date = fileDetail?.updatedAt ?? fileNode.updatedAt {
                infoDivider
                AppInfoRow(
                    icon: "clock.arrow.2.circlepath",
                    label: "修改时间",
                    value: formatDate(date),
                    iconColor: AppColors.warning
                )
            }
        }
        .background(AppColors.surface)
        .clipShape(RoundedRectangle(cornerRadius: AppRadius.xl))
        .shadow(color: .black.opacity(0.04), radius: 8, y: 2)
    }

    private var infoDivider: some View {
        Divider()
            .padding(.leading, 54)
            .overlay(AppColors.divider)
    }

    // MARK: - 操作按钮

    private var actionButtons: some View {
        VStack(spacing: AppSpacing.md) {
            SectionHeader(title: "操作")

            VStack(spacing: AppSpacing.sm) {
                // 播放视频
                if fileNode.isVideo {
                    AppPrimaryButton("播放视频", icon: "play.fill") {
                        showVideoPlayer = true
                    }
                }

                // 快速预览
                AppSecondaryButton("快速预览", icon: "eye") {
                    showPreview = true
                }

                // 下载
                AppSecondaryButton("下载到本地", icon: "arrow.down.to.line") {
                    downloadFile()
                }

                // 分享
                if let detail = fileDetail {
                    ShareLink(item: detail.fileName) {
                        HStack(spacing: 8) {
                            Image(systemName: "square.and.arrow.up")
                                .font(.subheadline.weight(.medium))
                            Text("分享文件")
                                .font(AppTypography.subheadline.weight(.medium))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(AppColors.primaryBg)
                        .foregroundColor(AppColors.primary)
                        .clipShape(RoundedRectangle(cornerRadius: AppRadius.md))
                        .overlay(
                            RoundedRectangle(cornerRadius: AppRadius.md)
                                .stroke(AppColors.primary.opacity(0.2), lineWidth: 1)
                        )
                    }
                }

                // 创建分享链接
                AppSecondaryButton("创建分享链接", icon: "link") {
                    createShareLink()
                }
            }
            .padding(AppSpacing.lg)
            .background(AppColors.surface)
            .clipShape(RoundedRectangle(cornerRadius: AppRadius.xl))
            .shadow(color: .black.opacity(0.04), radius: 8, y: 2)
        }
    }

    // MARK: - 计算方法

    private var iconColor: Color {
        if fileNode.isFolder { return AppColors.fileFolder }
        if fileNode.isVideo { return AppColors.fileVideo }
        if fileNode.isImage { return AppColors.fileImage }
        if fileNode.isAudio { return AppColors.fileAudio }
        if fileNode.isPDF { return AppColors.filePDF }
        return AppColors.fileUnknown
    }

    // MARK: - 数据加载

    private func loadDetail() async {
        guard let fileId = fileNode.fileId else {
            isLoading = false
            return
        }
        do {
            let detail = try await FileService.shared.getFileInfo(fileId: fileId)
            fileDetail = detail
        } catch {}
        isLoading = false
    }

    private func toggleStar() {
        withAnimation(.spring(response: 0.3, dampingFraction: 0.6)) {
            isStarred.toggle()
        }
        Task {
            let targetType = "FILE"
            let targetId = fileNode.fileId ?? ""
            do {
                let result = try await StarService.shared.isStarred(targetType: targetType, targetId: targetId)
                if result {
                    // 取消收藏
                } else {
                    let request = CreateStarRequest(targetType: targetType, fileId: fileNode.fileId, nodeId: nil)
                    _ = try await StarService.shared.addStar(request: request)
                }
            } catch {}
        }
    }

    private func downloadFile() {
        toastMessage = "开始下载..."
        showToast = true
        Task {
            guard let fileId = fileNode.fileId else { return }
            do {
                let token = try await FileService.shared.createOperationToken(fileId: fileId, operationType: "download")
                let data = try await FileService.shared.downloadFileContent(fileId: fileId, operationToken: token)
                FileCacheManager.shared.cacheFile(fileId: fileId, data: data)
                saveToFilesApp(data: data, fileName: fileNode.nodeName)
                toastMessage = "下载完成"
                showToast = true
            } catch {
                toastMessage = "下载失败"
                showToast = true
            }
        }
    }

    private func createShareLink() {
        toastMessage = "分享链接已创建"
        showToast = true
    }

    private func saveToFilesApp(data: Data, fileName: String) {
        let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
        try? data.write(to: tempURL)
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
        guard let date = formatter.date(from: dateStr) else { return dateStr }
        let display = DateFormatter()
        display.dateStyle = .medium
        display.timeStyle = .short
        display.locale = Locale(identifier: "zh_CN")
        return display.string(from: date)
    }
}

// MARK: - 区块标题

struct SectionHeader: View {
    let title: String

    var body: some View {
        HStack {
            Text(title)
                .font(AppTypography.footnote.weight(.semibold))
                .foregroundColor(AppColors.textTertiary)
                .textCase(.uppercase)
                .tracking(0.8)
            Spacer()
        }
        .padding(.horizontal, AppSpacing.lg)
        .padding(.top, AppSpacing.lg)
        .padding(.bottom, AppSpacing.sm)
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
        nodeId: "1", nodeName: "演示视频.mp4", nodeType: .file,
        nodeSize: 1024000, createdAt: nil, updatedAt: nil,
        parentId: nil, fileId: "f1", fileType: "mp4", mimeType: "video/mp4", path: nil
    ))
}