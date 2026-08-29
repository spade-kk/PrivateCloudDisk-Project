package org.project.im.platform.service;

import lombok.RequiredArgsConstructor;
import org.project.im.platform.entity.ImConversation;
import org.project.im.platform.entity.ImGroupMember;
import org.project.im.platform.entity.ImMessage;
import org.project.im.platform.mapper.ImGroupMemberMapper;
import org.project.im.platform.mapper.ImMessageMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.project.im.platform.util.ConversationIdGenerator.GROUP;

/**
 * Redis 会话摘要缓存。
 *
 * <p>AUDIT FIX [5.3-5.4] / IM-EMOJI-SESSION-20260810：会话表不再写入最后消息和
 * 未读计数。本组件将它们放入按「用户 + session」隔离的 Redis Hash；缓存未命中时按消息表
 * 和群成员已读游标回查，避免缓存故障影响会话列表可用性。</p>
 */
@Component
@RequiredArgsConstructor
public class ConversationSummaryCache {

    private static final String PREFIX = "im:conversation:summary:";
    private static final long TTL_DAYS = 30;

    private final StringRedisTemplate redisTemplate;
    private final ImMessageMapper messageMapper;
    private final ImGroupMemberMapper groupMemberMapper;

    public Summary getOrLoad(ImConversation conversation) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(key(conversation.getUserId(), conversation.getSessionId()));
        if (!values.isEmpty()) return from(values);
        ImMessage last = messageMapper.selectLastMessage(conversation.getSessionId());
        int unread = loadUnread(conversation);
        Summary summary = new Summary(last == null ? "" : last.getContent(),
                last == null ? null : last.getMessageType(),
                last == null ? null : last.getSendTime(), unread);
        write(conversation.getUserId(), conversation.getSessionId(), summary);
        return summary;
    }

    public void onMessagePersisted(ImMessage message) {
        Summary summary = new Summary(message.getContent(), message.getMessageType(), message.getSendTime(), 0);
        if (message.getConversationType() == GROUP) {
            List<ImGroupMember> members = groupMemberMapper.selectByGroupId(message.getReceiverId());
            members.forEach(member -> updateMember(member.getUserId(), message, summary,
                    !member.getUserId().equals(message.getSenderId())));
        } else {
            updateMember(message.getSenderId(), message, summary, false);
            updateMember(message.getReceiverId(), message, summary, true);
        }
    }

    public void markRead(String userId, String sessionId) {
        redisTemplate.opsForHash().put(key(userId, sessionId), "unreadCount", "0");
        redisTemplate.expire(key(userId, sessionId), TTL_DAYS, TimeUnit.DAYS);
    }

    public int getTotalUnread(List<ImConversation> conversations) {
        return conversations.stream().mapToInt(item -> getOrLoad(item).unreadCount()).sum();
    }

    private void updateMember(String userId, ImMessage message, Summary summary, boolean incrementUnread) {
        String key = key(userId, message.getConversationId());
        Map<Object, Object> existing = redisTemplate.opsForHash().entries(key);
        int unread = existing.isEmpty() ? 0 : parseInt(existing.get("unreadCount"));
        write(userId, message.getConversationId(), new Summary(summary.lastMessage(), summary.lastMessageType(),
                summary.lastMessageTime(), incrementUnread ? unread + 1 : unread));
    }

    private int loadUnread(ImConversation conversation) {
        if (conversation.getSessionType() == GROUP) {
            ImGroupMember member = groupMemberMapper.selectByGroupIdAndUserId(conversation.getPeerId(), conversation.getUserId());
            if (member == null) return 0;
            Long max = messageMapper.selectMaxSeqByConversationId(conversation.getSessionId());
            return (int) Math.max(0, (max == null ? 0 : max) - (member.getLastReadSeq() == null ? 0 : member.getLastReadSeq()));
        }
        return messageMapper.countUnreadByConversationIdAndReceiver(conversation.getSessionId(), conversation.getUserId());
    }

    private void write(String userId, String sessionId, Summary summary) {
        Map<String, String> values = new HashMap<>();
        values.put("lastMessage", summary.lastMessage() == null ? "" : summary.lastMessage());
        values.put("lastMessageType", summary.lastMessageType() == null ? "" : String.valueOf(summary.lastMessageType()));
        values.put("lastMessageTime", summary.lastMessageTime() == null ? "" : String.valueOf(summary.lastMessageTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));
        values.put("unreadCount", String.valueOf(summary.unreadCount()));
        redisTemplate.opsForHash().putAll(key(userId, sessionId), values);
        redisTemplate.expire(key(userId, sessionId), TTL_DAYS, TimeUnit.DAYS);
    }

    private Summary from(Map<Object, Object> values) {
        String millis = String.valueOf(values.getOrDefault("lastMessageTime", ""));
        // AUDIT FIX [5.4] / IM-EMOJI-SESSION-20260810：Redis 是可失效、可被旧版本残留污染的
        // 加速层。旧行为会因单个损坏的时间戳/类型字段让整个会话列表失败；新行为将异常字段
        // 降级为空值或 0，并由下一次写入/缓存未命中回查自愈，不改变消息主链路。
        LocalDateTime time = parseTime(millis);
        String type = String.valueOf(values.getOrDefault("lastMessageType", ""));
        return new Summary(String.valueOf(values.getOrDefault("lastMessage", "")), parseNullableInt(type),
                time, parseInt(values.get("unreadCount")));
    }

    private LocalDateTime parseTime(String millis) {
        try {
            return millis == null || millis.isBlank() ? null
                    : LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(Long.parseLong(millis)), ZoneId.systemDefault());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Integer parseNullableInt(String value) {
        try { return value == null || value.isBlank() ? null : Integer.valueOf(value); }
        catch (RuntimeException ignored) { return null; }
    }

    private int parseInt(Object value) { try { return Integer.parseInt(String.valueOf(value)); } catch (Exception ignored) { return 0; } }
    private String key(String userId, String sessionId) { return PREFIX + userId + ":" + sessionId; }

    public record Summary(String lastMessage, Integer lastMessageType, LocalDateTime lastMessageTime, int unreadCount) { }
}
