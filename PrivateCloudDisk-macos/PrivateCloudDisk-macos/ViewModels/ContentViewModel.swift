import Foundation
import Combine
import SwiftUI

// MARK: - 主内容视图模型

/// 应用主视图模型
///
/// 管理应用的整体导航状态和全局数据
@MainActor
final class ContentViewModel: ObservableObject {

    // MARK: - 导航状态

    enum NavigationTab: String, CaseIterable {
        case home = "我的文件"
        case favorites = "收藏"
        case trash = "回收站"
        case virtualDisk = "虚拟磁盘"
        case settings = "设置"

        var sfSymbol: String {
            switch self {
            case .home: return "folder.fill"
            case .favorites: return "star.fill"
            case .trash: return "trash.fill"
            case .virtualDisk: return "externaldrive.fill"
            case .settings: return "gearshape.fill"
            }
        }
    }

    @Published var selectedTab: NavigationTab = .home
    @Published var isSidebarVisible = true
    @Published var showSettings = false

    // MARK: - 认证状态

    @Published var isAuthenticated = false
    @Published var isLoading = true

    // MARK: - 全局状态

    @Published var currentPath: [FileNode] = [] // 面包屑导航
    @Published var searchQuery = ""
    @Published var isSearching = false
    @Published var toastMessage: String?
    @Published var toastType: ToastType = .info

    // MARK: - 设备自动注册状态

    /// 设备注册状态
    @Published var deviceRegistrationStatus: DeviceIdentityManager.RegistrationStatus = .unregistered
    /// 是否正在注册
    @Published var isDeviceRegistering = false
    /// 注册进度消息
    @Published var deviceRegistrationProgress: String = ""

    private var cancellables = Set<AnyCancellable>()

    init() {
        setupBindings()
    }

    private func setupBindings() {
        AuthService.shared.$isAuthenticated
            .receive(on: DispatchQueue.main)
            .assign(to: &$isAuthenticated)

        // 观察设备自动注册状态
        let regManager = DeviceIdentityManager.shared
        regManager.$autoRegStatus
            .receive(on: DispatchQueue.main)
            .assign(to: &$deviceRegistrationStatus)
        regManager.$isAutoRegistering
            .receive(on: DispatchQueue.main)
            .assign(to: &$isDeviceRegistering)
        regManager.$autoRegProgressMessage
            .receive(on: DispatchQueue.main)
            .assign(to: &$deviceRegistrationProgress)
    }

    // MARK: - Toast 通知

    enum ToastType {
        case info, success, warning, error
    }

    func showToast(_ message: String, type: ToastType = .info) {
        toastMessage = message
        toastType = type
        DispatchQueue.main.asyncAfter(deadline: .now() + 3) { [weak self] in
            self?.toastMessage = nil
        }
    }
}