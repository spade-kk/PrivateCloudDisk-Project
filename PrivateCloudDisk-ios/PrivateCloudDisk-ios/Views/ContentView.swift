//
//  ContentView.swift
//  PrivateCloudDisk-ios
//
//  主容器视图 — 根据登录状态显示登录页或主界面
//  主界面使用 TabView + 自定义 TabBar
//

import SwiftUI

struct ContentView: View {
    @StateObject private var authVM = AuthViewModel()

    var body: some View {
        Group {
            if authVM.isLoggedIn {
                MainTabView()
                    .environmentObject(authVM)
            } else {
                LoginView()
            }
        }
        .onAppear {
            BackgroundTaskManager.shared.registerAllTasks()
        }
    }
}

// MARK: - 主 Tab 界面

struct MainTabView: View {
    @EnvironmentObject var authVM: AuthViewModel
    @State private var selectedTab: Tab = .files

    enum Tab: String, CaseIterable {
        case files, starred, shares, messages, profile

        var icon: String {
            switch self {
            case .files: return "folder"
            case .starred: return "star"
            case .shares: return "link"
            case .messages: return "message"
            case .profile: return "person"
            }
        }

        var selectedIcon: String {
            switch self {
            case .files: return "folder.fill"
            case .starred: return "star.fill"
            case .shares: return "link"
            case .messages: return "message.fill"
            case .profile: return "person.fill"
            }
        }

        var title: String {
            switch self {
            case .files: return "文件"
            case .starred: return "收藏"
            case .shares: return "分享"
            case .messages: return "消息"
            case .profile: return "我的"
            }
        }
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            TabView(selection: $selectedTab) {
                FileBrowserView()
                    .tag(Tab.files)

                StarredView()
                    .tag(Tab.starred)

                SharesView()
                    .tag(Tab.shares)

                MessagesView()
                    .tag(Tab.messages)

                ProfileView()
                    .tag(Tab.profile)
            }

            // 自定义 TabBar
            VStack(spacing: 0) {
                Divider()
                HStack(spacing: 0) {
                    ForEach(Tab.allCases, id: \.self) { tab in
                        Button(action: {
                            withAnimation(.easeInOut(duration: 0.2)) {
                                selectedTab = tab
                            }
                        }) {
                            VStack(spacing: 4) {
                                Image(systemName: selectedTab == tab ? tab.selectedIcon : tab.icon)
                                    .font(.system(size: 20))
                                    .symbolEffect(.bounce, value: selectedTab == tab)
                                Text(tab.title)
                                    .font(.system(size: 10))
                            }
                            .foregroundStyle(selectedTab == tab ? Color.blue : Color.gray)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                        }
                    }
                }
                .padding(.bottom, 1)
                .background(.bar)
            }
        }
    }
}

// MARK: - 个人中心

struct ProfileView: View {
    @EnvironmentObject var authVM: AuthViewModel

    var body: some View {
        NavigationStack {
            List {
                // 用户信息
                Section {
                    HStack(spacing: 16) {
                        if let user = authVM.user {
                            Text(user.initial)
                                .font(.title.bold())
                                .frame(width: 60, height: 60)
                                .background(Color.blue.opacity(0.1))
                                .foregroundColor(.blue)
                                .clipShape(Circle())
                        } else {
                            Image(systemName: "person.circle.fill")
                                .font(.system(size: 60))
                                .foregroundStyle(.blue)
                        }

                        VStack(alignment: .leading, spacing: 4) {
                            if let user = authVM.user {
                                Text(user.displayName)
                                    .font(.headline)
                                Text(user.account)
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                    .padding(.vertical, 8)
                }

                // 设置
                Section("设置") {
                    NavigationLink(destination: Text("存储管理")) {
                        Label("存储管理", systemImage: "externaldrive")
                    }
                    NavigationLink(destination: Text("缓存管理")) {
                        Label("清除缓存", systemImage: "trash")
                    }
                    NavigationLink(destination: Text("自动备份")) {
                        Label("自动备份", systemImage: "icloud.and.arrow.up")
                    }
                    NavigationLink(destination: Text("安全设置")) {
                        Label("安全设置", systemImage: "lock.shield")
                    }
                }

                // iOS 原生特性
                Section("功能") {
                    Toggle(isOn: .constant(true)) {
                        Label("Face ID 解锁", systemImage: "faceid")
                    }
                    Toggle(isOn: .constant(true)) {
                        Label("后台自动同步", systemImage: "arrow.triangle.2.circlepath")
                    }
                    NavigationLink(destination: Text("小组件设置")) {
                        Label("桌面小组件", systemImage: "square.grid.2x2")
                    }
                }

                Section("关于") {
                    LabeledContent("版本", value: "1.0.0")
                    LabeledContent("构建", value: "1")
                }

                // 退出登录
                Section {
                    Button("退出登录", role: .destructive) {
                        Task { await authVM.logout() }
                    }
                }
            }
            .navigationTitle("我的")
        }
    }
}

#Preview {
    ContentView()
}