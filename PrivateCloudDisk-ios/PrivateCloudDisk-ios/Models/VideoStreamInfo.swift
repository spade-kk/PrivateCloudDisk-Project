//
//  VideoStreamInfo.swift
//  PrivateCloudDisk-ios
//
//  视频流媒体信息模型
//

import Foundation

struct VideoStreamInfo: Codable {
    let fileId: String
    let fileName: String
    let duration: Double
    let width: Int
    let height: Int
    let bitrate: Int
    let codec: String
    let resolutions: [ResolutionOption]
    let hasHLS: Bool
    let hasDASH: Bool
    let hlsURL: String?
    let dashURL: String?
    let mp4URL: String
    let thumbnailURL: String
    let spriteURL: String?
    let spriteConfig: SpriteConfig?

    var formattedDuration: String {
        let totalSeconds = Int(duration)
        let h = totalSeconds / 3600
        let m = (totalSeconds % 3600) / 60
        let s = totalSeconds % 60
        if h > 0 {
            return String(format: "%d:%02d:%02d", h, m, s)
        }
        return String(format: "%02d:%02d", m, s)
    }
    var resolutionLabel: String {
        "\(width)x\(height)"
    }

    enum CodingKeys: String, CodingKey {
        case fileId = "file_id"
        case fileName = "file_name"
        case duration, width, height, bitrate, codec, resolutions
        case hasHLS = "has_hls"
        case hasDASH = "has_dash"
        case hlsURL = "hls_url"
        case dashURL = "dash_url"
        case mp4URL = "mp4_url"
        case thumbnailURL = "thumbnail_url"
        case spriteURL = "sprite_url"
        case spriteConfig = "sprite_config"
    }
}

struct ResolutionOption: Codable, Identifiable {
    let label: String
    let width: Int
    let height: Int
    let bitrate: Int

    var id: String { label }
}

struct SpriteConfig: Codable {
    let cols: Int
    let rows: Int
    let interval: Int
}

struct VideoProgress: Codable {
    let currentTime: Double
    let duration: Double
    let resolution: String
    let playbackRate: Double

    enum CodingKeys: String, CodingKey {
        case currentTime = "current_time"
        case duration
        case resolution
        case playbackRate = "playback_rate"
    }
}

struct VideoHistory: Codable {
    let watchedDuration: Double
    let totalDuration: Double
    let completed: Bool

    var progress: Double {
        guard totalDuration > 0 else { return 0 }
        return watchedDuration / totalDuration
    }

    enum CodingKeys: String, CodingKey {
        case watchedDuration = "watched_duration"
        case totalDuration = "total_duration"
        case completed
    }
}