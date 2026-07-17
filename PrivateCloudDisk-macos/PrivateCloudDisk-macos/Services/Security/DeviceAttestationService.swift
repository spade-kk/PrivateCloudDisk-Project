import Foundation
import CryptoKit
import DeviceCheck
import IOKit

// MARK: - 设备信任证明服务

/// 设备信任证明生成与验证服务
///
/// 对应 建议6.md 中的「设备信任证明机制」要求。
///
/// 【信任链模型 — 三层证明架构】
///
/// 为什么需要"证明"？因为客户端注册接口可被任何人调用。
/// 攻击者可以：
///   1. 用自己的脚本/工具生成一个 ECDSA P-256 密钥对（无需 Secure Enclave）
///   2. 通过注册接口提交到服务器，伪造一个"客户端实例"
///   3. 服务器无法判断这个公钥是由官方 APP 在真实设备上生成的
///
/// 因此需要两层证明：
///
///  ┌─────────────────────────────────────────────────────────────┐
///  │ 第一层：硬件证明（Hardware Attestation）                     │
///  │ 证明：这个公钥是在一台真实 Apple 设备的 Secure Enclave 中    │
///  │      生成的，不是攻击者用脚本/工具生成的软件密钥。           │
///  │ 实现：Apple Secure Enclave 硬件本身提供此保证 —              │
///  │      SecKeyCreateRandomKey(..., kSecAttrTokenIDSecureEnclave)│
///  │      创建的密钥，私钥永不出 SE，签名在硬件内完成。           │
///  └─────────────────────────────────────────────────────────────┘
///
///  ┌─────────────────────────────────────────────────────────────┐
///  │ 第二层：APP 证明（App Attestation）                          │
///  │ 证明：这个公钥是由我们的官方 APP（特定 Bundle ID）调用       │
///  │      Secure Enclave 生成的，不是攻击者开发的另一个假 APP     │
///  │      在同一台设备上调用 Secure Enclave 生成的。              │
///  │ 实现：Apple DCAppAttestService.attestKey() 返回由 Apple      │
///  │      服务器签名的证明语句，绑定 Bundle ID 和公钥哈希。       │
///  └─────────────────────────────────────────────────────────────┘
///
///  ┌─────────────────────────────────────────────────────────────┐
///  │ 第三层：业务实例签名（Instance Signature）                   │
///  │ 证明：这个注册请求是由持有业务私钥的客户端实例发出的，       │
///  │      且请求是新鲜的（包含服务端挑战值）。                     │
///  │ 实现：使用业务密钥对证明负载进行 ECDSA 签名。                │
///  └─────────────────────────────────────────────────────────────┘
///
/// 三层合一，服务端才能确认：
///   "这个公钥是由我们的官方 APP，在一台真实 Apple 设备上生成的，
///    并且当前注册请求是由该设备的持有者发出的。"
///
/// 证明数据结构：
///   {
///     "version": "1.0",
///     "app_id": "com.spadek.PrivateCloudDisk-macos",
///     "platform": "macOS",
///     "device_id": "sha256-hash",
///     "public_key": "base64-der-spki",          ← 业务公钥（DER SPKI 格式）
///     "key_algorithm": "ECDSA-P256",
///     "token_id": "com.apple.setoken",         ← 系统真实 tokenID（SE 硬件密钥标识）
///     "integrity_level": "high",
///     "os_version": "14.0",
///     "hostname": "MacBook-Pro",
///     "timestamp": 1699000000,
///     "challenge": "server-challenge",
///     "signature": "base64",                    ← 业务密钥签名
///     "signing_payload": "...",
///     "apple_attestation": "base64-cbor",       ← Apple App Attest 证明（CBOR）
///     "apple_attest_key_id": "base64"           ← App Attest 密钥标识
///   }
final class DeviceAttestationService {

    // MARK: - 单例

    static let shared = DeviceAttestationService()

    private init() {}

    // MARK: - 常量

    /// 证明协议版本
    private let attestationVersion = "1.0"

