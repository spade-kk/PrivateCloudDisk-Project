//
//  BiometricAuthManager.swift
//  PrivateCloudDisk-ios
//
//  生物识别认证 — Face ID / Touch ID
//  利用 iOS 原生 LocalAuthentication 框架，实现安全快捷的二次认证
//  支持：
//    - 登录时生物识别自动填充
//    - 解锁加密文件/文件夹
//    - 分享链接密码自动填充
//    - App 切换回来时的二次确认
//

import LocalAuthentication
import Foundation
import Combine

@MainActor
class BiometricAuthManager: ObservableObject {
    static let shared = BiometricAuthManager()

    @Published var isBiometricAvailable = false
    @Published var biometricType: LABiometryType = .none

    private let context = LAContext()

    private init() {
        checkAvailability()
    }

    // MARK: - 可用性检查

    func checkAvailability() {
        var error: NSError?
        isBiometricAvailable = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)
        if isBiometricAvailable {
            biometricType = context.biometryType
        }
    }

    var biometricTypeName: String {
        switch biometricType {
        case .faceID: return "Face ID"
        case .touchID: return "Touch ID"
        case .opticID: return "Optic ID"
        default: return "生物识别"
        }
    }

    var biometricIconName: String {
        switch biometricType {
        case .faceID: return "faceid"
        case .touchID: return "touchid"
        default: return "lock.shield"
        }
    }

    // MARK: - 认证

    enum BiometricPurpose {
        case unlockApp
        case unlockFile
        case autoFillPassword
        case confirmAction
    }

    func authenticate(purpose: BiometricPurpose) async throws -> Bool {
        guard isBiometricAvailable else { return false }

        let reason: String
        switch purpose {
        case .unlockApp: reason = "使用\(biometricTypeName)解锁私有云盘"
        case .unlockFile: reason = "使用\(biometricTypeName)查看加密文件"
        case .autoFillPassword: reason = "使用\(biometricTypeName)自动填充密码"
        case .confirmAction: reason = "使用\(biometricTypeName)确认操作"
        }

        let freshContext = LAContext()
        do {
            let result = try await freshContext.evaluatePolicy(
                .deviceOwnerAuthenticationWithBiometrics,
                localizedReason: reason
            )
            return result
        } catch {
            throw error
        }
    }

    // MARK: - 密钥存储

    func saveSecureData(_ data: String, forKey key: String) {
        KeychainManager.shared.save(key: "bio_\(key)", value: data)
    }

    func getSecureData(forKey key: String) -> String? {
        return KeychainManager.shared.read(key: "bio_\(key)")
    }
}