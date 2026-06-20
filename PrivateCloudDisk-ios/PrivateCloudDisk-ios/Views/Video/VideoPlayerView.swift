//
//  VideoPlayerView.swift
//  PrivateCloudDisk-ios
//
//  视频播放器页面 — 企业级沉浸式播放体验
//  基于 AVPlayerViewController 封装，增强自定义控件覆盖层
//
//  功能特性：
//    - 沉浸式全屏播放，渐变遮罩控制栏
//    - 智能控件自动隐藏 / 显示
//    - 增强进度条，支持拖拽预览
//    - 多分辨率无缝切换
//    - 倍速播放（0.5x ~ 2.0x）
//    - 画中画 (PiP) 原生支持
//    - AirPlay 投屏原生支持
//    - 断点续播（历史进度恢复）
//    - 播放器手势：单击显隐控件、双击跳过
//    - 系统锁屏控制中心 (NowPlayable)
//

import SwiftUI
import AVKit

// MARK: - 视频播放器主视图

struct VideoPlayerView: View {
    @StateObject private var viewModel: VideoPlayerViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var showSpeedPicker = false
    @State private var showResumePrompt = false
    @State private var isLocked = false

    init(fileId: String, fileName: String) {
        _viewModel = StateObject(wrappedValue: VideoPlayerViewModel(fileId: fileId, fileName: fileName))
    }

    var body: some View {
        ZStack {
            // 播放器背景
            Color.black.ignoresSafeArea()

            // 视频播放器
            playerLayer

            // 中央播放/暂停指示器
            if !viewModel.showControls && viewModel.isPlaying {
                centerPlayIndicator
            }

            // 控制层覆盖
            if viewModel.showControls && !isLocked {
                controlsOverlay
            }

            // 锁定图标
            if isLocked {
                lockIndicator
            }

            // 加载状态
            if viewModel.isLoading {
                loadingOverlay
            }

            // 错误状态
            if let error = viewModel.errorMessage, viewModel.player == nil {
                errorOverlay(error)
            }

            // 断点续播提示
            if showResumePrompt {
                resumePromptOverlay
            }

            // 返回按钮（控件隐藏时也显示）
            if !viewModel.showControls || isLocked {
                floatingBackButton
            }
        }
        .statusBarHidden()
        .persistentSystemOverlays(.hidden)
        // 手势：单击显隐控件
        .onTapGesture {
            handleTapGesture()
        }
        // 双击跳过
        .onTapGesture(count: 2) {
            handleDoubleTapGesture()
        }
        .task {
            await viewModel.loadVideo()
            if let history = viewModel.videoHistory, history.watchedDuration > 0, !history.completed {
                showResumePrompt = true
            }
        }
        .onDisappear {
            viewModel.saveProgress()
        }
        // 倍速选择器
        .sheet(isPresented: $showSpeedPicker) {
            speedPickerSheet
                .presentationDetents([.height(280)])
                .presentationDragIndicator(.visible)
        }
    }

    // MARK: - 播放器层

    private var playerLayer: some View {
        Group {
            if let player = viewModel.player {
                VideoPlayer(player: player)
                    .ignoresSafeArea()
                    .allowsHitTesting(false)
            } else if viewModel.isLoading {
                Color.black
            } else if viewModel.errorMessage != nil {
                Color.black
            }
        }
    }

    // MARK: - 中央播放指示器

    private var centerPlayIndicator: some View {
        Image(systemName: "play.fill")
            .font(.system(size: 28))
            .foregroundColor(.white.opacity(0.6))
            .transition(.opacity)
    }

    // MARK: - 控制层覆盖