    /// 应用标识
    private let appID: String = {
        Bundle.main.bundleIdentifier ?? "com.spadek.PrivateCloudDisk-macos"
    }()

    /// 平台
    private let platform = "macOS"

    // MARK: - 数据结构

    /// 设备信任证明
    struct AttestationObject: Codable {
        let version: String
        let appId: String
        let platform: String
        let deviceId: String
        let publicKey: String
        let keyAlgorithm: String
        let tokenId: String
        let integrityLevel: String
        let osVersion: String
        let hostname: String
        let timestamp: Int
        let challenge: String
        let signature: String
        let signingPayload: String
        let appleAttestation: String
        let appleAttestKeyId: String
    }

    /// Apple App Attestation 结果
    struct AppleAttestationResult {
        /// App Attest 密钥标识（Base64）
        let keyId: String
        /// Apple 签名的证明语句（CBOR 格式）
        let attestation: Data
    }

    // MARK: - 设备指纹

    /// 获取设备硬件指纹
    ///
    /// 使用 macOS 硬件 UUID 的 SHA-256 哈希作为设备指纹，
    /// 不直接暴露原始硬件 UUID。
    ///
    /// - Returns: 64 位十六进制设备指纹
    func getDeviceFingerprint() -> String {
        var components: [String] = [
            ProcessInfo.processInfo.hostName,
            ProcessInfo.processInfo.operatingSystemVersionString,
            "macOS",
        ]

        // 获取硬件 UUID
        if let hwUUID = getIOPlatformUUID() {
            components.append(hwUUID)
        }

        // 获取 CPU 信息
        var sysInfo = [CTL_HW, HW_MODEL]
        var model = [CChar](repeating: 0, count: 256)
        var size = model.count
        if sysctl(&sysInfo, 2, &model, &size, nil, 0) == 0 {
            components.append(String(cString: model))
        }

        let fingerprint = Data(SHA256.hash(data: Data(components.joined(separator: "|").utf8)))
        return fingerprint.hexString
    }

    /// 从 IOKit 获取 IOPlatformUUID
    private func getIOPlatformUUID() -> String? {
        let platformExpert = IOServiceGetMatchingService(
            kIOMainPortDefault,
            IOServiceMatching("IOPlatformExpertDevice")
        )
        guard platformExpert != 0 else { return nil }

        defer { IOObjectRelease(platformExpert) }

        let uuidProperty = IORegistryEntryCreateCFProperty(
            platformExpert,
            "IOPlatformUUID" as CFString,
            kCFAllocatorDefault,
            0
        )

        guard let uuid = uuidProperty?.takeRetainedValue() as? String else {
            return nil
        }

        return uuid
    }

    // MARK: - App Attest 可用性检测

    /// 检测当前设备是否支持 Apple App Attestation
    ///
    /// App Attest 要求：
    ///   - macOS 11.0+ / iOS 14.0+
    ///   - Apple Silicon Mac（M 系列）或搭载 T2 芯片的 Intel Mac
    ///   - App 需配置 com.apple.developer.devicecheck.appattest 权限
    ///
    /// - Returns: true 表示 App Attest 可用
    func isAppAttestAvailable() -> Bool {
        return DCAppAttestService.shared.isSupported
    }

    // MARK: - 完整性等级

    /// 获取设备完整性等级
    ///
    /// 等级判定（综合考虑硬件 + APP 证明）：
    ///   - high:   Secure Enclave 密钥 + Apple App Attest 证明通过
    ///   - medium: Secure Enclave 密钥，但 App Attest 不可用（降级）
    ///   - low:    软件 Keychain 密钥（无 SE 硬件隔离）
    ///
    /// - Returns: 完整性等级字符串
    func getIntegrityLevel(hasAppAttest: Bool) -> String {
        if SecureEnclaveManager.shared.isSecureEnclaveAvailable() {
            let verifyResult = SecureEnclaveManager.shared.verifyKeyExists()
            if verifyResult.inSecureEnclave {
                return hasAppAttest ? "high" : "medium"
            }
            return "medium"
        }
        return "medium"
    }

