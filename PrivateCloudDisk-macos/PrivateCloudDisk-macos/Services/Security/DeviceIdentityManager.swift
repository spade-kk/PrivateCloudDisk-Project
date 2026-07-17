import Foundation
import CryptoKit
import Combine

// MARK: - 设备身份管理器

/// 客户端设备身份生命周期管理服务
///
/// 职责：
///   - 管理客户端实例的完整生命周期
///   - 客户端 ID 持久化存储（Keychain）
///   - 首次注册流程编排
///   - 密钥轮换支持
///   - 身份状态管理
///
/// 对应 建议6.md 中的「客户端身份认证体系构建」要求。
///
/// 生命周期：
///   首次启动 → 生成密钥对 → 获取挑战值 → 生成证明 → 注册到服务器
///         → 获取 client_id → 持久化 → 后续请求携带 client_id
///
/// 与后端 Client Registration Service 的交互：
///   POST /api/v1/client/register-challenge — 获取挑战值
///   POST /api/v1/client/register           — 首次注册
///   POST /api/v1/client/rotate-key          — 密钥轮换
///   DELETE /api/v1/client/:id               — 注销设备
///   GET /api/v1/client/:id/status           — 查询状态
final class DeviceIdentityManager: ObservableObject {

    // MARK: - 单例

    static let shared = DeviceIdentityManager()

    private init() {
        // 启动时加载缓存的身份
        _cachedIdentity = loadIdentityFromKeychain()
    }

    // MARK: - 发布属性（供 UI 观察自动注册状态）

    /// 当前注册状态
    @Published var autoRegStatus: RegistrationStatus = .unregistered
    /// 是否正在执行注册
    @Published var isAutoRegistering = false
    /// 注册进度消息
    @Published var autoRegProgressMessage: String = ""

    /// 服务器基础 URL
    private var serverBaseURL: String {
         "http://localhost:8080"
    }

    // MARK: - 数据结构

    /// 客户端身份
    struct ClientIdentity: Codable {
        /// 服务端分配的唯一客户端 ID
        var clientId: String
        /// 设备指纹
        var deviceId: String
        /// 平台标识
        let platform: String
        /// 应用标识
        let appId: String
        /// 密钥算法
        let keyAlgorithm: String
        /// 密钥存储位置
        var tokenId: String
        /// 完整性等级
        var integrityLevel: String
        /// 注册时间戳
        var registeredAt: Int
        /// 最后验证时间
        var lastVerifiedAt: Int
        /// 状态
        var status: IdentityStatus
        /// 公钥指纹（SHA-256）
        var publicKeyFingerprint: String

        /// 是否处于活跃状态
        var isActive: Bool { status == .active }
    }

    /// 身份状态
    enum IdentityStatus: String, Codable {
        case active    // 活跃
        case revoked   // 已吊销
        case pending   // 注册中
        case unknown   // 未知

        init(from decoder: Decoder) throws {
            let container = try decoder.singleValueContainer()
            let rawValue = try container.decode(String.self)
            self = IdentityStatus(rawValue: rawValue) ?? .unknown
        }
    }

    /// 注册状态
    enum RegistrationStatus {
        case unregistered  // 未注册
        case registering   // 注册中
        case registered    // 已注册
        case failed        // 注册失败
        case revoked       // 已吊销
        case noKey         // 密钥不存在
    }

    /// 注册结果
    struct RegistrationResult {
        let success: Bool
        let status: RegistrationStatus
        let clientId: String?
        let integrityLevel: String?
        let tokenId: String?
        let error: String?
        let duration: TimeInterval
    }

    // MARK: - Keychain 存储

    /// Keychain 中存储设备身份的 key
    private let keychainIdentityKey = "device_client_identity"

    /// 内存缓存
    private var _cachedIdentity: ClientIdentity?

    /// 从 Keychain 读取身份
    private func loadIdentityFromKeychain() -> ClientIdentity? {
        guard let data = KeychainManager.shared.readData(key: keychainIdentityKey) else {
            return nil
        }
        do {
            return try JSONDecoder().decode(ClientIdentity.self, from: data)
        } catch {
            print("[DeviceIdentity] 身份解析失败: \(error.localizedDescription)")
            return nil
        }
    }

    /// 保存身份到 Keychain
    private func saveIdentityToKeychain(_ identity: ClientIdentity) {
        do {
            let data = try JSONEncoder().encode(identity)
            KeychainManager.shared.storeData(key: keychainIdentityKey, data: data)
            _cachedIdentity = identity
            print("[DeviceIdentity] 身份已保存: \(identity.clientId)")
        } catch {
            print("[DeviceIdentity] 身份保存失败: \(error.localizedDescription)")
        }
    }

