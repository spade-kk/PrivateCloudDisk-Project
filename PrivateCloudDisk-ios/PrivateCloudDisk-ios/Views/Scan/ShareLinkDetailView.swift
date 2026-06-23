//
//  ShareLinkDetailView.swift
//  PrivateCloudDisk-ios
//
//  分享链接详情页
//  扫码识别到文件分享链接时跳转此页面
//  显示文件信息，支持打开/保存
//

import SwiftUI

// MARK: - 分享链接详情视图

struct ShareLinkDetailView: View {
    let shareId: String
    let token: String?
    let shareURL: String

    @Environment(\.dismiss) private var dismiss
    @State private var isLoading = true
    @State private var loadError: String?

    /// 模拟的文件信息
    @State private var fileName = ""
    @State private var fileSize = ""
    @State private var fileType = ""
    @State private var shareFrom = ""

    var onOpen: (() -> Void)?

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                // 文件图标区
                fileIconSection
                    .padding(.top, AppSpacing.xxxl + AppSpacing.xl)

                // 文件信息区
                if isLoading {
                    loadingView
                        .padding(.top, AppSpacing.xxxl)
                } else if let error = loadError {
                    errorView(error)
                        .padding(.top, AppSpacing.xxxl)
                } else {
                    fileInfoSection
                        .padding(.top, AppSpacing.xxl)
                }

