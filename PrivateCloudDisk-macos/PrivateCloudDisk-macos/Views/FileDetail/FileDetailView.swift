import SwiftUI
import QuickLookUI

// MARK: - 文件详情视图（企业级设计）

/// 文件/文件夹详情视图
///
/// 参考百度网盘 macOS 客户端设计：
/// - 毛玻璃工具栏
/// - 文件预览头 + 渐变图标
/// - 分段标签页
/// - 分享链接管理
/// - 品牌色点缀
struct FileDetailView: View {
    @StateObject private var viewModel = FileDetailViewModel()
    let nodeId: String
    let nodeName: String

    @State private var selectedTab = DetailTab.info
    @State private var showQuickLook = false
    @State private var showShareSheet = false
    @State private var sharePassword = ""
    @State private var shareExpireDays = 7

    private let brandBlue = AppColors.primary

    enum DetailTab: String, CaseIterable {
        case info = "信息"
        case share = "分享"
        case versions = "版本"

        var sfSymbol: String {
            switch self {
            case .info: return "info.circle"
            case .share: return "square.and.arrow.up"
            case .versions: return "clock"
            }
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            // 工具栏
            detailToolbar

            Divider()
                .opacity(0.4)

            // 文件预览头
            if let node = viewModel.node {
                fileHeader(node: node)

                Divider()
                    .opacity(0.3)

                // 标签页
                segmentedTabPicker

                Divider()
                    .opacity(0.3)

                // 内容
                ScrollView(showsIndicators: false) {
                    switch selectedTab {
                    case .info:
                        infoTab(node: node)
                    case .share:
                        shareTab
                    case .versions:
                        versionsTab
                    }
                }
            } else if viewModel.isLoading {
                LoadingStateView(message: "加载中...")
            } else if let error = viewModel.errorMessage {
                VStack(spacing: 16) {
                    Spacer()
                    ZStack {
                        Circle()
                            .fill(.orange.opacity(0.1))
                            .frame(width: 64, height: 64)
                        Image(systemName: "exclamationmark.triangle")
                            .font(.system(size: 28, weight: .light))
                            .foregroundColor(.orange)
                    }
                    Text(error)
                        .font(.system(size: 13, design: .rounded))
                        .foregroundColor(.secondary)
                    Spacer()
                }
            }

            Spacer()
        }
        .frame(minWidth: 500, idealWidth: 600, minHeight: 400)
        .background(.ultraThinMaterial)
        .onAppear {
            Task { await viewModel.loadDetail(nodeId: nodeId) }
        }
        .sheet(isPresented: $showShareSheet) {
            shareSheetView
        }
    }

    // MARK: - 工具栏

