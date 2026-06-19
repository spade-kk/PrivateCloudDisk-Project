//
//  AppGroupManager.swift
//  PrivateCloudDisk-ios
//
//  App Group 共享数据管理 — 主 App 与 Widget、Share Extension 之间的数据同步
//  利用 iOS App Group 共享容器：
//    - Widget 读取存储用量和最近文件
//    - Share Extension 读取认证信息和最近文件夹
//    - 所有扩展共享 UserDefaults suite
//

import Foundation
import WidgetKit

@MainActor
class AppGroupManager {
    static let shared = AppGroupManager()

    private let suiteName = "group.com.privateclouddisk.ios"
    private var sharedDefaults: UserDefaults? {
        UserDefaults(suiteName: suiteName)
    }

    private init() {}

    // MARK: - 存储数据

    /// 更新存储用量
    func updateStorageUsage(used: Int64, total: Int64) {
        sharedDefaults?.set(Int(used), forKey: "storageUsed")
        sharedDefaults?.set(Int(total), forKey: "storageTotal")
    }

    /// 更新最近文件
    func updateRecentFiles(_ files: [RecentFileItem]) {
        if let data = try? JSONEncoder().encode(files) {
            sharedDefaults?.set(data, forKey: "recentFiles")
        }
    }

    /// 更新登录状态
    func updateLoginStatus(_ isLoggedIn: Bool) {
        sharedDefaults?.set(isLoggedIn, forKey: "isLoggedIn")
    }

    /// 更新认证令牌
    func updateAuthToken(_ token: String) {
        sharedDefaults?.set(token, forKey: "authToken")
    }

    /// 更新服务器地址
    func updateServerURL(_ url: String) {
        sharedDefaults?.set(url, forKey: "serverURL")
    }

    /// 更新最近使用的文件夹
    func updateRecentFolders(_ folders: [AppGroupFolderItem]) {
        if let data = try? JSONEncoder().encode(folders) {
            sharedDefaults?.set(data, forKey: "recentFolders")
        }
    }

    // MARK: - 刷新 Widget

    func refreshWidget() {
        WidgetCenter.shared.reloadAllTimelines()
    }

    /// 同步所有数据到共享存储
    func syncAllData(
        storageUsed: Int64,
        storageTotal: Int64,
        recentFiles: [RecentFileItem],
        isLoggedIn: Bool,
        serverURL: String
    ) {
        updateStorageUsage(used: storageUsed, total: storageTotal)
        updateRecentFiles(recentFiles)
        updateLoginStatus(isLoggedIn)
        updateServerURL(serverURL)
        refreshWidget()
    }
}

// MARK: - 数据模型

struct RecentFileItem: Codable {
    let name: String
    let size: String
    let date: String
}

struct AppGroupFolderItem: Codable {
    let id: String
    let name: String
}