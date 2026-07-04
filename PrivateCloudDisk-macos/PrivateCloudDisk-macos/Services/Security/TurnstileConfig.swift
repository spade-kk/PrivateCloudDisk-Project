import Foundation

// MARK: - Cloudflare Turnstile 配置

/// Turnstile 人机验证配置
///
/// 与 Web 前端 .env 文件中的 Turnstile 配置对齐：
/// - 开发环境: 0x4AAAAAADf7cwlBLsuoXeH-
/// - 生产环境: 0x4AAAAAADf7Z44g-izJGKIB
enum TurnstileConfig {

    /// Turnstile Site Key（与 Web 前端 VITE_TURNSTILE_SITE_KEY 一致）
    #if DEBUG
    static let siteKey = "0x4AAAAAADf7cwlBLsuoXeH-"
    #else
    static let siteKey = "0x4AAAAAADf7Z44g-izJGKIB"
    #endif

    /// Turnstile JS API 地址
    static let scriptURL = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit"

    /// 验证 action 标识（与平台服务 captcha_action 对应）
    static let action = "login"

    /// 主题
    static let theme = "light"

    /// 尺寸
    static let size = "flexible"

    /// 执行模式（主动调用 execute）
    static let execution = "execute"

    /// 外观模式（interaction-only: 无感验证，用户无感知）
    static let appearance = "interaction-only"
}
