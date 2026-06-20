//
//  FileBrowserView.swift
//  PrivateCloudDisk-ios
//
//  文件浏览器 — 企业级文件管理页面
//  支持列表/网格视图、搜索、排序、面包屑导航、多选操作
//

import SwiftUI

struct FileBrowserView: View {
    @StateObject private var viewModel = FileBrowserViewModel()
    @State private var showCreateFolder = false
    @State private var newFolderName = ""
    @State private var showFileDetail: FileNode?
    @State private var showActionSheet = false
    @State private var selectedNode: FileNode?
    @State private var isMultiSelectMode = false
    @State private var selectedNodes: Set<String> = []
    @State private var showSortPicker = false

    var body: some View {
        NavigationStack {
            ZStack {
                AppColors.background.ignoresSafeArea(edges: .bottom)

                VStack(spacing: 0) {
                    // 搜索栏
                    searchSection
                        .padding(.horizontal, AppSpacing.lg)
                        .padding(.top, AppSpacing.sm)

                    // 面包屑导航
                    breadcrumbBar

                    // 内容区
                    contentArea
                }
            }
            .navigationTitle(viewModel.currentNode?.nodeName ?? "文件")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                toolbarContent
            }
            .refreshable {
                await viewModel.loadChildren()
            }
            .task {
                await viewModel.loadRoot()
            }
            .alert("新建文件夹", isPresented: $showCreateFolder) {
                TextField("文件夹名称", text: $newFolderName)
                Button("取消", role: .cancel) {}
                Button("创建") {
                    Task { await viewModel.createFolder(name: newFolderName) }
                    newFolderName = ""
                }
            }
            .sheet(item: $showFileDetail) { node in
                FileDetailView(fileNode: node)
            }
        }
    }

    // MARK: - Toolbar

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        ToolbarItem(placement: .topBarTrailing) {
            HStack(spacing: 2) {
                if isMultiSelectMode {
                    Button("完成") {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            isMultiSelectMode = false
                            selectedNodes.removeAll()
                        }
                    }
                    .font(AppTypography.subheadline.weight(.medium))
                } else {
                    AppIconButton("folder.badge.plus", color: AppColors.primary) {
                        showCreateFolder = true
                    }

                    Menu {
                        Button(action: { viewModel.sortOrder = .nameAsc }) {
                            Label("名称 A-Z", systemImage: viewModel.sortOrder == .nameAsc ? "checkmark" : "")
                        }
                        Button(action: { viewModel.sortOrder = .dateNewest }) {
                            Label("最新优先", systemImage: viewModel.sortOrder == .dateNewest ? "checkmark" : "")
                        }
                        Button(action: { viewModel.sortOrder = .sizeLargest }) {
                            Label("最大优先", systemImage: viewModel.sortOrder == .sizeLargest ? "checkmark" : "")
                        }
                    } label: {
                        Image(systemName: "arrow.up.arrow.down")
                            .font(.subheadline)
                            .foregroundColor(AppColors.textSecondary)
                            .frame(width: 36, height: 36)
                            .background(AppColors.textSecondary.opacity(0.08))
                            .clipShape(Circle())
                    }

                    Button(action: {
                        withAnimation(.easeInOut(duration: 0.25)) {
                            viewModel.viewMode = viewModel.viewMode == .list ? .grid : .list
                        }
                    }) {
                        Image(systemName: viewModel.viewMode == .list ? "square.grid.2x2" : "list.bullet")
                            .font(.subheadline)
                            .foregroundColor(AppColors.textSecondary)
                            .frame(width: 36, height: 36)
                            .background(AppColors.textSecondary.opacity(0.08))
                            .clipShape(Circle())
                    }
                }
            }
        }
    }

    // MARK: - 搜索区

    private var searchSection: some View {
        HStack(spacing: AppSpacing.md) {
            AppSearchBar(text: $viewModel.searchText, placeholder: "搜索文件")

            if !viewModel.searchText.isEmpty {
                Button("取消") {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        viewModel.searchText = ""
                    }
                }
                .font(AppTypography.subheadline)
                .foregroundColor(AppColors.primary)
                .transition(.move(edge: .trailing).combined(with: .opacity))
            }
        }
        .animation(.easeInOut(duration: 0.2), value: viewModel.searchText)
    }

    // MARK: - 面包屑

    private var breadcrumbBar: some View {
        Group {
            if viewModel.navigationStack.count > 1 {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 2) {
                        ForEach(Array(viewModel.navigationStack.enumerated()), id: \.offset) { index, node in
                            if index > 0 {
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 9, weight: .medium))
                                    .foregroundColor(AppColors.textTertiary)
                                    .padding(.horizontal, 2)
                            }
                            Button(action: { viewModel.navigateToNode(node) }) {
                                Text(node.nodeName)
                                    .font(AppTypography.caption1)
                                    .foregroundColor(
                                        index == viewModel.navigationStack.count - 1
                                            ? AppColors.textPrimary
                                            : AppColors.textSecondary
                                    )
                                    .fontWeight(index == viewModel.navigationStack.count - 1 ? .semibold : .regular)
                                    .lineLimit(1)
                            }
                        }
                    }
                    .padding(.horizontal, AppSpacing.lg)
                    .padding(.vertical, 8)
                }
                .background(AppColors.surfaceSecondary.opacity(0.5))
            }
        }
    }

    // MARK: - 内容区

    private var contentArea: some View {
        Group {
            if viewModel.isLoading && viewModel.children.isEmpty {
                AppEmptyState(
                    icon: "folder",
                    title: "加载中...",
                    message: "正在获取文件列表"
                )
            } else if let error = viewModel.errorMessage {
                AppEmptyState(
                    icon: "exclamationmark.triangle",
                    title: "加载失败",
                    message: error,
                    actionTitle: "重试",
                    action: { Task { await viewModel.loadRoot() } }
                )
            } else if viewModel.filteredChildren.isEmpty && !viewModel.searchText.isEmpty {
                AppEmptyState(
                    icon: "magnifyingglass",
                    title: "没有找到文件",
                    message: "尝试其他关键词搜索"
                )
            } else if viewModel.filteredChildren.isEmpty {
                AppEmptyState(
                    icon: "folder",
                    title: "暂无文件",
                    message: "当前目录为空，点击右上角上传文件",
                    actionTitle: "新建文件夹",
                    action: { showCreateFolder = true }
                )
            } else {
                Group {
                    // 统计栏
                    statsBar

                    // 文件列表
                    if viewModel.viewMode == .list {
                        fileListView
                    } else {
                        fileGridView
                    }
                }
            }
        }
    }

    private var statsBar: some View {
        HStack {
            Text("\(viewModel.filteredChildren.count) 个项目")
                .font(AppTypography.footnote)
                .foregroundColor(AppColors.textSecondary)

            Spacer()

            if isMultiSelectMode {
                Text("已选 \(selectedNodes.count) 项")
                    .font(AppTypography.footnote.weight(.medium))
                    .foregroundColor(AppColors.primary)
            }
        }
        .padding(.horizontal, AppSpacing.lg)
        .padding(.vertical, 6)
    }

    // MARK: - 列表视图

    private var fileListView: some View {
        List {
            ForEach(viewModel.filteredChildren) { node in
                FileRowView(
                    node: node,
                    isSelected: selectedNodes.contains(node.id),
                    isMultiSelectMode: isMultiSelectMode
                ) {
                    if isMultiSelectMode {
                        toggleSelection(node)
                    } else if node.isFolder {
                        viewModel.navigateTo(node)
                    } else {
                        showFileDetail = node
                    }
                } onLongPress: {
                    if !isMultiSelectMode {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            isMultiSelectMode = true
                            selectedNodes.insert(node.id)
                        }
                        let generator = UIImpactFeedbackGenerator(style: .medium)
                        generator.impactOccurred()
                    }
                } onMore: {
                    selectedNode = node
                    showActionSheet = true
                }
                .listRowInsets(EdgeInsets(top: 2, leading: AppSpacing.lg, bottom: 2, trailing: AppSpacing.lg))
                .listRowSeparatorTint(AppColors.dividerLight)
                .listRowBackground(Color.clear)
                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                    Button(role: .destructive) {
                        Task { await viewModel.deleteNode(node) }
                    } label: {
                        Label("删除", systemImage: "trash")
                    }

                    Button {
                        Task { await viewModel.toggleStar(node) }
                    } label: {
                        Label("收藏", systemImage: "star")
                    }
                    .tint(AppColors.warning)
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
    }

    // MARK: - 网格视图

    private var fileGridView: some View {
        ScrollView {
            LazyVGrid(
                columns: [
                    GridItem(.adaptive(minimum: 110, maximum: 160), spacing: AppSpacing.md)
                ],
                spacing: AppSpacing.md
            ) {
                ForEach(viewModel.filteredChildren) { node in
                    FileGridItemView(
                        node: node,
                        isSelected: selectedNodes.contains(node.id),
                        isMultiSelectMode: isMultiSelectMode
                    ) {
                        if isMultiSelectMode {
                            toggleSelection(node)
                        } else if node.isFolder {
                            viewModel.navigateTo(node)
                        } else {
                            showFileDetail = node
                        }
                    } onLongPress: {
                        if !isMultiSelectMode {
                            withAnimation(.easeInOut(duration: 0.2)) {
                                isMultiSelectMode = true
                                selectedNodes.insert(node.id)
                            }
                            let generator = UIImpactFeedbackGenerator(style: .medium)
                            generator.impactOccurred()
                        }
                    }
                }
            }
            .padding(AppSpacing.lg)
        }
    }

    // MARK: - 辅助方法

    private func toggleSelection(_ node: FileNode) {
        if selectedNodes.contains(node.id) {
            selectedNodes.remove(node.id)
        } else {
            selectedNodes.insert(node.id)
        }
    }
}

