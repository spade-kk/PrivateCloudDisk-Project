import SwiftUI

// MARK: - 上传进度视图（企业级设计）

/// 上传进度浮窗
///
/// 参考百度网盘 macOS 客户端设计：
/// - 毛玻璃卡片
/// - 渐变进度条
/// - 状态图标动画
/// - 优雅的悬停交互
struct UploadProgressView: View {
    @EnvironmentObject var uploadVM: UploadViewModel

    private let brandBlue = Color(red: 0.24, green: 0.47, blue: 0.96)

    var body: some View {
        VStack(spacing: 0) {
            // 标题栏
            HStack {
                HStack(spacing: 8) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 6)
                            .fill(brandBlue.opacity(0.12))
                            .frame(width: 28, height: 28)

                        Image(systemName: "arrow.up.circle.fill")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(brandBlue)
                    }
                    Text("上传中")
                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                }

                Spacer()

                Text("\(Int(uploadVM.overallProgress * 100))%")
                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                    .foregroundColor(brandBlue)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)

            Divider()
                .opacity(0.3)

            // 总体进度条
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 2)
                        .fill(.quaternary)
                        .frame(height: 3)

                    RoundedRectangle(cornerRadius: 2)
                        .fill(
                            LinearGradient(
                                colors: [brandBlue, brandBlue.opacity(0.7)],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .frame(width: geo.size.width * uploadVM.overallProgress, height: 3)
                }
            }
            .frame(height: 3)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)

            if uploadVM.activeTasks.isEmpty {
                VStack(spacing: 12) {
                    Spacer()
                        .frame(height: 20)

                    ZStack {
                        Circle()
                            .fill(.quaternary.opacity(0.4))
                            .frame(width: 48, height: 48)
                        Image(systemName: "tray")
                            .font(.system(size: 20, weight: .light))
                            .foregroundColor(.secondary.opacity(0.5))
                    }

                    Text("暂无上传任务")
                        .font(.system(size: 13, design: .rounded))
                        .foregroundColor(.secondary)

                    Spacer()
                        .frame(height: 20)
                }
            } else {
                List {
                    ForEach(uploadVM.activeTasks) { task in
                        UploadTaskRow(task: task)
                            .listRowSeparator(.hidden)
                            .listRowInsets(EdgeInsets(top: 2, leading: 10, bottom: 2, trailing: 10))
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
                .frame(minHeight: 100)
            }
        }
        .frame(width: 380)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(.regularMaterial)
                .shadow(color: .black.opacity(0.12), radius: 16, x: 0, y: 6)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(.quaternary.opacity(0.3), lineWidth: 1)
        )
    }
}

// MARK: - 上传任务行

struct UploadTaskRow: View {
    let task: UploadTask

    private let brandBlue = Color(red: 0.24, green: 0.47, blue: 0.96)

    var body: some View {
        HStack(spacing: 12) {
            // 状态图标
            ZStack {
                RoundedRectangle(cornerRadius: 8)
                    .fill(task.status.color.opacity(0.12))
                    .frame(width: 36, height: 36)

                Image(systemName: task.status.sfSymbolName)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(task.status.color)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(task.filename)
                    .font(.system(size: 12, weight: .medium, design: .rounded))
                    .lineLimit(1)

                HStack(spacing: 8) {
                    if task.status == .uploading {
                        ProgressView(
                            value: task.totalBytes > 0
                                ? Double(task.uploadedBytes) / Double(task.totalBytes)
                                : 0
                        )
                        .frame(width: 100)
                        .tint(brandBlue)
                    }

                    Text(formattedProgress)
                        .font(.system(size: 10, design: .rounded))
                        .foregroundColor(.secondary)
                }
            }

            Spacer()

            // 操作按钮
            if task.status == .uploading {
                Button(action: {}) {
                    Image(systemName: "pause.circle")
                        .font(.system(size: 14))
                        .foregroundColor(.secondary.opacity(0.6))
                }
                .buttonStyle(.plain)
            }

            Button(action: {}) {
                Image(systemName: "xmark.circle")
                    .font(.system(size: 14))
                    .foregroundColor(.secondary.opacity(0.5))
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .background(
            RoundedRectangle(cornerRadius: 8)
                .fill(.clear)
        )
    }

    private var formattedProgress: String {
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        let uploaded = formatter.string(fromByteCount: task.uploadedBytes)
        let total = formatter.string(fromByteCount: task.totalBytes)
        return "\(uploaded) / \(total)"
    }
}

// MARK: - 上传状态扩展

extension UploadStatus {
    var sfSymbolName: String {
        switch self {
        case .pending: return "clock.fill"
        case .uploading: return "arrow.up.circle.fill"
        case .completed: return "checkmark.circle.fill"
        case .failed: return "exclamationmark.circle.fill"
        case .cancelled: return "xmark.circle.fill"
        case .paused: return "pause.circle.fill"
        }
    }

    var color: Color {
        let brandBlue = Color(red: 0.24, green: 0.47, blue: 0.96)
        switch self {
        case .pending: return .orange
        case .uploading: return brandBlue
        case .completed: return .green
        case .failed: return .red
        case .cancelled: return .gray
        case .paused: return .orange
        }
    }
}