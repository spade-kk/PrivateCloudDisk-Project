//
//  FileCacheManager.swift
//  PrivateCloudDisk-ios
//
//  文件缓存管理 — 管理本地文件缓存、离线文件、缩略图
//  利用 iOS 文件系统和 NSCache，实现高效的文件缓存策略
//

import Foundation
import UIKit

@MainActor
class FileCacheManager {
    static let shared = FileCacheManager()

    private let fileManager = FileManager.default
    private let cacheDirectory: URL
    private let thumbnailDirectory: URL
    private let offlineDirectory: URL
    private let maxCacheSize: Int64 = 500 * 1024 * 1024 // 500MB

    private let imageCache = NSCache<NSString, UIImage>()
    private let metadataCache = NSCache<NSString, AnyObject>()

    private init() {
        let caches = fileManager.urls(for: .cachesDirectory, in: .userDomainMask).first!

        cacheDirectory = caches.appendingPathComponent("FileCache", isDirectory: true)
        thumbnailDirectory = caches.appendingPathComponent("Thumbnails", isDirectory: true)
        offlineDirectory = caches.appendingPathComponent("Offline", isDirectory: true)

        try? fileManager.createDirectory(at: cacheDirectory, withIntermediateDirectories: true)
        try? fileManager.createDirectory(at: thumbnailDirectory, withIntermediateDirectories: true)
        try? fileManager.createDirectory(at: offlineDirectory, withIntermediateDirectories: true)

        // 配置缓存限制
        imageCache.countLimit = 200
        imageCache.totalCostLimit = 100 * 1024 * 1024 // 100MB
    }

    // MARK: - 文件缓存

    func cacheFile(fileId: String, data: Data) {
        let url = cacheDirectory.appendingPathComponent(fileId)
        try? data.write(to: url, options: .atomic)
    }

    func getCachedFile(fileId: String) -> Data? {
        let url = cacheDirectory.appendingPathComponent(fileId)
        return try? Data(contentsOf: url)
    }

    func removeCachedFile(fileId: String) {
        let url = cacheDirectory.appendingPathComponent(fileId)
        try? fileManager.removeItem(at: url)
    }

    // MARK: - 缩略图

    func cacheThumbnail(fileId: String, image: UIImage) {
        let key = NSString(string: fileId)
        imageCache.setObject(image, forKey: key)

        if let data = image.jpegData(compressionQuality: 0.8) {
            let url = thumbnailDirectory.appendingPathComponent("\(fileId).jpg")
            try? data.write(to: url, options: .atomic)
        }
    }

    func getThumbnail(fileId: String) -> UIImage? {
        let key = NSString(string: fileId)
        if let cached = imageCache.object(forKey: key) {
            return cached
        }

        let url = thumbnailDirectory.appendingPathComponent("\(fileId).jpg")
        if let data = try? Data(contentsOf: url), let image = UIImage(data: data) {
            imageCache.setObject(image, forKey: key)
            return image
        }
        return nil
    }

    // MARK: - 离线文件

    func saveOfflineFile(fileId: String, fileName: String, data: Data) {
        let url = offlineDirectory.appendingPathComponent("\(fileId)_\(fileName)")
        try? data.write(to: url, options: .atomic)
    }

    func getOfflineFile(fileId: String, fileName: String) -> Data? {
        let url = offlineDirectory.appendingPathComponent("\(fileId)_\(fileName)")
        return try? Data(contentsOf: url)
    }

    func removeOfflineFile(fileId: String, fileName: String) {
        let url = offlineDirectory.appendingPathComponent("\(fileId)_\(fileName)")
        try? fileManager.removeItem(at: url)
    }

    var offlineFiles: [URL] {
        (try? fileManager.contentsOfDirectory(at: offlineDirectory, includingPropertiesForKeys: nil)) ?? []
    }

    // MARK: - 缓存清理

    func clearCache() {
        try? fileManager.removeItem(at: cacheDirectory)
        try? fileManager.createDirectory(at: cacheDirectory, withIntermediateDirectories: true)

        try? fileManager.removeItem(at: thumbnailDirectory)
        try? fileManager.createDirectory(at: thumbnailDirectory, withIntermediateDirectories: true)

        imageCache.removeAllObjects()
    }

    func cacheSize() -> Int64 {
        let cacheSize = directorySize(cacheDirectory)
        let thumbSize = directorySize(thumbnailDirectory)
        return cacheSize + thumbSize
    }

    private func directorySize(_ url: URL) -> Int64 {
        guard let enumerator = fileManager.enumerator(at: url, includingPropertiesForKeys: [.fileSizeKey]) else {
            return 0
        }
        var size: Int64 = 0
        for case let fileURL as URL in enumerator {
            if let attrs = try? fileURL.resourceValues(forKeys: [.fileSizeKey]),
               let fileSize = attrs.fileSize {
                size += Int64(fileSize)
            }
        }
        return size
    }
}