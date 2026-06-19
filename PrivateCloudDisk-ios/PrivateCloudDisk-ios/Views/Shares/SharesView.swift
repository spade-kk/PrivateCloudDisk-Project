//
//  SharesView.swift
//  PrivateCloudDisk-ios
//
//  分享管理页面 — 查看、创建、撤销分享链接
//

import SwiftUI

struct SharesView: View {
    @StateObject private var viewModel = SharesViewModel()

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.isLoading && viewModel.shares.isEmpty {
                    ProgressView("加载中...")
                } else if viewModel.shares.isEmpty {
                    ContentUnavailableView(
                        "暂无分享",
                        systemImage: "link.badge.plus",
                        description: Text("创建分享链接，与他人共享文件")
                    )
                } else {
                    List {
                        ForEach(viewModel.shares) { share in
                            ShareRowView(share: share) {
                                Task { await viewModel.revokeShare(shareToken: share.shareToken) }
                            }
                        }
                    }
                    .listStyle(.plain)
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

// MARK: - 分享行组件

struct ShareRowView: View {
    let share: ShareLinkItem
    let onRevoke: () -> Void
    @State private var showCopyToast = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: share.targetType == .file ? "doc.fill" : "folder.fill")
                    .foregroundStyle(.blue)
                VStack(alignment: .leading, spacing: 2) {
                    Text(share.targetName)
                        .font(.subheadline.bold())
                        .lineLimit(1)
                    Text(share.targetTypeLabel)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                statusBadge
            }

            HStack {
                Button(action: {
                    UIPasteboard.general.string = share.shareURL
                    showCopyToast = true
                    DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                        showCopyToast = false
                    }
                }) {
                    Label("复制链接", systemImage: "doc.on.doc")
                        .font(.caption)
                }
                .buttonStyle(.bordered)
                .tint(.blue)

                if share.hasPassword {
                    Label("有密码", systemImage: "lock.fill")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Text("访问 \(share.accessCount) 次")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                Spacer()

                if share.isActive {
                    Button("撤销", role: .destructive, action: onRevoke)
                        .font(.caption)
                        .buttonStyle(.bordered)
                }
            }

            if let expire = share.expireAt {
                Text("过期时间: \(formatDate(expire))")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 6)
        .overlay(alignment: .topTrailing) {
            if showCopyToast {
                Text("已复制")
                    .font(.caption2)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(.ultraThinMaterial)
                    .clipShape(Capsule())
                    .transition(.opacity)
            }
        }
    }

    private var statusBadge: some View {
        Text(share.statusLabel)
            .font(.caption2)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(statusColor.opacity(0.15))
            .foregroundStyle(statusColor)
            .clipShape(Capsule())
    }

    private var statusColor: Color {
        switch share.status {
        case .active: return .green
        case .revoked: return .orange
        case .expired: return .gray
        }
    }

    private func formatDate(_ dateStr: String) -> String {
        let formatter = ISO8601DateFormatter()
        if let date = formatter.date(from: dateStr) {
            let display = DateFormatter()
            display.dateStyle = .medium
            return display.string(from: date)
        }
        return dateStr
    }
}

#Preview {
    SharesView()
}