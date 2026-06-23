//
//  MessagesView.swift
//  PrivateCloudDisk-ios
//
//  消息中心 — 企业级即时通讯界面
//  设计参考：QQ / 微信 聊天界面，主打简约现代风格
//  整体色调与客户端设计系统保持一致（深邃蓝 #2563EB）
//
//  功能特性：
//    - 会话列表：圆角方头像 + 未读红点/数字徽章 + 滑动操作
//    - 聊天界面：气泡尾巴 + 时间戳分组 + 输入工具栏 + 语音切换
//    - 联系人列表：按字母分组 + 在线状态
//    - 通话界面：全屏渐变 + 控制按钮
//

import SwiftUI

// MARK: - 聊天专用背景色（微信风格浅灰）

extension Color {
    static let chatBg = Color(hex: "#EDEDED")
    static let chatBubbleReceived = Color.white
    static let chatBubbleSent = AppColors.primary
}

// MARK: - 消息中心主视图

struct MessagesView: View {
    @StateObject private var viewModel = MessagesViewModel()
    @State private var showCallView = false
    @State private var callPeerName = ""
    @State private var callType = "video"

    /// 预览模式：跳过网络请求，直接加载 mock 数据
    var previewMode = false

    var body: some View {
        NavigationStack {
            if viewModel.activeConversation == nil {
                conversationList
            } else {
                chatView
            }
        }
        .task {
            if previewMode {
                viewModel.loadPreviewData()
            } else {
                await viewModel.loadConversations()
                await viewModel.loadFriends()
            }
        }
        .onAppear {
            if !previewMode {
                viewModel.connectWebSocket()
            }
        }
        .overlay {
            if viewModel.incomingCall {
                incomingCallOverlay
            }
        }
        .fullScreenCover(isPresented: $showCallView) {
            CallView(
                peerName: callPeerName,
                callType: callType,
                viewModel: viewModel
            )
        }
    }

    // MARK: - 会话列表（微信风格）

    private var conversationList: some View {
        ZStack {
            AppColors.background.ignoresSafeArea()

            if viewModel.conversations.isEmpty {
                AppEmptyState(
                    icon: "message",
                    title: "暂无消息",
                    message: "与好友开始聊天吧"
                )
            } else {
                List {
                    ForEach(viewModel.conversations) { conv in
                        Button(action: { viewModel.openConversation(conv) }) {
                            ConversationRowView(conversation: conv)
                        }
                        .listRowInsets(EdgeInsets(
                            top: 0, leading: AppSpacing.lg,
                            bottom: 0, trailing: AppSpacing.lg
                        ))
                        .listRowSeparatorTint(AppColors.dividerLight)
                        .listRowBackground(Color.clear)
                        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                            Button(role: .destructive) {
                                // 删除会话
                            } label: {
                                Label("删除", systemImage: "trash")
                            }

                            Button {
                                // 标记已读
                            } label: {
                                Label("已读", systemImage: "checkmark.message")
                            }
                            .tint(AppColors.primary)
                        }
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            }
        }
        .navigationTitle("消息")
        .navigationBarTitleDisplayMode(.large)
        .refreshable {
            await viewModel.loadConversations()
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink(destination: friendsList) {
                    Image(systemName: "person.2.fill")
                        .font(.subheadline)
                        .foregroundColor(AppColors.primary)
                        .frame(width: 36, height: 36)
                        .background(AppColors.primaryBg)
                        .clipShape(Circle())
                }
            }
        }
    }

    // MARK: - 聊天视图（微信/QQ 风格）

    private var chatView: some View {
        VStack(spacing: 0) {
            // 聊天导航栏
            chatNavigationBar

            // 消息列表
            chatMessageList

            // 输入工具栏
            ChatInputBar(viewModel: viewModel)
        }
        .background(Color.chatBg)
        .navigationBarBackButtonHidden(true)
        .navigationBarHidden(true)
    }

    // MARK: - 聊天导航栏

    private var chatNavigationBar: some View {
        HStack(spacing: 0) {
            // 返回按钮
            Button(action: { viewModel.activeConversation = nil }) {
                Image(systemName: "chevron.left")
                    .font(.title3.weight(.semibold))
                    .foregroundColor(AppColors.textPrimary)
                    .frame(width: 36, height: 36)
            }

            // 头像 + 名称
            HStack(spacing: AppSpacing.sm) {
                // 头像
                ZStack {
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [AppColors.primaryLight, AppColors.primary],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: 36, height: 36)

                    Text(viewModel.activeConversation?.initial ?? "?")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.white)
                }

                VStack(alignment: .leading, spacing: 1) {
                    Text(viewModel.activeConversation?.title ?? "")
                        .font(AppTypography.subheadline.weight(.semibold))
                        .foregroundColor(AppColors.textPrimary)
                        .lineLimit(1)

                    if viewModel.peerTyping {
                        Text("正在输入...")
                            .font(.system(size: 10))
                            .foregroundColor(AppColors.primary)
                            .transition(.opacity)
                    }
                }
            }

            Spacer()

