import SwiftUI

// MARK: - 文件列表视图（企业级 v4 — 与 Web 前端统一）

/// 文件列表视图 —— 列表模式（独立组件，可复用于收藏夹、回收站等）
///
/// 参考百度网盘 macOS 客户端设计：
/// - 悬停时显示操作按钮
/// - 彩色文件类型图标
/// - 优雅的右键菜单
/// - 多选支持
struct FileListView: View {
    @EnvironmentObject var fileListVM: FileListViewModel
    @EnvironmentObject var contentVM: ContentViewModel
    @State private var hoveredNodeId: String?

    private let brandBlue = AppColors.primary

    var body: some View {
        List(selection: $fileListVM.selectedNodeIds) {
            ForEach(sortedFiles, id: \.id) { node in
                FileRowView(node: node, isSelected: fileListVM.selectedNodeIds.contains(node.id))
                    .onHover { hovering in
                        withAnimation(AppAnimation.fast) {
                            hoveredNodeId = hovering ? node.id : nil
                        }
                    }
                    .onTapGesture(count: 2) {
                        if node.isFolder {
                            Task { await fileListVM.loadFiles(parentId: node.id) }
                            contentVM.currentPath.append(node)
                        } else {
                            contentVM.showToast("正在打开: \(node.name)", type: .info)
                        }
                    }
                    .onTapGesture(count: 1) {
                        if fileListVM.isSelectionMode {
                            fileListVM.toggleSelection(nodeId: node.id)
                        }
                    }
                    .contextMenu {
                        fileContextMenu(for: node)
                    }
                    .listRowSeparator(.hidden)
                    .listRowInsets(EdgeInsets(top: 0, leading: 8, bottom: 0, trailing: 8))
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
    }

    private var sortedFiles: [FileNode] {
        fileListVM.files.sorted { a, b in
            if a.isFolder != b.isFolder { return a.isFolder }
            return a.name.localizedCaseInsensitiveCompare(b.name) == .orderedAscending
        }
    }

    // MARK: - 右键菜单

    @ViewBuilder
    private func fileContextMenu(for node: FileNode) -> some View {
        if node.isFolder {
            Button("打开") {
                Task { await fileListVM.loadFiles(parentId: node.id) }
                contentVM.currentPath.append(node)
            }
        } else {
            Button("下载") {
                Task {
                    _ = try? await DownloadManager.shared.downloadFile(
                        nodeId: node.id,
                        filename: node.name
                    )
                }
            }
            Button("快速预览") { }
        }

        Divider()

        Button("重命名") { }
        Button("移动...") { }
        Button("复制") { }

        Divider()

        if node.isStarred == true {
            Button("取消收藏") {
                Task { await fileListVM.toggleStar(nodeId: node.id, starred: false) }
            }
        } else {
            Button("添加到收藏") {
                Task { await fileListVM.toggleStar(nodeId: node.id, starred: true) }
            }
        }

        Button("分享链接...") { }

        Divider()

        Button("移到回收站") {
            Task { await fileListVM.deleteNodes([node.id]) }
        }
        .keyboardShortcut(.delete, modifiers: .command)
    }
}

// MARK: - 文件网格视图（企业级 v4 — 与 Web 前端统一）

/// 文件网格视图 —— 独立组件，可复用于收藏夹、回收站等
struct FileGridView: View {
    @EnvironmentObject var fileListVM: FileListViewModel
    @EnvironmentObject var contentVM: ContentViewModel
    @State private var hoveredNodeId: String?

    private let brandBlue = AppColors.primary

    private let columns = [
        GridItem(.adaptive(minimum: 120, maximum: 160), spacing: 12)
    ]

    var body: some View {
        ScrollView {
            LazyVGrid(columns: columns, spacing: 12) {
                ForEach(sortedFiles, id: \.id) { node in
                    FileGridCell(node: node, isSelected: fileListVM.selectedNodeIds.contains(node.id))
                        .onHover { hovering in
                            withAnimation(AppAnimation.fast) {
                                hoveredNodeId = hovering ? node.id : nil
                            }
                        }
                        .onTapGesture(count: 2) {
                            if node.isFolder {
                                Task { await fileListVM.loadFiles(parentId: node.id) }
                                contentVM.currentPath.append(node)
                            }
                        }
                        .contextMenu {
                            fileGridContextMenu(for: node)
                        }
                }
            }
            .padding(16)
        }
    }

    private var sortedFiles: [FileNode] {
        fileListVM.files.sorted { a, b in
            if a.isFolder != b.isFolder { return a.isFolder }
            return a.name.localizedCaseInsensitiveCompare(b.name) == .orderedAscending
        }
    }

    @ViewBuilder
    private func fileGridContextMenu(for node: FileNode) -> some View {
        if node.isFolder {
            Button("打开") {
                Task { await fileListVM.loadFiles(parentId: node.id) }
                contentVM.currentPath.append(node)
            }
        } else {
            Button("下载") { }
        }

        Divider()

        Button("重命名") { }
        Button("移到回收站") {
            Task { await fileListVM.deleteNodes([node.id]) }
        }
    }
}