import Foundation
import Security
import CryptoKit
import LocalAuthentication

// MARK: - Secure Enclave 密钥管理器

/// 基于 Apple Secure Enclave 协处理器的设备身份密钥管理服务
///
/// Secure Enclave 是 Apple 设备（T2 / Apple Silicon Mac）内置的硬件安全模块，
/// 提供以下安全能力：
/// - 硬件隔离的密钥生成与存储（私钥永不出 SE）
/// - 基于硬件的 ECDSA P-256 签名
/// - 操作系统级别的访问控制（生物识别 / 设备密码）
/// - 防篡改保护（即使内核被攻破也无法提取私钥）
///
/// 在不支持 Secure Enclave 的设备上（Intel Mac 无 T2），
/// 自动降级为 Keychain 存储的软件密钥。
///
/// 【公钥格式说明】
/// 使用 CryptoKit 的 `P256.Signing.PublicKey.derRepresentation` 导出标准
/// SubjectPublicKeyInfo (SPKI) DER 格式，服务端 Go 的 `x509.ParsePKIXPublicKey`
/// 可直接解析。不再手动构造 DER 字节。
///
/// 使用方式：
///   let manager = SecureEnclaveManager.shared
///   let keyInfo = try await manager.generateKeyPair()
///   let signature = try await manager.sign(data: payload)
///   let pubKeyB64 = try await manager.exportPublicKey()
final class SecureEnclaveManager {

    // MARK: - 单例

    static let shared = SecureEnclaveManager()

    private init() {}

    // MARK: - 常量

    /// 应用标签 — 使用 Bundle ID 动态生成，确保与 entitlements 中的 keychain-access-groups 匹配
    private let appTagPrefix: String = {
        Bundle.main.bundleIdentifier ?? "com.spadek.PrivateCloudDisk-macos"
    }()

    /// 设备身份密钥标签
    private var keyTag: String { "\(appTagPrefix).device-identity-key" }

    /// 密钥算法
    private let keyAlgorithm = kSecAttrKeyTypeECSECPrimeRandom // ECDSA P-256

    /// 密钥大小（位）
    private let keySize = 256

    // MARK: - 数据结构

    /// 密钥信息
    struct KeyInfo {
        /// 公钥（SPKI DER 格式 Base64）
        let publicKey: String
        /// 算法
        let algorithm: String
        /// 密钥存储位置（系统真实值，如 "com.apple.setoken" 或 "Keychain"）
        let tokenID: String
        /// 密钥大小
        let keySize: Int
        /// 格式（SPKI = SubjectPublicKeyInfo DER）
        let format: String
        /// 密钥标签
        let tag: String
        /// 是否在 Secure Enclave 中
        var isInSecureEnclave: Bool { tokenID == (kSecAttrTokenIDSecureEnclave as String) }
    }

    /// 签名结果
    struct SignResult {
        /// 签名（Base64）
        let signature: String
        /// 算法
        let algorithm: String
        /// 数据长度
        let dataLength: Int
    }

    /// 密钥验证结果
    struct VerifyResult {
        /// 密钥是否存在
        let exists: Bool
        /// 是否在 Secure Enclave 中
        let inSecureEnclave: Bool
        /// 存储位置
        let tokenID: String
        /// 密钥类型
        let keyClass: String
        /// 密钥大小
        let keySize: Int
        /// 创建时间
        let creationDate: Date?
    }

    // MARK: - Secure Enclave 可用性检测

    /// 检测当前设备是否支持 Secure Enclave
    ///
    /// 支持 Secure Enclave 的设备：
    /// - 搭载 Apple T2 安全芯片的 Intel Mac（2018 年及以后）
    /// - 所有 Apple Silicon Mac（M1/M2/M3/M4 系列）
    ///
    /// - Returns: true 表示 Secure Enclave 可用
    func isSecureEnclaveAvailable() -> Bool {
        // 尝试在 Secure Enclave 中创建一个临时密钥对来检测
        guard let accessControl = SecAccessControlCreateWithFlags(
            kCFAllocatorDefault,
            kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            .privateKeyUsage,
            nil
        ) else {
            return false
        }

        let tempTag = "\(appTagPrefix).se-test-\(UUID().uuidString)".data(using: .utf8)!

        let attributes: [String: Any] = [
            kSecAttrKeyType as String: keyAlgorithm,
            kSecAttrKeySizeInBits as String: keySize,
            kSecAttrTokenID as String: kSecAttrTokenIDSecureEnclave,
            kSecPrivateKeyAttrs as String: [
                kSecAttrIsPermanent as String: true,
                kSecAttrApplicationTag as String: tempTag,
                kSecAttrAccessControl as String: accessControl,
            ]
        ]

        var error: Unmanaged<CFError>?
        guard let _ = SecKeyCreateRandomKey(attributes as CFDictionary, &error) else {
            return false
        }

        // 清理临时密钥
        let deleteQuery: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecAttrApplicationTag as String: tempTag,
        ]
        SecItemDelete(deleteQuery as CFDictionary)