            // 右侧操作按钮
            HStack(spacing: AppSpacing.xs) {
                // 语音通话
                Button(action: {
                    callType = "voice"
                    callPeerName = viewModel.activeConversation?.title ?? ""
                    Task { await viewModel.startCall(peerId: "", peerName: callPeerName, callType: "voice") }
                    showCallView = true
                }) {
                    Image(systemName: "phone")
                        .font(.subheadline)
                        .foregroundColor(AppColors.textPrimary)
                        .frame(width: 36, height: 36)
                }

                // 视频通话
                Button(action: {
                    callType = "video"
                    callPeerName = viewModel.activeConversation?.title ?? ""
                    Task { await viewModel.startCall(peerId: "", peerName: callPeerName, callType: "video") }
                    showCallView = true
                }) {
                    Image(systemName: "video")
                        .font(.subheadline)
                        .foregroundColor(AppColors.textPrimary)
                        .frame(width: 36, height: 36)
                }

                // 更多菜单
                Menu {
                    Button(action: {}) {
                        Label("搜索聊天记录", systemImage: "magnifyingglass")
                    }
                    Button(action: {}) {
                        Label("置顶聊天", systemImage: "pin")
                    }
                    Button(role: .destructive, action: {}) {
                        Label("清空聊天记录", systemImage: "trash")
                    }
                } label: {
                    Image(systemName: "ellipsis")
                        .font(.subheadline)
                        .foregroundColor(AppColors.textPrimary)
                        .frame(width: 36, height: 36)
                }
            }
        }
        .padding(.horizontal, AppSpacing.md)
        .padding(.top, 48)
        .padding(.bottom, 8)
        .background(
            AppColors.surface
                .shadow(color: .black.opacity(0.04), radius: 4, y: 2)
        )
    }

    // MARK: - 消息列表

    private var chatMessageList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 0) {
                    // 顶部加载更多
                    Color.clear.frame(height: 8)

                    ForEach(groupedMessages, id: \.dateKey) { group in
                        // 时间戳
                        MessageTimeStampView(timeText: group.formattedTime)
                            .padding(.vertical, AppSpacing.md)

                        // 消息气泡
                        ForEach(group.messages) { msg in
                            ChatBubbleRow(
                                message: msg,
                                showAvatar: shouldShowAvatar(for: msg, in: group.messages),
                                onRetry: { Task { await viewModel.retryMessage(msg) } }
                            )
                            .id(msg.id)
                            .padding(.vertical, 2)
                        }
                    }

                    // 底部留白
                    Color.clear.frame(height: 8)
                }
            }
            .defaultScrollAnchor(.bottom)
            .onChange(of: viewModel.messages.count) { _, _ in
                if let last = viewModel.messages.last {
                    withAnimation(.easeOut(duration: 0.15)) {
                        proxy.scrollTo(last.id, anchor: .bottom)
                    }
                }
            }
        }
    }

    // MARK: - 消息分组（按时间间隔 > 3分钟分组）

    private var groupedMessages: [(dateKey: String, formattedTime: String, messages: [ChatMessage])] {
        var groups: [(dateKey: String, formattedTime: String, messages: [ChatMessage])] = []
        let formatter = ISO8601DateFormatter()

        for msg in viewModel.messages {
            let date = formatter.date(from: msg.createdAt) ?? Date()
            let timeKey = formatTimeStamp(date)

            if let last = groups.last,
               let lastDate = formatter.date(from: last.messages.last?.createdAt ?? ""),
               abs(date.timeIntervalSince(lastDate)) < 180 {
                // 同一组，追加
                var updatedMessages = last.messages
                updatedMessages.append(msg)
                groups[groups.count - 1] = (last.dateKey, last.formattedTime, updatedMessages)
            } else {
                // 新组
                groups.append((timeKey, timeKey, [msg]))
            }
        }
        return groups
    }

    private func formatTimeStamp(_ date: Date) -> String {
        let calendar = Calendar.current
        let fmt = DateFormatter()
        if calendar.isDateInToday(date) {
            fmt.dateFormat = "HH:mm"
        } else if calendar.isDateInYesterday(date) {
            return "昨天 \(DateFormatter.localizedString(from: date, dateStyle: .none, timeStyle: .short))"
        } else if calendar.isDate(date, equalTo: Date(), toGranularity: .year) {
            fmt.dateFormat = "MM月dd日 HH:mm"
        } else {
            fmt.dateFormat = "yyyy年MM月dd日 HH:mm"
        }
        return fmt.string(from: date)
    }

    private func shouldShowAvatar(for msg: ChatMessage, in messages: [ChatMessage]) -> Bool {
        // 非自己发送的消息，显示头像
        // 如果是连续同一个人发送，只显示第一个的头像
        guard !msg.isFromMe else { return false }
        guard let index = messages.firstIndex(where: { $0.id == msg.id }) else { return true }
        if index == 0 { return true }
        let prev = messages[index - 1]
        return prev.isFromMe || prev.senderId != msg.senderId
    }

    // MARK: - 联系人列表

    private var friendsList: some View {
        ZStack {
            AppColors.background.ignoresSafeArea()

            if viewModel.friends.isEmpty {
                VStack(spacing: AppSpacing.xl) {
                    Spacer()

                    // 添加好友入口
                    VStack(spacing: AppSpacing.md) {
                        HStack(spacing: AppSpacing.md) {
                            TextField("输入好友账号", text: $viewModel.friendAccount)
                                .font(AppTypography.subheadline)
                                .appInputField()

                            Button("添加") {
                                Task { await viewModel.addFriend() }
                            }
                            .font(AppTypography.subheadline.weight(.semibold))
                            .foregroundColor(.white)
                            .padding(.horizontal, AppSpacing.lg)
                            .padding(.vertical, 12)
                            .background(AppColors.primary)
                            .clipShape(RoundedRectangle(cornerRadius: AppRadius.md))
                            .disabled(viewModel.friendAccount.isEmpty)
                        }
                        .padding(.horizontal, AppSpacing.lg)
                    }

                    AppEmptyState(
                        icon: "person.2.slash",
                        title: "暂无好友",
                        message: "添加好友开始聊天"
                    )
                    Spacer()
                }
            } else {
                List {
                    // 添加好友区
                    Section {
                        HStack(spacing: AppSpacing.md) {
                            TextField("输入好友账号", text: $viewModel.friendAccount)
                                .font(AppTypography.subheadline)
                            Button("添加") {
                                Task { await viewModel.addFriend() }
                            }
                            .font(AppTypography.subheadline.weight(.medium))
                            .foregroundColor(AppColors.primary)
                            .disabled(viewModel.friendAccount.isEmpty)
                        }
                        .padding(.vertical, 4)
                    } header: {
                        Text("添加好友")
                            .font(AppTypography.footnote.weight(.semibold))
                            .foregroundColor(AppColors.textTertiary)
                    }

                    // 好友列表
                    Section {
                        ForEach(viewModel.friends) { friend in
                            FriendRow(friend: friend) {
                                Task {
                                    await viewModel.createDirectConversation(peerId: friend.id)
                                }
                            } onVideoCall: {
                                callType = "video"
                                callPeerName = friend.displayName
                                Task { await viewModel.startCall(peerId: friend.id, peerName: friend.displayName, callType: "video") }
                                showCallView = true
                            }
                        }
                    } header: {
                        Text("好友列表 (\(viewModel.friends.count))")
                            .font(AppTypography.footnote.weight(.semibold))
                            .foregroundColor(AppColors.textTertiary)
                    }
                }
                .listStyle(.insetGrouped)
                .scrollContentBackground(.hidden)
            }
        }
        .navigationTitle("联系人")
        .navigationBarTitleDisplayMode(.large)
    }

    // MARK: - 来电弹窗

    private var incomingCallOverlay: some View {
        ZStack {
            Color.black.opacity(0.5)
                .ignoresSafeArea()
                .onTapGesture { /* 不响应 */ }

            VStack(spacing: AppSpacing.xxl) {
                // 头像
                ZStack {
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [AppColors.primary, AppColors.primaryLight],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: 80, height: 80)

                    Text(viewModel.incomingCallInfo?.callerName.prefix(1).uppercased() ?? "?")
                        .font(.system(size: 32, weight: .bold))
                        .foregroundColor(.white)
                }
                .shadow(color: AppColors.primary.opacity(0.3), radius: 16, y: 8)

                VStack(spacing: AppSpacing.sm) {
                    Text(viewModel.incomingCallInfo?.callerName ?? "未知用户")
                        .font(AppTypography.title2)
                        .foregroundColor(.white)

                    HStack(spacing: 6) {
                        Image(systemName: viewModel.incomingCallInfo?.callType == "video" ? "video.fill" : "phone.fill")
                            .font(.caption)
                        Text(viewModel.incomingCallInfo?.callType == "video" ? "邀请你视频通话" : "邀请你语音通话")
                            .font(AppTypography.subheadline)
                    }
                    .foregroundColor(.white.opacity(0.8))
                }

                // 操作按钮
                HStack(spacing: 56) {
                    // 拒绝
                    VStack(spacing: AppSpacing.sm) {
                        Button(action: { viewModel.rejectCall() }) {
                            Image(systemName: "phone.down.fill")
                                .font(.title2)
                                .foregroundColor(.white)
                                .padding(18)
                                .background(AppColors.danger)
                                .clipShape(Circle())
                                .shadow(color: AppColors.danger.opacity(0.4), radius: 10, y: 4)
                        }
                        Text("拒绝")
                            .font(AppTypography.caption1)
                            .foregroundColor(.white.opacity(0.8))
                    }

                    // 接听
                    VStack(spacing: AppSpacing.sm) {
                        Button(action: {
                            viewModel.acceptCall()
                            showCallView = true
                        }) {
                            Image(systemName: "phone.fill")
                                .font(.title2)
                                .foregroundColor(.white)
                                .padding(18)
                                .background(AppColors.success)
                                .clipShape(Circle())
                                .shadow(color: AppColors.success.opacity(0.4), radius: 10, y: 4)
                        }
                        Text("接听")
                            .font(AppTypography.caption1)
                            .foregroundColor(.white.opacity(0.8))
                    }
                }
            }
            .padding(.vertical, AppSpacing.xxxl)
            .padding(.horizontal, AppSpacing.xxl)
            .background(.ultraThinMaterial)
            .clipShape(RoundedRectangle(cornerRadius: AppRadius.xxl))
            .shadow(color: .black.opacity(0.2), radius: 24, y: 12)
            .padding(.horizontal, AppSpacing.xl)
        }
    }
}