    private var detailToolbar: some View {
        HStack(spacing: 14) {
            // 文件图标
            if let node = viewModel.node {
                ZStack {
                    RoundedRectangle(cornerRadius: 6)
                        .fill(node.isFolder ? brandBlue.opacity(0.1) : Color.secondary.opacity(0.1))
                        .frame(width: 28, height: 28)
                    Image(systemName: node.isFolder ? "folder.fill" : node.category.sfSymbolName)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(node.isFolder ? brandBlue : .secondary)
                }
            }

            Text(nodeName)
                .font(.system(size: 14, weight: .semibold, design: .rounded))
                .lineLimit(1)

            Spacer()

            if let node = viewModel.node, !node.isFolder {
                HStack(spacing: 8) {
                    Button(action: {
                        Task { await viewModel.downloadFile() }
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "arrow.down.circle")
                                .font(.system(size: 12))
                            Text("下载")
                                .font(.system(size: 12, design: .rounded))
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 7)
                        .background(
                            RoundedRectangle(cornerRadius: 8)
                                .fill(brandBlue)
                        )
                        .foregroundColor(.white)
                    }
                    .buttonStyle(.plain)
                    .disabled(viewModel.isDownloading)

                    Button(action: { showQuickLook = true }) {
                        HStack(spacing: 6) {
                            Image(systemName: "eye")
                                .font(.system(size: 12))
                            Text("预览")
                                .font(.system(size: 12, design: .rounded))
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 7)
                        .background(
                            RoundedRectangle(cornerRadius: 8)
                                .fill(.quaternary.opacity(0.5))
                        )
                        .foregroundColor(.primary)
                    }
                    .buttonStyle(.plain)

                    Button(action: { showShareSheet = true }) {
                        HStack(spacing: 6) {
                            Image(systemName: "square.and.arrow.up")
                                .font(.system(size: 12))
                            Text("分享")
                                .font(.system(size: 12, design: .rounded))
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 7)
                        .background(
                            RoundedRectangle(cornerRadius: 8)
                                .fill(.quaternary.opacity(0.5))
                        )
                        .foregroundColor(.primary)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .background(.regularMaterial)
    }

    // MARK: - 文件头部

    private func fileHeader(node: FileNode) -> some View {
        HStack(spacing: 20) {
            // 预览图标
            ZStack {
                RoundedRectangle(cornerRadius: 18)
                    .fill(
                        node.isFolder
                            ? brandBlue.opacity(0.1)
                            : Color.secondary.opacity(0.08)
                    )
                    .frame(width: 88, height: 88)

                Image(systemName: node.isFolder ? "folder.fill" : node.category.sfSymbolName)
                    .font(.system(size: 38))
                    .foregroundColor(node.isFolder ? brandBlue : .secondary)
            }

            VStack(alignment: .leading, spacing: 6) {
                Text(node.name)
                    .font(.system(size: 20, weight: .bold, design: .rounded))

                if !node.isFolder {
                    HStack(spacing: 8) {
                        HStack(spacing: 4) {
                            Circle()
                                .fill(brandBlue)
                                .frame(width: 6, height: 6)
                            Text(node.formattedSize)
                                .font(.system(size: 13, design: .rounded))
                        }
                        .foregroundColor(.secondary)
                    }
                }

                HStack(spacing: 4) {
                    Image(systemName: "clock")
                        .font(.system(size: 10))
                    Text("修改于 \(node.formattedDate)")
                        .font(.system(size: 11, design: .rounded))
                }
                .foregroundStyle(.tertiary)
            }

            Spacer()
        }
        .padding(20)
    }

    // MARK: - 分段标签页

    private var segmentedTabPicker: some View {
        HStack(spacing: 0) {
            ForEach(DetailTab.allCases, id: \.self) { tab in
                Button(action: {
                    withAnimation(.easeInOut(duration: 0.15)) {
                        selectedTab = tab
                    }
                }) {
                    HStack(spacing: 6) {
                        Image(systemName: tab.sfSymbol)
                            .font(.system(size: 12))
                        Text(tab.rawValue)
                            .font(.system(size: 12, weight: selectedTab == tab ? .semibold : .regular, design: .rounded))
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(
                        RoundedRectangle(cornerRadius: 8)
                            .fill(selectedTab == tab ? brandBlue.opacity(0.1) : .clear)
                    )
                    .foregroundColor(selectedTab == tab ? brandBlue : .secondary)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }

    // MARK: - 信息标签页

    private func infoTab(node: FileNode) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            DetailInfoRow(label: "名称", value: node.name)
            DetailDivider()
            DetailInfoRow(label: "类型", value: node.isFolder ? "文件夹" : (node.mimeType ?? "未知"))
            DetailDivider()
            if !node.isFolder {
                DetailInfoRow(label: "大小", value: node.formattedSize)
                DetailDivider()
            }
            DetailInfoRow(label: "创建时间", value: node.formattedDate)
            DetailDivider()
            DetailInfoRow(label: "修改时间", value: node.formattedDate)
            if let md5 = node.md5 {
                DetailDivider()
                DetailInfoRow(label: "MD5", value: md5)
            }
            if let sha256 = node.sha256 {
                DetailDivider()
                DetailInfoRow(label: "SHA-256", value: String(sha256.prefix(32)) + "...")
            }
            DetailDivider()
            DetailInfoRow(label: "ID", value: node.id)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
    }

    // MARK: - 分享标签页

    private var shareTab: some View {
        VStack(alignment: .leading, spacing: 16) {
            if viewModel.shareLinks.isEmpty {
                VStack(spacing: 16) {
                    Spacer()
                        .frame(height: 20)
                    ZStack {
                        Circle()
                            .fill(brandBlue.opacity(0.06))
                            .frame(width: 64, height: 64)
                        Image(systemName: "square.and.arrow.up")
                            .font(.system(size: 24, weight: .light))
                            .foregroundColor(brandBlue.opacity(0.4))
                    }

                    Text("暂无分享链接")
                        .font(.system(size: 14, weight: .medium, design: .rounded))
                        .foregroundColor(.secondary)
                    Button("创建分享链接") {
                        showShareSheet = true
                    }
                    .buttonStyle(.plain)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 10)
                    .background(
                        RoundedRectangle(cornerRadius: 10)
                            .fill(brandBlue.opacity(0.1))
                    )
                    .foregroundColor(brandBlue)
                    Spacer()
                        .frame(height: 20)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 20)
            } else {
                ForEach(viewModel.shareLinks) { link in
                    ShareLinkRow(link: link) {
                        viewModel.copyShareLink(link.shareUrl)
                    } onDelete: {
                        Task { await viewModel.deleteShareLink(shareId: link.id) }
                    }
                }
            }
        }
        .padding(20)
    }

    // MARK: - 版本标签页

    private var versionsTab: some View {
        VStack(spacing: 16) {
            Spacer()
                .frame(height: 20)
            ZStack {
                Circle()
                    .fill(brandBlue.opacity(0.06))
                    .frame(width: 64, height: 64)
                Image(systemName: "clock.arrow.circlepath")
                    .font(.system(size: 24, weight: .light))
                    .foregroundColor(brandBlue.opacity(0.4))
            }
            Text("版本历史")
                .font(.system(size: 14, weight: .semibold, design: .rounded))
            Text("当前版本为最新版本")
                .font(.system(size: 12, design: .rounded))
                .foregroundColor(.secondary)
            Spacer()
                .frame(height: 20)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 20)
    }

    // MARK: - 分享对话框

    private var shareSheetView: some View {
        VStack(spacing: 24) {
            Text("创建分享链接")
                .font(.system(size: 16, weight: .bold, design: .rounded))

            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Text("过期天数")
                        .font(.system(size: 13, design: .rounded))
                        .foregroundColor(.secondary)
                        .frame(width: 80, alignment: .leading)
                    Picker("", selection: $shareExpireDays) {
                        Text("1天").tag(1)
                        Text("7天").tag(7)
                        Text("30天").tag(30)
                        Text("永久").tag(0)
                    }
                    .frame(width: 120)
                    Spacer()
                }

                HStack {
                    Text("访问密码")
                        .font(.system(size: 13, design: .rounded))
                        .foregroundColor(.secondary)
                        .frame(width: 80, alignment: .leading)
                    SecureField("可选（留空则无密码）", text: $sharePassword)
                        .textFieldStyle(.plain)
                        .font(.system(size: 13, design: .rounded))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 9)
                        .background(
                            RoundedRectangle(cornerRadius: 8)
                                .fill(.quaternary.opacity(0.4))
                        )
                }
            }

            HStack(spacing: 12) {
                Button("取消") {
                    showShareSheet = false
                }
                .buttonStyle(.plain)
                .foregroundColor(.secondary)

                Button("创建") {
                    Task {
                        await viewModel.createShareLink(
                            expireDays: shareExpireDays,
                            password: sharePassword.isEmpty ? nil : sharePassword
                        )
                        showShareSheet = false
                    }
                }
                .buttonStyle(.borderedProminent)
                .tint(brandBlue)
                .keyboardShortcut(.return)
            }
        }
        .padding(24)
        .frame(width: 420, height: 280)
        .background(.ultraThinMaterial)
    }
}

// MARK: - 信息行

struct DetailInfoRow: View {
    let label: String
    let value: String

    private let brandBlue = AppColors.primary

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Text(label)
                .font(.system(size: 13, design: .rounded))
                .foregroundColor(.secondary)
                .frame(width: 100, alignment: .trailing)

            Text(value)
                .font(.system(size: 13, design: .rounded))
                .textSelection(.enabled)

            Spacer()
        }
        .padding(.vertical, 10)
    }
}

