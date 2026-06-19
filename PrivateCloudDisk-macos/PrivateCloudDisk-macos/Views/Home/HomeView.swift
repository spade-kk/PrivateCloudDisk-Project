import SwiftUI

// MARK: - 文件列表主页（企业级设计 v2）

/// 文件列表主页
///
/// 参考百度网盘、夸克网盘 macOS 客户端设计：
/// - 可排序的列标题（名称、大小、修改时间、类型）
/// - 多选操作工具栏
/// - 文件数量统计徽章
/// - 列表/网格视图切换
/// - 搜索与路径导航
/// - 右键菜单与键盘快捷键
struct HomeView: View {
    @EnvironmentObject var fileListVM: FileListViewModel
    @EnvironmentObject var uploadVM: UploadViewModel

    @State private var isGridView = false
    @State private var searchText = ""
    @State private var selectedFileIds = Set<String>()
    @State private var localSortField: SortField = .name
    @State private var sortAscending = true
    @State private var showUploadSheet = false
    @State private var showCreateFolder = false
    @State private var newFolderName = ""

    private let brandBlue = Color(red: 0.24, green: 0.47, blue: 0.96)

    enum SortField: String, CaseIterable {
        case name = "名称"
        case size = "大小"
        case type = "类型"
        case modifiedDate = "修改时间"
    }

    var body: some View {
        VStack(spacing: 0) {
            // ── 工具栏 ──
            toolbarView

            // ── 路径面包屑 ──
            breadcrumbView

            // ── 列标题 ──
            if !isGridView {
                columnHeaderView
            }

            // ── 文件列表内容 ──
            if isGridView {
                fileGridView
            } else {
                fileListView
            }
        }
        .background(Color(nsColor: .windowBackgroundColor))
        .sheet(isPresented: $showCreateFolder) {
            createFolderSheet
        }
        .onChange(of: searchText) { newValue in
            fileListVM.searchQuery = newValue
            if !newValue.isEmpty {
                Task { await fileListVM.search() }
            } else {
                fileListVM.clearSearch()
            }
        }
        .onChange(of: localSortField) { _ in
            applySorting()
        }
        .onChange(of: sortAscending) { _ in
            applySorting()
        }
    }

    private func applySorting() {
        switch localSortField {
        case .name:
            fileListVM.sortBy = "name"
        case .size:
            fileListVM.sortBy = "size"
        case .modifiedDate:
            fileListVM.sortBy = "updated_at"
        case .type:
            fileListVM.sortBy = "mime_type"
        }
        fileListVM.sortOrder = sortAscending ? "asc" : "desc"
        Task { await fileListVM.refreshFiles() }
    }

    // MARK: - 工具栏

    private var toolbarView: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                // 文件数量徽章
                fileCountBadge

                Spacer()

                // 多选操作按钮
                if !selectedFileIds.isEmpty {
                    multiSelectToolbar
                        .transition(.scale(scale: 0.95).combined(with: .opacity))
                }

                // 搜索框
                searchBar

                // 视图切换
                viewToggle
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .background(
                VisualEffectView(
                    material: .headerView,
                    blendingMode: .withinWindow
                )
            )

