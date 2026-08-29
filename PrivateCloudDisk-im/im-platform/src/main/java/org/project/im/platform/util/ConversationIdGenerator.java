package org.project.im.platform.util;

import org.springframework.util.StringUtils;

/**
 * 会话 ID 生成器。
 *
 * <p>AUDIT FIX [5.2] / IM-EMOJI-SESSION-20260810：原实现分别在会话服务和 MQ
 * 消费者中使用下划线拼接，且群聊直接使用 groupId，容易发生格式漂移。新行为统一为
 * {@code minUserId*maxUserId} 与 {@code group*groupId}；该值同时是消息表的共享
 * conversation_id 和会话元数据表的 session_id。</p>
 */
public final class ConversationIdGenerator {

    public static final int SINGLE = 1;
    public static final int GROUP = 2;

    private ConversationIdGenerator() {
    }

    public static String generate(String userId, String peerId, int sessionType) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(peerId)) {
            throw new IllegalArgumentException("会话参与方不能为空");
        }
        if (sessionType == SINGLE) {
            return userId.compareTo(peerId) <= 0
                    ? userId + "*" + peerId
                    : peerId + "*" + userId;
        }
        if (sessionType == GROUP) {
            return "group*" + peerId;
        }
        throw new IllegalArgumentException("不支持的会话类型: " + sessionType);
    }
}