    /// 删除 Keychain 中的身份
    private func deleteIdentityFromKeychain() {
        KeychainManager.shared.delete(key: keychainIdentityKey)
        _cachedIdentity = nil
    }

    // MARK: - 身份查询

    /// 获取当前客户端身份
    func getCurrentIdentity() -> ClientIdentity? {
        if _cachedIdentity == nil {
            _cachedIdentity = loadIdentityFromKeychain()
        }
        return _cachedIdentity
    }

    /// 检查客户端是否已注册
    func isRegistered() -> Bool {
        guard let identity = getCurrentIdentity() else { return false }
        return identity.isActive
    }

    /// 获取当前注册状态
    func getRegistrationStatus() -> RegistrationStatus {
        guard let identity = getCurrentIdentity() else {
            // 检查是否有密钥
            let verifyResult = SecureEnclaveManager.shared.verifyKeyExists()
            return verifyResult.exists ? .unregistered : .noKey
        }

        switch identity.status {
        case .active: return .registered
        case .revoked: return .revoked
        case .pending: return .registering
        case .unknown: return .unregistered
        }
    }

    /// 获取客户端 ID（如果已注册）
    func getClientId() -> String? {
        return getCurrentIdentity()?.clientId
    }

    // MARK: - 身份状态管理

    /// 更新身份状态
    func setStatus(_ status: IdentityStatus) {
        guard var identity = getCurrentIdentity() else { return }
        identity.status = status
        saveIdentityToKeychain(identity)
    }

    /// 更新最后验证时间
    func updateLastVerified() {
        guard var identity = getCurrentIdentity() else { return }
        identity.lastVerifiedAt = Int(Date().timeIntervalSince1970)
        saveIdentityToKeychain(identity)
    }

    /// 刷新缓存
    func refreshCache() {
        _cachedIdentity = loadIdentityFromKeychain()
    }

    // MARK: - 注册流程

    /// 执行完整的客户端注册流程
    ///
    /// 流程：
    ///   1. 确保密钥存在（不存在则生成）
    ///   2. 导出公钥
    ///   3. 从服务器获取挑战值
    ///   4. 生成设备信任证明
    ///   5. 提交注册请求到服务器
    ///   6. 获取 client_id 并持久化
    ///
    /// - Parameters:
    ///   - serverBaseURL: 后端 API 基础 URL
    ///   - onProgress: 进度回调
    /// - Returns: 注册结果
    func performRegistration(
        serverBaseURL: String,
        onProgress: ((String, String) -> Void)? = nil
    ) async -> RegistrationResult {
        let startTime = Date()

        let report = { (step: String, message: String) in
            print("[DeviceIdentity] \(step): \(message)")
            onProgress?(step, message)
        }

        do {
            // 步骤 1: 确保密钥存在
            report("key_check", "正在检查密钥状态...")
            let verifyResult = SecureEnclaveManager.shared.verifyKeyExists()
            if !verifyResult.exists {
                report("key_gen", "正在生成设备密钥对...")
                _ = try SecureEnclaveManager.shared.generateKeyPair()
                report("key_gen", "密钥对已生成 (tokenID: \(SecureEnclaveManager.shared.verifyKeyExists().tokenID))")
            } else {
                report("key_check", "密钥已存在 (tokenID: \(verifyResult.tokenID))")
            }

            // 步骤 2: 导出公钥
            report("pubkey", "正在导出公钥...")
            let keyInfo = try SecureEnclaveManager.shared.exportPublicKey()
            report("pubkey", "公钥已导出")

            // 步骤 3: 获取挑战值
            report("challenge", "正在获取服务器挑战值...")
            let challenge = try await fetchChallenge(serverBaseURL: serverBaseURL, publicKey: keyInfo.publicKey)
            report("challenge", "挑战值已获取")

            // 步骤 4: 生成设备信任证明
            report("attest", "正在生成设备信任证明...")
            let attestation = try await DeviceAttestationService.shared.generateAttestation(
                challenge: challenge,
                clientId: nil
            )
            report("attest", "证明已生成 (integrity: \(attestation.integrityLevel))")

            // 步骤 5: 本地预检
            let localValid = DeviceAttestationService.shared.verifyAttestationLocally(attestation)
            if !localValid {
                report("verify", "本地验证未通过，但继续提交")
            }

            // 步骤 6: 提交注册请求
            report("register", "正在提交注册请求到服务器...")
            let registrationResponse = try await submitRegistration(
                serverBaseURL: serverBaseURL,
                attestation: attestation
            )
            report("register", "服务器注册成功")

            // 步骤 7: 保存身份
            let identity = ClientIdentity(
                clientId: registrationResponse.clientId,
                deviceId: attestation.deviceId,
                platform: attestation.platform,
                appId: attestation.appId,
                keyAlgorithm: "ECDSA-P256",
                tokenId: keyInfo.tokenID,
                integrityLevel: attestation.integrityLevel,
                registeredAt: Int(Date().timeIntervalSince1970),
                lastVerifiedAt: Int(Date().timeIntervalSince1970),
                status: .active,
                publicKeyFingerprint: Data(SHA256.hash(data: Data(keyInfo.publicKey.utf8))).hexString
            )

            saveIdentityToKeychain(identity)

            let duration = Date().timeIntervalSince(startTime)
            report("done", "注册完成 (耗时 \(String(format: "%.1f", duration * 1000))ms)")

            return RegistrationResult(
                success: true,
                status: .registered,
                clientId: identity.clientId,
                integrityLevel: identity.integrityLevel,
                tokenId: identity.tokenId,
                error: nil,
                duration: duration
            )

        } catch {
            let duration = Date().timeIntervalSince(startTime)
            print("[DeviceIdentity] 注册失败: \(error.localizedDescription)")

            return RegistrationResult(
                success: false,
                status: .failed,
                clientId: nil,
                integrityLevel: nil,
                tokenId: nil,
                error: error.localizedDescription,
                duration: duration
            )
        }
    }

