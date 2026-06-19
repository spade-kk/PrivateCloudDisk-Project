import Foundation
import ServiceManagement

// MARK: - 开机启动管理器

/// 登录项（开机启动）管理器
///
/// 使用 SMAppService (macOS 13+) 管理登录项
/// 对比 Windows 注册表 Run 键的方式，macOS 使用 Service Management 框架更安全
final class LoginItemManager {

    private let service = SMAppService.mainApp

    /// 是否启用开机启动
    var isEnabled: Bool {
        service.status == .enabled
    }

    /// 启用开机启动
    func enable() throws {
        try service.register()
    }

    /// 禁用开机启动
    func disable() throws {
        try service.unregister()
    }

    /// 切换开机启动状态
    func toggle() throws {
        if isEnabled {
            try disable()
        } else {
            try enable()
        }
    }
}