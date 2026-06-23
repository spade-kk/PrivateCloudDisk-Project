//
//  MessagesPreviewData.swift
//  PrivateCloudDisk-ios
//
//  IM 消息中心预览 mock 数据
//  仅用于 #Preview 代码块，便于在 Xcode 中查看各页面布局效果
//

import Foundation

// MARK: - 会话 Mock 数据

extension Conversation {
    static let previewConversations: [Conversation] = [
        Conversation(
            id: "conv_001",
            title: "张晓明",
            subtitle: nil,
            avatarURL: nil,
            type: .direct,
            lastMessage: ChatMessage(
                id: "msg_101",
                conversationId: "conv_001",
                senderId: "user_002",
                senderName: "张晓明",
                type: .text,
                content: "好的，明天下午三点会议室见，我把方案带过去",
                status: .delivered,
                serverSeq: 101,
                createdAt: ISO8601DateFormatter().string(from: Date().addingTimeInterval(-120)),
                extra: nil
            ),
            unreadCount: 3,
            updatedAt: ISO8601DateFormatter().string(from: Date().addingTimeInterval(-120))
        ),
        Conversation(
            id: "conv_002",
            title: "项目开发群",
            subtitle: nil,
            avatarURL: nil,
            type: .group,
            lastMessage: ChatMessage(
                id: "msg_201",
                conversationId: "conv_002",
                senderId: "user_005",
                senderName: "李工",
                type: .text,
                content: "@所有人 新版本已部署到测试环境，大家有空测一下",
                status: .delivered,
                serverSeq: 201,
                createdAt: ISO8601DateFormatter().string(from: Date().addingTimeInterval(-600)),
                extra: nil
            ),
            unreadCount: 0,
            updatedAt: ISO8601DateFormatter().string(from: Date().addingTimeInterval(-600))
        ),
        Conversation(
            id: "conv_003",
            title: "王经理",
            subtitle: nil,
            avatarURL: nil,
            type: .direct,
            lastMessage: ChatMessage(
                id: "msg_301",
                conversationId: "conv_003",
                senderId: "me",
                senderName: nil,
                type: .text,
                content: "收到，我马上处理",
                status: .read,
                serverSeq: 301,
                createdAt: ISO8601DateFormatter().string(from: Date().addingTimeInterval(-1800)),
                extra: nil
            ),
            unreadCount: 0,
            updatedAt: ISO8601DateFormatter().string(from: Date().addingTimeInterval(-1800))
        ),
        Conversation(
            id: "conv_004",
            title: "设计组",
            subtitle: nil,
            avatarURL: nil,
            type: .group,
            lastMessage: ChatMessage(
                id: "msg_401",
                conversationId: "conv_004",
                senderId: "user_007",
                senderName: "UED-小陈",
                type: .image,
                content: "[图片]",
                status: .delivered,
                serverSeq: 401,
                createdAt: ISO8601DateFormatter().string(from: Date().addingTimeInterval(-3600)),
                extra: nil
            ),
            unreadCount: 12,
            updatedAt: ISO8601DateFormatter().string(from: Date().addingTimeInterval(-3600))
        ),
        Conversation(
            id: "conv_005",
            title: "赵丽",
            subtitle: nil,
            avatarURL: nil,
            type: .direct,
            lastMessage: ChatMessage(
                id: "msg_501",
                conversationId: "conv_005",
                senderId: "user_003",
                senderName: "赵丽",
                type: .text,
                content: "周末有空吗？一起吃个饭",
                status: .delivered,
                serverSeq: 501,
                createdAt: ISO8601DateFormatter().string(from: Date().addingTimeInterval(-7200)),
                extra: nil
            ),
            unreadCount: 1,
            updatedAt: ISO8601DateFormatter().string(from: Date().addingTimeInterval(-7200))
        ),
        Conversation(
            id: "conv_006",
            title: "系统通知",
            subtitle: nil,
            avatarURL: nil,
            type: .system,
            lastMessage: ChatMessage(
                id: "msg_601",
                conversationId: "conv_006",
                senderId: "system",
                senderName: "系统",
                type: .system,
                content: "您的存储空间即将用完，请及时清理或升级套餐",
                status: .delivered,
                serverSeq: 601,
                createdAt: ISO8601DateFormatter().string(from: Date().addingTimeInterval(-14400)),
                extra: nil
            ),
            unreadCount: 1,
            updatedAt: ISO8601DateFormatter().string(from: Date().addingTimeInterval(-14400))
        ),
        Conversation(
            id: "conv_007",
            title: "运维团队",
            subtitle: nil,
            avatarURL: nil,
            type: .group,
            lastMessage: ChatMessage(
                id: "msg_701",
                conversationId: "conv_007",
                senderId: "user_010",
                senderName: "运维-老周",
                type: .file,
                content: "[文件] 服务器巡检报告_2026Q2.pdf",
                status: .delivered,
                serverSeq: 701,
                createdAt: ISO8601DateFormatter().string(from: Date().addingTimeInterval(-86400)),
                extra: nil
            ),
            unreadCount: 0,
            updatedAt: ISO8601DateFormatter().string(from: Date().addingTimeInterval(-86400))
        ),
        Conversation(
            id: "conv_008",
            title: "刘总监",
            subtitle: nil,
            avatarURL: nil,
            type: .direct,
            lastMessage: ChatMessage(
                id: "msg_801",
                conversationId: "conv_008",
                senderId: "me",
                senderName: nil,
                type: .text,
                content: "好的刘总，方案我明天上午发您邮箱",
                status: .sent,
                serverSeq: 801,
                createdAt: ISO8601DateFormatter().string(from: Date().addingTimeInterval(-172800)),
                extra: nil
            ),
            unreadCount: 0,
            updatedAt: ISO8601DateFormatter().string(from: Date().addingTimeInterval(-172800))
        ),
    ]
}

