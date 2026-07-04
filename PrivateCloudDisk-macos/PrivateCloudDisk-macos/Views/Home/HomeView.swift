import SwiftUI

// MARK: - 文件列表主页（企业级 v4 — 与 Web 前端 FileListView/FileGridView 统一）

/// 文件列表主页
///
/// 与 Web 前端完全统一：
/// - 工具栏（文件计数、搜索、视图切换、操作按钮）
/// - 面包屑导航已移至 console-header（MainView）
/// - 列表/网格视图切换
/// - 多选操作工具栏
/// - 卡片式白色背景（bg-white + shadow-card）
/// - 参考百度网盘 macOS 客户端设计
struct HomeView: View {
    @EnvironmentObject var fileListVM: FileListViewModel
    @EnvironmentObject var uploadVM: UploadViewModel

    @State private var isGridView = false
    @State private var searchText = ""
    @State private var selectedFileIds = Set<String>()
    @State private var localSortField: SortField = .name
    @State private var sortAscending = true
    @State private var showCreateFolder = false
    @State private var newFolderName = ""

    private let brandBlue = AppColors.primary

    enum SortField: String, CaseIterable {
        case name = "名称", size = "大小", type = "类型", modifiedDate = "修改时间"
    }

    var body: some View {
        VStack(spacing: 0) {
            // ── 工具栏 ──
            toolbarView

            // ── 文件内容 ──
            if fileListVM.isLoading {
                SkeletonLoading(count: 8)
                    .padding(16)
            } else if fileListVM.files.isEmpty {
                EmptyState(
                    icon: "folder.fill",
                    title: "暂无文件",
                    subtitle: "点击上方按钮上传或创建文件夹",
                    actionTitle: "上传文件",
                    action: { showUploadPicker() }
                )
            } else {
                if isGridView {
                    fileGridView
                } else {
                    fileListView
                }
            }
        }
        .background(AppColors.background)
        .sheet(isPresented: $showCreateFolder) { createFolderSheet }
        .onChange(of: searchText) { newValue in
            fileListVM.searchQuery = newValue
            if !newValue.isEmpty {
                Task { await fileListVM.search() }
            } else {
                fileListVM.clearSearch()
            }
        }
        .onChange(of: localSortField) { _ in applySorting() }
        .onChange(of: sortAscending) { _ in applySorting() }
    }

    private func showUploadPicker() {
        let panel = NSOpenPanel()
        panel.allowsMultipleSelection = true
        panel.canChooseDirectories = true
        panel.canChooseFiles = true
        panel.begin { response in
            if response == .OK {
                uploadVM.uploadFiles(urls: panel.urls)
            }
        }
    }

    private func applySorting() {
        switch localSortField {
        case .name: fileListVM.sortBy = "name"
        case .size: fileListVM.sortBy = "size"
        case .modifiedDate: fileListVM.sortBy = "updated_at"
        case .type: fileListVM.sortBy = "mime_type"
        }
        fileListVM.sortOrder = sortAscending ? "asc" : "desc"
        Task { await fileListVM.refreshFiles() }
    }

    // MARK: - 工具栏

    private var toolbarView: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                // 文件计数
                fileCountBadge

                Spacer()

                // 多选操作
                if !selectedFileIds.isEmpty {
                    multiSelectToolbar
                        .transition(.scale(scale: 0.95).combined(with: .opacity))
                }

                // 搜索框
                searchBar