struct DetailDivider: View {
    var body: some View {
        Divider()
            .opacity(0.3)
    }
}

// MARK: - 分享链接行

struct ShareLinkRow: View {
    let link: ShareLink
    let onCopy: () -> Void
    let onDelete: () -> Void

    private let brandBlue = AppColors.primary

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(link.filename)
                    .font(.system(size: 13, weight: .medium, design: .rounded))

                HStack(spacing: 8) {
                    Text(link.shareUrl)
                        .font(.system(size: 11, design: .rounded))
                        .foregroundColor(.secondary)
                        .lineLimit(1)

                    if link.password != nil {
                        Image(systemName: "lock.fill")
                            .font(.system(size: 9))
                            .foregroundColor(.orange)
                    }

                    Text("下载: \(link.downloadCount)")
                        .font(.system(size: 10, design: .rounded))
                        .foregroundStyle(.tertiary)
                }
            }

            Spacer()

            Button(action: onCopy) {
                Image(systemName: "doc.on.doc")
                    .font(.system(size: 13))
                    .foregroundColor(.secondary.opacity(0.6))
            }
            .buttonStyle(.plain)

            Button(action: onDelete) {
                Image(systemName: "trash")
                    .font(.system(size: 13))
                    .foregroundColor(.red.opacity(0.6))
            }
            .buttonStyle(.plain)
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(.quaternary.opacity(0.3))
        )
    }
}

#Preview {
    FileDetailView(nodeId: "test", nodeName: "测试文件")
            .environmentObject(FileDetailViewModel())
}