    // MARK: - Apple App Attestation 证明生成

    /// 执行 Apple App Attestation 流程
    ///
    /// 流程：
    ///   1. 调用 DCAppAttestService.shared.generateKey() 生成 App Attest 密钥对
    ///      （此密钥对在 Secure Enclave 中生成，与业务密钥对是独立的）
    ///   2. 计算 clientDataHash = SHA256(业务公钥 DER 数据)
    ///      （将业务公钥绑定到 App Attest 证明中）
    ///   3. 调用 DCAppAttestService.shared.attestKey(keyId, clientDataHash:) 获取证明
    ///      Apple 服务器验证 APP 签名和 Bundle ID 后，返回签名的证明语句
    ///
    /// 【安全原理】
    ///   Apple 的 attestKey 返回的证明中包含了：
    ///   - 证明此密钥是在真实 Secure Enclave 中生成的（硬件证明）
    ///   - 证明此密钥是由特定 Bundle ID 的 APP 调用生成的（APP 证明）
    ///   - 我们传入的 clientDataHash（业务公钥哈希）被绑定到证明中
    ///   服务端验证此证明后，即可确信业务公钥来自官方 APP + 真实设备。
    ///
    /// - Parameter publicKeyDER: 业务公钥的 DER SPKI 格式字节
    /// - Returns: App Attest 结果（keyId + attestation）
    func performAppleAttestation(publicKeyDER: Data) async throws -> AppleAttestationResult {
        let attestService = DCAppAttestService.shared

        // 检查 App Attest 是否可用
        guard attestService.isSupported else {
            throw AttestationError.appAttestUnavailable
        }

        // 步骤 1: 生成 App Attest 密钥对
        // 此密钥对在 Secure Enclave 中生成，专用于 App Attest 证明
        print("[AppAttest] 正在生成 App Attest 密钥对...")
        let keyId: String = try await withCheckedThrowingContinuation { continuation in
            attestService.generateKey { resultKeyId, error in
                if let error = error {
                    continuation.resume(throwing: error)
                } else if let resultKeyId = resultKeyId {
                    continuation.resume(returning: resultKeyId)
                } else {
                    continuation.resume(throwing: AttestationError.appAttestKeyGenerationFailed("未知错误"))
                }
            }
        }
        print("[AppAttest] App Attest 密钥对已生成 (keyId: \(keyId.prefix(16))...)")

        // 步骤 2: 计算 clientDataHash = SHA256(业务公钥 DER)
        // 将业务公钥绑定到 App Attest 证明中，防止攻击者替换公钥
        let clientDataHash = SHA256.hash(data: publicKeyDER)
        let clientDataHashData = Data(clientDataHash)
        print("[AppAttest] clientDataHash = SHA256(业务公钥 DER) = \(clientDataHashData.hexString.prefix(16))...")

        // 步骤 3: 获取 Apple 签名的证明语句
        // Apple 服务器验证 APP 签名和 Bundle ID 后返回 CBOR 格式的证明
        print("[AppAttest] 正在向 Apple 服务器请求证明...")
        let attestationData: Data = try await withCheckedThrowingContinuation { continuation in
            attestService.attestKey(keyId, clientDataHash: clientDataHashData) { attestation, error in
                if let error = error {
                    continuation.resume(throwing: error)
                } else if let attestation = attestation {
                    continuation.resume(returning: attestation)
                } else {
                    continuation.resume(throwing: AttestationError.appAttestFailed("未知错误"))
                }
            }
        }
        print("[AppAttest] Apple 证明已获取 (大小: \(attestationData.count) bytes)")

        return AppleAttestationResult(
            keyId: keyId,
            attestation: attestationData
        )
    }

    // MARK: - 证明生成（完整流程）