// MARK: - 文件行组件

struct FileRowView: View {
    let node: FileNode
    let isSelected: Bool
    let isMultiSelectMode: Bool
    let onTap: () -> Void
    let onLongPress: () -> Void
    let onMore: () -> Void

    var body: some View {
        HStack(spacing: AppSpacing.md) {
            // 选择指示器
            if isMultiSelectMode {
                ZStack {
                    Circle()
                        .stroke(isSelected ? AppColors.primary : AppColors.textTertiary, lineWidth: 2)
                        .frame(width: 22, height: 22)

                    if isSelected {
                        Circle()
                            .fill(AppColors.primary)
                            .frame(width: 22, height: 22)
                        Image(systemName: "checkmark")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(.white)
                    }
                }
                .transition(.scale.combined(with: .opacity))
            }

            // 文件图标
            FileIcon(node: node, size: 42)

            // 文件信息
            VStack(alignment: .leading, spacing: 3) {
                Text(node.nodeName)
                    .font(AppTypography.subheadline.weight(.medium))
                    .foregroundColor(AppColors.textPrimary)
                    .lineLimit(1)

                HStack(spacing: 6) {
                    if !node.isFolder {
                        Text(node.formattedSize)
                            .font(AppTypography.caption2)
                            .foregroundColor(AppColors.textSecondary)
                        Text("·")
                            .foregroundColor(AppColors.textTertiary)
                    }
                    Text(node.fileExtension.uppercased())
                        .font(AppTypography.caption2)
                        .foregroundColor(AppColors.textSecondary)
                    if let date = node.updatedAt {
                        Text("·")
                            .foregroundColor(AppColors.textTertiary)
                        Text(formatDate(date))
                            .font(AppTypography.caption2)
                            .foregroundColor(AppColors.textSecondary)
                    }
                }
            }

            Spacer()

            // 操作
            if !isMultiSelectMode {
                Button(action: onMore) {
                    Image(systemName: "ellipsis")
                        .font(.subheadline)
                        .foregroundColor(AppColors.textTertiary)
                        .frame(width: 32, height: 32)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.vertical, 6)
        .padding(.horizontal, AppSpacing.md)
        .background(
            RoundedRectangle(cornerRadius: AppRadius.md)
                .fill(isSelected ? AppColors.primaryBg : Color.clear)
        )
        .contentShape(Rectangle())
        .onTapGesture(perform: onTap)
        .onLongPressGesture(perform: onLongPress)
        .animation(.easeInOut(duration: 0.2), value: isMultiSelectMode)
    }

    private func formatDate(_ dateStr: String) -> String {
        let formatter = ISO8601DateFormatter()
        guard let date = formatter.date(from: dateStr) else { return dateStr }
        let relative = RelativeDateTimeFormatter()
        relative.unitsStyle = .abbreviated
        return relative.localizedString(for: date, relativeTo: Date())
    }
}

// MARK: - 网格项组件

struct FileGridItemView: View {
    let node: FileNode
    let isSelected: Bool
    let isMultiSelectMode: Bool
    let onTap: () -> Void
    let onLongPress: () -> Void

    var body: some View {
        VStack(spacing: AppSpacing.sm) {
            // 图标
            ZStack(alignment: .topTrailing) {
                FileIcon(node: node, size: 56)

                if isMultiSelectMode {
                    ZStack {
                        Circle()
                            .fill(isSelected ? AppColors.primary : AppColors.surface)
                            .frame(width: 22, height: 22)
                            .overlay(
                                Circle()
                                    .stroke(isSelected ? AppColors.primary : AppColors.textTertiary, lineWidth: 2)
                            )

                        if isSelected {
                            Image(systemName: "checkmark")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(.white)
                        }
                    }
                    .offset(x: 6, y: -6)
                }
            }

            // 名称
            Text(node.nodeName)
                .font(AppTypography.caption1)
                .foregroundColor(AppColors.textPrimary)
                .lineLimit(2)
                .multilineTextAlignment(.center)

            // 大小
            if !node.isFolder {
                Text(node.formattedSize)
                    .font(AppTypography.caption2)
                    .foregroundColor(AppColors.textSecondary)
            }
        }
        .padding(AppSpacing.md)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: AppRadius.lg)
                .fill(isSelected ? AppColors.primaryBg : AppColors.surface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: AppRadius.lg)
                .stroke(isSelected ? AppColors.primary.opacity(0.3) : AppColors.dividerLight, lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.03), radius: 4, y: 2)
        .contentShape(Rectangle())
        .onTapGesture(perform: onTap)
        .onLongPressGesture(perform: onLongPress)
        .animation(.easeInOut(duration: 0.2), value: isMultiSelectMode)
    }
}

#Preview {
    FileBrowserView()
}