// MARK: - 聊天消息 Mock 数据

extension ChatMessage {
    /// 一段完整的聊天记录，包含双方多条消息
    static func previewMessages(conversationId: String) -> [ChatMessage] {
        let now = Date()
        let cal = Calendar.current
        let fmt = ISO8601DateFormatter()

        func time(minutesAgo: Int) -> String {
            let d = cal.date(byAdding: .minute, value: -minutesAgo, to: now)!
            return fmt.string(from: d)
        }

        let me = "me"
        let peerId = "user_002"
        let peerName = "张晓明"

        return [
            ChatMessage(
                id: "p_msg_001", conversationId: conversationId,
                senderId: peerId, senderName: peerName,
                type: .text, content: "你好，上次讨论的那个项目方案怎么样了？",
                status: .read, serverSeq: 1, createdAt: time(minutesAgo: 180), extra: nil
            ),
            ChatMessage(
                id: "p_msg_002", conversationId: conversationId,
                senderId: me, senderName: nil,
                type: .text, content: "已经差不多了，我再优化一下细节",
                status: .read, serverSeq: 2, createdAt: time(minutesAgo: 178), extra: nil
            ),
            ChatMessage(
                id: "p_msg_003", conversationId: conversationId,
                senderId: peerId, senderName: peerName,
                type: .text, content: "好的，有几个地方需要调整：\n1. 预算部分需要再细化\n2. 时间线要提前两周\n3. 加上风险评估",
                status: .read, serverSeq: 3, createdAt: time(minutesAgo: 175), extra: nil
            ),
            ChatMessage(
                id: "p_msg_004", conversationId: conversationId,
                senderId: me, senderName: nil,
                type: .text, content: "收到，我马上修改",
                status: .read, serverSeq: 4, createdAt: time(minutesAgo: 174), extra: nil
            ),
            ChatMessage(
                id: "p_msg_005", conversationId: conversationId,
                senderId: peerId, senderName: peerName,
                type: .text, content: "不着急，下周一之前给我就行",
                status: .read, serverSeq: 5, createdAt: time(minutesAgo: 172), extra: nil
            ),
            ChatMessage(
                id: "p_msg_006", conversationId: conversationId,
                senderId: me, senderName: nil,
                type: .text, content: "好的，我周末加个班赶出来",
                status: .read, serverSeq: 6, createdAt: time(minutesAgo: 60), extra: nil
            ),
            ChatMessage(
                id: "p_msg_007", conversationId: conversationId,
                senderId: peerId, senderName: peerName,
                type: .text, content: "辛苦啦！对了，陈总那边也想要一份，你到时候顺便抄送他",
                status: .read, serverSeq: 7, createdAt: time(minutesAgo: 58), extra: nil
            ),
            ChatMessage(
                id: "p_msg_008", conversationId: conversationId,
                senderId: me, senderName: nil,
                type: .text, content: "没问题，陈总的邮箱我有",
                status: .read, serverSeq: 8, createdAt: time(minutesAgo: 56), extra: nil
            ),
            ChatMessage(
                id: "p_msg_009", conversationId: conversationId,
                senderId: peerId, senderName: peerName,
                type: .videoCall, content: "邀请你进行视频通话",
                status: .read, serverSeq: 9, createdAt: time(minutesAgo: 30),
                extra: ["call_type": "video", "call_id": "call_preview_001", "call_status": "ended"]
            ),
            ChatMessage(
                id: "p_msg_010", conversationId: conversationId,
                senderId: peerId, senderName: peerName,
                type: .text, content: "视频沟通效率高多了，我们确认一下最终的交付时间",
                status: .read, serverSeq: 10, createdAt: time(minutesAgo: 10),
                extra: nil
            ),
            ChatMessage(
                id: "p_msg_011", conversationId: conversationId,
                senderId: me, senderName: nil,
                type: .text, content: "下周三之前可以完成全部内容",
                status: .read, serverSeq: 11, createdAt: time(minutesAgo: 8),
                extra: nil
            ),
            ChatMessage(
                id: "p_msg_012", conversationId: conversationId,
                senderId: peerId, senderName: peerName,
                type: .text, content: "好的，明天下午三点会议室见，我把方案带过去",
                status: .delivered, serverSeq: 12, createdAt: time(minutesAgo: 2),
                extra: nil
            ),
        ]
    }
}

