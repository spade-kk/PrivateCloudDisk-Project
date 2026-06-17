package org.project.im.server.netty;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户会话管理器
 * <p>
 * 管理 WebSocket 连接与用户的映射关系，支持多端登录：
 * <ul>
 *   <li>userId → Channel 映射（多端在线时存储多个 Channel）</li>
 *   <li>Channel → userId 反向映射</li>
 *   <li>连接数限制</li>
 *   <li>广播消息</li>
 * </ul>
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class SessionManager {

    /** 用户 ID → Channel 映射（支持多端） */
    private final Map<String, Map<String, Channel>> userChannelMap = new ConcurrentHashMap<>();

    /** Channel ID → 用户 ID 反向映射 */
    private final Map<String, String> channelUserMap = new ConcurrentHashMap<>();

    /** 最大连接数 */
    private static final int MAX_CONNECTIONS = 10000;

    /** 单用户最大连接数 */
    private static final int MAX_CONNECTIONS_PER_USER = 5;

    /**
     * 注册用户连接
     *
     * @param userId  用户 ID
     * @param channel WebSocket Channel
     * @return 是否注册成功
     */
    public boolean register(String userId, Channel channel) {
        // 检查总连接数
        if (channelUserMap.size() >= MAX_CONNECTIONS) {
            log.warn("连接数已达上限: {}", MAX_CONNECTIONS);
            return false;
        }
        // 检查单用户连接数
        Map<String, Channel> userChannels = userChannelMap.computeIfAbsent(
                userId, k -> new ConcurrentHashMap<>());
        if (userChannels.size() >= MAX_CONNECTIONS_PER_USER) {
            log.warn("用户 {} 的连接数已达上限: {}", userId, MAX_CONNECTIONS_PER_USER);
            return false;
        }

        String channelId = channel.id().asLongText();
        userChannels.put(channelId, channel);
        channelUserMap.put(channelId, userId);

        log.info("用户上线: userId={}, channelId={}, 当前在线: {}", userId, channelId, userChannelMap.size());
        return true;
    }

    /**
     * 移除用户连接
     *
     * @param channel WebSocket Channel
     */
    public void remove(Channel channel) {
        String channelId = channel.id().asLongText();
        String userId = channelUserMap.remove(channelId);
        if (userId != null) {
            Map<String, Channel> userChannels = userChannelMap.get(userId);
            if (userChannels != null) {
                userChannels.remove(channelId);
                if (userChannels.isEmpty()) {
                    userChannelMap.remove(userId);
                }
            }
            log.info("用户下线: userId={}, channelId={}, 当前在线: {}", userId, channelId, userChannelMap.size());
        }
    }

    /**
     * 判断用户是否在线
     */
    public boolean isOnline(String userId) {
        Map<String, Channel> channels = userChannelMap.get(userId);
        return channels != null && !channels.isEmpty();
    }

    /**
     * 获取用户的 Channel 集合
     */
    public Map<String, Channel> getUserChannels(String userId) {
        return userChannelMap.getOrDefault(userId, Map.of());
    }

    /**
     * 获取 Channel 对应的用户 ID
     */
    public String getUserId(Channel channel) {
        return channelUserMap.get(channel.id().asLongText());
    }

    /**
     * 获取当前在线用户数
     */
    public int getOnlineCount() {
        return userChannelMap.size();
    }

    /**
     * 获取所有在线用户 ID
     */
    public java.util.Set<String> getOnlineUsers() {
        return userChannelMap.keySet();
    }

    /**
     * 向指定用户发送消息
     *
     * @param userId  目标用户 ID
     * @param message 消息内容（JSON 字符串）
     */
    public void sendToUser(String userId, String message) {
        Map<String, Channel> channels = userChannelMap.get(userId);
        if (channels == null || channels.isEmpty()) {
            log.debug("用户 {} 不在线，消息将通过离线队列处理", userId);
            return;
        }
        TextWebSocketFrame frame = new TextWebSocketFrame(message);
        for (Channel channel : channels.values()) {
            if (channel.isActive()) {
                channel.writeAndFlush(frame.duplicate().retain());
            }
        }
    }

    /**
     * 向群组所有在线成员发送消息
     *
     * @param memberIds 群成员 ID 集合
     * @param message   消息内容
     * @param excludeId 排除的用户 ID（发送者）
     */
    public void sendToGroup(java.util.Set<String> memberIds, String message, String excludeId) {
        TextWebSocketFrame frame = new TextWebSocketFrame(message);
        for (String userId : memberIds) {
            if (userId.equals(excludeId)) continue;
            Map<String, Channel> channels = userChannelMap.get(userId);
            if (channels != null) {
                for (Channel channel : channels.values()) {
                    if (channel.isActive()) {
                        channel.writeAndFlush(frame.duplicate().retain());
                    }
                }
            }
        }
        frame.release();
    }

    /**
     * 广播消息给所有在线用户
     */
    public void broadcast(String message) {
        TextWebSocketFrame frame = new TextWebSocketFrame(message);
        for (Map<String, Channel> channels : userChannelMap.values()) {
            for (Channel channel : channels.values()) {
                if (channel.isActive()) {
                    channel.writeAndFlush(frame.duplicate().retain());
                }
            }
        }
        frame.release();
    }

    /**
     * 强制踢出用户（所有端）
     */
    public void kickUser(String userId) {
        Map<String, Channel> channels = userChannelMap.remove(userId);
        if (channels != null) {
            for (Channel channel : channels.values()) {
                channelUserMap.remove(channel.id().asLongText());
                channel.close();
            }
            log.info("强制踢出用户: userId={}", userId);
        }
    }
}