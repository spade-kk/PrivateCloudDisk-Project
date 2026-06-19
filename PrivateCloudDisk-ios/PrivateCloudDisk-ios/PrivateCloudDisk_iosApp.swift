//
//  PrivateCloudDisk_iosApp.swift
//  PrivateCloudDisk-ios
//
//  App 入口 — 应用生命周期管理
//  利用 iOS 原生特性：
//    - ScenePhase 监听应用生命周期
//    - BGTaskScheduler 后台任务
//    - 应用启动时恢复登录状态
//

import SwiftUI
import BackgroundTasks

@main
struct PrivateCloudDisk_iosApp: App {
    @StateObject private var authVM = AuthViewModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(authVM)
                .onChange(of: scenePhase) { _, newPhase in
                    switch newPhase {
                    case .active:
                        onAppBecomeActive()
                    case .background:
                        onAppEnterBackground()
                    case .inactive:
                        break
                    @unknown default:
                        break
                    }
                }
        }
    }

    // MARK: - 生命周期

    private func onAppBecomeActive() {
        // 恢复 WebSocket 连接
        if authVM.isLoggedIn {
            WebSocketClient.shared.reconnect()
        }
    }

    private func onAppEnterBackground() {
        // 调度后台任务
        BackgroundTaskManager.shared.scheduleFileSync()
        BackgroundTaskManager.shared.scheduleMessageSync()
    }
}