// MARK: - 会话行（微信风格）

struct ConversationRowView: View {
    let conversation: Conversation

    var body: some View {
        HStack(spacing: AppSpacing.md) {
            // 头像 — 微信风格：圆角方形
            ZStack(alignment: .bottomTrailing) {
                RoundedRectangle(cornerRadius: AppRadius.sm)
                    .fill(
                        LinearGradient(
                            colors: [AppColors.primaryLight, AppColors.primary],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 48, height: 48)

                Text(conversation.initial)
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.white)

                // 在线状态小圆点
                if conversation.type == .direct {
                    Circle()
                        .fill(AppColors.success)
                        .frame(width: 12, height: 12)
                        .overlay(
                            Circle()
                                .stroke(AppColors.background, lineWidth: 2)
                        )
                        .offset(x: 4, y: 4)
                }
            }

            // 内容区
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 4) {
                    Text(conversation.title)
                        .font(AppTypography.subheadline.weight(.medium))
                        .foregroundColor(AppColors.textPrimary)
                        .lineLimit(1)

                    if conversation.type == .group {
                        Image(systemName: "person.2.fill")
                            .font(.system(size: 10))
                            .foregroundColor(AppColors.textTertiary)
                    }

                    Spacer()

                    if let msg = conversation.lastMessage {
                        Text(formatConversationTime(msg.createdAt))
                            .font(.system(size: 11))
                            .foregroundColor(AppColors.textTertiary)
                    }
                }

                HStack(spacing: 4) {
                    // 消息状态图标
                    if let msg = conversation.lastMessage, msg.isFromMe {
                        messageStatusIcon(msg.status)
                    }

                    // 最后一条消息预览
                    if let msg = conversation.lastMessage {
                        Text(conversationSummary(msg))
                            .font(AppTypography.caption2)
                            .foregroundColor(AppColors.textSecondary)
                            .lineLimit(1)
                    }

                    Spacer()

                    // 未读红点/数字
                    if conversation.unreadCount > 0 {
                        unreadBadge(conversation.unreadCount)
                    }
                }
            }
        }
        .padding(.vertical, 10)
    }

    private func conversationSummary(_ msg: ChatMessage) -> String {
        switch msg.type {
        case .image:
            return "[图片]"
        case .file:
            return "[文件]"
        case .videoCall:
            return "[\(msg.callType == "video" ? "视频" : "语音")通话]"
        case .system:
            return msg.content
        default:
            return msg.content
        }
    }

    @ViewBuilder
    private func messageStatusIcon(_ status: ChatMessage.MessageStatus) -> some View {
        switch status {
        case .sending:
            ProgressView()
                .scaleEffect(0.5)
        case .failed:
            Image(systemName: "exclamationmark.circle.fill")
                .font(.system(size: 10))
                .foregroundColor(AppColors.danger)
        default:
            EmptyView()
        }
    }

    @ViewBuilder
    private func unreadBadge(_ count: Int) -> some View {
        if count <= 0 {
            EmptyView()
        } else if count == 1 {
            // 微信风格：红点
            Circle()
                .fill(AppColors.danger)
                .frame(width: 8, height: 8)
        } else {
            // 数字徽章
            Text(count > 99 ? "99+" : "\(count)")
                .font(.system(size: 11, weight: .semibold))
                .foregroundColor(.white)
                .padding(.horizontal, 6)
                .padding(.vertical, 2)
                .background(AppColors.danger)
                .clipShape(Capsule())
        }
    }

    private func formatConversationTime(_ dateStr: String) -> String {
        let formatter = ISO8601DateFormatter()
        guard let date = formatter.date(from: dateStr) else { return "" }
        let calendar = Calendar.current
        if calendar.isDateInToday(date) {
            let fmt = DateFormatter()
            fmt.dateFormat = "HH:mm"
            return fmt.string(from: date)
        } else if calendar.isDateInYesterday(date) {
            return "昨天"
        } else if calendar.isDate(date, equalTo: Date(), toGranularity: .year) {
            let fmt = DateFormatter()
            fmt.dateFormat = "MM/dd"
            return fmt.string(from: date)
        } else {
            let fmt = DateFormatter()
            fmt.dateFormat = "yyyy/MM/dd"
            return fmt.string(from: date)
        }
    }
}