                // 操作按钮 + 视图切换
                actionButtons
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 10)

            Divider().foregroundColor(AppColors.divider)
        }
        .background(AppColors.surface)
        .animation(AppAnimation.default, value: selectedFileIds.isEmpty)
    }

    // MARK: - 文件计数

    private var fileCountBadge: some View {
        HStack(spacing: 8) {
            Image(systemName: "folder.fill")
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(brandBlue)

            Text("全部文件")
                .font(.system(size: 13, weight: .semibold, design: .default))
                .foregroundColor(AppColors.textPrimary)

            Text("\(fileListVM.files.count)")
                .font(.system(size: 11, weight: .semibold, design: .default))
                .foregroundColor(AppColors.textSecondary)
                .padding(.horizontal, 6).padding(.vertical, 2)
                .background(RoundedRectangle(cornerRadius: 4).fill(AppColors.background))
        }
    }

    // MARK: - 多选工具栏

    private var multiSelectToolbar: some View {
        HStack(spacing: 4) {
            Text("已选 \(selectedFileIds.count) 项")
                .font(.system(size: 12, weight: .medium, design: .default))
                .foregroundColor(AppColors.textSecondary)

            HStack(spacing: 2) {
                toolbarButton(icon: "arrow.down.to.line", color: brandBlue, action: {})
                toolbarButton(icon: "square.and.arrow.up", color: AppColors.textSecondary, action: {})
                toolbarButton(icon: "trash", color: AppColors.danger, isDanger: true) {
                    Task { await fileListVM.deleteNodes(Array(selectedFileIds)) }
                }
                toolbarButton(icon: "ellipsis", color: AppColors.textSecondary, action: {})
            }
        }
    }

    private func toolbarButton(icon: String, color: Color, isDanger: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 12, weight: .medium))
        }
        .buttonStyle(.plain)
        .padding(6)
        .background(RoundedRectangle(cornerRadius: 6).fill(isDanger ? AppColors.dangerBg : AppColors.background))
        .foregroundColor(color)
    }

    // MARK: - 搜索栏

    private var searchBar: some View {
        HStack(spacing: 6) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 11, weight: .medium))
                .foregroundColor(AppColors.textTertiary)

            TextField("搜索文件...", text: $searchText)
                .textFieldStyle(.plain)
                .font(.system(size: 12, design: .default))

            if !searchText.isEmpty {
                Button(action: { searchText = "" }) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 11))
                        .foregroundColor(AppColors.textTertiary)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 10).padding(.vertical, 6)
        .frame(width: 200)
        .background(RoundedRectangle(cornerRadius: 8).fill(AppColors.background))
    }

    // MARK: - 操作按钮

    private var actionButtons: some View {
        HStack(spacing: 2) {
            Button(action: { showCreateFolder = true }) {
                Image(systemName: "folder.badge.plus").font(.system(size: 13, weight: .medium))
            }
            .buttonStyle(.plain).padding(6)
            .background(RoundedRectangle(cornerRadius: 6).fill(AppColors.background))

            Button(action: { showUploadPicker() }) {
                Image(systemName: "arrow.up.to.line").font(.system(size: 13, weight: .medium))
            }
            .buttonStyle(.plain).padding(6)
            .background(RoundedRectangle(cornerRadius: 6).fill(AppColors.background))

            Divider().frame(height: 16).padding(.horizontal, 4)

            Button(action: {
                withAnimation(AppAnimation.default) { isGridView.toggle() }
            }) {
                Image(systemName: isGridView ? "list.bullet" : "square.grid.2x2")
                    .font(.system(size: 13, weight: .medium))
            }
            .buttonStyle(.plain).padding(6)
            .background(RoundedRectangle(cornerRadius: 6).fill(AppColors.background))
        }
    }

    // MARK: - 文件列表

    private var fileListView: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                ForEach(sortedFiles, id: \.id) { node in
                    FileRowView(node: node, isSelected: selectedFileIds.contains(node.id))
                        .onTapGesture(count: 2) {
                            if node.isFolder {
                                Task { await fileListVM.loadFiles(parentId: node.id) }
                            }
                        }
                        .onTapGesture(count: 1) {
                            toggleSelection(node.id)
                        }
                        .contextMenu { fileContextMenu(for: node) }

                    Divider()
                        .foregroundColor(AppColors.divider)
                        .padding(.leading, 56)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(AppColors.surface)
                    .shadow(color: AppShadow.card.color, radius: AppShadow.card.radius, y: AppShadow.card.y)
            )
            .padding(16)
        }
    }

    // MARK: - 文件网格

    private var fileGridView: some View {
        ScrollView {
            LazyVGrid(
                columns: [GridItem(.adaptive(minimum: 120, maximum: 150), spacing: 16)],
                spacing: 16
            ) {
                ForEach(sortedFiles, id: \.id) { node in
                    FileGridCell(node: node, isSelected: selectedFileIds.contains(node.id))
                        .onTapGesture(count: 2) {
                            if node.isFolder {
                                Task { await fileListVM.loadFiles(parentId: node.id) }
                            }
                        }
                        .contextMenu { fileGridContextMenu(for: node) }
                }
            }
            .padding(16)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(AppColors.surface)
                    .shadow(color: AppShadow.card.color, radius: AppShadow.card.radius, y: AppShadow.card.y)
            )
            .padding(16)
        }
    }

    // MARK: - 右键菜单

    @ViewBuilder
    private func fileContextMenu(for node: FileNode) -> some View {
        if node.isFolder {
            Button("打开") {
                Task { await fileListVM.loadFiles(parentId: node.id) }
            }
        } else {
            Button("下载") {
                Task { _ = try? await DownloadManager.shared.downloadFile(nodeId: node.id, filename: node.name) }
            }
        }
        Divider()
        Button("重命名") {}
        Button("移动...") {}
        Button("复制") {}
        Divider()
        if node.isStarred == true {
            Button("取消收藏") { Task { await fileListVM.toggleStar(nodeId: node.id, starred: false) } }
        } else {
            Button("添加到收藏") { Task { await fileListVM.toggleStar(nodeId: node.id, starred: true) } }
        }
        Divider()
        Button("移到回收站", role: .destructive) {
            Task { await fileListVM.deleteNodes([node.id]) }
        }
    }

    @ViewBuilder
    private func fileGridContextMenu(for node: FileNode) -> some View {
        if node.isFolder {
            Button("打开") { Task { await fileListVM.loadFiles(parentId: node.id) } }
        } else {
            Button("下载") {}
        }
        Divider()
        Button("重命名") {}
        Button("移到回收站", role: .destructive) {
            Task { await fileListVM.deleteNodes([node.id]) }
        }
    }

    // MARK: - 创建文件夹 Sheet

    private var createFolderSheet: some View {
        VStack(spacing: 20) {
            Text("新建文件夹").font(AppTypography.title3)
            HStack(spacing: 10) {
                Image(systemName: "folder.fill").foregroundColor(brandBlue)
                TextField("文件夹名称", text: $newFolderName)
                    .textFieldStyle(.plain)
                    .font(.system(size: 14, design: .default))
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .background(RoundedRectangle(cornerRadius: 10).fill(AppColors.background))

            HStack(spacing: 12) {
                Button("取消") { showCreateFolder = false }
                    .buttonStyle(.plain).foregroundColor(AppColors.textSecondary)
                Button("创建") {
                    if !newFolderName.isEmpty {
                        Task { await fileListVM.createFolder(name: newFolderName) }
                        newFolderName = ""
                        showCreateFolder = false
                    }
                }
                .buttonStyle(.borderedProminent).tint(brandBlue)
                .disabled(newFolderName.isEmpty)
            }
        }
        .padding(24).frame(width: 320, height: 180)
    }

    // MARK: - Helpers

    private var sortedFiles: [FileNode] {
        fileListVM.files.sorted { a, b in
            if a.isFolder != b.isFolder { return a.isFolder }
            return a.name.localizedCaseInsensitiveCompare(b.name) == .orderedAscending
        }
    }

    private func toggleSelection(_ id: String) {
        if selectedFileIds.contains(id) {
            selectedFileIds.remove(id)
        } else {
            selectedFileIds.insert(id)
        }
    }
}