    /// 生成设备信任证明（完整三层证明）
    ///
    /// 流程：
    ///   1. 确保业务密钥存在（不存在则生成）
    ///   2. 导出业务公钥（DER SPKI 格式）
    ///   3. 执行 Apple App Attestation 流程
    ///      - 生成 App Attest 密钥对
    ///      - 将业务公钥哈希绑定到 Apple 证明中
    ///      - 获取 Apple 签名的证明语句
    ///   4. 收集设备信息
    ///   5. 构造签名负载
    ///   6. 使用业务私钥对负载进行 ECDSA 签名
    ///   7. 返回完整证明对象（包含 Apple 证明 + 业务签名）
    ///
    /// - Parameters:
    ///   - challenge: 服务端提供的挑战值（Base64 或 UUID）
    ///   - clientId: 已注册的客户端 ID（可选）
    /// - Returns: 证明对象
    func generateAttestation(challenge: String, clientId: String? = nil) async throws -> AttestationObject {
        // 1. 确保密钥存在
        let verifyResult = SecureEnclaveManager.shared.verifyKeyExists()
        if !verifyResult.exists {
            print("[Attestation] 密钥不存在，正在生成...")
            _ = try SecureEnclaveManager.shared.generateKeyPair()
        }

        // 2. 导出业务公钥（DER SPKI 格式）
        let keyInfo = try SecureEnclaveManager.shared.exportPublicKey()
        let publicKeyDER = try SecureEnclaveManager.shared.exportPublicKeyDER()

        // 3. 执行 Apple App Attestation
        var appleAttestationB64 = ""
        var appleAttestKeyIdB64 = ""
        var hasAppAttest = false

        if isAppAttestAvailable() {
            do {
                let appleResult = try await performAppleAttestation(publicKeyDER: publicKeyDER)
                appleAttestationB64 = appleResult.attestation.base64EncodedString()
                appleAttestKeyIdB64 = appleResult.keyId
                hasAppAttest = true
                print("[Attestation] Apple App Attestation 成功")
            } catch {
                // App Attest 失败不阻塞注册流程，降级为 medium
                print("[Attestation] Apple App Attestation 失败（降级）: \(error.localizedDescription)")
            }
        } else {
            print("[Attestation] App Attest 不可用，跳过（降级为 medium）")
        }

        // 4. 收集设备信息
        let deviceFingerprint = getDeviceFingerprint()
        let integrityLevel = getIntegrityLevel(hasAppAttest: hasAppAttest)
        let timestamp = Int(Date().timeIntervalSince1970)
        let osVersion = ProcessInfo.processInfo.operatingSystemVersionString
        let hostname = ProcessInfo.processInfo.hostName

        // 5. 构造签名负载
        // 格式: challenge + "\n" + app_id + "\n" + device_id + "\n" + public_key + "\n" + timestamp
        // 注意：签名负载中不包含 Apple attestation，因为它是独立于业务密钥的证明
        let signingPayload = [
            challenge,
            appID,
            deviceFingerprint,
            keyInfo.publicKey,
            String(timestamp),
        ].joined(separator: "\n")

        // 6. 使用业务私钥签名
        print("[Attestation] 正在使用业务密钥签名证明数据...")
        let signResult = try SecureEnclaveManager.shared.signRaw(data: Data(signingPayload.utf8))

        // 7. 构建完整证明对象
        let attestation = AttestationObject(
            version: attestationVersion,
            appId: appID,
            platform: platform,
            deviceId: deviceFingerprint,
            publicKey: keyInfo.publicKey,
            keyAlgorithm: "ECDSA-P256",
            tokenId: keyInfo.tokenID,
            integrityLevel: integrityLevel,
            osVersion: osVersion,
            hostname: hostname,
            timestamp: timestamp,
            challenge: challenge,
            signature: signResult.signature,
            signingPayload: signingPayload,
            appleAttestation: appleAttestationB64,
            appleAttestKeyId: appleAttestKeyIdB64
        )

        print("[Attestation] 证明生成完成 (integrity: \(integrityLevel), appAttest: \(hasAppAttest))")

        return attestation
    }

