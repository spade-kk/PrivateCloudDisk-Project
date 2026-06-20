//
//  SharesView.swift
//  PrivateCloudDisk-ios
//
//  分享管理 — 企业级卡片式分享管理
//  查看、创建、撤销分享链接
//

import SwiftUI

struct SharesView: View {
    @StateObject private var viewModel = SharesViewModel()

    var body: some View {
        NavigationStack {
            ZStack {
                AppColors.background.ignoresSafeArea()

                if viewModel.isLoading && viewModel.shares.isEmpty {
                    VStack(spacing: AppSpacing.lg) {
                        ProgressView()
                            .scaleEffect(1.2)
                            .tint(AppColors.primary)
                        Text("加载中...")
                            .font(AppTypography.subheadline)
                            .foregroundColor(AppColors.textSecondary)
                    }
                } else if viewModel.shares.isEmpty {
                    AppEmptyState(
                        icon: "link.badge.plus",
                        title: "暂无分享",
                        message: "创建分享链接，与他人共享文件"
                    )
                } else {
                    ScrollView {
                        LazyVStack(spacing: AppSpacing.md) {
                            ForEach(viewModel.shares) { share in
                                ShareCardView(share: share) {
                                    Task { await viewModel.revokeShare(shareToken: share.shareToken) }
                                }
                            }
                        }
                        .padding(AppSpacing.lg)
                    }
                }
            }
            .navigationTitle("分享管理")
            .refreshable {
                await viewModel.loadMyShares()
            }
            .task {
                await viewModel.loadMyShares()
            }
        }
    }
}

// MARK: - 分享卡片

struct ShareCardView: View {
    let share: ShareLinkItem
    let onRevoke: () -> Void
    @State private var showCopyToast = false
    @State private var showRevokeConfirm = false

    var body: some View {
        VStack(spacing: 0) {
            // 头部
            HStack(spacing: AppSpacing.md) {
                // 图标
                ZStack {
                    RoundedRectangle(cornerRadius: AppRadius.md)
                        .fill(share.targetType == .file ? AppColors.fileDocument.opacity(0.1) : AppColors.fileFolder.opacity(0.1))
                        .frame(width: 44, height: 44)

                    Image(systemName: share.targetType == .file ? "doc.fill" : "folder.fill")
                        .font(.system(size: 18))
                        .foregroundColor(share.targetType == .file ? AppColors.fileDocument : AppColors.fileFolder)
                }

                VStack(alignment: .leading, spacing: 3) {
                    Text(share.targetName)
                        .font(AppTypography.subheadline.weight(.medium))
                        .foregroundColor(AppColors.textPrimary)
                        .lineLimit(1)
                    Text(share.targetTypeLabel)
                        .font(AppTypography.caption2)
                        .foregroundColor(AppColors.textSecondary)
                }

                Spacer()

                AppBadge(text: share.statusLabel, style: statusBadgeStyle)
            }
            .padding(AppSpacing.lg)

            Divider()
                .overlay(AppColors.dividerLight)
                .padding(.leading, AppSpacing.lg)

            // 操作区
            HStack(spacing: AppSpacing.md) {
                Button(action: {
                    UIPasteboard.general.string = share.shareURL
                    showCopyToast = true
                    DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                        showCopyToast = false
                    }
                }) {
                    Label("复制链接", systemImage: "doc.on.doc")
                        .font(AppTypography.caption1)
                }
                .buttonStyle(.bordered)
                .tint(AppColors.primary)

                if share.hasPassword {
                    Label("密码保护", systemImage: "lock.fill")
                        .font(AppTypography.caption2)
                        .foregroundColor(AppColors.warning)
                }

                Spacer()

                HStack(spacing: 4) {
                    Image(systemName: "eye")
                        .font(.system(size: 10))
                    Text("\(share.accessCount)")
                        .font(AppTypography.caption2)
                }
                .foregroundColor(AppColors.textSecondary)

                if share.isActive {
                    Button(action: { showRevokeConfirm = true }) {
                        Text("撤销")
                            .font(AppTypography.caption1.weight(.medium))
                            .foregroundColor(AppColors.danger)
                    }
                }
            }
            .padding(AppSpacing.lg)

            // 过期时间
            if let expire = share.expireAt {
                Divider()
                    .overlay(AppColors.dividerLight)
                    .padding(.leading, AppSpacing.lg)

                HStack {
                    Image(systemName: "clock")
                        .font(.system(size: 10))
                    Text("过期时间: \(formatDate(expire))")
                        .font(AppTypography.caption2)
                    Spacer()
                }
                .foregroundColor(AppColors.textTertiary)
                .padding(.horizontal, AppSpacing.lg)
                .padding(.vertical, 10)
            }
        }
        .background(AppColors.surface)
        .clipShape(RoundedRectangle(cornerRadius: AppRadius.lg))
        .shadow(color: .black.opacity(0.04), radius: 8, y: 2)
        .overlay(alignment: .topTrailing) {
            if showCopyToast {
                Text("已复制")
                    .font(AppTypography.caption2)
                    .foregroundColor(.white)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(AppColors.textPrimary.opacity(0.8))
                    .clipShape(Capsule())
                    .padding(8)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: showCopyToast)
        .confirmationDialog("确定撤销此分享吗？", isPresented: $showRevokeConfirm, titleVisibility: .visible) {
            Button("撤销", role: .destructive, action: onRevoke)
            Button("取消", role: .cancel) {}
        }
    }

    private var statusBadgeStyle: AppBadge.Style {
        switch share.status {
        case .active: return .success
        case .revoked: return .warning
        case .expired: return .neutral
        }
    }

    private func formatDate(_ dateStr: String) -> String {
        let formatter = ISO8601DateFormatter()
        guard let date = formatter.date(from: dateStr) else { return dateStr }
        let display = DateFormatter()
        display.dateStyle = .medium
        display.locale = Locale(identifier: "zh_CN")
        return display.string(from: date)
    }
}

#Preview {
    SharesView()
}