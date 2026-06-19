//
//  FileBrowserView.swift
//  PrivateCloudDisk-ios
//
//  文件浏览器 — 主文件列表页面
//  支持列表/网格视图、下拉刷新、搜索、排序、多选操作
//

import SwiftUI

struct FileBrowserView: View {
    @StateObject private var viewModel = FileBrowserViewModel()
    @State private var showCreateFolder = false
    @State private var newFolderName = ""
    @State private var showFileDetail: FileNode?
    @State private var showActionSheet = false
    @State private var selectedNode: FileNode?

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // 搜索栏
                searchBar

                // 视图模式 & 排序
                toolbarBar

                // 面包屑导航
                breadcrumbBar

                // 文件列表
                if viewModel.isLoading && viewModel.children.isEmpty {
                    Spacer()
                    ProgressView("加载中...")
                    Spacer()
                } else if let error = viewModel.errorMessage {
                    Spacer()
                    VStack(spacing: 12) {
                        Image(systemName: "exclamationmark.triangle")
                            .font(.largeTitle)
                            .foregroundStyle(.orange)
                        Text(error)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        Button("重试") {
                            Task { await viewModel.loadRoot() }
                        }
                        .buttonStyle(.bordered)
                    }
                    Spacer()
                } else if viewModel.filteredChildren.isEmpty {
                    Spacer()
                    ContentUnavailableView(
                        "暂无文件",
                        systemImage: "folder",
                        description: Text("当前目录为空")
                    )
                    Spacer()
                } else {
                    fileList
                }
            }
            .navigationTitle(viewModel.currentNode?.nodeName ?? "文件")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    HStack(spacing: 4) {
                        Button(action: { showCreateFolder = true }) {
                            Image(systemName: "folder.badge.plus")
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
                        }
                        Button(action: { viewModel.viewMode = viewModel.viewMode == .list ? .grid : .list }) {
                            Image(systemName: viewModel.viewMode == .list ? "square.grid.2x2" : "list.bullet")
                        }
                    }
                }
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

    // MARK: - 搜索栏

    private var searchBar: some View {
        HStack {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(.secondary)
            TextField("搜索文件", text: $viewModel.searchText)
                .textFieldStyle(.plain)
            if !viewModel.searchText.isEmpty {
                Button(action: { viewModel.searchText = "" }) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(.secondary)
                }
            }
        }
        .padding(10)
        .background(Color(.systemGray6))
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .padding(.horizontal)
        .padding(.top, 8)
    }

    // MARK: - 工具栏

    private var toolbarBar: some View {
        HStack {
            Text("\(viewModel.filteredChildren.count) 个项目")
                .font(.caption)
                .foregroundStyle(.secondary)
            Spacer()
        }
        .padding(.horizontal)
        .padding(.vertical, 6)
    }

    // MARK: - 面包屑

    private var breadcrumbBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 4) {
                ForEach(Array(viewModel.navigationStack.enumerated()), id: \.offset) { index, node in
                    if index > 0 {
                        Image(systemName: "chevron.right")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                    Button(node.nodeName) {
                        viewModel.navigateToNode(node)
                    }
                    .font(.caption)
                    .foregroundStyle(index == viewModel.navigationStack.count - 1 ? .primary : .secondary)
                    .lineLimit(1)
                }
            }
            .padding(.horizontal)
            .padding(.vertical, 6)
        }
    }

    // MARK: - 文件列表

    private var fileList: some View {
        Group {
            if viewModel.viewMode == .list {
                List {
                    ForEach(viewModel.filteredChildren) { node in
                        FileRowView(node: node) {
                            viewModel.navigateTo(node)
                        } onTapDetail: {
                            showFileDetail = node
                        } onMore: {
                            selectedNode = node
                            showActionSheet = true
                        }
                    }
                }
                .listStyle(.plain)
            } else {
                ScrollView {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 120), spacing: 12)], spacing: 12) {
                        ForEach(viewModel.filteredChildren) { node in
                            FileGridItemView(node: node) {
                                if node.isFolder {
                                    viewModel.navigateTo(node)
                                } else {
                                    showFileDetail = node
                                }
                            }
                        }
                    }
                    .padding()
                }
            }
        }
        .confirmationDialog("操作", isPresented: $showActionSheet, presenting: selectedNode) { node in
            Button("重命名") { /* TODO */ }
            Button("移动") { /* TODO */ }
            Button("收藏", action: {
                Task { await viewModel.toggleStar(node) }
            })
            Button("分享", action: { /* TODO: 打开分享创建 */ })
            Button("删除", role: .destructive) {
                Task { await viewModel.deleteNode(node) }
            }
            Button("取消", role: .cancel) {}
        }
    }
}

// MARK: - 文件行组件

struct FileRowView: View {
    let node: FileNode
    let onTapFolder: () -> Void
    let onTapDetail: () -> Void
    let onMore: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            // 图标
            Image(systemName: node.systemIcon)
                .font(.title3)
                .foregroundStyle(iconColor)
                .frame(width: 36, height: 36)
                .background(iconColor.opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: 8))

            // 信息
            VStack(alignment: .leading, spacing: 2) {
                Text(node.nodeName)
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .lineLimit(1)
                HStack(spacing: 8) {
                    if !node.isFolder {
                        Text(node.formattedSize)
                    }
                    Text(node.fileExtension.uppercased())
                    if let date = node.updatedAt {
                        Text(formatDate(date))
                    }
                }
                .font(.caption)
                .foregroundStyle(.secondary)
            }

            Spacer()

            // 操作按钮
            if node.isFolder {
                Button(action: onTapFolder) {
                    Image(systemName: "chevron.right")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            } else {
                Button(action: onTapDetail) {
                    Image(systemName: "info.circle")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            Button(action: onMore) {
                Image(systemName: "ellipsis")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .padding(8)
            }
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
        .onTapGesture {
            if node.isFolder { onTapFolder() } else { onTapDetail() }
        }
    }

    private var iconColor: Color {
        if node.isFolder { return .blue }
        if node.isVideo { return .purple }
        if node.isImage { return .green }
        if node.isAudio { return .orange }
        if node.isPDF { return .red }
        return .gray
    }

    private func formatDate(_ dateStr: String) -> String {
        let formatter = ISO8601DateFormatter()
        if let date = formatter.date(from: dateStr) {
            let relative = RelativeDateTimeFormatter()
            relative.unitsStyle = .abbreviated
            return relative.localizedString(for: date, relativeTo: Date())
        }
        return dateStr
    }
}

// MARK: - 网格项组件

struct FileGridItemView: View {
    let node: FileNode
    let onTap: () -> Void

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: node.systemIcon)
                .font(.system(size: 40))
                .foregroundStyle(iconColor)
                .frame(height: 50)

            Text(node.nodeName)
                .font(.caption)
                .lineLimit(2)
                .multilineTextAlignment(.center)

            if !node.isFolder {
                Text(node.formattedSize)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity)
        .background(Color(.systemGray6))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .onTapGesture(perform: onTap)
    }

    private var iconColor: Color {
        if node.isFolder { return .blue }
        if node.isVideo { return .purple }
        if node.isImage { return .green }
        return .gray
    }
}

#Preview {
    FileBrowserView()
}