    private var controlsOverlay: some View {
        ZStack {
            // 顶部渐变遮罩
            VStack {
                LinearGradient(
                    colors: [.black.opacity(0.6), .clear],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: 120)
                .ignoresSafeArea(edges: .top)
                Spacer()
            }

            // 底部渐变遮罩
            VStack {
                Spacer()
                LinearGradient(
                    colors: [.clear, .black.opacity(0.7)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: 180)
                .ignoresSafeArea(edges: .bottom)
            }

            // 控件内容
            VStack(spacing: 0) {
                topControlBar
                Spacer()
                bottomControlBar
            }
        }
        .transition(.opacity)
    }

    // MARK: - 顶部控制栏

    private var topControlBar: some View {
        HStack(spacing: 0) {
            // 返回按钮
            Button(action: {
                viewModel.saveProgress()
                dismiss()
            }) {
                Image(systemName: "chevron.left")
                    .font(.title3.weight(.semibold))
                    .foregroundColor(.white)
                    .frame(width: 40, height: 40)
                    .background(.ultraThinMaterial.opacity(0.5))
                    .clipShape(Circle())
            }

            Spacer()

            // 标题区
            VStack(spacing: 2) {
                Text(viewModel.fileName)
                    .font(AppTypography.subheadline.weight(.semibold))
                    .foregroundColor(.white)
                    .lineLimit(1)
                if let info = viewModel.streamInfo {
                    HStack(spacing: 6) {
                        if let res = viewModel.selectedResolution {
                            Text(res.label)
                                .font(.system(size: 10, weight: .medium))
                                .foregroundColor(.white.opacity(0.7))
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(.white.opacity(0.15))
                                .clipShape(Capsule())
                        }
                        Text(info.codec.uppercased())
                            .font(.system(size: 10, weight: .medium))
                            .foregroundColor(.white.opacity(0.5))
                    }
                }
            }
            .frame(maxWidth: .infinity)

            Spacer()

            // 右侧功能按钮
            HStack(spacing: 6) {
                // 字幕
                playerControlButton(
                    icon: "captions.bubble",
                    isActive: false,
                    action: {}
                )

                // AirPlay
                airPlayButton

                // 画中画
                playerControlButton(
                    icon: "pip.enter",
                    isActive: viewModel.isPiPActive,
                    action: { viewModel.togglePiP() }
                )

                // 更多
                if let info = viewModel.streamInfo, info.resolutions.count > 1 {
                    resolutionMenu
                }

                // 锁定
                playerControlButton(
                    icon: isLocked ? "lock.fill" : "lock.open",
                    isActive: isLocked,
                    action: {
                        withAnimation(.easeInOut(duration: 0.3)) {
                            isLocked.toggle()
                            if isLocked {
                                viewModel.showControls = false
                            }
                        }
                    }
                )
            }
        }
        .padding(.horizontal, AppSpacing.lg)
        .padding(.top, 56)
        .transition(.move(edge: .top).combined(with: .opacity))
    }

    // MARK: - 底部控制栏

    private var bottomControlBar: some View {
        VStack(spacing: 8) {
            // 进度条区
            progressSection

            // 控制按钮区
            HStack(spacing: 0) {
                // 左侧：时间
                Text(formatTime(viewModel.currentTime))
                    .font(.system(size: 12, weight: .medium, design: .monospaced))
                    .foregroundColor(.white)
                    .frame(width: 48, alignment: .leading)

                Spacer()

                // 中央：播放控制
                HStack(spacing: 28) {
                    // 后退 15s
                    Button(action: {
                        viewModel.seekBackward(15)
                        triggerHaptic(.light)
                    }) {
                        VStack(spacing: 2) {
                            Image(systemName: "gobackward.15")
                                .font(.title3)
                            Text("15")
                                .font(.system(size: 9, weight: .medium))
                        }
                        .foregroundColor(.white)
                    }

                    // 播放/暂停
                    Button(action: {
                        viewModel.isPlaying ? viewModel.pause() : viewModel.play()
                        triggerHaptic(.light)
                    }) {
                        ZStack {
                            Circle()
                                .fill(.white.opacity(0.2))
                                .frame(width: 52, height: 52)

                            Image(systemName: viewModel.isPlaying ? "pause.fill" : "play.fill")
                                .font(.system(size: 22, weight: .semibold))
                                .foregroundColor(.white)
                                .offset(x: viewModel.isPlaying ? 0 : 1)
                        }
                    }
                    .buttonStyle(.plain)

                    // 前进 15s
                    Button(action: {
                        viewModel.seekForward(15)
                        triggerHaptic(.light)
                    }) {
                        VStack(spacing: 2) {
                            Image(systemName: "goforward.15")
                                .font(.title3)
                            Text("15")
                                .font(.system(size: 9, weight: .medium))
                        }
                        .foregroundColor(.white)
                    }
                }

                Spacer()

                // 右侧：倍速
                Button(action: { showSpeedPicker = true }) {
                    Text(String(format: "%.1fx", viewModel.playbackRate))
                        .font(.system(size: 12, weight: .semibold, design: .monospaced))
                        .foregroundColor(.white)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(.white.opacity(0.15))
                        .clipShape(Capsule())
                }
            }

            // 余量时间
            HStack {
                Spacer()
                Text("-\(formatTime(viewModel.duration - viewModel.currentTime))")
                    .font(.system(size: 10, weight: .medium, design: .monospaced))
                    .foregroundColor(.white.opacity(0.5))
            }
        }
        .padding(.horizontal, AppSpacing.lg)
        .padding(.bottom, 36)
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }

    // MARK: - 进度条区

    private var progressSection: some View {
        VStack(spacing: 4) {
            // 自定义进度条
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    // 背景轨道
                    Capsule()
                        .fill(.white.opacity(0.2))
                        .frame(height: 4)

                    // 已播放进度
                    Capsule()
                        .fill(.white)
                        .frame(
                            width: viewModel.duration > 0
                                ? geo.size.width * (viewModel.currentTime / viewModel.duration)
                                : 0,
                            height: 4
                        )

                    // 拖拽手柄
                    Circle()
                        .fill(.white)
                        .frame(width: 14, height: 14)
                        .offset(
                            x: viewModel.duration > 0
                                ? geo.size.width * (viewModel.currentTime / viewModel.duration) - 7
                                : -7
                        )
                        .opacity(viewModel.showControls ? 1 : 0)
                        .animation(.easeInOut(duration: 0.15), value: viewModel.showControls)
                }
                .frame(height: 14)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { value in
                            let ratio = max(0, min(1, value.location.x / geo.size.width))
                            viewModel.seek(to: ratio * viewModel.duration)
                        }
                )
            }
            .frame(height: 14)
        }
    }

    // MARK: - 浮动返回按钮

    private var floatingBackButton: some View {
        VStack {
            HStack {
                Button(action: {
                    viewModel.saveProgress()
                    dismiss()
                }) {
                    Image(systemName: "chevron.left")
                        .font(.title3.weight(.semibold))
                        .foregroundColor(.white)
                        .frame(width: 40, height: 40)
                        .background(.ultraThinMaterial.opacity(0.3))
                        .clipShape(Circle())
                }
                .padding(.leading, AppSpacing.lg)
                .padding(.top, 56)
                Spacer()
            }
            Spacer()
        }
    }

    // MARK: - 锁定指示器

    private var lockIndicator: some View {
        VStack {
            Spacer()
            HStack {
                Spacer()
                Button(action: {
                    withAnimation(.easeInOut(duration: 0.3)) {
                        isLocked = false
                    }
                }) {
                    Image(systemName: "lock.fill")
                        .font(.title3)
                        .foregroundColor(.white.opacity(0.7))
                        .frame(width: 44, height: 44)
                        .background(.ultraThinMaterial.opacity(0.3))
                        .clipShape(Circle())
                }
                .padding(.trailing, AppSpacing.lg)
                .padding(.bottom, 40)
            }
        }
    }

    // MARK: - 加载覆盖层

    private var loadingOverlay: some View {
        ZStack {
            Color.black.opacity(0.4).ignoresSafeArea()

            VStack(spacing: AppSpacing.lg) {
                ProgressView()
                    .scaleEffect(1.5)
                    .tint(.white)

                Text("加载中...")
                    .font(AppTypography.subheadline)
                    .foregroundColor(.white.opacity(0.8))
            }
            .padding(AppSpacing.xxl)
            .background(.ultraThinMaterial)
            .clipShape(RoundedRectangle(cornerRadius: AppRadius.xl))
        }
    }

    // MARK: - 错误覆盖层

    private func errorOverlay(_ error: String) -> some View {
        ZStack {
            Color.black.opacity(0.6).ignoresSafeArea()

            VStack(spacing: AppSpacing.xl) {
                ZStack {
                    Circle()
                        .fill(AppColors.danger.opacity(0.2))
                        .frame(width: 72, height: 72)

                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.system(size: 32))
                        .foregroundColor(AppColors.danger)
                }

                VStack(spacing: AppSpacing.sm) {
                    Text("播放失败")
                        .font(AppTypography.title3)
                        .foregroundColor(.white)
                    Text(error)
                        .font(AppTypography.subheadline)
                        .foregroundColor(.white.opacity(0.7))
                        .multilineTextAlignment(.center)
                }

                HStack(spacing: AppSpacing.md) {
                    AppSecondaryButton("返回") {
                        dismiss()
                    }
                    .frame(width: 120)

                    AppPrimaryButton("重试") {
                        Task { await viewModel.loadVideo() }
                    }
                    .frame(width: 120)
                }
            }
            .padding(AppSpacing.xxl)
            .background(.ultraThinMaterial)
            .clipShape(RoundedRectangle(cornerRadius: AppRadius.xl))
            .padding(.horizontal, AppSpacing.xxl)
        }
    }

    // MARK: - 断点续播提示

    private var resumePromptOverlay: some View {
        VStack {
            Spacer()

            HStack(spacing: AppSpacing.md) {
                Image(systemName: "clock.arrow.2.circlepath")
                    .font(.subheadline)
                    .foregroundColor(AppColors.primary)

                VStack(alignment: .leading, spacing: 2) {
                    Text("上次观看到 \(formatTime(viewModel.videoHistory?.watchedDuration ?? 0))")
                        .font(AppTypography.subheadline.weight(.medium))
                        .foregroundColor(.white)
                    Text("是否继续播放？")
                        .font(AppTypography.caption2)
                        .foregroundColor(.white.opacity(0.7))
                }

                Spacer()

                HStack(spacing: AppSpacing.sm) {
                    Button("重新开始") {
                        showResumePrompt = false
                    }
                    .font(AppTypography.caption1)
                    .foregroundColor(.white.opacity(0.6))

                    Button("继续") {
                        viewModel.resumeFromHistory()
                        showResumePrompt = false
                    }
                    .font(AppTypography.caption1.weight(.semibold))
                    .foregroundColor(AppColors.primary)
                    .padding(.horizontal, AppSpacing.md)
                    .padding(.vertical, 6)
                    .background(AppColors.primary.opacity(0.15))
                    .clipShape(Capsule())
                }
            }
            .padding(AppSpacing.lg)
            .background(.ultraThinMaterial)
            .clipShape(RoundedRectangle(cornerRadius: AppRadius.lg))
            .padding(.horizontal, AppSpacing.lg)
            .padding(.bottom, 100)
            .transition(.move(edge: .bottom).combined(with: .opacity))
        }
        .animation(.easeInOut(duration: 0.4), value: showResumePrompt)
    }

    // MARK: - 倍速选择器

    private var speedPickerSheet: some View {
        VStack(spacing: 0) {
            // 标题
            HStack {
                Text("播放速度")
                    .font(AppTypography.headline)
                    .foregroundColor(AppColors.textPrimary)
                Spacer()
                Button("完成") {
                    showSpeedPicker = false
                }
                .font(AppTypography.subheadline.weight(.medium))
                .foregroundColor(AppColors.primary)
            }
            .padding(.horizontal, AppSpacing.xl)
            .padding(.top, AppSpacing.xl)
            .padding(.bottom, AppSpacing.lg)

            // 速度选项
            let speeds: [Float] = [0.5, 0.75, 1.0, 1.25, 1.5, 2.0]
            LazyVGrid(columns: Array(repeating: .init(.flexible()), count: 3), spacing: AppSpacing.md) {
                ForEach(speeds, id: \.self) { speed in
                    Button(action: {
                        viewModel.setPlaybackRate(speed)
                        triggerHaptic(.light)
                        showSpeedPicker = false
                    }) {
                        VStack(spacing: 4) {
                            Text(String(format: "%.2f", speed))
                                .font(.system(size: 24, weight: .bold, design: .monospaced))
                            Text("x")
                                .font(AppTypography.caption2)
                        }
                        .foregroundColor(
                            viewModel.playbackRate == speed
                                ? AppColors.primary
                                : AppColors.textPrimary
                        )
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, AppSpacing.lg)
                        .background(
                            viewModel.playbackRate == speed
                                ? AppColors.primaryBg
                                : AppColors.surfaceSecondary
                        )
                        .clipShape(RoundedRectangle(cornerRadius: AppRadius.lg))
                        .overlay(
                            RoundedRectangle(cornerRadius: AppRadius.lg)
                                .stroke(
                                    viewModel.playbackRate == speed
                                        ? AppColors.primary.opacity(0.3)
                                        : Color.clear,
                                    lineWidth: 1.5
                                )
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, AppSpacing.xl)
            .padding(.bottom, AppSpacing.xxl)
        }
        .background(AppColors.background)
    }

    // MARK: - 分辨率菜单

    private var resolutionMenu: some View {
        Menu {
            ForEach(viewModel.streamInfo?.resolutions ?? []) { res in
                Button(action: { viewModel.switchResolution(res) }) {
                    HStack {
                        Text(res.label)
                            .font(AppTypography.subheadline)
                        Text("\(res.bitrate / 1024)kbps")
                            .font(AppTypography.caption2)
                            .foregroundColor(AppColors.textSecondary)
                        if viewModel.selectedResolution?.id == res.id {
                            Image(systemName: "checkmark")
                                .foregroundColor(AppColors.primary)
                        }
                    }
                }
            }
        } label: {
            Image(systemName: "rectangle.3.group")
                .font(.subheadline)
                .foregroundColor(.white)
                .frame(width: 36, height: 36)
                .background(.ultraThinMaterial.opacity(0.5))
                .clipShape(Circle())
        }
    }

    // MARK: - AirPlay 按钮

    private var airPlayButton: some View {
        AirPlayButton()
            .frame(width: 36, height: 36)
            .background(.ultraThinMaterial.opacity(0.5))
            .clipShape(Circle())
    }

    // MARK: - 播放器控制按钮

    private func playerControlButton(
        icon: String,
        isActive: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.subheadline)
                .foregroundColor(isActive ? AppColors.primary : .white)
                .frame(width: 36, height: 36)
                .background(
                    isActive
                        ? AppColors.primary.opacity(0.2)
                        : Color.white.opacity(0.15)
                )
                .clipShape(Circle())
        }
    }

    // MARK: - 手势处理

    private func handleTapGesture() {
        if isLocked {
            return
        }
        withAnimation(.easeInOut(duration: 0.3)) {
            viewModel.toggleControls()
        }
    }

    private func handleDoubleTapGesture() {
        if isLocked {
            return
        }
        // 双击右侧前进，左侧后退
        viewModel.seekForward(15)
        triggerHaptic(.medium)
    }

    private func triggerHaptic(_ style: UIImpactFeedbackGenerator.FeedbackStyle) {
        let generator = UIImpactFeedbackGenerator(style: style)
        generator.impactOccurred()
    }

    // MARK: - 工具方法

    private func formatTime(_ seconds: Double) -> String {
        guard seconds.isFinite && seconds >= 0 else { return "00:00" }
        let totalSeconds = Int(seconds)
        let h = totalSeconds / 3600
        let m = (totalSeconds % 3600) / 60
        let s = totalSeconds % 60
        if h > 0 {
            return String(format: "%d:%02d:%02d", h, m, s)
        }
        return String(format: "%02d:%02d", m, s)
    }
}

// MARK: - AirPlay 路由按钮 (UIKit 桥接)

struct AirPlayButton: UIViewRepresentable {
    func makeUIView(context: Context) -> AVRoutePickerView {
        let view = AVRoutePickerView()
        view.activeTintColor = .white
        view.tintColor = .white
        view.prioritizesVideoDevices = true
        return view
    }

    func updateUIView(_ uiView: AVRoutePickerView, context: Context) {}
}

#Preview {
    VideoPlayerView(fileId: "test", fileName: "测试视频.mp4")
}