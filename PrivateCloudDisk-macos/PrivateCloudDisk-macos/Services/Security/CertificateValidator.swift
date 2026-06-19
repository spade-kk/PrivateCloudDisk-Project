import Foundation
import Security

/// TLS 证书验证器
///
/// 增强 HTTPS 连接安全性：
/// - 证书链验证
/// - 证书固定（Certificate Pinning）
/// - 自签名证书支持（开发环境）
final class CertificateValidator: NSObject, URLSessionDelegate {

    static let shared = CertificateValidator()

    // MARK: - 证书固定

    /// 已知的服务器公钥哈希（SHA-256）
    private let pinnedPublicKeyHashes: Set<String> = [
        // 生产环境公钥哈希（部署时需要替换为实际值）
        // "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    ]

    /// 是否启用证书固定
    private var isPinningEnabled: Bool {
        #if DEBUG
        return false
        #else
        return !pinnedPublicKeyHashes.isEmpty
        #endif
    }

    // MARK: - URLSessionDelegate

    func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              let serverTrust = challenge.protectionSpace.serverTrust else {
            completionHandler(.performDefaultHandling, nil)
            return
        }

        // 1. 验证证书链
        var error: CFError?
        let isValid = SecTrustEvaluateWithError(serverTrust, &error)

        if !isValid {
            print("[CertificateValidator] 证书链验证失败: \(error?.localizedDescription ?? "unknown")")
            completionHandler(.cancelAuthenticationChallenge, nil)
            return
        }

        // 2. 证书固定（生产环境）
        if isPinningEnabled {
            guard let certificate = SecTrustGetCertificateAtIndex(serverTrust, 0) else {
                completionHandler(.cancelAuthenticationChallenge, nil)
                return
            }

            let publicKey = SecCertificateCopyKey(certificate)
            guard let publicKeyData = SecKeyCopyExternalRepresentation(publicKey!, nil) as? Data else {
                completionHandler(.cancelAuthenticationChallenge, nil)
                return
            }

            let publicKeyHash = SHA256.hash(data: publicKeyData)
            let hashString = Data(publicKeyHash).base64EncodedString()

            if !pinnedPublicKeyHashes.contains(hashString) {
                print("[CertificateValidator] 公钥固定验证失败")
                completionHandler(.cancelAuthenticationChallenge, nil)
                return
            }
        }

        // 3. 验证主机名
        let host = challenge.protectionSpace.host
        let policy = SecPolicyCreateSSL(true, host as CFString)
        SecTrustSetPolicies(serverTrust, policy)

        var trustError: CFError?
        if SecTrustEvaluateWithError(serverTrust, &trustError) {
            completionHandler(.useCredential, URLCredential(trust: serverTrust))
        } else {
            print("[CertificateValidator] 主机名验证失败: \(trustError?.localizedDescription ?? "unknown")")
            completionHandler(.cancelAuthenticationChallenge, nil)
        }
    }
}

import CryptoKit