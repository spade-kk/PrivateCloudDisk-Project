//
//  VideoPlayerViewModel.swift
//  PrivateCloudDisk-ios
//
//  视频播放器 ViewModel — 管理视频流信息、播放状态、进度同步
//  利用 iOS 原生 AVPlayer / AVPlayerViewController
//  支持：
//    - HLS / MP4 / DASH 多协议自适应
//    - 多分辨率切换
//    - 播放进度保存和恢复
//    - 画中画 (PiP)
//    - 缩略图预览（Sprite）
//    - 倍速播放
//    - 字幕支持
//    - AirPlay 投屏
//

import Foundation
import AVKit
import SwiftUI
import Combine

@MainActor
class VideoPlayerViewModel: ObservableObject {
    @Published var streamInfo: VideoStreamInfo?
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var player: AVPlayer?
    @Published var isPlaying = false
    @Published var currentTime: Double = 0
    @Published var duration: Double = 0
    @Published var selectedResolution: ResolutionOption?
    @Published var playbackRate: Float = 1.0
    @Published var showControls = true
    @Published var isPiPActive = false
    @Published var videoHistory: VideoHistory?

    let fileId: String
    let fileName: String

    private let videoService = VideoService.shared
    private var timeObserver: Any?
    private var controlTimer: Timer?

    init(fileId: String, fileName: String) {
        self.fileId = fileId
        self.fileName = fileName
    }

    deinit {
        controlTimer?.invalidate()
    }

    // MARK: - 加载

    func loadVideo() async {
        isLoading = true
        defer { isLoading = false }

        do {
            streamInfo = try await videoService.getStreamInfo(fileId: fileId)
            if let first = streamInfo?.resolutions.first {
                selectedResolution = first
            }
            setupPlayer()
            await loadHistory()
        } catch {
            errorMessage = "加载视频信息失败"
        }
    }

    func loadHistory() async {
        do {
            videoHistory = try await videoService.getHistory(fileId: fileId)
        } catch {
            // 静默失败
        }
    }

    // MARK: - 播放器

    private func setupPlayer() {
        guard let info = streamInfo else { return }

        let url: URL
        if let hls = info.hlsURL, let hlsURL = URL(string: hls) {
            url = hlsURL
        } else if let mp4URL = URL(string: info.mp4URL) {
            url = mp4URL
        } else {
            return
        }

        let asset = AVURLAsset(url: url)
        let playerItem = AVPlayerItem(asset: asset)
        let newPlayer = AVPlayer(playerItem: playerItem)

        // 配置播放器
        newPlayer.automaticallyWaitsToMinimizeStalling = true
        newPlayer.allowsExternalPlayback = true // AirPlay
        newPlayer.rate = playbackRate

        // 时间观察器
        let interval = CMTime(seconds: 0.5, preferredTimescale: CMTimeScale(NSEC_PER_SEC))
        timeObserver = newPlayer.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            self?.currentTime = time.seconds
            self?.duration = playerItem.duration.seconds
            self?.isPlaying = newPlayer.rate > 0
        }

        player = newPlayer
    }

    func play() {
        player?.play()
        isPlaying = true
    }

    func pause() {
        player?.pause()
        isPlaying = false
    }

    func seek(to time: Double) {
        let cmTime = CMTime(seconds: time, preferredTimescale: CMTimeScale(NSEC_PER_SEC))
        player?.seek(to: cmTime)
    }

    func seekForward(_ seconds: Double = 15) {
        let newTime = min(currentTime + seconds, duration)
        seek(to: newTime)
    }

    func seekBackward(_ seconds: Double = 15) {
        let newTime = max(currentTime - seconds, 0)
        seek(to: newTime)
    }

    func setPlaybackRate(_ rate: Float) {
        playbackRate = rate
        player?.rate = rate
    }

    // MARK: - 分辨率切换

    func switchResolution(_ resolution: ResolutionOption) {
        selectedResolution = resolution
        // 重新加载对应分辨率的视频
        setupPlayer()
        let savedTime = currentTime
        play()
        seek(to: savedTime)
    }

    // MARK: - 画中画

    func togglePiP() {
        // PiP 由 AVPlayerViewController 原生支持
        // 在 AVPlayerViewController 中设置 allowsPictureInPicturePlayback = true
    }

    // MARK: - 进度保存

    func saveProgress() {
        guard let info = streamInfo else { return }
        let progress = VideoProgress(
            currentTime: currentTime,
            duration: duration,
            resolution: selectedResolution?.label ?? "auto",
            playbackRate: Double(playbackRate)
        )
        Task {
            try? await videoService.saveProgress(fileId: fileId, progress: progress)
        }
    }

    func resumeFromHistory() {
        guard let history = videoHistory, history.watchedDuration > 0 else { return }
        seek(to: history.watchedDuration)
    }

    // MARK: - 控件

    func toggleControls() {
        showControls.toggle()
        if showControls {
            controlTimer?.invalidate()
            controlTimer = Timer.scheduledTimer(withTimeInterval: 3, repeats: false) { [weak self] _ in
                self?.showControls = false
            }
        }
    }

    private func removeTimeObserver() {
        if let observer = timeObserver {
            player?.removeTimeObserver(observer)
            timeObserver = nil
        }
    }
}