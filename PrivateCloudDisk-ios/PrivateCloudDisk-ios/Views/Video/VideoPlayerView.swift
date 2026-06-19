//
//  VideoPlayerView.swift
//  PrivateCloudDisk-ios
//
//  视频播放器页面 — 基于 AVPlayerViewController 封装
//  利用 iOS 原生视频播放管道：
//    - AVPlayer + AVPlayerViewController 原生播放
//    - 画中画 (PiP) 原生支持
//    - AirPlay 投屏原生支持
//    - 系统级手势控制（音量、亮度、进度）
//    - 倍速播放
//    - 系统锁屏控制中心 (NowPlayable)
//    - 视频缩略图磁盘缓存
//

import SwiftUI
import AVKit

struct VideoPlayerView: View {
    @StateObject private var viewModel: VideoPlayerViewModel
    @Environment(\.dismiss) private var dismiss

    init(fileId: String, fileName: String) {
        _viewModel = StateObject(wrappedValue: VideoPlayerViewModel(fileId: fileId, fileName: fileName))
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            VStack {
                // 顶部导航栏
                if viewModel.showControls {
                    HStack {
                        Button(action: {
                            viewModel.saveProgress()
                            dismiss()
                        }) {
                            Image(systemName: "xmark")
                                .font(.title3)
                                .foregroundColor(.white)
                                .padding(12)
                                .background(.ultraThinMaterial)
                                .clipShape(Circle())
                        }

                        Spacer()

                        VStack {
                            Text(viewModel.fileName)
                                .font(.subheadline.bold())
                                .foregroundColor(.white)
                                .lineLimit(1)
                            if let resolution = viewModel.selectedResolution {
                                Text(resolution.label)
                                    .font(.caption2)
                                    .foregroundColor(.white.opacity(0.7))
                            }
                        }

                        Spacer()

                        HStack(spacing: 12) {
                            if let info = viewModel.streamInfo, info.resolutions.count > 1 {
                                resolutionMenu
                            }
                            Button(action: { viewModel.togglePiP() }) {
                                Image(systemName: "pip.enter")
                                    .foregroundColor(.white)
                                    .padding(8)
                                    .background(.ultraThinMaterial)
                                    .clipShape(Circle())
                            }
                        }
                    }
                    .padding(.horizontal)
                    .padding(.top, 8)
                    .transition(.move(edge: .top).combined(with: .opacity))
                }

                Spacer()

                // 播放器
                if let player = viewModel.player {
                    VideoPlayer(player: player)
                        .frame(height: 250)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .padding(.horizontal)
                } else if viewModel.isLoading {
                    ProgressView()
                        .tint(.white)
                } else if let error = viewModel.errorMessage {
                    VStack(spacing: 12) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .font(.largeTitle)
                            .foregroundStyle(.orange)
                        Text(error)
                            .foregroundColor(.white)
                    }
                }

                Spacer()

                // 底部控制栏
                if viewModel.showControls {
                    VStack(spacing: 12) {
                        // 进度条
                        VStack(spacing: 4) {
                            Slider(value: Binding(
                                get: { viewModel.currentTime },
                                set: { viewModel.seek(to: $0) }
                            ), in: 0...max(viewModel.duration, 1))
                            .tint(.white)

                            HStack {
                                Text(formatTime(viewModel.currentTime))
                                Spacer()
                                Text(formatTime(viewModel.duration))
                            }
                            .font(.caption2.monospacedDigit())
                            .foregroundColor(.white.opacity(0.7))
                        }

                        // 播放控制
                        HStack(spacing: 32) {
                            Button(action: { viewModel.seekBackward(15) }) {
                                Image(systemName: "gobackward.15")
                                    .font(.title2)
                                    .foregroundColor(.white)
                            }

                            Button(action: {
                                viewModel.isPlaying ? viewModel.pause() : viewModel.play()
                            }) {
                                Image(systemName: viewModel.isPlaying ? "pause.fill" : "play.fill")
                                    .font(.system(size: 40))
                                    .foregroundColor(.white)
                            }

                            Button(action: { viewModel.seekForward(15) }) {
                                Image(systemName: "goforward.15")
                                    .font(.title2)
                                    .foregroundColor(.white)
                            }
                        }

                        // 倍速
                        HStack(spacing: 16) {
                            ForEach([0.5, 0.75, 1.0, 1.25, 1.5, 2.0], id: \.self) { rate in
                                Button(action: { viewModel.setPlaybackRate(Float(rate)) }) {
                                    Text("\(rate, specifier: "%.2f")x")
                                        .font(.caption)
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 4)
                                        .background(
                                            viewModel.playbackRate == Float(rate)
                                                ? Color.white.opacity(0.3)
                                                : Color.clear
                                        )
                                        .foregroundColor(.white)
                                        .clipShape(Capsule())
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 24)
                    .padding(.bottom, 20)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
        }
        .onTapGesture {
            withAnimation(.easeInOut(duration: 0.3)) {
                viewModel.toggleControls()
            }
        }
        .task {
            await viewModel.loadVideo()
        }
        .onDisappear {
            viewModel.saveProgress()
        }
    }

    // MARK: - 分辨率菜单

    private var resolutionMenu: some View {
        Menu {
            ForEach(viewModel.streamInfo?.resolutions ?? []) { res in
                Button(action: { viewModel.switchResolution(res) }) {
                    HStack {
                        Text(res.label)
                        if viewModel.selectedResolution?.id == res.id {
                            Image(systemName: "checkmark")
                        }
                    }
                }
            }
        } label: {
            Image(systemName: "rectangle.3.group")
                .foregroundColor(.white)
                .padding(8)
                .background(.ultraThinMaterial)
                .clipShape(Circle())
        }
    }

    private func formatTime(_ seconds: Double) -> String {
        let m = Int(seconds) / 60
        let s = Int(seconds) % 60
        return String(format: "%02d:%02d", m, s)
    }
}

#Preview {
    VideoPlayerView(fileId: "test", fileName: "test.mp4")
}