                // 操作按钮
                if !isLoading && loadError == nil {
                    actionButtons
                        .padding(.top, AppSpacing.xxxl)
                        .padding(.horizontal, AppSpacing.xl)
                }
            }
            .padding(.bottom, AppSpacing.xxxl)
        }
        .background(AppColors.background)
        .navigationTitle("分享文件")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { loadShareDetail() }
    }

    // MARK: - 文件图标区

    private var fileIconSection: some View {
        VStack(spacing: AppSpacing.lg) {
            ZStack {
                RoundedRectangle(cornerRadius: AppRadius.xl)
                    .fill(AppColors.primaryBg)
                    .frame(width: 88, height: 88)

                Image(systemName: fileIconName)
                    .font(.system(size: 36))
                    .foregroundColor(fileIconColor)
            }

            if !fileName.isEmpty {
                Text(fileName)
                    .font(AppTypography.title2)
                    .foregroundColor(AppColors.textPrimary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, AppSpacing.xl)
            }

            if !shareFrom.isEmpty {
                Text("来自 \(shareFrom) 的分享")
                    .font(AppTypography.subheadline)
                    .foregroundColor(AppColors.textSecondary)
            }
        }
    }

    private var fileIconName: String {
        if fileType.lowercased().contains("pdf") { return "doc.richtext" }
        if fileType.lowercased().contains("image") || fileType.lowercased().contains("png") || fileType.lowercased().contains("jpg") { return "photo" }
        if fileType.lowercased().contains("video") || fileType.lowercased().contains("mp4") { return "play.rectangle" }
        if fileType.lowercased().contains("audio") || fileType.lowercased().contains("mp3") { return "music.note" }
        if fileType.lowercased().contains("zip") || fileType.lowercased().contains("rar") { return "archivebox" }
        if fileType.lowercased().contains("folder") { return "folder" }
        return "doc"
    }

    private var fileIconColor: Color {
        if fileType.lowercased().contains("pdf") { return AppColors.filePDF }
        if fileType.lowercased().contains("image") || fileType.lowercased().contains("png") || fileType.lowercased().contains("jpg") { return AppColors.fileImage }
        if fileType.lowercased().contains("video") || fileType.lowercased().contains("mp4") { return AppColors.fileVideo }
        if fileType.lowercased().contains("audio") || fileType.lowercased().contains("mp3") { return AppColors.fileAudio }
        if fileType.lowercased().contains("zip") || fileType.lowercased().contains("rar") { return AppColors.fileArchive }
        if fileType.lowercased().contains("folder") { return AppColors.fileFolder }
        return AppColors.fileUnknown
    }

    // MARK: - 加载状态

    private var loadingView: some View {
        VStack(spacing: AppSpacing.lg) {
            ProgressView()
                .scaleEffect(1.2)
            Text("加载分享信息...")
                .font(AppTypography.subheadline)
                .foregroundColor(AppColors.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, AppSpacing.xxxl)
    }

    private func errorView(_ message: String) -> some View {
        VStack(spacing: AppSpacing.lg) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 40))
                .foregroundColor(AppColors.warning)

            Text(message)
                .font(AppTypography.subheadline)
                .foregroundColor(AppColors.textSecondary)
                .multilineTextAlignment(.center)

            AppSecondaryButton("重试") {
                isLoading = true
                loadError = nil
                loadShareDetail()
            }
            .frame(width: 120)
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, AppSpacing.xl)
        .padding(.vertical, AppSpacing.xxxl)
    }

    // MARK: - 文件信息区

    private var fileInfoSection: some View {
        VStack(alignment: .leading, spacing: AppSpacing.md) {
            Text("文件详情")
                .font(AppTypography.headline)
                .foregroundColor(AppColors.textPrimary)
                .padding(.horizontal, AppSpacing.xl)

            VStack(spacing: 0) {
                infoRow(icon: "doc.text", title: "文件名称", value: fileName)
                Divider().padding(.leading, 48)
                infoRow(icon: "internaldrive", title: "文件大小", value: fileSize)
                Divider().padding(.leading, 48)
                infoRow(icon: "tag", title: "文件类型", value: fileType.uppercased())
                Divider().padding(.leading, 48)
                infoRow(icon: "person", title: "分享者", value: shareFrom)
                Divider().padding(.leading, 48)
                infoRow(icon: "number", title: "分享 ID", value: String(shareId.prefix(12)))
            }
            .appCard(padding: 0)
            .padding(.horizontal, AppSpacing.xl)
        }
    }

    private func infoRow(icon: String, title: String, value: String) -> some View {
        HStack(spacing: AppSpacing.md) {
            Image(systemName: icon)
                .font(.subheadline)
                .foregroundColor(AppColors.textTertiary)
                .frame(width: 20)

            Text(title)
                .font(AppTypography.subheadline)
                .foregroundColor(AppColors.textSecondary)

            Spacer()

            Text(value)
                .font(AppTypography.subheadline)
                .foregroundColor(AppColors.textPrimary)
                .lineLimit(1)
        }
        .padding(.horizontal, AppSpacing.lg)
        .padding(.vertical, 14)
    }

    // MARK: - 操作按钮

    private var actionButtons: some View {
        VStack(spacing: AppSpacing.md) {
            AppPrimaryButton("打开文件", icon: "arrow.down.doc") {
                onOpen?()
                dismiss()
            }

            AppSecondaryButton("保存到 CloudDrive", icon: "icloud.and.arrow.down") {
                onOpen?()
                dismiss()
            }

            Button("取消") {
                dismiss()
            }
            .font(AppTypography.subheadline)
            .foregroundColor(AppColors.textSecondary)
            .padding(.vertical, AppSpacing.sm)
        }
    }

    // MARK: - 加载分享详情

    private func loadShareDetail() {
        // 模拟网络请求（实际项目中替换为真实 API）
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            // 模拟数据
            fileName = "项目方案_v3.2.pdf"
            fileSize = "12.8 MB"
            fileType = "pdf"
            shareFrom = "王经理"
            isLoading = false
        }
    }
}

// MARK: - 预览

#Preview("分享链接详情页") {
    NavigationStack {
        ShareLinkDetailView(
            shareId: "7f3a8b2c1d",
            token: "abc123def456",
            shareURL: "https://clouddrive.example.com/share/s/7f3a8b2c1d?token=abc123def456&type=file"
        )
    }
}

#Preview("分享链接 - 暗色模式") {
    NavigationStack {
        ShareLinkDetailView(
            shareId: "x9k2m5p8q3",
            token: nil,
            shareURL: "https://clouddrive.example.com/s/x9k2m5p8q3"
        )
    }
    .preferredColorScheme(.dark)
}