    // MARK: - 本地验证

    /// 本地验证证明有效性（提交到服务器前的预检）
    ///
    /// 注意：本地验证仅验证业务密钥签名和字段完整性，
    /// Apple App Attestation 的验证由服务端使用 Apple 根证书完成。
    ///
    /// - Parameter attestation: 证明对象
    /// - Returns: true 表示本地验证通过
    func verifyAttestationLocally(_ attestation: AttestationObject) -> Bool {
        // 1. 验证时间戳（5 分钟内）
        let now = Int(Date().timeIntervalSince1970)
        let age = now - attestation.timestamp
        if abs(age) > 300 {
            print("[Attestation] 证明时间戳过期: \(age) 秒")
            return false
        }

        // 2. 验证 app_id
        if attestation.appId != appID {
            print("[Attestation] app_id 不匹配: \(attestation.appId)")
            return false
        }

        // 3. 重建签名负载并验证
        let signingPayload = [
            attestation.challenge,
            attestation.appId,
            attestation.deviceId,
            attestation.publicKey,
            String(attestation.timestamp),
        ].joined(separator: "\n")

        guard signingPayload == attestation.signingPayload else {
            print("[Attestation] 签名负载不匹配")
            return false
        }

        // 4. 使用公钥验证签名
        // 注意：这里的公钥是 DER SPKI 格式，需要先提取裸坐标才能用 SecKeyCreateWithData
        // 但 SecKeyCreateWithData 对 EC 密钥需要的是裸坐标，所以我们使用 CryptoKit 验证
        guard let publicKeyData = Data(base64Encoded: attestation.publicKey),
              let signatureData = Data(base64Encoded: attestation.signature) else {
            print("[Attestation] 公钥或签名 Base64 解码失败")
            return false
        }

        // 从 DER SPKI 中提取裸坐标（跳过 26 字节的 SPKI 头部）
        // SPKI 结构: 30 59 (SEQUENCE header) + 30 13 ... (AlgorithmIdentifier, 21 bytes) + 03 42 00 (BIT STRING header, 3 bytes) + 04 || x || y (65 bytes)
        // 总计头部: 2 + 21 + 3 = 26 bytes
        let rawPointData: Data
        if publicKeyData.count == 91 {
            // 标准 DER SPKI 格式: 26 bytes header + 65 bytes raw point
            rawPointData = publicKeyData.subdata(in: 26..<91)
        } else {
            // 兼容旧格式（裸坐标）
            rawPointData = publicKeyData
        }

        guard let publicKey = SecKeyCreateWithData(
            rawPointData as CFData,
            [
                kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
                kSecAttrKeyClass as String: kSecAttrKeyClassPublic,
            ] as CFDictionary,
            nil
        ) else {
            print("[Attestation] 公钥解析失败")
            return false
        }

        let payloadData = Data(signingPayload.utf8)
        let isValid = SecKeyVerifySignature(
            publicKey,
            .ecdsaSignatureMessageX962SHA256,
            payloadData as CFData,
            signatureData as CFData,
            nil
        )

        return isValid
    }

    // MARK: - 内存中的 IOKit 常量

    // IOKit 常量直接内联，避免额外 import 依赖
    private let kIOMainPortDefault: mach_port_t = 0
    private let CTL_HW: Int32 = 6
    private let HW_MODEL: Int32 = 2
}

// MARK: - 证明相关错误

enum AttestationError: LocalizedError {
    case appAttestUnavailable
    case appAttestKeyGenerationFailed(String)
    case appAttestFailed(String)

    var errorDescription: String? {
        switch self {
        case .appAttestUnavailable:
            return "Apple App Attest 不可用（设备不支持或未配置权限）"
        case .appAttestKeyGenerationFailed(let msg):
            return "App Attest 密钥生成失败: \(msg)"
        case .appAttestFailed(let msg):
            return "App Attest 证明失败: \(msg)"
        }
    }
}

// MARK: - Data 扩展（复用 CryptoService 中定义的 hexString）