// MARK: - 消息气泡形状（带尾巴）

struct ChatBubbleShape: Shape {
    let isFromMe: Bool

    func path(in rect: CGRect) -> Path {
        let cornerRadius: CGFloat = 18
        let tailWidth: CGFloat = 6
        let tailHeight: CGFloat = 8
        var path = Path()

        if isFromMe {
            // 右侧气泡（有右下角尾巴）
            path.move(to: CGPoint(x: cornerRadius, y: 0))
            // 上边
            path.addLine(to: CGPoint(x: rect.width - cornerRadius, y: 0))
            path.addArc(
                center: CGPoint(x: rect.width - cornerRadius, y: cornerRadius),
                radius: cornerRadius,
                startAngle: .degrees(-90),
                endAngle: .degrees(0),
                clockwise: false
            )
            // 右边（预留尾巴位置）
            path.addLine(to: CGPoint(x: rect.width, y: rect.height - cornerRadius - tailHeight))
            path.addArc(
                center: CGPoint(x: rect.width - cornerRadius, y: rect.height - cornerRadius - tailHeight),
                radius: cornerRadius,
                startAngle: .degrees(0),
                endAngle: .degrees(90),
                clockwise: false
            )
            // 尾巴
            path.addLine(to: CGPoint(x: rect.width - cornerRadius + tailWidth, y: rect.height - tailHeight))
            path.addLine(to: CGPoint(x: rect.width, y: rect.height))
            // 下边
            path.addLine(to: CGPoint(x: cornerRadius, y: rect.height))
            path.addArc(
                center: CGPoint(x: cornerRadius, y: rect.height - cornerRadius),
                radius: cornerRadius,
                startAngle: .degrees(90),
                endAngle: .degrees(180),
                clockwise: false
            )
            // 左边
            path.addLine(to: CGPoint(x: 0, y: cornerRadius))
            path.addArc(
                center: CGPoint(x: cornerRadius, y: cornerRadius),
                radius: cornerRadius,
                startAngle: .degrees(180),
                endAngle: .degrees(270),
                clockwise: false
            )
        } else {
            // 左侧气泡（有左下角尾巴）
            path.move(to: CGPoint(x: cornerRadius + tailWidth, y: 0))
            // 上边
            path.addLine(to: CGPoint(x: rect.width - cornerRadius, y: 0))
            path.addArc(
                center: CGPoint(x: rect.width - cornerRadius, y: cornerRadius),
                radius: cornerRadius,
                startAngle: .degrees(-90),
                endAngle: .degrees(0),
                clockwise: false
            )
            // 右边
            path.addLine(to: CGPoint(x: rect.width, y: rect.height - cornerRadius))
            path.addArc(
                center: CGPoint(x: rect.width - cornerRadius, y: rect.height - cornerRadius),
                radius: cornerRadius,
                startAngle: .degrees(0),
                endAngle: .degrees(90),
                clockwise: false
            )
            // 下边
            path.addLine(to: CGPoint(x: cornerRadius + tailWidth, y: rect.height))
            path.addArc(
                center: CGPoint(x: cornerRadius + tailWidth, y: rect.height - cornerRadius),
                radius: cornerRadius,
                startAngle: .degrees(90),
                endAngle: .degrees(180),
                clockwise: false
            )
            // 尾巴
            path.addLine(to: CGPoint(x: tailWidth, y: rect.height - tailHeight))
            path.addLine(to: CGPoint(x: 0, y: rect.height))
            // 左边
            path.addLine(to: CGPoint(x: cornerRadius + tailWidth, y: cornerRadius))
            path.addArc(
                center: CGPoint(x: cornerRadius + tailWidth, y: cornerRadius),
                radius: cornerRadius,
                startAngle: .degrees(180),
                endAngle: .degrees(270),
                clockwise: false
            )
        }

        path.closeSubpath()
        return path
    }
}

// MARK: - 时间戳展示

struct MessageTimeStampView: View {
    let timeText: String

    var body: some View {
        HStack {
            Spacer()
            Text(timeText)
                .font(.system(size: 11, weight: .medium))
                .foregroundColor(AppColors.textTertiary)
                .padding(.horizontal, AppSpacing.md)
                .padding(.vertical, 4)
                .background(AppColors.textTertiary.opacity(0.08))
                .clipShape(Capsule())
            Spacer()
        }
    }
}

// MARK: - 聊天气泡行

struct ChatBubbleRow: View {
    let message: ChatMessage
    let showAvatar: Bool
    let onRetry: () -> Void

    var body: some View {
        HStack(alignment: .bottom, spacing: AppSpacing.sm) {
            if message.isFromMe {
                // 自己发送的消息：右侧
                Spacer(minLength: 60)

                // 内容区
                VStack(alignment: .trailing, spacing: 2) {
                    bubbleContent

                    // 发送状态
                    HStack(spacing: 3) {
                        Text(formatMessageTime(message.createdAt))
                            .font(.system(size: 10))
                            .foregroundColor(AppColors.textTertiary)

                        messageStatusView
                    }
                    .padding(.trailing, 4)
                }
            } else {
                // 对方发送的消息：左侧
                // 头像
                if showAvatar {
                    receiveAvatar
                } else {
                    Color.clear.frame(width: 36, height: 36)
                }

                // 内容区
                VStack(alignment: .leading, spacing: 2) {
                    bubbleContent

                    Text(formatMessageTime(message.createdAt))
                        .font(.system(size: 10))
                        .foregroundColor(AppColors.textTertiary)
                        .padding(.leading, 4)
                }

                Spacer(minLength: 60)
            }
        }
        .padding(.horizontal, AppSpacing.md)
    }

