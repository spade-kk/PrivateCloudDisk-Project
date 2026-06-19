//
//  StarredView.swift
//  PrivateCloudDisk-ios
//
//  收藏页面 — 查看、取消收藏
//

import SwiftUI

struct StarredView: View {
    @StateObject private var viewModel = StarredViewModel()

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.isLoading && viewModel.stars.isEmpty {
                    ProgressView("加载中...")
                } else if viewModel.stars.isEmpty {
                    ContentUnavailableView(
                        "暂无收藏",
                        systemImage: "star",
                        description: Text("收藏文件和文件夹，方便快速访问")
                    )
                } else {
                    List {
                        ForEach(viewModel.stars) { star in
                            HStack(spacing: 12) {
                                Image(systemName: star.isFolder ? "folder.fill" : "doc.fill")
                                    .font(.title3)
                                    .foregroundStyle(star.isFolder ? .blue : .gray)
                                    .frame(width: 36, height: 36)
                                    .background((star.isFolder ? Color.blue : Color.gray).opacity(0.1))
                                    .clipShape(RoundedRectangle(cornerRadius: 8))

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(star.targetName)
                                        .font(.subheadline)
                                        .fontWeight(.medium)
                                    Text(star.isFolder ? "文件夹" : "文件 · \(star.formattedSize)")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }

                                Spacer()

                                Button(action: {
                                    Task { await viewModel.removeStar(starId: star.starId) }
                                }) {
                                    Image(systemName: "star.fill")
                                        .foregroundStyle(.orange)
                                }
                            }
                            .padding(.vertical, 4)
                        }
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("我的收藏")
            .refreshable {
                await viewModel.loadStars()
            }
            .task {
                await viewModel.loadStars()
            }
        }
    }
}

#Preview {
    StarredView()
}