    // MARK: - 自动注册触发（三个条件检查 + 编排）

    /// 检查是否需要触发自动注册（基于三个触发条件）
    ///
    /// 触发条件：
    ///   1. 应用首次运行 → 本地无设备 ID 且无注册身份
    ///   2. 本地缓存中未找到设备 ID → Keychain 中无 device_id
    ///   3. 安全模块中不存在设备公钥或私钥 → SecureEnclave 中无密钥对
    ///
    /// - Returns: 是否需要注册 + 触发原因列表
    func checkAutoRegistrationNeeded() -> (needed: Bool, reasons: [String]) {
        var reasons: [String] = []

        // 条件 1 & 2: 检查设备 ID 是否存在
        let deviceIdExists = KeychainManager.shared.read(key: "device_id") != nil
        if !deviceIdExists {
            reasons.append("本地缓存中未找到设备ID")
        }

        // 检查是否已有注册身份
        let hasIdentity = getCurrentIdentity() != nil
        if !hasIdentity && !deviceIdExists {
            reasons.append("应用首次运行，无注册信息")
        }

        // 条件 3: 检查安全模块中是否存在密钥对
        let keyResult = SecureEnclaveManager.shared.verifyKeyExists()
        if !keyResult.exists {
            reasons.append("安全模块中不存在设备密钥对（公钥/私钥）")
        }

        return (needed: !reasons.isEmpty, reasons: reasons)
    }

    /// 检查并在需要时触发自动注册
    ///
    /// 在以下时机调用：
    ///   - AppDelegate.applicationDidFinishLaunching（应用启动时）
    ///   - AuthService 登录/注册/会话恢复成功后
    ///
    /// - Returns: 注册结果，无需注册时返回 nil
    @discardableResult
    func triggerAutoRegistrationIfNeeded() async -> RegistrationResult? {
        let (needed, reasons) = checkAutoRegistrationNeeded()

        guard needed else {
            print("[DeviceIdentity] 无需自动注册，当前状态: \(getRegistrationStatus())")
            autoRegStatus = getRegistrationStatus()
            return nil
        }

        print("[DeviceIdentity] 触发自动注册，原因: \(reasons.joined(separator: ", "))")

        // 确保设备 ID 存在
        if KeychainManager.shared.read(key: "device_id") == nil {
            let deviceId = DeviceAttestationService.shared.getDeviceFingerprint()
            KeychainManager.shared.store(key: "device_id", value: deviceId)
            print("[DeviceIdentity] 设备 ID 已生成: \(deviceId.prefix(16))...")
        }

        autoRegStatus = .registering
        isAutoRegistering = true
        autoRegProgressMessage = "正在准备自动注册..."

        let result = await performRegistration(serverBaseURL: serverBaseURL) { [weak self] step, message in
            DispatchQueue.main.async {
                self?.autoRegProgressMessage = "[\(step)] \(message)"
            }
        }

        isAutoRegistering = false
        autoRegStatus = result.success ? .registered : .failed

        if result.success {
            autoRegProgressMessage = "设备注册完成"
            print("[DeviceIdentity] 自动注册成功: clientId=\(result.clientId ?? "unknown")")
        } else {
            autoRegProgressMessage = "设备注册失败: \(result.error ?? "未知错误")"
            print("[DeviceIdentity] 自动注册失败: \(result.error ?? "未知错误")")
        }

        return result
    }