    // MARK: 头像

    private var receiveAvatar: some View {
        ZStack {
            Circle()
                .fill(
                    LinearGradient(
                        colors: [AppColors.primaryLight, AppColors.primary],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .frame(width: 36, height: 36)

            Text(String(message.senderName?.prefix(1) ?? message.senderId.prefix(1)).uppercased())
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(.white)
        }
    }

    // MARK: 气泡内容

    @ViewBuilder
    private var bubbleContent: some View {
        if message.isCallInvite {
            callInviteBubble
        } else {
            Text(message.content)
                .font(AppTypography.subheadline)
                .foregroundColor(message.isFromMe ? .white : AppColors.textPrimary)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(
                    ChatBubbleShape(isFromMe: message.isFromMe)
                        .fill(message.isFromMe ? Color.chatBubbleSent : Color.chatBubbleReceived)
                )
        }
    }

    // MARK: 通话邀请气泡

    private var callInviteBubble: some View {
        HStack(spacing: AppSpacing.sm) {
            Image(systemName: message.callType == "video" ? "video.fill" : "phone.fill")
                .font(.subheadline)
                .foregroundColor(message.isFromMe ? .white : AppColors.primary)

            VStack(alignment: .leading, spacing: 2) {
                Text(message.isFromMe ? "已发起通话" : message.content)
                    .font(AppTypography.subheadline)
                    .foregroundColor(message.isFromMe ? .white : AppColors.textPrimary)

                if let status = message.extra?["call_status"] {
                    Text(callStatusText(status))
                        .font(.system(size: 11))
                        .foregroundColor(message.isFromMe ? .white.opacity(0.7) : AppColors.textSecondary)
                }
            }

            Spacer()
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .frame(width: 220)
        .background(
            ChatBubbleShape(isFromMe: message.isFromMe)
                .fill(message.isFromMe ? Color.chatBubbleSent : Color.chatBubbleReceived)
        )
    }

    private func callStatusText(_ status: String) -> String {
        switch status {
        case "inviting": return "等待接听..."
        case "accepted": return "已接听"
        case "rejected": return "已拒绝"
        case "ended":   return "通话已结束"
        default:        return status
        }
    }

    // MARK: 消息状态

    @ViewBuilder
    private var messageStatusView: some View {
        switch message.status {
        case .sending:
            ProgressView()
                .scaleEffect(0.5)
        case .sent:
            Image(systemName: "checkmark")
                .font(.system(size: 10))
                .foregroundColor(AppColors.textTertiary)
        case .delivered:
            Image(systemName: "checkmark.circle")
                .font(.system(size: 10))
                .foregroundColor(AppColors.textTertiary)
        case .read:
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 10))
                .foregroundColor(AppColors.primary)
        case .failed:
            Button(action: onRetry) {
                Image(systemName: "exclamationmark.circle.fill")
                    .font(.system(size: 10))
                    .foregroundColor(AppColors.danger)
            }
        case .recalled:
            Text("已撤回")
                .font(.system(size: 10))
                .foregroundColor(AppColors.textTertiary)
        }
    }

    // MARK: 时间格式化

    private func formatMessageTime(_ dateStr: String) -> String {
        let formatter = ISO8601DateFormatter()
        guard let date = formatter.date(from: dateStr) else { return "" }
        let display = DateFormatter()
        display.dateFormat = "HH:mm"
        return display.string(from: date)
    }
}

// MARK: - 聊天输入工具栏（微信风格）

struct ChatInputBar: View {
    @ObservedObject var viewModel: MessagesViewModel
    @State private var showMoreActions = false
    @State private var inputHeight: CGFloat = 40

    var body: some View {
        VStack(spacing: 0) {
            // 更多操作面板
            if showMoreActions {
                moreActionsPanel
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }

            Divider()
                .overlay(AppColors.divider)

            // 输入主栏
            VStack(spacing: 0) {
                HStack(alignment: .bottom, spacing: AppSpacing.sm) {
                    // 语音/键盘切换
                    Button(action: { /* 语音输入切换 */ }) {
                        Image(systemName: "mic")
                            .font(.system(size: 20))
                            .foregroundColor(AppColors.textSecondary)
                            .frame(width: 36, height: 36)
                    }

                    // 文本输入框
                    ZStack(alignment: .leading) {
                        if viewModel.draft.isEmpty {
                            Text("输入消息...")
                                .font(AppTypography.subheadline)
                                .foregroundColor(AppColors.textPlaceholder)
                                .padding(.horizontal, AppSpacing.md)
                                .padding(.vertical, 10)
                        }

                        TextField("", text: $viewModel.draft, axis: .vertical)
                            .font(AppTypography.subheadline)
                            .foregroundColor(AppColors.textPrimary)
                            .lineLimit(1...4)
                            .padding(.horizontal, AppSpacing.md)
                            .padding(.vertical, 10)
                    }
                    .background(AppColors.surface)
                    .clipShape(RoundedRectangle(cornerRadius: 20))
                    .overlay(
                        RoundedRectangle(cornerRadius: 20)
                            .stroke(AppColors.divider, lineWidth: 0.5)
                    )

                    // 更多按钮
                    Button(action: {
                        withAnimation(.easeInOut(duration: 0.25)) {
                            showMoreActions.toggle()
                        }
                    }) {
                        Image(systemName: showMoreActions ? "xmark" : "plus")
                            .font(.system(size: 20))
                            .foregroundColor(AppColors.textSecondary)
                            .frame(width: 36, height: 36)
                            .rotationEffect(.degrees(showMoreActions ? 45 : 0))
                    }

                    // 发送按钮
                    if !viewModel.draft.trimmingCharacters(in: .whitespaces).isEmpty {
                        Button(action: {
                            Task { await viewModel.sendMessage() }
                        }) {
                            Image(systemName: "paperplane.fill")
                                .font(.system(size: 18))
                                .foregroundColor(.white)
                                .frame(width: 36, height: 36)
                                .background(AppColors.primary)
                                .clipShape(Circle())
                        }
                        .transition(.scale.combined(with: .opacity))
                    }
                }
                .padding(.horizontal, AppSpacing.md)
                .padding(.vertical, 8)
            }
            .background(AppColors.surface)
        }
        .animation(.easeInOut(duration: 0.25), value: showMoreActions)
        .animation(.easeInOut(duration: 0.2), value: viewModel.draft)
    }

