import Foundation

// MARK: - 配额服务

/// 用户存储配额服务
/// 参考 Vue 3 Web 项目 API 规范（business/quotas/me）
final class QuotaService {

    static let shared = QuotaService()
    private let api = APIClient.shared

    private init() {}

    /// 获取当前用户的存储配额信息
    func getMyQuotaInfo() async throws -> QuotaInfo {
        return try await api.get("business/quotas/me")
    }
}