// MARK: - 好友 Mock 数据

extension Friend {
    static let previewFriends: [Friend] = [
        Friend(id: "friend_001", name: "张晓明", account: "zhangxm@clouddrive.com", email: "zhangxm@clouddrive.com", role: "产品经理", online: true, avatarPath: nil),
        Friend(id: "friend_002", name: "李工", account: "liji@clouddrive.com", email: "liji@clouddrive.com", role: "高级工程师", online: true, avatarPath: nil),
        Friend(id: "friend_003", name: "王经理", account: "wangjl@clouddrive.com", email: "wangjl@clouddrive.com", role: "部门经理", online: false, avatarPath: nil),
        Friend(id: "friend_004", name: "赵丽", account: "zhaol@clouddrive.com", email: "zhaol@clouddrive.com", role: "UI设计师", online: true, avatarPath: nil),
        Friend(id: "friend_005", name: "陈总", account: "chenz@clouddrive.com", email: "chenz@clouddrive.com", role: "技术总监", online: false, avatarPath: nil),
        Friend(id: "friend_006", name: "UED-小陈", account: "xiaochen@clouddrive.com", email: "xiaochen@clouddrive.com", role: "交互设计师", online: true, avatarPath: nil),
        Friend(id: "friend_007", name: "运维-老周", account: "laozhou@clouddrive.com", email: "laozhou@clouddrive.com", role: "运维工程师", online: true, avatarPath: nil),
        Friend(id: "friend_008", name: "刘总监", account: "liuzj@clouddrive.com", email: "liuzj@clouddrive.com", role: "研发总监", online: false, avatarPath: nil),
        Friend(id: "friend_009", name: "孙财务", account: "suncy@clouddrive.com", email: "suncy@clouddrive.com", role: "财务主管", online: false, avatarPath: nil),
        Friend(id: "friend_010", name: "周测试", account: "zhoucs@clouddrive.com", email: "zhoucs@clouddrive.com", role: "测试工程师", online: true, avatarPath: nil),
    ]
}