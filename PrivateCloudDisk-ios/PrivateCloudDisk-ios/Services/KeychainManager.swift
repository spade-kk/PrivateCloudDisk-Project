//
//  KeychainManager.swift
//  PrivateCloudDisk-ios
//
//  Keychain 安全存储 — 管理 Token、密码等敏感数据
//  利用 iOS 原生 Keychain Services，数据加密存储于系统安全区域
//

import Foundation
import Security

class KeychainManager {
    static let shared = KeychainManager()

    private let serviceName = "com.privateclouddisk.ios"
    private let tokenKey = "auth_token"
    private let userIdKey = "user_id"
    private let serverURLKey = "server_url"

    // MARK: - Token

    func saveToken(_ token: String) {
        save(key: tokenKey, value: token)
    }

    func getToken() -> String? {
        return read(key: tokenKey)
    }

    func deleteToken() {
        delete(key: tokenKey)
    }

    // MARK: - User ID

    func saveUserId(_ userId: String) {
        save(key: userIdKey, value: userId)
        UserDefaults.standard.set(userId, forKey: "currentUserId")
    }

    func getUserId() -> String? {
        return read(key: userIdKey) ?? UserDefaults.standard.string(forKey: "currentUserId")
    }

    // MARK: - Server URL

    func saveServerURL(_ url: String) {
        save(key: serverURLKey, value: url)
    }

    func getServerURL() -> String? {
        return read(key: serverURLKey)
    }

    // MARK: - 通用操作

    func save(key: String, value: String) {
        guard let data = value.data(using: .utf8) else { return }

        // 先删除旧值
        delete(key: key)

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        ]

        SecItemAdd(query as CFDictionary, nil)
    }

    func read(key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess, let data = result as? Data else {
            return nil
        }
        return String(data: data, encoding: .utf8)
    }

    func delete(key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key
        ]
        SecItemDelete(query as CFDictionary)
    }

    func clearAll() {
        delete(key: tokenKey)
        delete(key: userIdKey)
        UserDefaults.standard.removeObject(forKey: "currentUserId")
    }
}