    // MARK: 更多操作面板

    private var moreActionsPanel: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                moreActionItem(icon: "photo", label: "相册", color: AppColors.fileImage) {}
                moreActionItem(icon: "camera", label: "拍摄", color: AppColors.primary) {}
                moreActionItem(icon: "doc", label: "文件", color: AppColors.fileDocument) {}
                moreActionItem(icon: "location", label: "位置", color: AppColors.success) {}
            }
            .padding(.horizontal, AppSpacing.md)
            .padding(.vertical, AppSpacing.lg)

            Divider()
                .overlay(AppColors.dividerLight)
                .padding(.horizontal, AppSpacing.lg)

            HStack(spacing: 0) {
                moreActionItem(icon: "video", label: "视频通话", color: AppColors.fileVideo) {
                    // 触发视频通话
                }
                moreActionItem(icon: "phone", label: "语音通话", color: AppColors.success) {
                    // 触发语音通话
                }
                moreActionItem(icon: "person.2", label: "群聊", color: AppColors.info) {}
                moreActionItem(icon: "calendar", label: "日程", color: AppColors.warning) {}
            }
            .padding(.horizontal, AppSpacing.md)
            .padding(.vertical, AppSpacing.lg)
        }
        .background(AppColors.surfaceSecondary)
    }

    private func moreActionItem(
        icon: String,
        label: String,
        color: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            VStack(spacing: AppSpacing.sm) {
                ZStack {
                    RoundedRectangle(cornerRadius: AppRadius.md)
                        .fill(color.opacity(0.1))
                        .frame(width: 56, height: 56)

                    Image(systemName: icon)
                        .font(.system(size: 24))
                        .foregroundColor(color)
                }

                Text(label)
                    .font(.system(size: 11))
                    .foregroundColor(AppColors.textSecondary)
            }
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - 好友行

struct FriendRow: View {
    let friend: Friend
    let onMessage: () -> Void
    let onVideoCall: () -> Void

    var body: some View {
        HStack(spacing: AppSpacing.md) {
            // 头像
            ZStack(alignment: .bottomTrailing) {
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [AppColors.primaryLight, AppColors.primary],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 42, height: 42)

                Text(friend.initial)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.white)

                // 在线状态
                if friend.online {
                    Circle()
                        .fill(AppColors.success)
                        .frame(width: 12, height: 12)
                        .overlay(
                            Circle()
                                .stroke(AppColors.background, lineWidth: 2)
                        )
                        .offset(x: 4, y: 4)
                }
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(friend.displayName)
                    .font(AppTypography.subheadline.weight(.medium))
                    .foregroundColor(AppColors.textPrimary)
                Text(friend.account)
                    .font(AppTypography.caption2)
                    .foregroundColor(AppColors.textSecondary)
            }

            Spacer()

            HStack(spacing: AppSpacing.sm) {
                Button(action: onMessage) {
                    Image(systemName: "message")
                        .font(.subheadline)
                        .foregroundColor(AppColors.primary)
                        .frame(width: 34, height: 34)
                        .background(AppColors.primaryBg)
                        .clipShape(Circle())
                }

                Button(action: onVideoCall) {
                    Image(systemName: "video")
                        .font(.subheadline)
                        .foregroundColor(AppColors.primary)
                        .frame(width: 34, height: 34)
                        .background(AppColors.primaryBg)
                        .clipShape(Circle())
                }
            }
        }
        .padding(.vertical, 4)
    }
}

// MARK: - 通话页面

struct CallView: View {
    let peerName: String
    let callType: String
    @ObservedObject var viewModel: MessagesViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var isMuted = false
    @State private var isSpeakerOn = true
    @State private var isCameraOff = false
    @State private var showControls = true

    var body: some View {
        ZStack {
            // 渐变背景
            LinearGradient(
                colors: [Color(hex: "#1a1a2e"), Color(hex: "#16213e"), Color(hex: "#0f3460")],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            // 点击切换控件显示
            Color.clear
                .contentShape(Rectangle())
                .onTapGesture {
                    withAnimation(.easeInOut(duration: 0.3)) {
                        showControls.toggle()
                    }
                }

            VStack(spacing: 0) {
                if showControls {
                    // 顶部导航
                    callTopBar
                        .transition(.move(edge: .top).combined(with: .opacity))
                }

                Spacer()

                // 用户信息
                VStack(spacing: AppSpacing.lg) {
                    ZStack {
                        Circle()
                            .fill(.white.opacity(0.15))
                            .frame(width: 88, height: 88)

                        Text(peerName.prefix(1).uppercased())
                            .font(.system(size: 36, weight: .bold))
                            .foregroundColor(.white)
                    }
                    .shadow(color: .black.opacity(0.2), radius: 16, y: 8)

                    Text(peerName)
                        .font(AppTypography.title2)
                        .foregroundColor(.white)

                    Text(viewModel.callDuration)
                        .font(AppTypography.monospacedTitle)
                        .foregroundColor(.white.opacity(0.7))

                    if callType == "voice" {
                        // 语音波形动画
                        HStack(spacing: 3) {
                            ForEach(0..<5) { i in
                                RoundedRectangle(cornerRadius: 2)
                                    .fill(AppColors.success)
                                    .frame(width: 3, height: CGFloat([8, 16, 24, 16, 8][i]))
                                    .animation(
                                        .easeInOut(duration: 0.5)
                                            .repeatForever(autoreverses: true)
                                            .delay(Double(i) * 0.1),
                                        value: UUID()
                                    )
                            }
                        }
                        .padding(.top, AppSpacing.md)
                    }
                }

                Spacer()

                if showControls {
                    // 控制按钮
                    callControlPanel
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
        }
        .animation(.easeInOut(duration: 0.3), value: showControls)
    }

    // MARK: 通话顶部栏

    private var callTopBar: some View {
        HStack {
            Button(action: {
                viewModel.hangupCall()
                dismiss()
            }) {
                Image(systemName: "chevron.down")
                    .font(.title3.weight(.semibold))
                    .foregroundColor(.white)
                    .frame(width: 40, height: 40)
                    .background(.white.opacity(0.15))
                    .clipShape(Circle())
            }

            Spacer()

            Button(action: {}) {
                Image(systemName: "person.2.fill")
                    .font(.subheadline)
                    .foregroundColor(.white)
                    .frame(width: 40, height: 40)
                    .background(.white.opacity(0.15))
                    .clipShape(Circle())
            }
        }
        .padding(.horizontal, AppSpacing.lg)
        .padding(.top, 56)
    }

    // MARK: 通话控制面板

    private var callControlPanel: some View {
        VStack(spacing: AppSpacing.xxl) {
            HStack(spacing: AppSpacing.xxl) {
                CallControlButton(
                    icon: isMuted ? "mic.slash.fill" : "mic.fill",
                    label: "静音",
                    isActive: isMuted,
                    activeColor: AppColors.danger
                ) { isMuted.toggle() }

                Button(action: {
                    viewModel.hangupCall()
                    dismiss()
                }) {
                    Image(systemName: "phone.down.fill")
                        .font(.title2)
                        .foregroundColor(.white)
                        .padding(20)
                        .background(AppColors.danger)
                        .clipShape(Circle())
                        .shadow(color: AppColors.danger.opacity(0.4), radius: 12, y: 4)
                }

                CallControlButton(
                    icon: isSpeakerOn ? "speaker.wave.2.fill" : "speaker.slash.fill",
                    label: "扬声器",
                    isActive: !isSpeakerOn,
                    activeColor: AppColors.warning
                ) { isSpeakerOn.toggle() }
            }

            if callType == "video" {
                HStack(spacing: AppSpacing.xxl) {
                    CallControlButton(
                        icon: isCameraOff ? "video.slash.fill" : "video.fill",
                        label: "摄像头",
                        isActive: isCameraOff,
                        activeColor: AppColors.danger
                    ) { isCameraOff.toggle() }

                    CallControlButton(
                        icon: "arrow.triangle.2.circlepath",
                        label: "翻转",
                        isActive: false,
                        activeColor: AppColors.primary
                    ) {}
                }
            }
        }
        .padding(.bottom, 48)
    }
}

// MARK: - 通话控制按钮

struct CallControlButton: View {
    let icon: String
    let label: String
    let isActive: Bool
    let activeColor: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: AppSpacing.xs) {
                Image(systemName: icon)
                    .font(.title2)
                    .foregroundColor(.white)
                    .padding(16)
                    .background(isActive ? activeColor : Color.white.opacity(0.15))
                    .clipShape(Circle())

                Text(label)
                    .font(AppTypography.caption1)
                    .foregroundColor(.white.opacity(0.8))
            }
        }
    }
}

