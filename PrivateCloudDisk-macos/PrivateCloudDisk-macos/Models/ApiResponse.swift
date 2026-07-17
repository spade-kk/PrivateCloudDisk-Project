import Foundation

// MARK: - 通用 API 响应（与后端 JsonResult 完全对齐）

/// 通用 API 响应模型
/// 与后端 org.project.control.result.JsonResult 完全对齐
struct ApiResponse<T: Decodable>: Decodable {
    let code: Int
    let message: String?
    let data: T?

    var isSuccess: Bool { code == 200 }
}

/// 分页响应（与后端 PageResultVO 对齐）
struct PaginatedResponse<T: Decodable>: Decodable {
    let code: Int
    let message: String
    let data: PaginatedData<T>?
}

struct PaginatedData<T: Decodable>: Decodable {
    let items: [T]
    let total: Int
    let page: Int
    let pageSize: Int
    let totalPages: Int
}

/// 空响应（某些 API 只返回 code/message）
struct EmptyResponse: Codable {
    let code: Int
    let message: String
}

/// 空请求体
struct EmptyBody: Codable {}

// MARK: - API 错误

enum ApiError: LocalizedError, Equatable {
    case networkError(String)
    case unauthorized
    case serverError(Int, String)
    case decodingError(String)
    case invalidURL
    case cancelled

    var errorDescription: String? {
        switch self {
        case .networkError(let msg): return "网络错误: \(msg)"
        case .unauthorized: return "认证已过期，请重新登录"
        case .serverError(let code, let msg): return "服务器错误 (\(code)): \(msg)"
        case .decodingError(let msg): return "数据解析错误: \(msg)"
        case .invalidURL: return "无效的 URL"
        case .cancelled: return "操作已取消"
        }
    }
}