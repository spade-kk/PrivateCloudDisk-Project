import SwiftUI

// MARK: - 文件列表视图（企业级设计）

/// 文件列表视图 —— 列表模式
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
                FileRowView(node: node, isHovered: hoveredNodeId == node.id)
                    .onHover { hovering in
                        withAnimation(.easeInOut(duration: 0.12)) {
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

// MARK: - 文件行视图（企业级设计）

struct FileRowView: View {
    let node: FileNode
    let isHovered: Bool

    private let brandBlue = AppColors.primary

    var body: some View {
        HStack(spacing: 12) {
            // 文件图标
            ZStack {
                RoundedRectangle(cornerRadius: 6)
                    .fill(node.isFolder ? brandBlue.opacity(0.1) : fileColor.opacity(0.1))
                    .frame(width: 34, height: 34)

                Image(systemName: node.isFolder ? "folder.fill" : node.category.sfSymbolName)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(node.isFolder ? brandBlue : fileColor)
            }

            // 文件信息
            VStack(alignment: .leading, spacing: 3) {
                Text(node.name)
                    .font(.system(size: 13, design: .rounded))
                    .lineLimit(1)

                HStack(spacing: 10) {
                    if !node.isFolder {
                        Text(node.formattedSize)
                            .font(.system(size: 11, design: .rounded))
                            .foregroundColor(.secondary)
                    }
                    Text(node.formattedDate)
                        .font(.system(size: 11, design: .rounded))
                        .foregroundColor(.secondary)
                }
            }

            Spacer()

            // 操作按钮（悬停时显示）
            if isHovered {
                HStack(spacing: 6) {
                    // 收藏标记
                    if node.isStarred == true {
                        Image(systemName: "star.fill")
                            .font(.system(size: 10))
                            .foregroundColor(.yellow)
                    }

                    // 下载按钮
                    if !node.isFolder {
                        Button(action: {}) {
                            Image(systemName: "arrow.down.circle")
                                .font(.system(size: 14))
                                .foregroundColor(brandBlue)
                        }
                        .buttonStyle(.plain)
                    }

                    // 更多操作
                    Button(action: {}) {
                        Image(systemName: "ellipsis.circle")
                            .font(.system(size: 14))
                            .foregroundColor(.secondary.opacity(0.6))
                    }
                    .buttonStyle(.plain)
                }
                .transition(.opacity.combined(with: .scale(scale: 0.9)))
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 7)
        .background(
            RoundedRectangle(cornerRadius: 8)
                .fill(isHovered ? Color.primary.opacity(0.04) : .clear)
        )
        .animation(.easeInOut(duration: 0.15), value: isHovered)
    }

    private var fileColor: Color {
        switch node.category {
        case .document: return brandBlue
        case .image: return .purple
        case .video: return .pink
        case .audio: return .orange
        case .archive: return .brown
        case .code: return .green
        case .other: return .gray
        }
    }
}

// MARK: - 文件网格视图（企业级设计）

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
                    FileGridCell(node: node, isHovered: hoveredNodeId == node.id)
                        .onHover { hovering in
                            withAnimation(.easeInOut(duration: 0.12)) {
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

// MARK: - 文件网格单元格

struct FileGridCell: View {
    let node: FileNode
    let isHovered: Bool

    private let brandBlue = AppColors.primary

    var body: some View {
        VStack(spacing: 8) {
            // 图标
            ZStack {
                RoundedRectangle(cornerRadius: 14)
                    .fill(node.isFolder ? brandBlue.opacity(0.1) : fileColor.opacity(0.1))
                    .frame(width: 64, height: 64)

                Image(systemName: node.isFolder ? "folder.fill" : node.category.sfSymbolName)
                    .font(.system(size: 26))
                    .foregroundColor(node.isFolder ? brandBlue : fileColor)
            }

            // 文件名
            Text(node.name)
                .font(.system(size: 11, design: .rounded))
                .lineLimit(2)
                .multilineTextAlignment(.center)
                .foregroundColor(.primary)
                .frame(width: 100)

            if !node.isFolder {
                Text(node.formattedSize)
                    .font(.system(size: 10, design: .rounded))
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 10)
        .padding(.horizontal, 6)
        .frame(width: 120)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(isHovered ? Color.primary.opacity(0.04) : .clear)
        )
        .animation(.easeInOut(duration: 0.15), value: isHovered)
    }

    private var fileColor: Color {
        switch node.category {
        case .document: return brandBlue
        case .image: return .purple
        case .video: return .pink
        case .audio: return .orange
        case .archive: return .brown
        case .code: return .green
        case .other: return .gray
        }
    }
}