    // MARK: - 服务器通信

    /// 从服务器获取挑战值
    private func fetchChallenge(serverBaseURL: String, publicKey: String) async throws -> String {
        guard let url = URL(string: "\(serverBaseURL)/api/v1/client/register-challenge") else {
            throw DeviceIdentityError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = 15

        let body: [String: Any] = [
            "platform": "macOS",
            "public_key": publicKey,
            "key_algorithm": "ECDSA-P256",
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, response) = try await URLSession.shared.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse,
              (200...299).contains(httpResponse.statusCode) else {
            throw DeviceIdentityError.challengeRequestFailed
        }

        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let responseData = json["data"] as? [String: Any],
              let challenge = responseData["challenge"] as? String else {
            // 服务器未返回挑战值，使用本地 UUID
            return UUID().uuidString
        }

        return challenge
    }

    /// 提交注册请求到服务器
    private func submitRegistration(
        serverBaseURL: String,
        attestation: DeviceAttestationService.AttestationObject
    ) async throws -> RegistrationResponse {
        guard let url = URL(string: "\(serverBaseURL)/api/v1/client/register") else {
            throw DeviceIdentityError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = 30

        let attestationData = try JSONEncoder().encode(attestation)
        let body: [String: Any] = [
            "attestation": try JSONSerialization.jsonObject(with: attestationData),
            "platform": "macOS",
            "app_version": Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0",
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        
        if let jsonData = request.httpBody,
           let jsonString = String(data: jsonData, encoding: .utf8) {
            print("📤 [DeviceIdentity] 注册请求体 JSON:\n\(jsonString)")
        }

        let (data, response) = try await URLSession.shared.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw DeviceIdentityError.registrationFailed("无效的响应")
        }

        guard (200...299).contains(httpResponse.statusCode) else {
            let errorMsg = (try? JSONSerialization.jsonObject(with: data) as? [String: Any])?["message"] as? String
                ?? "服务器返回错误: HTTP \(httpResponse.statusCode)"
            throw DeviceIdentityError.registrationFailed(errorMsg)
        }

        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        let responseData = json?["data"] as? [String: Any]

        guard let clientId = responseData?["client_id"] as? String ?? responseData?["clientId"] as? String else {
            throw DeviceIdentityError.registrationFailed("服务器未返回 client_id")
        }

        return RegistrationResponse(clientId: clientId)
    }

    /// 服务器注册响应
    private struct RegistrationResponse {
        let clientId: String
    }

    // MARK: - 密钥轮换

    /// 刷新客户端注册（密钥轮换后重新注册）
    ///
    /// - Parameter serverBaseURL: 后端 API 基础 URL
    /// - Returns: 注册结果
    func refreshRegistration(serverBaseURL: String) async -> RegistrationResult {
        print("[DeviceIdentity] 执行密钥轮换并重新注册...")

        // 删除旧密钥
        try? SecureEnclaveManager.shared.deleteKeyPair()

        // 删除旧身份
        deleteIdentityFromKeychain()

        // 重新注册
        return await performRegistration(serverBaseURL: serverBaseURL)
    }

    // MARK: - 注销

    /// 注销客户端
    ///
    /// 清除本地密钥和身份信息。
    func decommission(serverBaseURL: String? = nil) async {
        print("[DeviceIdentity] 注销客户端...")

        // 通知服务器（如果提供了 URL）
        if let baseURL = serverBaseURL, let clientId = getClientId() {
            do {
                var request = URLRequest(
                    url: URL(string: "\(baseURL)/api/v1/client/\(clientId)")!
                )
                request.httpMethod = "DELETE"
                request.timeoutInterval = 10
                let _ = try await URLSession.shared.data(for: request)
            } catch {
                print("[DeviceIdentity] 服务器注销通知失败: \(error.localizedDescription)")
            }
        }

        // 清除本地数据
        try? SecureEnclaveManager.shared.deleteKeyPair()
        deleteIdentityFromKeychain()

        print("[DeviceIdentity] 注销完成")
    }
}

// MARK: - 设备身份错误

enum DeviceIdentityError: LocalizedError {
    case invalidURL
    case challengeRequestFailed
    case registrationFailed(String)
    case keyNotFound
    case securityModuleUnavailable

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "无效的 URL"
        case .challengeRequestFailed:
            return "获取挑战值失败"
        case .registrationFailed(let msg):
            return "注册失败: \(msg)"
        case .keyNotFound:
            return "设备密钥不存在"
        case .securityModuleUnavailable:
            return "安全模块不可用"
        }
    }
}

// MARK: - Data 扩展（复用 CryptoService 中定义的 hexString）
