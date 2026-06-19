//
//  MessagesView.swift
//  PrivateCloudDisk-ios
//
//  消息中心 — 会话列表、聊天、通话界面
//  利用 iOS 原生 UI 组件和系统交互
//

import SwiftUI

struct MessagesView: View {
    @StateObject private var viewModel = MessagesViewModel()
    @State private var showCallView = false
    @State private var callPeerName = ""
    @State private var callType = "video"

    var body: some View {
        NavigationStack {
            if viewModel.activeConversation == nil {
                conversationList
            } else {
                chatView
            }
        }
        .task {
            await viewModel.loadConversations()
            await viewModel.loadFriends()
        }
        .onAppear {
            viewModel.connectWebSocket()
        }
        .overlay {
            // 来电弹窗
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

    // MARK: - 会话列表

    private var conversationList: some View {
        List {
            ForEach(viewModel.conversations) { conv in
                Button(action: { viewModel.openConversation(conv) }) {
                    ConversationRowView(conversation: conv)
                }
            }
        }
        .listStyle(.plain)
        .navigationTitle("消息")
        .refreshable {
            await viewModel.loadConversations()
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink(destination: friendsList) {
                    Image(systemName: "person.2")
                }
            }
        }
    }

    // MARK: - 聊天视图

    private var chatView: some View {
        VStack(spacing: 0) {
            // 聊天头部
            HStack {
                Button(action: { viewModel.activeConversation = nil }) {
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.left")
                        Text("返回")
                    }
                }

                Spacer()

                VStack {
                    Text(viewModel.activeConversation?.title ?? "")
                        .font(.headline)
                    if viewModel.peerTyping {
                        Text("正在输入...")
                            .font(.caption2)
                            .foregroundStyle(.blue)
                    }
                }

                Spacer()

                // 通话按钮
                HStack(spacing: 12) {
                    Button(action: {
                        callType = "video"
                        callPeerName = viewModel.activeConversation?.title ?? ""
                        Task { await viewModel.startCall(peerId: "", peerName: callPeerName, callType: "video") }
                        showCallView = true
                    }) {
                        Image(systemName: "video.fill")
                            .foregroundStyle(.blue)
                    }
                    Button(action: {
                        callType = "voice"
                        callPeerName = viewModel.activeConversation?.title ?? ""
                        Task { await viewModel.startCall(peerId: "", peerName: callPeerName, callType: "voice") }
                        showCallView = true
                    }) {
                        Image(systemName: "phone.fill")
                            .foregroundStyle(.green)
                    }
                }
            }
            .padding(.horizontal)
            .padding(.vertical, 10)
            .background(.bar)

            Divider()

            // 消息列表
            ScrollViewReader { proxy in
                List {
                    ForEach(viewModel.messages) { msg in
                        MessageBubbleView(message: msg) {
                            Task { await viewModel.retryMessage(msg) }
                        }
                        .id(msg.id)
                        .listRowSeparator(.hidden)
                        .flippedUpsideDown()
                    }
                    .flippedUpsideDown()
                }
                .listStyle(.plain)
                .flippedUpsideDown()
                .onChange(of: viewModel.messages.count) { _, _ in
                    if let last = viewModel.messages.last {
                        withAnimation { proxy.scrollTo(last.id, anchor: .bottom) }
                    }
                }
            }

            Divider()

            // 输入框
            HStack(spacing: 8) {
                TextField("输入消息...", text: $viewModel.draft, axis: .vertical)
                    .lineLimit(1...4)
                    .padding(10)
                    .background(Color(.systemGray6))
                    .clipShape(RoundedRectangle(cornerRadius: 20))

                Button(action: {
                    Task { await viewModel.sendMessage() }
                }) {
                    Image(systemName: "paperplane.fill")
                        .foregroundStyle(.white)
                        .padding(10)
                        .background(viewModel.draft.trimmingCharacters(in: .whitespaces).isEmpty ? Color.gray : Color.blue)
                        .clipShape(Circle())
                }
                .disabled(viewModel.draft.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            .padding(.horizontal)
            .padding(.vertical, 8)
        }
    }

    // MARK: - 联系人列表

    private var friendsList: some View {
        List {
            Section("添加好友") {
                HStack {
                    TextField("输入好友账号", text: $viewModel.friendAccount)
                    Button("添加") {
                        Task { await viewModel.addFriend() }
                    }
                    .disabled(viewModel.friendAccount.isEmpty)
                }
            }
            Section("好友列表") {
                ForEach(viewModel.friends) { friend in
                    HStack {
                        // 头像
                        Text(friend.initial)
                            .font(.headline)
                            .frame(width: 40, height: 40)
                            .background(Color.blue.opacity(0.1))
                            .foregroundColor(.blue)
                            .clipShape(Circle())
                            .overlay(alignment: .bottomTrailing) {
                                if friend.online {
                                    Circle()
                                        .fill(.green)
                                        .frame(width: 10, height: 10)
                                        .overlay(Circle().stroke(.background, lineWidth: 2))
                                }
                            }

                        VStack(alignment: .leading, spacing: 2) {
                            Text(friend.displayName)
                                .font(.subheadline.bold())
                            Text(friend.account)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }

                        Spacer()

                        HStack(spacing: 8) {
                            Button(action: {
                                Task {
                                    await viewModel.createDirectConversation(peerId: friend.id)
                                }
                            }) {
                                Image(systemName: "message")
                                    .foregroundStyle(.blue)
                            }
                            Button(action: {
                                callType = "video"
                                callPeerName = friend.displayName
                                Task { await viewModel.startCall(peerId: friend.id, peerName: friend.displayName, callType: "video") }
                                showCallView = true
                            }) {
                                Image(systemName: "video")
                                    .foregroundStyle(.blue)
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle("联系人")
    }

    // MARK: - 来电弹窗

    private var incomingCallOverlay: some View {
        ZStack {
            Color.black.opacity(0.4)
                .ignoresSafeArea()
                .onTapGesture { }

            VStack(spacing: 24) {
                Text(viewModel.incomingCallInfo?.callerName ?? "未知用户")
                    .font(.title2.bold())
                    .foregroundColor(.white)

                Text(viewModel.incomingCallInfo?.callType == "video" ? "邀请你进行视频通话" : "邀请你进行语音通话")
                    .foregroundColor(.white.opacity(0.8))

                HStack(spacing: 40) {
                    // 拒绝
                    Button(action: { viewModel.rejectCall() }) {
                        VStack {
                            Image(systemName: "phone.down.fill")
                                .font(.largeTitle)
                                .foregroundColor(.white)
                                .padding()
                                .background(Color.red)
                                .clipShape(Circle())
                            Text("拒绝")
                                .font(.caption)
                                .foregroundColor(.white)
                        }
                    }

                    // 接受
                    Button(action: {
                        viewModel.acceptCall()
                        showCallView = true
                    }) {
                        VStack {
                            Image(systemName: "phone.fill")
                                .font(.largeTitle)
                                .foregroundColor(.white)
                                .padding()
                                .background(Color.green)
                                .clipShape(Circle())
                            Text("接听")
                                .font(.caption)
                                .foregroundColor(.white)
                        }
                    }
                }
            }
            .padding(40)
            .background(.ultraThinMaterial)
            .clipShape(RoundedRectangle(cornerRadius: 24))
        }
    }
}

// MARK: - 消息气泡

struct MessageBubbleView: View {
    let message: ChatMessage
    let onRetry: () -> Void

    var body: some View {
        HStack {
            if message.isFromMe { Spacer() }

            VStack(alignment: message.isFromMe ? .trailing : .leading, spacing: 4) {
                // 视频通话邀请
                if message.isCallInvite {
                    callInviteBubble
                } else {
                    Text(message.content)
                        .font(.subheadline)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(message.isFromMe ? Color.blue : Color(.systemGray6))
                        .foregroundStyle(message.isFromMe ? .white : .primary)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                }

                // 状态指示器
                if message.isFromMe {
                    HStack(spacing: 4) {
                        Text(formatTime(message.createdAt))
                            .font(.caption2)
                            .foregroundStyle(.secondary)

                        switch message.status {
                        case .sending:
                            Image(systemName: "circle.dotted")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        case .sent:
                            Image(systemName: "checkmark")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        case .delivered:
                            Image(systemName: "checkmark.circle")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        case .read:
                            Image(systemName: "checkmark.circle.fill")
                                .font(.caption2)
                                .foregroundStyle(.blue)
                        case .failed:
                            Button(action: onRetry) {
                                Image(systemName: "exclamationmark.circle.fill")
                                    .font(.caption2)
                                    .foregroundStyle(.red)
                            }
                        case .recalled:
                            Text("已撤回")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }

            if !message.isFromMe { Spacer() }
        }
        .padding(.horizontal, 8)
    }

    private var callInviteBubble: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: message.callType == "video" ? "video.fill" : "phone.fill")
                    .foregroundStyle(message.isFromMe ? .white : .blue)
                Text(message.content)
                    .font(.subheadline)
            }

            // 操作按钮（仅接收方且有邀请状态）
            if !message.isFromMe,
               let extra = message.extra,
               extra["call_status"] == "inviting" {
                HStack(spacing: 12) {
                    Button(action: {}) {
                        Label("接听", systemImage: "phone.fill")
                            .font(.caption)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.green)

                    Button(action: {}) {
                        Label("拒绝", systemImage: "phone.down.fill")
                            .font(.caption)
                    }
                    .buttonStyle(.bordered)
                    .tint(.red)
                }
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(message.isFromMe ? Color.blue : Color(.systemGray6))
        .foregroundStyle(message.isFromMe ? .white : .primary)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private func formatTime(_ dateStr: String) -> String {
        let formatter = ISO8601DateFormatter()
        if let date = formatter.date(from: dateStr) {
            let display = DateFormatter()
            display.dateFormat = "HH:mm"
            return display.string(from: date)
        }
        return ""
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

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            VStack {
                // 对方信息
                Spacer()

                VStack(spacing: 12) {
                    Text(peerName)
                        .font(.title.bold())
                        .foregroundColor(.white)

                    Text(viewModel.callDuration)
                        .font(.title3.monospacedDigit())
                        .foregroundColor(.white.opacity(0.8))

                    if callType == "voice" {
                        Image(systemName: "phone.waveform")
                            .font(.system(size: 60))
                            .foregroundStyle(.green)
                            .padding(.top, 20)
                    }
                }

                Spacer()

                // 控制按钮
                HStack(spacing: 24) {
                    // 静音
                    ControlButton(
                        icon: isMuted ? "mic.slash.fill" : "mic.fill",
                        label: "静音",
                        isActive: isMuted,
                        activeColor: .red
                    ) {
                        isMuted.toggle()
                    }

                    // 挂断
                    Button(action: {
                        viewModel.hangupCall()
                        dismiss()
                    }) {
                        Image(systemName: "phone.down.fill")
                            .font(.title)
                            .foregroundColor(.white)
                            .padding(20)
                            .background(Color.red)
                            .clipShape(Circle())
                    }

                    // 扬声器
                    ControlButton(
                        icon: isSpeakerOn ? "speaker.wave.2.fill" : "speaker.slash.fill",
                        label: "扬声器",
                        isActive: !isSpeakerOn,
                        activeColor: .yellow
                    ) {
                        isSpeakerOn.toggle()
                    }
                }

                if callType == "video" {
                    HStack(spacing: 24) {
                        ControlButton(
                            icon: isCameraOff ? "video.slash.fill" : "video.fill",
                            label: "摄像头",
                            isActive: isCameraOff,
                            activeColor: .red
                        ) {
                            isCameraOff.toggle()
                        }

                        ControlButton(
                            icon: "arrow.triangle.2.circlepath",
                            label: "翻转",
                            isActive: false,
                            activeColor: .blue
                        ) {}
                    }
                    .padding(.top, 12)
                }

                Spacer()
                    .frame(height: 60)
            }
        }
    }
}

// MARK: - 通话控制按钮

struct ControlButton: View {
    let icon: String
    let label: String
    let isActive: Bool
    let activeColor: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.title2)
                    .foregroundColor(.white)
                    .padding(16)
                    .background(isActive ? activeColor : Color.white.opacity(0.2))
                    .clipShape(Circle())
                Text(label)
                    .font(.caption)
                    .foregroundColor(.white)
            }
        }
    }
}

// MARK: - 会话行

struct ConversationRowView: View {
    let conversation: Conversation

    var body: some View {
        HStack(spacing: 12) {
            Text(conversation.initial)
                .font(.headline)
                .frame(width: 48, height: 48)
                .background(Color.blue.opacity(0.1))
                .foregroundColor(.blue)
                .clipShape(Circle())

            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(conversation.title)
                        .font(.subheadline.bold())
                    Spacer()
                    if let msg = conversation.lastMessage {
                        Text(formatTime(msg.createdAt))
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }

                HStack {
                    if let msg = conversation.lastMessage {
                        Text(msg.content)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }

                    Spacer()

                    if conversation.unreadCount > 0 {
                        Text("\(conversation.unreadCount)")
                            .font(.caption2)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.red)
                            .foregroundColor(.white)
                            .clipShape(Capsule())
                    }
                }
            }
        }
        .padding(.vertical, 4)
    }

    private func formatTime(_ dateStr: String) -> String {
        let formatter = ISO8601DateFormatter()
        if let date = formatter.date(from: dateStr) {
            let relative = RelativeDateTimeFormatter()
            relative.unitsStyle = .abbreviated
            return relative.localizedString(for: date, relativeTo: Date())
        }
        return ""
    }
}

// MARK: - 翻转辅助

extension View {
    func flippedUpsideDown() -> some View {
        self.scaleEffect(y: -1)
    }
}

#Preview {
    MessagesView()
}