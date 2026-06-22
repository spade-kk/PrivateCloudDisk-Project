//
//  QRScanResultRouter.swift
//  PrivateCloudDisk-ios
//
//  扫码结果智能识别与路由
//  根据扫描内容自动判断类型并导航到对应页面
//

import Foundation

// MARK: - 扫码结果类型

enum QRScanResultType: Hashable {
    /// 设备授权登录
    /// URL 示例: https://clouddrive.example.com/device/authorize?user_code=KD8X-2P9A&device_token=xxx
    case deviceAuth(userCode: String, deviceToken: String?, url: String)

    /// 文件/分享链接
    /// URL 示例: https://clouddrive.example.com/share/s/7f3a8b2c1d?token=xxx
    case shareLink(shareId: String, token: String?, url: String)

    /// 好友个人二维码
    /// URL 示例: https://clouddrive.example.com/user/u_abc123 / clouddrive://user/u_abc123
    case friendProfile(userId: String, nickname: String?, url: String)

    /// 普通 URL
    case url(String)

    /// 纯文本（非 URL）
    case text(String)
}

// MARK: - 扫码结果路由器

struct QRScanResultRouter {

    /// 解析扫描到的原始字符串，返回对应的结果类型
    static func parse(_ raw: String) -> QRScanResultType {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)

        // 判断是否为有效 URL
        guard let urlComponents = URLComponents(string: trimmed),
              let host = urlComponents.host?.lowercased() else {
            // 纯文本
            return .text(trimmed)
        }

        let path = urlComponents.path.lowercased()
        let params = parseQueryParams(urlComponents.queryItems)

        // ── 1. 设备授权链接 ──
        // 匹配: /device/authorize 或 /device/auth
        if path.contains("/device/authorize") || path.contains("/device/auth") {
            if let userCode = params["user_code"] {
                return .deviceAuth(
                    userCode: userCode,
                    deviceToken: params["device_token"],
                    url: trimmed
                )
            }
        }

        // ── 2. 文件分享链接 ──
        // 匹配: /share/s/{shareId} 或 /s/{shareId}
        if let shareId = extractShareId(from: path) {
            return .shareLink(
                shareId: shareId,
                token: params["token"],
                url: trimmed
            )
        }

        // ── 3. 好友个人二维码 ──
        // 匹配: /user/{userId} 或 /u/{userId} 或 /friend/{userId}
        // 也支持自定义 scheme: clouddrive://user/{userId}
        if let userId = extractUserId(from: path, params: params) {
            return .friendProfile(
                userId: userId,
                nickname: params["nickname"] ?? params["name"],
                url: trimmed
            )
        }

        // ── 4. 普通 URL ──
        return .url(trimmed)
    }

    // MARK: - 辅助方法

    private static func parseQueryParams(_ items: [URLQueryItem]?) -> [String: String] {
        guard let items = items else { return [:] }
        var dict: [String: String] = [:]
        for item in items {
            if let value = item.value {
                dict[item.name] = value
            }
        }
        return dict
    }

    /// 从路径中提取分享 ID
    /// 匹配: /share/s/{id} 或 /s/{id}
    private static func extractShareId(from path: String) -> String? {
        let patterns = [
            #"/share/s/([a-zA-Z0-9_-]+)"#,
            #"/s/([a-zA-Z0-9_-]+)"#,
        ]
        for pattern in patterns {
            if let regex = try? NSRegularExpression(pattern: pattern, options: []),
               let match = regex.firstMatch(in: path, options: [], range: NSRange(path.startIndex..., in: path)),
               let range = Range(match.range(at: 1), in: path) {
                return String(path[range])
            }
        }
        return nil
    }

    /// 从路径中提取用户 ID
    /// 匹配: /user/{id}, /u/{id}, /friend/{id}, /profile/{id}
    private static func extractUserId(from path: String, params: [String: String]) -> String? {
        let patterns = [
            #"/user/([a-zA-Z0-9_]+)"#,
            #"/u/([a-zA-Z0-9_]+)"#,
            #"/friend/([a-zA-Z0-9_]+)"#,
            #"/profile/([a-zA-Z0-9_]+)"#,
        ]
        for pattern in patterns {
            if let regex = try? NSRegularExpression(pattern: pattern, options: []),
               let match = regex.firstMatch(in: path, options: [], range: NSRange(path.startIndex..., in: path)),
               let range = Range(match.range(at: 1), in: path) {
                return String(path[range])
            }
        }
        // 也支持 query 参数: ?user_id=xxx
        if let uid = params["user_id"] ?? params["uid"] {
            return uid
        }
        return nil
    }
}