// MARK: - Preview

#Preview("消息列表 - 会话列表") {
    MessagesView(previewMode: true)
}

#Preview("消息列表 - 暗色模式") {
    MessagesView(previewMode: true)
        .preferredColorScheme(.dark)
}

#Preview("聊天界面 - 聊天记录") {
    ChatPreviewView()
}

#Preview("聊天界面 - 暗色模式") {
    ChatPreviewView()
        .preferredColorScheme(.dark)
}

#Preview("联系人 - 好友列表") {
    FriendsPreviewView()
}

#Preview("联系人 - 暗色模式") {
    FriendsPreviewView()
        .preferredColorScheme(.dark)
}

#Preview("通话界面 - 视频通话") {
    CallViewPreview()
}

#Preview("通话界面 - 语音通话") {
    CallViewPreview(callType: "voice")
}

// MARK: - 聊天界面预览容器

private struct ChatPreviewView: View {
    @StateObject private var viewModel = MessagesViewModel()

    var body: some View {
        MessagesView_ChatPreviewContent(viewModel: viewModel)
            .task {
                viewModel.conversations = Conversation.previewConversations
                viewModel.activeConversation = Conversation.previewConversations.first
                viewModel.loadPreviewMessages(conversationId: Conversation.previewConversations.first?.id ?? "")
            }
    }
}

// MARK: - 聊天界面预览（复用 MessagesView 的聊天子视图）

private struct MessagesView_ChatPreviewContent: View {
    @ObservedObject var viewModel: MessagesViewModel
    @State private var callPeerName = ""
    @State private var callType = "video"
    @State private var showCallView = false

    var body: some View {
        VStack(spacing: 0) {
            // 聊天导航栏（复刻原版）
            chatPreviewNavBar

            // 消息列表
            ScrollView {
                LazyVStack(spacing: 0) {
                    Color.clear.frame(height: 8)

                    ForEach(groupedMessages, id: \.dateKey) { group in
                        MessageTimeStampView(timeText: group.formattedTime)
                            .padding(.vertical, AppSpacing.md)

                        ForEach(group.messages) { msg in
                            ChatBubbleRow(
                                message: msg,
                                showAvatar: shouldShowAvatar(for: msg, in: group.messages),
                                onRetry: {}
                            )
                            .id(msg.id)
                            .padding(.vertical, 2)
                        }
                    }
                    Color.clear.frame(height: 8)
                }
            }
            .defaultScrollAnchor(.bottom)

            // 输入工具栏
            ChatInputBar(viewModel: viewModel)
        }
        .background(Color.chatBg)
    }

