//
//  StarredView.swift
//  PrivateCloudDisk-ios
//
//  收藏页面 — 企业级收藏管理
//  查看、取消收藏，支持文件和文件夹分类
//

import SwiftUI

struct StarredView: View {
    @StateObject private var viewModel = StarredViewModel()

    var body: some View {
        NavigationStack {
            ZStack {
                AppColors.background.ignoresSafeArea()

                if viewModel.isLoading && viewModel.stars.isEmpty {
                    VStack(spacing: AppSpacing.lg) {
                        ProgressView()
                            .scaleEffect(1.2)
                            .tint(AppColors.primary)
                        Text("加载中...")
                            .font(AppTypography.subheadline)
                            .foregroundColor(AppColors.textSecondary)
                    }
                } else if viewModel.stars.isEmpty {
                    AppEmptyState(
                        icon: "star",
                        title: "暂无收藏",
                        message: "收藏文件和文件夹，方便快速访问"
                    )
                } else {
                    ScrollView {
                        LazyVStack(spacing: AppSpacing.sm) {
                            // 统计信息
                            HStack {
                                Text("共 \(viewModel.stars.count) 项收藏")
                                    .font(AppTypography.footnote)
                                    .foregroundColor(AppColors.textSecondary)
                                Spacer()
                            }
                            .padding(.horizontal, AppSpacing.lg)
                            .padding(.top, AppSpacing.sm)
                            .padding(.bottom, AppSpacing.xs)

                            ForEach(viewModel.stars) { star in
                                StarRowView(star: star) {
                                    Task { await viewModel.removeStar(starId: star.starId) }
                                }
                                .padding(.horizontal, AppSpacing.lg)
                            }
                        }
                        .padding(.bottom, AppSpacing.lg)
                    }
                }
            }
            .navigationTitle("我的收藏")
            .refreshable {
                await viewModel.loadStars()
            }
            .task {
                await viewModel.loadStars()
            }
        }
    }
}

// MARK: - 收藏行

struct StarRowView: View {
    let star: StarItem
    let onRemove: () -> Void
    @State private var showRemoveConfirm = false

    var body: some View {
        HStack(spacing: AppSpacing.md) {
            // 图标
            ZStack {
                RoundedRectangle(cornerRadius: AppRadius.md)
                    .fill(star.isFolder ? AppColors.fileFolder.opacity(0.1) : AppColors.fileUnknown.opacity(0.1))
                    .frame(width: 44, height: 44)

                Image(systemName: star.isFolder ? "folder.fill" : "doc.fill")
                    .font(.system(size: 18))
                    .foregroundColor(star.isFolder ? AppColors.fileFolder : AppColors.fileUnknown)
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(star.targetName)
                    .font(AppTypography.subheadline.weight(.medium))
                    .foregroundColor(AppColors.textPrimary)
                    .lineLimit(1)

                HStack(spacing: 6) {
                    AppBadge(
                        text: star.isFolder ? "文件夹" : "文件",
                        style: star.isFolder ? .custom(bg: AppColors.fileFolder.opacity(0.1), fg: AppColors.fileFolder) : .neutral
                    )

                    if !star.isFolder {
                        Text(star.formattedSize)
                            .font(AppTypography.caption2)
                            .foregroundColor(AppColors.textSecondary)
                    }
                }
            }

            Spacer()

            Button(action: { showRemoveConfirm = true }) {
                Image(systemName: "star.fill")
                    .font(.subheadline)
                    .foregroundColor(AppColors.warning)
                    .frame(width: 36, height: 36)
                    .background(AppColors.warning.opacity(0.08))
                    .clipShape(Circle())
            }
        }
        .padding(AppSpacing.md)
        .background(AppColors.surface)
        .clipShape(RoundedRectangle(cornerRadius: AppRadius.lg))
        .shadow(color: .black.opacity(0.03), radius: 4, y: 2)
        .confirmationDialog("取消收藏该文件？", isPresented: $showRemoveConfirm, titleVisibility: .visible) {
            Button("取消收藏", role: .destructive, action: onRemove)
            Button("取消", role: .cancel) {}
        }
    }
}

#Preview {
    StarredView()
}