            Divider()
                .opacity(0.3)
        }
        .animation(.easeInOut(duration: 0.25), value: selectedFileIds.isEmpty)
    }

    // MARK: - 文件数量徽章

    private var fileCountBadge: some View {
        HStack(spacing: 8) {
            Image(systemName: "folder.fill")
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(brandBlue)

            Text("全部文件")
                .font(.system(size: 13, weight: .semibold, design: .rounded))
                .foregroundColor(.primary)

            Text("\(fileListVM.files.count)")
                .font(.system(size: 11, weight: .semibold, design: .rounded))
                .foregroundColor(.secondary)
                .padding(.horizontal, 6)
                .padding(.vertical, 2)
                .background(
                    RoundedRectangle(cornerRadius: 4)
                        .fill(.quaternary.opacity(0.5))
                )
        }
    }

    // MARK: - 多选工具栏

    private var multiSelectToolbar: some View {
        HStack(spacing: 4) {
            Text("已选 \(selectedFileIds.count) 项")
                .font(.system(size: 12, weight: .medium, design: .rounded))
                .foregroundColor(.secondary)

            HStack(spacing: 2) {
                // 下载
                Button(action: {}) {
                    Image(systemName: "arrow.down.to.line")
                        .font(.system(size: 12, weight: .medium))
                }
                .buttonStyle(.plain)
                .padding(6)
                .background(
                    RoundedRectangle(cornerRadius: 6)
                        .fill(.quaternary.opacity(0.4))
                )

                // 分享
                Button(action: {}) {
                    Image(systemName: "square.and.arrow.up")
                        .font(.system(size: 12, weight: .medium))
                }
                .buttonStyle(.plain)
                .padding(6)
                .background(
                    RoundedRectangle(cornerRadius: 6)
                        .fill(.quaternary.opacity(0.4))
                )

                // 删除
                Button(action: {
                    Task { await fileListVM.deleteNodes(Array(selectedFileIds)) }
                }) {
                    Image(systemName: "trash")
                        .font(.system(size: 12, weight: .medium))
                }
                .buttonStyle(.plain)
                .padding(6)
                .background(
                    RoundedRectangle(cornerRadius: 6)
                        .fill(.red.opacity(0.08))
                )
                .foregroundColor(.red)

                // 更多
                Button(action: {}) {
                    Image(systemName: "ellipsis")
                        .font(.system(size: 12, weight: .medium))
                }
                .buttonStyle(.plain)
                .padding(6)
                .background(
                    RoundedRectangle(cornerRadius: 6)
                        .fill(.quaternary.opacity(0.4))
                )
            }
        }
    }

    // MARK: - 搜索栏

    private var searchBar: some View {
        HStack(spacing: 6) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 11, weight: .medium))
                .foregroundColor(.secondary.opacity(0.6))

            TextField("搜索文件...", text: $searchText)
                .textFieldStyle(.plain)
                .font(.system(size: 12, design: .rounded))

            if !searchText.isEmpty {
                Button(action: { searchText = "" }) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 11))
                        .foregroundColor(.secondary.opacity(0.6))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .frame(width: 200)
        .background(
            RoundedRectangle(cornerRadius: 8)
                .fill(.quaternary.opacity(0.4))
        )
    }

    // MARK: - 视图切换

    private var viewToggle: some View {
        HStack(spacing: 2) {
            // 操作按钮
            Button(action: { showCreateFolder = true }) {
                Image(systemName: "folder.badge.plus")
                    .font(.system(size: 13, weight: .medium))
            }
            .buttonStyle(.plain)
            .padding(6)
            .background(
                RoundedRectangle(cornerRadius: 6)
                    .fill(.quaternary.opacity(0.4))
            )

            Button(action: { showUploadSheet = true }) {
                Image(systemName: "arrow.up.to.line")
                    .font(.system(size: 13, weight: .medium))
            }
            .buttonStyle(.plain)
            .padding(6)
            .background(
                RoundedRectangle(cornerRadius: 6)
                    .fill(.quaternary.opacity(0.4))
            )

            Divider()
                .frame(height: 16)
                .padding(.horizontal, 4)

            // 列表/网格切换
            Button(action: {
                withAnimation(.easeInOut(duration: 0.2)) {
                    isGridView.toggle()
                }
            }) {
                Image(systemName: isGridView ? "list.bullet" : "square.grid.2x2")
                    .font(.system(size: 13, weight: .medium))
            }
            .buttonStyle(.plain)
            .padding(6)
            .background(
                RoundedRectangle(cornerRadius: 6)
                    .fill(.quaternary.opacity(0.4))
            )
        }
    }

    // MARK: - 面包屑

    private var breadcrumbView: some View {
        HStack(spacing: 4) {
            Button(action: {
                Task { await fileListVM.loadFiles(parentId: nil) }
            }) {
                HStack(spacing: 4) {
                    Image(systemName: "house.fill")
                        .font(.system(size: 10))
                    Text("根目录")
                        .font(.system(size: 11, design: .rounded))
                }
                .foregroundColor(brandBlue)
            }
            .buttonStyle(.plain)

            Image(systemName: "chevron.right")
                .font(.system(size: 9, weight: .semibold))
                .foregroundColor(.secondary.opacity(0.4))

            Text(fileListVM.currentParentId ?? "根目录")
                .font(.system(size: 11, design: .rounded))
                .foregroundColor(.secondary)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 6)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.quaternary.opacity(0.15))
    }

    // MARK: - 列标题

    private var columnHeaderView: some View {
        HStack(spacing: 0) {
            // 全选复选框
            Button(action: {
                if selectedFileIds.count == fileListVM.files.count {
                    selectedFileIds.removeAll()
                } else {
                    selectedFileIds = Set(fileListVM.files.map(\.id))
                }
            }) {
                Image(systemName: selectionCheckboxIcon)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(
                        selectedFileIds.isEmpty ? .secondary.opacity(0.3) : brandBlue
                    )
            }
            .buttonStyle(.plain)
            .frame(width: 32)

            // 名称列
            sortableColumnHeader(
                title: "名称",
                field: .name,
                width: nil
            )

            // 大小列
            sortableColumnHeader(
                title: "大小",
                field: .size,
                width: 100
            )

            // 类型列
            sortableColumnHeader(
                title: "类型",
                field: .type,
                width: 120
            )

            // 修改时间列
            sortableColumnHeader(
                title: "修改时间",
                field: .modifiedDate,
                width: 160
            )

            // 操作列
            Text("操作")
                .font(.system(size: 11, weight: .medium, design: .rounded))
                .foregroundColor(.secondary)
                .frame(width: 80, alignment: .center)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(.quaternary.opacity(0.25))
    }

    private func sortableColumnHeader(
        title: String,
        field: SortField,
        width: CGFloat?
    ) -> some View {
        Button(action: {
            if localSortField == field {
                withAnimation(.easeInOut(duration: 0.15)) {
                    sortAscending.toggle()
                }
            } else {
                localSortField = field
                sortAscending = true
            }
        }) {
            HStack(spacing: 4) {
                Text(title)
                    .font(.system(size: 11, weight: .medium, design: .rounded))
                    .foregroundColor(localSortField == field ? .primary : .secondary)

                if localSortField == field {
                    Image(systemName: sortAscending ? "chevron.up" : "chevron.down")
                        .font(.system(size: 9, weight: .semibold))
                        .foregroundColor(brandBlue)
                }
            }
            .frame(maxWidth: width != nil ? nil : .infinity, alignment: .leading)
            .frame(width: width)
        }
        .buttonStyle(.plain)
    }

    private var selectionCheckboxIcon: String {
        if selectedFileIds.isEmpty {
            return "square"
        }
        if selectedFileIds.count == fileListVM.files.count {
            return "checkmark.square.fill"
        }
        return "minus.square"
    }

    // MARK: - 列表视图

    private var fileListView: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                if fileListVM.files.isEmpty {
                    emptyStateView
                } else {
                    ForEach(fileListVM.files) { file in
                        FileListRowView(
                            file: file,
                            isSelected: selectedFileIds.contains(file.id),
                            onSelect: {
                                toggleSelection(file.id)
                            },
                            onOpen: {
                                // Navigate into folder or open file
                                if file.isFolder {
                                    Task { await fileListVM.loadFiles(parentId: file.id) }
                                }
                            }
                        )
                        .contextMenu {
                            fileContextMenu(for: file)
                        }

                        Divider()
                            .padding(.leading, 48)
                            .opacity(0.3)
                    }
                }
            }
        }
        .background(Color(nsColor: .controlBackgroundColor))
    }

    // MARK: - 网格视图

    private var fileGridView: some View {
        let columns = [
            GridItem(.adaptive(minimum: 140, maximum: 180), spacing: 12)
        ]

        return ScrollView {
            if fileListVM.files.isEmpty {
                emptyStateView
            } else {
                LazyVGrid(columns: columns, spacing: 16) {
                    ForEach(fileListVM.files) { file in
                        FileGridItemView(
                            file: file,
                            isSelected: selectedFileIds.contains(file.id),
                            onSelect: {
                                toggleSelection(file.id)
                            },
                            onOpen: {
                                if file.isFolder {
                                    Task { await fileListVM.loadFiles(parentId: file.id) }
                                }
                            }
                        )
                        .contextMenu {
                            fileContextMenu(for: file)
                        }
                    }
                }
                .padding(16)
            }
        }
        .background(Color(nsColor: .controlBackgroundColor))
    }

    // MARK: - 空状态

    private var emptyStateView: some View {
        VStack(spacing: 16) {
            Spacer()
                .frame(height: 80)

            ZStack {
                RoundedRectangle(cornerRadius: 20)
                    .fill(brandBlue.opacity(0.06))
                    .frame(width: 80, height: 80)

                Image(systemName: "folder")
                    .font(.system(size: 32, weight: .light))
                    .foregroundColor(brandBlue.opacity(0.3))
            }

            VStack(spacing: 4) {
                Text("此文件夹为空")
                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                    .foregroundColor(.secondary)

                Text("拖拽文件到此处或点击上传按钮")
                    .font(.system(size: 12, design: .rounded))
                    .foregroundColor(.secondary.opacity(0.6))
            }

            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - 右键菜单

    @ViewBuilder
    private func fileContextMenu(for file: FileNode) -> some View {
        Button("打开") {
            if file.isFolder {
                Task { await fileListVM.loadFiles(parentId: file.id) }
            }
        }

        Divider()

        Button("下载") {}
        Button("分享") {}

        Divider()

        Button("重命名") {}

        Button("复制") {}
        Button("移动") {}

        Divider()

        if file.isStarred == true {
            Button("取消收藏") {
                Task { await fileListVM.toggleStar(nodeId: file.id, starred: false) }
            }
        } else {
            Button("添加收藏") {
                Task { await fileListVM.toggleStar(nodeId: file.id, starred: true) }
            }
        }

        Divider()

        Button("删除", role: .destructive) {
            Task { await fileListVM.deleteNodes([file.id]) }
        }
    }

    // MARK: - 新建文件夹 Sheet

    private var createFolderSheet: some View {
        VStack(spacing: 20) {
            Text("新建文件夹")
                .font(.system(size: 16, weight: .bold, design: .rounded))

            HStack(spacing: 10) {
                Image(systemName: "folder.badge.plus")
                    .foregroundColor(.secondary)
                TextField("文件夹名称", text: $newFolderName)
                    .textFieldStyle(.plain)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(.quaternary.opacity(0.4))
            )

            HStack(spacing: 12) {
                Button("取消") {
                    showCreateFolder = false
                    newFolderName = ""
                }
                .buttonStyle(.plain)
                .foregroundColor(.secondary)

                Button("创建") {
                    Task { await fileListVM.createFolder(name: newFolderName) }
                    showCreateFolder = false
                    newFolderName = ""
                }
                .buttonStyle(.borderedProminent)
                .tint(brandBlue)
                .disabled(newFolderName.isEmpty)
            }
        }
        .padding(30)
        .frame(width: 380, height: 200)
    }

    // MARK: - Helpers

    private func toggleSelection(_ id: String) {
        if selectedFileIds.contains(id) {
            selectedFileIds.remove(id)
        } else {
            selectedFileIds.insert(id)
        }
    }
}

// MARK: - 文件列表行（v2）

struct FileListRowView: View {
    let file: FileNode
    let isSelected: Bool
    let onSelect: () -> Void
    let onOpen: () -> Void

    @State private var isHovered = false
    private let brandBlue = Color(red: 0.24, green: 0.47, blue: 0.96)

    var body: some View {
        Button(action: onOpen) {
            HStack(spacing: 0) {
                // 复选框
                Button(action: onSelect) {
                    Image(systemName: isSelected ? "checkmark.square.fill" : "square")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(isSelected ? brandBlue : .secondary.opacity(0.3))
                }
                .buttonStyle(.plain)
                .frame(width: 32)

                // 文件图标
                Image(systemName: fileIcon)
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(fileIconColor)
                    .frame(width: 28)

                // 名称
                HStack(spacing: 4) {
                    Text(file.name)
                        .font(.system(size: 13, design: .rounded))
                        .foregroundColor(.primary)
                        .lineLimit(1)

                    if file.isStarred == true {
                        Image(systemName: "star.fill")
                            .font(.system(size: 9))
                            .foregroundColor(.yellow)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // 大小
                Text(file.formattedSize)
                    .font(.system(size: 12, design: .rounded))
                    .foregroundColor(.secondary)
                    .frame(width: 100, alignment: .trailing)

                // 类型
                Text(file.category.rawValue)
                    .font(.system(size: 12, design: .rounded))
                    .foregroundColor(.secondary)
                    .frame(width: 120, alignment: .leading)

                // 修改时间
                Text(file.formattedDate)
                    .font(.system(size: 12, design: .rounded))
                    .foregroundColor(.secondary)
                    .frame(width: 160, alignment: .leading)

                // 操作按钮
                HStack(spacing: 4) {
                    if isHovered {
                        Button(action: {}) {
                            Image(systemName: "square.and.arrow.down")
                                .font(.system(size: 11))
                        }
                        .buttonStyle(.plain)
                        .padding(4)
                        .background(
                            RoundedRectangle(cornerRadius: 4)
                                .fill(.quaternary.opacity(0.4))
                        )

                        Button(action: {}) {
                            Image(systemName: "ellipsis")
                                .font(.system(size: 11))
                        }
                        .buttonStyle(.plain)
                        .padding(4)
                        .background(
                            RoundedRectangle(cornerRadius: 4)
                                .fill(.quaternary.opacity(0.4))
                        )
                    }
                }
                .frame(width: 80, alignment: .center)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 9)
            .background(
                isSelected
                    ? brandBlue.opacity(0.06)
                    : (isHovered ? Color.primary.opacity(0.03) : .clear)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .onHover { hovering in
            withAnimation(.easeInOut(duration: 0.15)) {
                isHovered = hovering
            }
        }
    }

    private var fileIcon: String {
        file.category.sfSymbolName
    }

    private var fileIconColor: Color {
        if file.isFolder {
            return brandBlue
        }
        switch file.category {
        case .image:
            return .pink
        case .video:
            return .purple
        case .audio:
            return .orange
        case .document:
            return .red
        case .archive:
            return .brown
        default:
            return .secondary
        }
    }
}

// MARK: - 文件网格项（v2）

struct FileGridItemView: View {
    let file: FileNode
    let isSelected: Bool
    let onSelect: () -> Void
    let onOpen: () -> Void

    @State private var isHovered = false
    private let brandBlue = Color(red: 0.24, green: 0.47, blue: 0.96)

    var body: some View {
        VStack(spacing: 8) {
            ZStack {
                // 文件图标
                RoundedRectangle(cornerRadius: 12)
                    .fill(fileIconColor.opacity(0.1))
                    .frame(width: 64, height: 64)

                Image(systemName: fileIcon)
                    .font(.system(size: 28, weight: .light))
                    .foregroundColor(fileIconColor)

                // 选中标记
                if isSelected {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(brandBlue.opacity(0.2))
                        .frame(width: 64, height: 64)

                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(brandBlue)
                        .font(.system(size: 20, weight: .semibold))
                        .position(x: 56, y: 8)
                }

                // 悬停操作
                if isHovered {
                    HStack {
                        Button(action: {}) {
                            Image(systemName: "square.and.arrow.down")
                                .font(.system(size: 10, weight: .medium))
                        }
                        .buttonStyle(.plain)
                        .padding(4)
                        .background(
                            Circle()
                                .fill(.regularMaterial)
                        )

                        Button(action: onSelect) {
                            Image(systemName: isSelected ? "checkmark.square.fill" : "square")
                                .font(.system(size: 10, weight: .medium))
                        }
                        .buttonStyle(.plain)
                        .padding(4)
                        .background(
                            Circle()
                                .fill(.regularMaterial)
                        )
                    }
                    .position(x: 32, y: 56)
                }
            }

            // 文件名
            Text(file.name)
                .font(.system(size: 11, design: .rounded))
                .foregroundColor(.primary)
                .lineLimit(2)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 120)

            // 大小
            if !file.isFolder {
                Text(file.formattedSize)
                    .font(.system(size: 10, design: .rounded))
                    .foregroundColor(.secondary)
            }
        }
        .padding(10)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(isSelected
                    ? brandBlue.opacity(0.06)
                    : (isHovered ? Color.primary.opacity(0.03) : .clear)
                )
        )
        .onTapGesture(count: 2) {
            onOpen()
        }
        .onTapGesture {
            onSelect()
        }
        .onHover { hovering in
            withAnimation(.easeInOut(duration: 0.15)) {
                isHovered = hovering
            }
        }
    }

    private var fileIcon: String {
        file.category.sfSymbolName
    }

    private var fileIconColor: Color {
        if file.isFolder {
            return brandBlue
        }
        switch file.category {
        case .image:
            return .pink
        case .video:
            return .purple
        case .audio:
            return .orange
        case .document:
            return .red
        case .archive:
            return .brown
        default:
            return .secondary
        }
    }
}

#Preview {
    HomeView()
        .environmentObject(FileListViewModel())
        .environmentObject(UploadViewModel())
}