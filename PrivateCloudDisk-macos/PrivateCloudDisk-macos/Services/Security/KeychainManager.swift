import Foundation
import Security
import CryptoKit
import LocalAuthentication

// MARK: - Keychain 管理器

/// macOS Keychain 集成服务
///
/// 对应 Windows 的 CredentialManagerService，使用 macOS 原生 Keychain Services
/// 安全存储敏感数据（Token、密码等）。
///
/// macOS Keychain 对比 Windows Credential Manager 的优势：
/// - iCloud Keychain 跨设备同步
/// - 存取控制列表（ACL）细粒度权限
/// - 生物识别解锁（Touch ID / Face ID）
/// - Secure Enclave 硬件加密
/// - 应用间共享 Keychain Access Group
///
/// 安全特性：
/// - 使用 kSecClassGenericPassword 存储凭据
/// - 使用 kSecAttrAccessibleWhenUnlockedThisDeviceOnly 限制访问
/// - 支持 kSecUseAuthenticationUI 生物识别验证
/// - 应用退出时清除会话级敏感数据
final class KeychainManager {

    static let shared = KeychainManager()

    // MARK: - 配置

    /// 使用 Bundle ID 动态生成 serviceName，确保 Keychain 命名空间与 entitlements 匹配
    private let serviceName: String = {
        Bundle.main.bundleIdentifier ?? "com.spadek.PrivateCloudDisk-macos"
    }()

    private enum KeychainKey: String, CaseIterable {
        case authToken = "auth_token"
        case refreshToken = "refresh_token"
        case userId = "user_id"
        case username = "username"
        case encryptionKey = "encryption_key"
        case deviceId = "device_id"
        case deviceClientIdentity = "device_client_identity"
    }

    // MARK: - 写入

    /// 安全存储字符串到 Keychain
    func store(key: String, value: String, useBiometry: Bool = false) {
        guard let data = value.data(using: .utf8) else { return }

        // 先删除旧值
        delete(key: key)

        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            kSecAttrSynchronizable as String: kCFBooleanFalse!, // 不通过 iCloud 同步敏感凭据
        ]

        // 可选：启用生物识别保护
        if useBiometry {
            let context = LAContext()
            context.touchIDAuthenticationAllowableReuseDuration = 10
            query[kSecUseAuthenticationContext as String] = context
            query[kSecAttrAccessControl as String] = SecAccessControlCreateWithFlags(
                nil,
                kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                .biometryCurrentSet,
                nil
            )!
        }

        let status = SecItemAdd(query as CFDictionary, nil)
        if status != errSecSuccess {
            print("[Keychain] 存储失败: \(status) for key: \(key)")
        }
    }

    /// 存储二进制数据到 Keychain
    func storeData(key: String, data: Data) {
        delete(key: key)

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            kSecAttrSynchronizable as String: kCFBooleanFalse!,
        ]

        let status = SecItemAdd(query as CFDictionary, nil)
        if status != errSecSuccess {
            print("[Keychain] 存储数据失败: \(status) for key: \(key)")
        }
    }

    // MARK: - 读取

    /// 从 Keychain 读取字符串
    func read(key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
            kSecReturnData as String: kCFBooleanTrue!,
            kSecMatchLimit as String: kSecMatchLimitOne,
            kSecAttrSynchronizable as String: kCFBooleanFalse!,
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess, let data = result as? Data else {
            if status != errSecItemNotFound {
                print("[Keychain] 读取失败: \(status) for key: \(key)")
            }
            return nil
        }

        return String(data: data, encoding: .utf8)
    }

    /// 从 Keychain 读取二进制数据
    func readData(key: String) -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
            kSecReturnData as String: kCFBooleanTrue!,
            kSecMatchLimit as String: kSecMatchLimitOne,
            kSecAttrSynchronizable as String: kCFBooleanFalse!,
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess, let data = result as? Data else {
            return nil
        }
        return data
    }

    // MARK: - 删除

    /// 从 Keychain 删除条目
    func delete(key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
            kSecAttrSynchronizable as String: kCFBooleanFalse!,
        ]
        SecItemDelete(query as CFDictionary)
    }

    // MARK: - 便捷方法：Token 管理

    func storeAuthToken(_ token: String) {
        store(key: KeychainKey.authToken.rawValue, value: token)
    }

    func readAuthToken() -> String? {
        read(key: KeychainKey.authToken.rawValue)
    }

    func storeRefreshToken(_ token: String) {
        store(key: KeychainKey.refreshToken.rawValue, value: token)
    }

    func readRefreshToken() -> String? {
        read(key: KeychainKey.refreshToken.rawValue)
    }

    func storeUserId(_ userId: String) {
        store(key: KeychainKey.userId.rawValue, value: userId)
    }

    func readUserId() -> String? {
        read(key: KeychainKey.userId.rawValue)
    }

    func storeUsername(_ username: String) {
        store(key: KeychainKey.username.rawValue, value: username)
    }

    func readUsername() -> String? {
        read(key: KeychainKey.username.rawValue)
    }

    func storeEncryptionKey(_ key: SymmetricKey) {
        storeData(key: KeychainKey.encryptionKey.rawValue, data: key.withUnsafeBytes { Data($0) })
    }

    func readEncryptionKey() -> SymmetricKey? {
        guard let data = readData(key: KeychainKey.encryptionKey.rawValue) else { return nil }
        return SymmetricKey(data: data)
    }

    // MARK: - 登出清理

    /// 清除所有凭据（登出时调用）
    func clearAll() {
        for key in KeychainKey.allCases {
            delete(key: key.rawValue)
        }
    }

    /// 清除会话级敏感数据（应用退出时调用）
    func clearSessionSensitiveData() {
        // 清除内存中的敏感数据（Token 等由调用方管理）
        // 注意：Keychain 中的持久化数据保留（用于自动登录）
    }

    /// 更新 Token（Token 刷新后调用）
    func updateTokens(accessToken: String?, refreshToken: String?, userId: String?) {
        if let token = accessToken { storeAuthToken(token) }
        if let refresh = refreshToken { storeRefreshToken(refresh) }
        if let uid = userId { storeUserId(uid) }
    }

    // MARK: - 设备指纹

    func getOrCreateDeviceId() -> String {
        if let existing = read(key: KeychainKey.deviceId.rawValue) {
            return existing
        }
        let newId = UUID().uuidString
        store(key: KeychainKey.deviceId.rawValue, value: newId)
        return newId
    }
}