        return true
    }

    // MARK: - 密钥对生成

    /// 在 Secure Enclave（或 Keychain）中生成 ECDSA P-256 密钥对
    ///
    /// 优先使用 Secure Enclave 硬件隔离存储，
    /// 不可用时降级为 Keychain 软件存储。
    ///
    /// 如果密钥已存在，先删除旧密钥再生成新密钥。
    ///
    /// 【公钥导出】
    /// 使用 CryptoKit 的 `P256.Signing.PublicKey.derRepresentation` 导出标准
    /// SubjectPublicKeyInfo DER 格式，服务端可直接用 `x509.ParsePKIXPublicKey` 解析。
    ///
    /// 【tokenID】
    /// 从私钥属性中读取系统真实值（如 "com.apple.setoken"），
    /// 不再使用硬编码的 "SecureEnclave" / "Keychain"。
    ///
    /// - Returns: 密钥信息（公钥为 SPKI DER 格式 Base64）
    func generateKeyPair() throws -> KeyInfo {
        let tagData = keyTag.data(using: .utf8)!

        // 如果已存在密钥，先删除
        try? deleteKeyPair()

        // 创建访问控制
        guard let accessControl = SecAccessControlCreateWithFlags(
            kCFAllocatorDefault,
            kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            .privateKeyUsage,
            nil
        ) else {
            throw SecureEnclaveError.accessControlCreationFailed
        }

        // 私钥属性（不包含 tokenID）
        let privateKeyAttrs: [String: Any] = [
            kSecAttrIsPermanent as String: true,
            kSecAttrApplicationTag as String: tagData,
            kSecAttrAccessControl as String: accessControl,
        ]

        // ---- 尝试用 Secure Enclave 生成 ----
        var attributes: [String: Any] = [
            kSecAttrKeyType as String: keyAlgorithm,
            kSecAttrKeySizeInBits as String: keySize,
            kSecAttrTokenID as String: kSecAttrTokenIDSecureEnclave,
            kSecPrivateKeyAttrs as String: privateKeyAttrs,
            kSecPublicKeyAttrs as String: [
                kSecAttrIsPermanent as String: false,
            ]
        ]

        var error: Unmanaged<CFError>?
        var privateKey = SecKeyCreateRandomKey(attributes as CFDictionary, &error)

        // 如果 Secure Enclave 生成失败，回退到软件 Keychain
        if privateKey == nil {
            attributes.removeValue(forKey: kSecAttrTokenID as String)

            var fallbackError: Unmanaged<CFError>?
            privateKey = SecKeyCreateRandomKey(attributes as CFDictionary, &fallbackError)
            if privateKey == nil {
                let msg = fallbackError?.takeRetainedValue().localizedDescription ?? "未知错误"
                throw SecureEnclaveError.keyGenerationFailed(msg)
            }
        }

        // 从私钥属性中读取真实的 tokenID（系统值）
        let privateAttrs = SecKeyCopyAttributes(privateKey!) as? [String: Any] ?? [:]
        let realTokenID: String
        if let tokenVal = privateAttrs[kSecAttrTokenID as String] as? String {
            realTokenID = tokenVal  // Secure Enclave 会是 "com.apple.setoken"
        } else {
            realTokenID = "Keychain"  // 软件密钥没有此属性
        }

        // 获取公钥
        guard let secPublicKey = SecKeyCopyPublicKey(privateKey!) else {
            throw SecureEnclaveError.publicKeyExtractionFailed
        }

        // ---- 使用 CryptoKit 导出标准 SPKI DER 格式 ----
        let spkiData = try secPublicKeyToSPKI(secPublicKey)

        print("[SecureEnclave] 密钥对已生成 (tokenID: \(realTokenID), 公钥格式: SPKI DER)")

        return KeyInfo(
            publicKey: spkiData.base64EncodedString(),
            algorithm: "EC-P256",
            tokenID: realTokenID,
            keySize: keySize,
            format: "DER",
            tag: keyTag
        )
    }

    // MARK: - 公钥导出

    /// 导出公钥（SubjectPublicKeyInfo DER 格式 Base64）
    ///
    /// 使用 CryptoKit 的 `P256.Signing.PublicKey.derRepresentation` 导出，
    /// 服务端可直接用 `x509.ParsePKIXPublicKey` 解析。
    ///
    /// - Returns: 密钥信息
    func exportPublicKey() throws -> KeyInfo {
        guard let privateKey = loadPrivateKey() else {
            throw SecureEnclaveError.keyNotFound
        }

        guard let secPublicKey = SecKeyCopyPublicKey(privateKey) else {
            throw SecureEnclaveError.publicKeyExtractionFailed
        }

        // 从私钥属性中读取真实的 tokenID（系统值）
        let privateAttrs = SecKeyCopyAttributes(privateKey) as? [String: Any] ?? [:]
        let realTokenID: String
        if let tokenVal = privateAttrs[kSecAttrTokenID as String] as? String {
            realTokenID = tokenVal
        } else {
            realTokenID = "Keychain"
        }

        // 使用 CryptoKit 导出标准 SPKI DER
        let spkiData = try secPublicKeyToSPKI(secPublicKey)

        return KeyInfo(
            publicKey: spkiData.base64EncodedString(),
            algorithm: "EC-P256",
            tokenID: realTokenID,
            keySize: keySize,
            format: "DER",
            tag: keyTag
        )
    }

    /// 导出公钥为 SPKI DER 原始字节（供 Apple App Attestation 计算 clientDataHash 使用）
    ///
    /// - Returns: SPKI DER 格式的原始字节
    func exportPublicKeyDER() throws -> Data {
        guard let privateKey = loadPrivateKey() else {
            throw SecureEnclaveError.keyNotFound
        }

        guard let secPublicKey = SecKeyCopyPublicKey(privateKey) else {
            throw SecureEnclaveError.publicKeyExtractionFailed
        }

        return try secPublicKeyToSPKI(secPublicKey)
    }

    // MARK: - 密钥验证

    /// 验证密钥对是否存在及其存储位置
    ///
    /// - Returns: 验证结果
    func verifyKeyExists() -> VerifyResult {
        let tagData = keyTag.data(using: .utf8)!

        let query: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecAttrApplicationTag as String: tagData,
            kSecAttrKeyType as String: keyAlgorithm,
            kSecReturnAttributes as String: true,
            kSecReturnRef as String: true,
        ]

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)

        guard status == errSecSuccess,
              let dict = item as? [String: Any] else {
            return VerifyResult(
                exists: false,
                inSecureEnclave: false,
                tokenID: "None",
                keyClass: "unknown",
                keySize: 0,
                creationDate: nil
            )
        }

        let tokenID = dict[kSecAttrTokenID as String] as? String ?? "Keychain"
        let keyClass = dict[kSecAttrKeyClass as String] as? String ?? "unknown"
        let keySize = dict[kSecAttrKeySizeInBits as String] as? Int ?? 0
        let creationDate = dict[kSecAttrCreationDate as String] as? Date

        return VerifyResult(
            exists: true,
            inSecureEnclave: (tokenID == kSecAttrTokenIDSecureEnclave as String),
            tokenID: tokenID,
            keyClass: keyClass,
            keySize: keySize,
            creationDate: creationDate
        )
    }

    // MARK: - 数据签名

    /// 使用设备私钥对数据进行 ECDSA-SHA256 签名
    ///
    /// 私钥操作在 Secure Enclave 内部完成，私钥永不离开安全硬件。
    ///
    /// - Parameter data: 待签名数据（Base64 编码）
    /// - Returns: 签名结果
    func sign(dataB64: String) throws -> SignResult {
        guard let privateKey = loadPrivateKey() else {
            throw SecureEnclaveError.keyNotFound
        }

        guard let payloadData = Data(base64Encoded: dataB64) else {
            throw SecureEnclaveError.invalidBase64Data
        }

        var signError: Unmanaged<CFError>?
        guard let signature = SecKeyCreateSignature(
            privateKey,
            .ecdsaSignatureMessageX962SHA256,
            payloadData as CFData,
            &signError
        ) as Data? else {
            let errMsg = signError?.takeRetainedValue().localizedDescription ?? "未知错误"
            throw SecureEnclaveError.signingFailed(errMsg)
        }

        return SignResult(
            signature: signature.base64EncodedString(),
            algorithm: "ECDSA-P256-SHA256",
            dataLength: payloadData.count
        )
    }

    /// 使用设备私钥对原始字节数据签名
    func signRaw(data: Data) throws -> SignResult {
        return try sign(dataB64: data.base64EncodedString())
    }

    // MARK: - 密钥删除

    /// 删除设备密钥对
    func deleteKeyPair() throws {
        let tagData = keyTag.data(using: .utf8)!

        let deleteQuery: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecAttrApplicationTag as String: tagData,
        ]

        let status = SecItemDelete(deleteQuery as CFDictionary)
        if status != errSecSuccess && status != errSecItemNotFound {
            throw SecureEnclaveError.keyDeletionFailed(status)
        }

        print("[SecureEnclave] 密钥对已删除")
    }

    // MARK: - 私有方法

    /// 从 Keychain / Secure Enclave 中读取私钥引用
    private func loadPrivateKey() -> SecKey? {
        let tagData = keyTag.data(using: .utf8)!

        let query: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecAttrApplicationTag as String: tagData,
            kSecAttrKeyType as String: keyAlgorithm,
            kSecReturnRef as String: true,
        ]

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)

        guard status == errSecSuccess else {
            return nil
        }

        return (item as! SecKey)
    }

    // MARK: - 公钥格式转换（裸坐标 → SPKI DER，使用 CryptoKit 官方 API）

    /// 将 SecKey 公钥转换为 SPKI DER 格式字节
    ///
    /// 使用 Apple 官方 CryptoKit 的 `P256.Signing.PublicKey.derRepresentation`
    /// 导出标准 SubjectPublicKeyInfo DER 格式，而非手动构造 ASN.1 结构。
    ///
    /// 流程：
    ///   1. `SecKeyCopyExternalRepresentation` → 65 字节裸坐标（0x04 || x || y）
    ///   2. 去掉 0x04 前缀 → 64 字节紧凑坐标（x || y）
    ///   3. `P256.Signing.PublicKey(rawRepresentation:)` → CryptoKit 公钥
    ///   4. `.derRepresentation` → 标准 SPKI DER 格式
    ///
    /// - Parameter secPublicKey: SecKey 公钥引用
    /// - Returns: SPKI DER 格式字节
    private func secPublicKeyToSPKI(_ secPublicKey: SecKey) throws -> Data {
        // 步骤 1: 获取裸坐标（65 字节，0x04 || x || y）
        var exportError: Unmanaged<CFError>?
        guard let rawData = SecKeyCopyExternalRepresentation(secPublicKey, &exportError) as Data? else {
            let errMsg = exportError?.takeRetainedValue().localizedDescription ?? "未知错误"
            throw SecureEnclaveError.publicKeyExportFailed(errMsg)
        }

        // 步骤 2: 去掉 0x04 前缀，得到 64 字节 x||y
        let compactKey = rawData.dropFirst()  // 65 → 64

        // 步骤 3: 用 CryptoKit 解析为 P256 公钥
        guard let cryptoPubKey = try? P256.Signing.PublicKey(rawRepresentation: compactKey) else {
            throw SecureEnclaveError.publicKeyExportFailed("无法解析为 P256 公钥")
        }

        // 步骤 4: 导出标准 SubjectPublicKeyInfo DER
        return cryptoPubKey.derRepresentation
    }
}

// MARK: - Secure Enclave 错误

enum SecureEnclaveError: LocalizedError {
    case accessControlCreationFailed
    case keyGenerationFailed(String)
    case publicKeyExtractionFailed
    case publicKeyExportFailed(String)
    case keyNotFound
    case invalidBase64Data
    case signingFailed(String)
    case keyDeletionFailed(OSStatus)
    case secureEnclaveUnavailable

    var errorDescription: String? {
        switch self {
        case .accessControlCreationFailed:
            return "无法创建安全访问控制"
        case .keyGenerationFailed(let msg):
            return "密钥生成失败: \(msg)"
        case .publicKeyExtractionFailed:
            return "无法获取公钥"
        case .publicKeyExportFailed(let msg):
            return "公钥导出失败: \(msg)"
        case .keyNotFound:
            return "设备密钥不存在，请先生成密钥对"
        case .invalidBase64Data:
            return "无效的 Base64 数据"
        case .signingFailed(let msg):
            return "签名失败: \(msg)"
        case .keyDeletionFailed(let status):
            return "密钥删除失败: \(status)"
        case .secureEnclaveUnavailable:
            return "当前设备不支持 Secure Enclave"
        }
    }
}
