import Foundation
import CryptoKit

// MARK: - 请求签名服务

/// API 请求签名与防重放保护服务
///
/// 对应 建议5.md 中的「API接口安全机制实现」和 建议6.md 中的「防重放机制与接口签名算法」要求。
///
/// 签名算法：
///   SIGNING_PAYLOAD = HTTP_METHOD + "\n" + PATH + "\n" + CLIENT_ID + "\n" + TIMESTAMP + "\n" + NONCE + "\n" + BODY_HASH
///   SIGNATURE = ECDSA-P256-SHA256( SIGNING_PAYLOAD, 设备私钥 )
///
/// 请求头：
///   X-Client-ID:      客户端唯一标识
///   X-Request-Time:    请求时间戳（毫秒）
///   X-Request-Nonce:   请求随机数（UUID v4）
///   X-Request-Sign:    ECDSA 签名（Base64）
///   X-Sign-Algorithm:  签名算法标识
///   X-Integrity-Level:  设备完整性等级
///
/// 防重放机制：
///   - 每个请求携带唯一的 Nonce（UUID v4）
///   - 服务端维护 Nonce 缓存（如 Redis，5 分钟 TTL）
///   - 服务端验证时间戳（±5 分钟窗口）
///   - 组合 (ClientID, Nonce) 全局唯一，拒绝重复请求
///
/// 安全设计（多层纵深防御）：
///   HTTPS:          保护通信通道
///   接口签名:        证明请求由持有私钥的客户端产生
///   防重放:          证明请求是新鲜的
///   硬件信任链:      证明密钥属于可信客户端（Secure Enclave）
///   风控:           防止真实客户端被滥用（后端）
final class RequestSigningService {

    // MARK: - 单例

    static let shared = RequestSigningService()

    private init() {}

    // MARK: - 常量

    /// 签名算法标识
    private let signAlgorithm = "ECDSA-P256-SHA256"

    /// 时间戳窗口（毫秒），超过此窗口的请求将被服务端拒绝
    static let timestampWindowMs: TimeInterval = 5 * 60 * 1000 // 5 分钟

    // MARK: - 数据结构

    /// 安全请求头
    struct SecurityHeaders {
        /// 客户端 ID
        let clientId: String
        /// 请求时间戳（毫秒）
        let requestTime: String
        /// 请求随机数
        let requestNonce: String
        /// 请求签名（Base64）
        let requestSign: String
        /// 签名算法
        let signAlgorithm: String
        /// 完整性等级
        let integrityLevel: String

        /// 转换为 HTTP 请求头字典
        var httpHeaders: [String: String] {
            return [
                "X-Client-ID": clientId,
                "X-Request-Time": requestTime,
                "X-Request-Nonce": requestNonce,
                "X-Request-Sign": requestSign,
                "X-Sign-Algorithm": signAlgorithm,
                "X-Integrity-Level": integrityLevel,
            ]
        }
    }

    // MARK: - 签名生成

    /// 为 HTTP 请求生成安全头
    ///
    /// 如果客户端尚未注册，返回 nil（不影响请求正常发送）。
    ///
    /// - Parameters:
    ///   - method: HTTP 方法（GET/POST/PUT/DELETE/PATCH）
    ///   - path: 请求路径（如 /api/v1/business/user/info）
    ///   - body: 请求体数据（可选）
    /// - Returns: 安全头，如果客户端未注册则返回 nil
    func signRequest(
        method: String,
        path: String,
        body: Data? = nil
    ) async -> SecurityHeaders? {
        // 检查客户端是否已注册
        guard let identity = DeviceIdentityManager.shared.getCurrentIdentity(),
              identity.isActive else {
            return nil
        }

        let timestamp = String(Int64(Date().timeIntervalSince1970 * 1000))
        let nonce = UUID().uuidString

        // 计算请求体 SHA-256 哈希
        let bodyHash = computeBodyHash(body)

        // 构造签名负载
        let signingPayload = buildSigningPayload(
            method: method.uppercased(),
            path: path,
            clientId: identity.clientId,
            timestamp: timestamp,
            nonce: nonce,
            bodyHash: bodyHash
        )

        // 使用设备私钥签名
        do {
            let signResult = try SecureEnclaveManager.shared.signRaw(data: Data(signingPayload.utf8))

            return SecurityHeaders(
                clientId: identity.clientId,
                requestTime: timestamp,
                requestNonce: nonce,
                requestSign: signResult.signature,
                signAlgorithm: signAlgorithm,
                integrityLevel: identity.integrityLevel
            )
        } catch {
            print("[RequestSigning] 签名失败: \(error.localizedDescription)")
            return nil
        }
    }

    /// 判断客户端是否已注册（用于前端判断是否显示注册引导）
    func isClientRegistered() -> Bool {
        return DeviceIdentityManager.shared.isRegistered()
    }

    /// 获取客户端完整性等级
    func getClientIntegrityLevel() -> String {
        return DeviceIdentityManager.shared.getCurrentIdentity()?.integrityLevel ?? "medium"
    }

    // MARK: - 私有方法

    /// 计算请求体的 SHA-256 哈希
    private func computeBodyHash(_ body: Data?) -> String {
        guard let body = body, !body.isEmpty else {
            return ""
        }

        let hash = SHA256.hash(data: body)
        return Data(hash).hexString
    }

    /// 构造签名负载
    ///
    /// 格式: METHOD + "\n" + PATH + "\n" + CLIENT_ID + "\n" + TIMESTAMP + "\n" + NONCE + "\n" + BODY_HASH
    private func buildSigningPayload(
        method: String,
        path: String,
        clientId: String,
        timestamp: String,
        nonce: String,
        bodyHash: String
    ) -> String {
        return [
            method,
            path,
            clientId,
            timestamp,
            nonce,
            bodyHash,
        ].joined(separator: "\n")
    }
}

// MARK: - Data 扩展（复用 CryptoService 中定义的 hexString）