    private var chatPreviewNavBar: some View {
        HStack(spacing: 0) {
            Image(systemName: "chevron.left")
                .font(.title3.weight(.semibold))
                .foregroundColor(AppColors.textPrimary)
                .frame(width: 36, height: 36)

            HStack(spacing: AppSpacing.sm) {
                ZStack {
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [AppColors.primaryLight, AppColors.primary],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: 36, height: 36)
                    Text(viewModel.activeConversation?.initial ?? "?")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.white)
                }
                VStack(alignment: .leading, spacing: 1) {
                    Text(viewModel.activeConversation?.title ?? "")
                        .font(AppTypography.subheadline.weight(.semibold))
                        .foregroundColor(AppColors.textPrimary)
                        .lineLimit(1)
                }
            }
            Spacer()
            HStack(spacing: AppSpacing.xs) {
                ForEach(["phone", "video", "ellipsis"], id: \.self) { icon in
                    Image(systemName: icon)
                        .font(.subheadline)
                        .foregroundColor(AppColors.textPrimary)
                        .frame(width: 36, height: 36)
                }
            }
        }
        .padding(.horizontal, AppSpacing.md)
        .padding(.top, 48)
        .padding(.bottom, 8)
        .background(
            AppColors.surface
                .shadow(color: .black.opacity(0.04), radius: 4, y: 2)
        )
    }

    private var groupedMessages: [(dateKey: String, formattedTime: String, messages: [ChatMessage])] {
        var groups: [(dateKey: String, formattedTime: String, messages: [ChatMessage])] = []
        let formatter = ISO8601DateFormatter()
        for msg in viewModel.messages {
            let date = formatter.date(from: msg.createdAt) ?? Date()
            let timeKey = formatTimeStamp(date)
            if let last = groups.last,
               let lastDate = formatter.date(from: last.messages.last?.createdAt ?? ""),
               abs(date.timeIntervalSince(lastDate)) < 180 {
                var updatedMessages = last.messages
                updatedMessages.append(msg)
                groups[groups.count - 1] = (last.dateKey, last.formattedTime, updatedMessages)
            } else {
                groups.append((timeKey, timeKey, [msg]))
            }
        }
        return groups
    }

    private func formatTimeStamp(_ date: Date) -> String {
        let calendar = Calendar.current
        let fmt = DateFormatter()
        if calendar.isDateInToday(date) {
            fmt.dateFormat = "HH:mm"
        } else if calendar.isDateInYesterday(date) {
            return "昨天 \(DateFormatter.localizedString(from: date, dateStyle: .none, timeStyle: .short))"
        } else if calendar.isDate(date, equalTo: Date(), toGranularity: .year) {
            fmt.dateFormat = "MM月dd日 HH:mm"
        } else {
            fmt.dateFormat = "yyyy年MM月dd日 HH:mm"
        }
        return fmt.string(from: date)
    }

    private func shouldShowAvatar(for msg: ChatMessage, in messages: [ChatMessage]) -> Bool {
        guard !msg.isFromMe else { return false }
        guard let index = messages.firstIndex(where: { $0.id == msg.id }) else { return true }
        if index == 0 { return true }
        let prev = messages[index - 1]
        return prev.isFromMe || prev.senderId != msg.senderId
    }
}

// MARK: - 联系人列表预览容器

private struct FriendsPreviewView: View {
    @StateObject private var viewModel = MessagesViewModel()

    var body: some View {
        NavigationStack {
            ZStack {
                AppColors.background.ignoresSafeArea()
                List {
                    Section {
                        HStack(spacing: AppSpacing.md) {
                            TextField("输入好友账号", text: .constant(""))
                                .font(AppTypography.subheadline)
                            Button("添加") {}
                                .font(AppTypography.subheadline.weight(.medium))
                                .foregroundColor(AppColors.primary)
                        }
                        .padding(.vertical, 4)
                    } header: {
                        Text("添加好友")
                            .font(AppTypography.footnote.weight(.semibold))
                            .foregroundColor(AppColors.textTertiary)
                    }
                    Section {
                        ForEach(viewModel.friends) { friend in
                            FriendRow(friend: friend, onMessage: {}, onVideoCall: {})
                        }
                    } header: {
                        Text("好友列表 (\(viewModel.friends.count))")
                            .font(AppTypography.footnote.weight(.semibold))
                            .foregroundColor(AppColors.textTertiary)
                    }
                }
                .listStyle(.insetGrouped)
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("联系人")
            .navigationBarTitleDisplayMode(.large)
        }
        .task {
            viewModel.loadPreviewData()
        }
    }
}

// MARK: - 通话界面预览容器

private struct CallViewPreview: View {
    var callType: String = "video"
    @StateObject private var viewModel = MessagesViewModel()

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color(hex: "#1a1a2e"), Color(hex: "#16213e"), Color(hex: "#0f3460")],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                // 顶部导航
                HStack(spacing: AppSpacing.md) {
                    Image(systemName: "chevron.down")
                        .font(.title3)
                        .foregroundColor(.white)
                        .frame(width: 36, height: 36)
                    Spacer()
                    Text("通话中")
                        .font(AppTypography.subheadline.weight(.medium))
                        .foregroundColor(.white.opacity(0.8))
                    Spacer()
                    Image(systemName: "person.badge.plus")
                        .font(.title3)
                        .foregroundColor(.white)
                        .frame(width: 36, height: 36)
                }
                .padding(.horizontal, AppSpacing.md)
                .padding(.top, 48)
                .padding(.bottom, 8)

                Spacer()

                // 用户信息
                VStack(spacing: AppSpacing.lg) {
                    ZStack {
                        Circle()
                            .fill(.white.opacity(0.15))
                            .frame(width: 88, height: 88)
                        Text("张")
                            .font(.system(size: 36, weight: .bold))
                            .foregroundColor(.white)
                    }
                    .shadow(color: .black.opacity(0.2), radius: 16, y: 8)

                    Text("张晓明")
                        .font(AppTypography.title2)
                        .foregroundColor(.white)

                    Text("02:35")
                        .font(AppTypography.monospacedTitle)
                        .foregroundColor(.white.opacity(0.7))

                    if callType == "voice" {
                        HStack(spacing: 3) {
                            ForEach(0..<5) { i in
                                RoundedRectangle(cornerRadius: 2)
                                    .fill(AppColors.success)
                                    .frame(width: 3, height: [12, 20, 28, 20, 12][i])
                                    .opacity(0.8)
                            }
                        }
                    }
                }

                Spacer()

                // 底部控制栏
                HStack(spacing: AppSpacing.xxl) {
                    CallControlButton(icon: "mic.slash", label: "静音", isActive: false, activeColor: AppColors.danger) {}
                    CallControlButton(icon: "speaker.wave.2", label: "扬声器", isActive: false, activeColor: AppColors.primary) {}
                    if callType == "video" {
                        CallControlButton(icon: "video", label: "摄像头", isActive: false, activeColor: AppColors.primary) {}
                        CallControlButton(icon: "arrow.triangle.2.circlepath", label: "翻转", isActive: false, activeColor: AppColors.primary) {}
                    }
                }
                .padding(.bottom, 20)

                // 挂断按钮
                Button(action: {}) {
                    Image(systemName: "phone.down.fill")
                        .font(.title2)
                        .foregroundColor(.white)
                        .padding(18)
                        .background(AppColors.danger)
                        .clipShape(Circle())
                        .shadow(color: AppColors.danger.opacity(0.4), radius: 10, y: 4)
                }
                .padding(.bottom, 48)
            }
        }
        .task {
            viewModel.loadPreviewData()
        }
    }
}
