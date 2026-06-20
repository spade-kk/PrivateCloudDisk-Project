import SwiftUI

// MARK: - 收藏 + 回收站视图（企业级设计）

/// 收藏文件列表页
///
/// 参考百度网盘 macOS 客户端设计：
/// - 毛玻璃工具栏
/// - 现代文件行设计
/// - 优雅的空状态
/// - 品牌色点缀
struct FavoritesView: View {
    @EnvironmentObject var viewModel: FavoritesTrashViewModel

    private let brandBlue = AppColors.primary

    var body: some View {
        VStack(spacing: 0) {
            // 标题栏
            HStack {
                HStack(spacing: 8) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 6)
                            .fill(.yellow.opacity(0.15))
                            .frame(width: 28, height: 28)

                        Image(systemName: "star.fill")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(.yellow)
                    }
                    Text("收藏")
                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                }

                Spacer()

                if !viewModel.selectedNodeIds.isEmpty {
                    Button("取消收藏") {
                        for id in viewModel.selectedNodeIds {
                            Task { await viewModel.removeFromFavorites(nodeId: id) }
                        }
                    }
                    .buttonStyle(.plain)
                    .font(.system(size: 12, design: .rounded))
                    .padding(.horizontal, 14)
                    .padding(.vertical, 7)
                    .background(
                        RoundedRectangle(cornerRadius: 8)
                            .fill(.orange.opacity(0.1))
                    )
                    .foregroundColor(.orange)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(.regularMaterial)

            Divider()
                .opacity(0.4)

            if viewModel.isLoading {
                LoadingStateView(message: "加载收藏中...")
            } else if viewModel.isEmpty {
                EmptyStateView(
                    icon: "star",
                    title: "暂无收藏",
                    subtitle: "在文件上右键选择「添加到收藏」"
                )
            } else {
                favoritesList
            }
        }
        .onAppear {
            Task { await viewModel.loadFavorites() }
        }
    }

    private var favoritesList: some View {
        List {
            ForEach(viewModel.items) { node in
                HStack(spacing: 12) {
                    // 图标
                    ZStack {
                        RoundedRectangle(cornerRadius: 6)
                            .fill(node.isFolder ? brandBlue.opacity(0.1) : Color.secondary.opacity(0.1))
                            .frame(width: 34, height: 34)

                        Image(systemName: node.isFolder ? "folder.fill" : node.category.sfSymbolName)
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(node.isFolder ? brandBlue : .secondary)
                    }

                    // 信息
                    VStack(alignment: .leading, spacing: 3) {
                        HStack(spacing: 6) {
                            Text(node.name)
                                .font(.system(size: 13, design: .rounded))
                                .lineLimit(1)

                            Image(systemName: "star.fill")
                                .font(.system(size: 9))
                                .foregroundColor(.yellow)
                        }

                        Text(node.formattedDate)
                            .font(.system(size: 11, design: .rounded))
                            .foregroundColor(.secondary)
                    }

                    Spacer()

                    Button(action: {
                        Task { await viewModel.removeFromFavorites(nodeId: node.id) }
                    }) {
                        Image(systemName: "star.slash")
                            .font(.system(size: 13))
                            .foregroundColor(.secondary.opacity(0.5))
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 7)
                .background(
                    RoundedRectangle(cornerRadius: 8)
                        .fill(.clear)
                )
                .listRowSeparator(.hidden)
                .listRowInsets(EdgeInsets(top: 0, leading: 8, bottom: 0, trailing: 8))
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
    }
}

// MARK: - 回收站视图

/// 回收站页面 —— 企业级设计
struct TrashView: View {
    @EnvironmentObject var viewModel: FavoritesTrashViewModel
    @State private var showEmptyTrashAlert = false
    @State private var showPermanentDeleteAlert = false

    private let brandBlue = AppColors.primary

    var body: some View {
        VStack(spacing: 0) {
            // 标题栏
            HStack {
                HStack(spacing: 8) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 6)
                            .fill(.red.opacity(0.12))
                            .frame(width: 28, height: 28)

                        Image(systemName: "trash.fill")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(.red)
                    }
                    Text("回收站")
                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                }

                Spacer()

                if !viewModel.selectedNodeIds.isEmpty {
                    HStack(spacing: 8) {
                        Button("恢复") {
                            Task {
                                await viewModel.restoreNodes(Array(viewModel.selectedNodeIds))
                            }
                        }
                        .buttonStyle(.plain)
                        .font(.system(size: 12, design: .rounded))
                        .padding(.horizontal, 14)
                        .padding(.vertical, 7)
                        .background(
                            RoundedRectangle(cornerRadius: 8)
                                .fill(.green.opacity(0.1))
                        )
                        .foregroundColor(.green)

                        Button("永久删除") {
                            showPermanentDeleteAlert = true
                        }
                        .buttonStyle(.plain)
                        .font(.system(size: 12, design: .rounded))
                        .padding(.horizontal, 14)
                        .padding(.vertical, 7)
                        .background(
                            RoundedRectangle(cornerRadius: 8)
                                .fill(.red.opacity(0.1))
                        )
                        .foregroundColor(.red)
                    }
                }

                Button("清空回收站") {
                    showEmptyTrashAlert = true
                }
                .buttonStyle(.plain)
                .font(.system(size: 12, design: .rounded))
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(
                    RoundedRectangle(cornerRadius: 8)
                        .fill(.red.opacity(0.08))
                )
                .foregroundColor(.red)
                .disabled(viewModel.isEmpty)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(.regularMaterial)

            Divider()
                .opacity(0.4)

            if viewModel.isLoading {
                LoadingStateView(message: "加载回收站中...")
            } else if viewModel.isEmpty {
                EmptyStateView(
                    icon: "trash.slash",
                    title: "回收站为空",
                    subtitle: "删除的文件将显示在这里"
                )
            } else {
                trashList
            }
        }
        .onAppear {
            Task { await viewModel.loadTrash() }
        }
        .alert("确认清空回收站？", isPresented: $showEmptyTrashAlert) {
            Button("取消", role: .cancel) {}
            Button("清空", role: .destructive) {
                Task { await viewModel.emptyTrash() }
            }
        } message: {
            Text("清空后文件将无法恢复，请谨慎操作。")
        }
        .alert("确认永久删除？", isPresented: $showPermanentDeleteAlert) {
            Button("取消", role: .cancel) {}
            Button("删除", role: .destructive) {
                Task {
                    await viewModel.permanentDelete(Array(viewModel.selectedNodeIds))
                }
            }
        } message: {
            Text("删除后文件将无法恢复。")
        }
    }

    private var trashList: some View {
        List {
            ForEach(viewModel.items) { node in
                HStack(spacing: 12) {
                    // 图标
                    ZStack {
                        RoundedRectangle(cornerRadius: 6)
                            .fill(node.isFolder ? brandBlue.opacity(0.1) : Color.secondary.opacity(0.1))
                            .frame(width: 34, height: 34)

                        Image(systemName: node.isFolder ? "folder.fill" : node.category.sfSymbolName)
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(node.isFolder ? brandBlue : .secondary)
                    }

                    // 信息
                    VStack(alignment: .leading, spacing: 3) {
                        Text(node.name)
                            .font(.system(size: 13, design: .rounded))
                            .lineLimit(1)

                        HStack(spacing: 8) {
                            Text(node.formattedSize)
                                .font(.system(size: 11, design: .rounded))
                            Text("·")
                                .font(.system(size: 11))
                            Text("删除于 \(node.formattedDate)")
                                .font(.system(size: 11, design: .rounded))
                        }
                        .foregroundColor(.secondary)
                    }

                    Spacer()

                    HStack(spacing: 8) {
                        Button(action: {
                            Task { await viewModel.restoreNodes([node.id]) }
                        }) {
                            Image(systemName: "arrow.uturn.backward")
                                .font(.system(size: 13))
                                .foregroundColor(.green)
                        }
                        .buttonStyle(.plain)
                        .help("恢复")

                        Button(action: {
                            Task { await viewModel.permanentDelete([node.id]) }
                        }) {
                            Image(systemName: "trash")
                                .font(.system(size: 13))
                                .foregroundColor(.red.opacity(0.6))
                        }
                        .buttonStyle(.plain)
                        .help("永久删除")
                    }
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 7)
                .background(
                    RoundedRectangle(cornerRadius: 8)
                        .fill(.clear)
                )
                .listRowSeparator(.hidden)
                .listRowInsets(EdgeInsets(top: 0, leading: 8, bottom: 0, trailing: 8))
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
    }
}

#Preview {
    FavoritesView()
        .environmentObject(FavoritesTrashViewModel())
}