// MARK: - 文件行视图（企业级 v4 — 与 Web FileListView 统一）

struct FileRowView: View {
    let node: FileNode
    let isSelected: Bool
    @State private var isHovered = false

    private let brandBlue = AppColors.primary

    var body: some View {
        HStack(spacing: 12) {
            // 文件图标
            ZStack {
                RoundedRectangle(cornerRadius: 6)
                    .fill((node.isFolder ? brandBlue : fileColor).opacity(0.1))
                    .frame(width: 34, height: 34)

                Image(systemName: node.isFolder ? "folder.fill" : node.category.sfSymbolName)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(node.isFolder ? brandBlue : fileColor)
            }

            // 文件名称
            Text(node.name)
                .font(.system(size: 13, weight: .medium, design: .default))
                .foregroundColor(AppColors.textPrimary)
                .lineLimit(1)

            Spacer()

            // 类型
            Text(node.isFolder ? "文件夹" : fileExtension)
                .font(.system(size: 12, design: .default))
                .foregroundColor(AppColors.textSecondary)
                .frame(width: 100, alignment: .leading)

            // 大小
            Text(node.isFolder ? "--" : node.formattedSize)
                .font(.system(size: 12, design: .default))
                .foregroundColor(AppColors.textSecondary)
                .frame(width: 90, alignment: .leading)

            // 修改时间
            Text(node.formattedDate)
                .font(.system(size: 12, design: .default))
                .foregroundColor(AppColors.textSecondary)
                .frame(width: 140, alignment: .leading)

            // 操作按钮
            HStack(spacing: 4) {
                if node.isStarred == true {
                    Image(systemName: "star.fill")
                        .font(.system(size: 10))
                        .foregroundColor(AppColors.warning)
                }

                if !node.isFolder {
                    Button(action: {}) {
                        Image(systemName: "arrow.down.circle")
                            .font(.system(size: 14))
                            .foregroundColor(brandBlue)
                    }
                    .buttonStyle(.plain)
                }

                Button(action: {}) {
                    Image(systemName: "ellipsis.circle")
                        .font(.system(size: 14))
                        .foregroundColor(AppColors.textTertiary)
                }
                .buttonStyle(.plain)
            }
            .frame(width: 80, alignment: .trailing)
            .opacity(isHovered ? 1 : 0)
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 8)
        .background(
            RoundedRectangle(cornerRadius: 8)
                .fill(isSelected ? brandBlue.opacity(0.08) : (isHovered ? AppColors.background : .clear))
        )
        .onHover { hovering in
            withAnimation(AppAnimation.fast) { isHovered = hovering }
        }
    }

    private var fileColor: Color {
        switch node.category {
        case .document: return brandBlue
        case .image: return AppColors.fileImage
        case .video: return AppColors.fileVideo
        case .audio: return AppColors.fileAudio
        case .archive: return AppColors.fileArchive
        case .code: return AppColors.fileCode
        case .other: return AppColors.textTertiary
        }
    }

    private var fileExtension: String {
        let parts = node.name.split(separator: ".")
        return parts.count > 1 ? String(parts.last!).uppercased() : "--"
    }
}

// MARK: - 文件网格单元格（企业级 v4 — 与 Web FileGridView 统一）

struct FileGridCell: View {
    let node: FileNode
    let isSelected: Bool
    @State private var isHovered = false

    private let brandBlue = AppColors.primary

    var body: some View {
        VStack(spacing: 8) {
            ZStack(alignment: .topLeading) {
                // 图标
                ZStack {
                    RoundedRectangle(cornerRadius: 14)
                        .fill((node.isFolder ? brandBlue : fileColor).opacity(0.08))
                        .frame(width: 64, height: 64)

                    Image(systemName: node.isFolder ? "folder.fill" : node.category.sfSymbolName)
                        .font(.system(size: 26))
                        .foregroundColor(node.isFolder ? brandBlue : fileColor)
                }

                // 选中标记
                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 18))
                        .foregroundColor(brandBlue)
                        .background(Circle().fill(Color.white).frame(width: 14, height: 14))
                        .offset(x: -4, y: -4)
                }
            }

            // 文件名
            Text(node.name)
                .font(.system(size: 12, weight: .medium, design: .default))
                .lineLimit(2)
                .multilineTextAlignment(.center)
                .foregroundColor(AppColors.textPrimary)
                .frame(width: 100)

            // 元信息
            Text(node.isFolder ? "文件夹" : node.formattedSize)
                .font(.system(size: 10, design: .default))
                .foregroundColor(AppColors.textTertiary)
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 4)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(isSelected ? brandBlue.opacity(0.08) : (isHovered ? AppColors.background : .clear))
        )
        .onHover { hovering in
            withAnimation(AppAnimation.fast) { isHovered = hovering }
        }
    }

    private var fileColor: Color {
        switch node.category {
        case .document: return brandBlue
        case .image: return AppColors.fileImage
        case .video: return AppColors.fileVideo
        case .audio: return AppColors.fileAudio
        case .archive: return AppColors.fileArchive
        case .code: return AppColors.fileCode
        case .other: return AppColors.textTertiary
        }
    }
}