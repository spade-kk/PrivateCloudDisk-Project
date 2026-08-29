package org.project.im.server.netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.buffer.Unpooled;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.project.im.common.constant.ImConstants;
import org.project.im.common.protocol.v2.IMProtocolCodec;
import org.project.im.common.protocol.v2.IMProtocolV2;
import org.project.im.common.protocol.v2.MessageTypeDispatcher;
import org.project.im.common.security.IMSessionKeyManager;
import org.project.im.common.security.IMSessionKeys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.Cursor;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.RequiredArgsConstructor;

// ============================================================
// 分布式会话管理器 v3.0 — 启动注册 + 心跳维持 + 退出注销 + 用户映射分离
// ============================================================
// 重构要点：
//   1. 启动时主动向 Redis 注册节点信息（不再依赖用户首次连接）
//   2. 定时心跳更新 lastHeartbeatAt，支持 Router 存活检测
//   3. 正常退出时清理 Redis 节点注册和用户映射
//   4. Redis 操作增加重试机制（最多 3 次，指数退避）
//   5. 用户映射 TTL 调整为 90s（心跳间隔 30s × 3）
//   6. 节点信息存储为 JSON 结构（含 lastHeartbeatAt、grpcAddress、onlineCount）
//
// Redis Key 结构（与 im-common ImConstants 对齐）：
//   im:user:{userId}      → {nodeId}                 （用户连接节点映射，TTL=90s）
//   im:server:{nodeId}    → JSON(ServerNode)          （节点详细信息，含心跳时间戳）
//   im:servers            → Set<nodeId>               （所有活跃节点列表）
// ============================================================

/**
 * 分布式会话管理器
 * <p>
 * 管理 WebSocket 连接与用户的映射关系，支持多端登录和跨节点查询。
 * 本地 Map 存储 Channel 引用，Redis 存储用户→节点映射和节点注册信息。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 3.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionManager {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 连接 ID 在 Channel 上的属性键（与 V2MessageHandler 保持一致） */
    private static final String CONNECTION_ID = "v2:connection_id";

    /** 本地：用户 ID → Channel 映射（支持多端） */
    private final Map<String, Map<String, Channel>> userChannelMap = new ConcurrentHashMap<>();

    /** 本地：Channel ID → 用户 ID 反向映射 */
    private final Map<String, String> channelUserMap = new ConcurrentHashMap<>();

    /** IM Server 节点 ID（启动时生成或从配置读取） */
    @Value("${im.server.node-id:im-server-1}")
    private String nodeId;

    /** gRPC 端口（注册到 Redis 供 Router 查询） */
    @Value("${im.server.grpc-port:9092}")
    private int grpcPort;

    /** WebSocket 端口 */
    @Value("${netty.websocket.port:9090}")
    private int wsPort;

    /** 最大连接数 */
    private static final int MAX_CONNECTIONS = 100000;

    /** 单用户最大连接数 */
    private static final int MAX_CONNECTIONS_PER_USER = 5;

    /** Redis Key TTL（心跳间隔 30s × 3 = 90s，作为异常断开的兜底清理） */
    private static final long USER_MAPPING_TTL_SECONDS = 90;

    /** 心跳间隔（秒） */
    private static final long HEARTBEAT_INTERVAL_SECONDS = 30;

    /** Redis 操作最大重试次数 */
    private static final int MAX_REDIS_RETRIES = 3;

    /** Redis 操作重试初始退避（毫秒） */
    private static final long REDIS_RETRY_BASE_BACKOFF_MS = 200;

    /** 心跳定时任务执行器 */
    private ScheduledExecutorService heartbeatExecutor;

    /** 是否已注册到 Redis（防止重复注册） */
    private final AtomicBoolean registered = new AtomicBoolean(false);

    /** 是否已执行注销（防止 @PreDestroy 与 JVM 关闭钩子双路径重复清理） */
    private final AtomicBoolean shutdownExecuted = new AtomicBoolean(false);

    /** 本机 IP 缓存 */
    private String localHost;

    // ============================================================
    // 生命周期：启动注册 + 退出注销
    // ============================================================

    /**
     * 在 Spring 容器初始化完成后、Netty 绑定端口成功后调用。
     * <p>
     * 注意：此方法由 {@link NettyWebSocketServer} 在端口绑定成功后显式调用，
     * 而非通过 @PostConstruct 自动触发（确保 Netty 已就绪）。
     * </p>
     */
    public void onServerStarted() {
        // 缓存本机 IP
        localHost = resolveLocalHost();

        // 注册节点到 Redis（带重试）
        boolean success = registerNodeWithRetry();
        if (success) {
            registered.set(true);
            log.info("Node {} registered in Redis (grpc={}, ws={})", nodeId, grpcPort, wsPort);
        } else {
            log.error("Node {} failed to register in Redis after {} retries — " +
                    "WebSocket service will start but message routing may be affected", nodeId, MAX_REDIS_RETRIES);
        }

        // 启动心跳定时任务
        startHeartbeat();

        // 注册 JVM 关闭钩子：即使 Spring 容器销毁顺序异常导致 @PreDestroy 未及时
        // 触发 Redis 清理，进程退出时也能主动注销节点信息与用户映射。
        // onServerShutdown 内部幂等，与 NettyWebSocketServer.stop() 双路径只执行一次。
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                onServerShutdown();
            } catch (Exception e) {
                log.warn("JVM 关闭钩子清理失败: nodeId={}, error={}", nodeId, e.getMessage());
            }
        }, "im-shutdown-hook-" + nodeId));
    }

    /**
     * 在 Spring 容器销毁前调用。
     * <p>
     * 注意：此方法不再使用 @PreDestroy 自动触发（因 Spring 销毁顺序问题：
     * NettyWebSocketServer 依赖 SessionManager，@PreDestroy 会先销毁 SessionManager）。
     * 改为由 {@link NettyWebSocketServer#stop()} 在关闭所有连接后显式调用。
     * </p>
     */
    public void onServerShutdown() {
        // 幂等保护：只执行一次（@PreDestroy 与 JVM 关闭钩子可能都会调用）
        if (!shutdownExecuted.compareAndSet(false, true)) {
            log.info("Node {} 已执行过注销，跳过重复清理", nodeId);
            return;
        }
        log.info("Node {} shutting down, cleaning up Redis registrations...", nodeId);

        // 1. 停止心跳
        stopHeartbeat();

        // 2. 从活跃节点集合中移除自身
        unregisterNodeWithRetry();

        // 3. 清理所有映射到本节点的用户映射
        cleanupUserMappings();

        registered.set(false);
        log.info("Node {} unregistered from Redis", nodeId);
    }

    // ============================================================
    // 注册 / 移除连接
    // ============================================================

    /**
     * 注册用户连接
     * <p>
     * 1. 本地 Map 存储 Channel 引用
     * 2. Redis 存储用户→节点映射（供 IM Router 查询）
     * 3. 若 Redis 操作失败，重试 1-2 次，仍失败则记录告警但不阻断连接
     * </p>
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

        // 注册到 Redis：用户→节点映射（带重试，失败不阻断连接）
        String userKey = String.format(ImConstants.REDIS_USER_SERVER, userId);
        boolean redisOk = executeWithRetry(() -> {
            redisTemplate.opsForValue().set(userKey, nodeId, USER_MAPPING_TTL_SECONDS, TimeUnit.SECONDS);
            return null;
        }, "注册用户映射 userId=" + userId);

        if (!redisOk) {
            log.error("Redis 用户映射注册失败（降级：连接保持但消息路由可能受影响）: userId={}", userId);
        }

        // 更新节点在线数（递增连接计数，异步，不阻塞）
        updateNodeOnlineCount();

        log.info("User {} mapped to Node {} (channelId={}, online={})",
                userId, nodeId, channelId, userChannelMap.size());
        return true;
    }

    /**
     * 移除用户连接
     * <p>
     * 1. 本地 Map 移除 Channel 引用
     * 2. 如果用户所有连接都已断开，清除 Redis 中的用户→节点映射
     * </p>
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
                    // 用户所有连接断开，清除 Redis 映射
                    String userKey = String.format(ImConstants.REDIS_USER_SERVER, userId);
                    executeWithRetry(() -> {
                        redisTemplate.delete(userKey);
                        return null;
                    }, "删除用户映射 userId=" + userId);
                }
            }
            // 更新节点在线数
            updateNodeOnlineCount();
            log.info("用户下线: userId={}, channelId={}, 当前在线: {}", userId, channelId, userChannelMap.size());
        }
    }

    // ============================================================
    // 查询方法
    // ============================================================

    /**
     * 判断用户是否在线（本地）
     */
    public boolean isOnline(String userId) {
        Map<String, Channel> channels = userChannelMap.get(userId);
        return channels != null && !channels.isEmpty();
    }

    /**
     * 判断用户是否在线（全局，查询 Redis）
     */
    public boolean isOnlineGlobal(String userId) {
        if (isOnline(userId)) return true;
        String userKey = String.format(ImConstants.REDIS_USER_SERVER, userId);
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(userKey));
        } catch (Exception e) {
            log.warn("Redis 查询用户在线状态失败: userId={}", userId, e);
            return false;
        }
    }

    /**
     * 刷新用户在线映射的 TTL（在客户端心跳时调用）。
     * <p>
     * 用户映射 im:user:{userId} 的 TTL 为 90s（心跳间隔 30s × 3）。
     * 若长连接存活期间不刷新，映射会在用户仍在线时过期，导致 Router 误判用户离线、
     * 消息被错误写入离线队列。客户端每次心跳时刷新，保证长连接在线状态持续有效。
     * </p>
     */
    public void refreshUserMapping(String userId) {
        if (userId == null || userId.isBlank() || !isOnline(userId)) {
            return;
        }
        try {
            String userKey = String.format(ImConstants.REDIS_USER_SERVER, userId);
            redisTemplate.expire(userKey, USER_MAPPING_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("刷新用户映射 TTL 失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 获取用户的 Channel 集合（本地）
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
     * 获取当前节点在线用户数
     */
    public int getOnlineCount() {
        return userChannelMap.size();
    }

    /**
     * 获取所有本地在线用户 ID
     */
    public Set<String> getOnlineUsers() {
        return userChannelMap.keySet();
    }

    // ============================================================
    // 消息推送
    // ============================================================

    /**
     * 向指定用户发送二进制消息（V2 协议）
     * <p>
     * 入参 {@code envelopeBytes} 为未加密、未加帧的原始 IMEnvelope Protobuf 字节
     * （由 IM Business 构建、经 Router 转发）。此处按 V2 协议使用该连接的会话密钥
     * 封装为「帧头 + AES-GCM 加密 + HMAC 签名」的加密帧后再下发，
     * 与客户端 codec（及 V2MessageHandler 的解码逻辑）保持一致。
     * </p>
     */
    public void sendToUser(String userId, byte[] envelopeBytes) {
        Map<String, Channel> channels = userChannelMap.get(userId);
        if (channels == null || channels.isEmpty()) {
            log.debug("用户 {} 不在本节点，消息应由 Router 路由", userId);
            return;
        }
        IMProtocolV2.IMEnvelope envelope = parseEnvelope(envelopeBytes);
        if (envelope == null) {
            log.warn("推送消息无法解析为 IMEnvelope，跳过下发: userId={}, bytes={}",
                    userId, envelopeBytes.length);
            return;
        }
        for (Channel channel : channels.values()) {
            if (channel.isActive()) {
                byte[] frame = buildEncryptedFrame(channel, envelope);
                if (frame != null) {
                    channel.writeAndFlush(new BinaryWebSocketFrame(
                            Unpooled.wrappedBuffer(frame)));
                }
            }
        }
    }


    // ============================================================
    // V2 协议帧封装
    // ============================================================

    /**
     * 解析原始 IMEnvelope Protobuf 字节。
     * 若入参不是合法的 IMEnvelope（例如已是加密帧），返回 null。
     */
    private IMProtocolV2.IMEnvelope parseEnvelope(byte[] envelopeBytes) {
        try {
            return IMProtocolV2.IMEnvelope.parseFrom(envelopeBytes);
        } catch (Exception e) {
            log.error("IMEnvelope 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 使用连接对应的会话密钥，将 IMEnvelope 封装为加密帧。
     * 连接未完成密钥协商或会话密钥缺失时返回 null（跳过该连接，不阻断其他连接）。
     */
    private byte[] buildEncryptedFrame(Channel channel, IMProtocolV2.IMEnvelope envelope) {
        try {
            String connectionId = channel.attr(AttributeKey.<String>valueOf(CONNECTION_ID)).get();
            if (connectionId == null || connectionId.isEmpty()) {
                log.warn("连接未完成密钥协商，跳过推送: channelId={}", channel.id().asLongText());
                return null;
            }
            IMSessionKeys sessionKeys = IMSessionKeyManager.getInstance().getByConnection(connectionId);
            if (sessionKeys == null) {
                log.warn("会话密钥不存在，跳过推送: connectionId={}", connectionId);
                return null;
            }
            // Layer 2：若 Envelope 携带明文负载，则使用该连接的派生密钥加密（依 messageType 选择 Codec）
            IMProtocolV2.IMEnvelope outbound = envelope;
            if (envelope.getEncryptedPayload() != null
                    && envelope.getEncryptedPayload().size() > 0) {
                outbound = MessageTypeDispatcher.encodePayloadFromBytes(
                        envelope.toBuilder(),
                        envelope.getEncryptedPayload().toByteArray(),
                        sessionKeys);
            }
            // Layer 1 + 帧头 + HMAC
            return IMProtocolCodec.encode(outbound, sessionKeys);
        } catch (Exception e) {
            log.error("封装加密帧失败: channelId={}, error={}",
                    channel.id().asLongText(), e.getMessage());
            return null;
        }
    }

    /**
     * 向群组所有在线成员发送二进制消息
     */
    public void sendToGroup(Set<String> memberIds, byte[] envelopeBytes, String excludeId) {
        for (String userId : memberIds) {
            if (userId.equals(excludeId)) continue;
            sendToUser(userId, envelopeBytes);
        }
    }

    /**
     * 广播消息给所有本地在线用户
     */
    public void broadcast(byte[] envelopeBytes) {
        IMProtocolV2.IMEnvelope envelope = parseEnvelope(envelopeBytes);
        if (envelope == null) {
            log.warn("广播消息无法解析为 IMEnvelope，跳过下发: bytes={}", envelopeBytes.length);
            return;
        }
        for (Map<String, Channel> channels : userChannelMap.values()) {
            for (Channel channel : channels.values()) {
                if (channel.isActive()) {
                    byte[] frame = buildEncryptedFrame(channel, envelope);
                    if (frame != null) {
                        channel.writeAndFlush(new BinaryWebSocketFrame(
                                Unpooled.wrappedBuffer(frame)));
                    }
                }
            }
        }
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
            String userKey = String.format(ImConstants.REDIS_USER_SERVER, userId);
            redisTemplate.delete(userKey);
            updateNodeOnlineCount();
            log.info("强制踢出用户: userId={}", userId);
        }
    }

    // ============================================================
    // 节点管理
    // ============================================================

    /**
     * 获取节点 ID
     */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * 获取本机 IP 地址
     */
    public String getLocalHost() {
        if (localHost == null) {
            localHost = resolveLocalHost();
        }
        return localHost;
    }

    // ============================================================
    // 私有方法：节点注册与注销
    // ============================================================

    /**
     * 带重试的节点注册到 Redis
     * <p>
     * 操作：
     * 1. SADD im:servers {nodeId}（加入活跃节点集合）
     * 2. SET im:server:{nodeId} → JSON（节点详细信息，含 lastHeartbeat）
     * </p>
     *
     * @return true=注册成功, false=重试耗尽后仍失败
     */
    private boolean registerNodeWithRetry() {
        return executeWithRetry(() -> {
            // 1. 加入活跃节点集合
            redisTemplate.opsForSet().add(ImConstants.REDIS_SERVER_SET, nodeId);
            // 2. 写入节点详细信息（JSON 格式，含 lastHeartbeat）
            String nodeKey = String.format(ImConstants.REDIS_SERVER_NODE, nodeId);
            String nodeInfo = buildNodeInfoJson();
            redisTemplate.opsForValue().set(nodeKey, nodeInfo, USER_MAPPING_TTL_SECONDS * 2, TimeUnit.SECONDS);
            return null;
        }, "注册节点信息 nodeId=" + nodeId);
    }

    /**
     * 带重试的节点从 Redis 注销
     * <p>
     * 操作：
     * 1. SREM im:servers {nodeId}（从活跃节点集合移除）
     * 2. DEL im:server:{nodeId}（删除节点详细信息）
     * </p>
     */
    private void unregisterNodeWithRetry() {
        boolean ok = executeWithRetry(() -> {
            redisTemplate.opsForSet().remove(ImConstants.REDIS_SERVER_SET, nodeId);
            String nodeKey = String.format(ImConstants.REDIS_SERVER_NODE, nodeId);
            redisTemplate.delete(nodeKey);
            return null;
        }, "注销节点信息 nodeId=" + nodeId);

        if (!ok) {
            log.error("Redis 节点注销失败（节点可能在异常宕机后由心跳超时自动清理）: nodeId={}", nodeId);
        }
    }

    /**
     * 清理所有映射到本节点的用户映射
     * <p>
     * 注意：由于 Redis String 类型无法直接按值查询，此处采用简化策略：
     * SCAN im:user:* 键，逐个检查值是否等于本节点 ID，是则删除。
     * 对于大规模用户量，建议使用 Redis Lua 脚本优化。
     * </p>
     */
    private void cleanupUserMappings() {
        try {
            // 使用 RedisTemplate 的 scan 遍历所有 im:user:* 键
            var options = org.springframework.data.redis.core.ScanOptions
                    .scanOptions()
                    .match("im:user:*")
                    .count(100)
                    .build();
            Cursor<String> cursor = redisTemplate.scan(options);   // 返回 String 类型的键
            int cleaned = 0;
            while (cursor.hasNext()) {
                String key = cursor.next();
                try {
                    String value = redisTemplate.opsForValue().get(key);
                    if (nodeId.equals(value)) {
                        redisTemplate.delete(key);
                        cleaned++;
                    }
                } catch (Exception e) {
                    log.warn("清理用户映射失败: key={}", key, e);
                }
            }
            if (cleaned > 0) {
                log.info("已清理 {} 条映射到本节点的用户映射", cleaned);
            }
        } catch (Exception e) {
            log.warn("批量清理用户映射失败（Redis 可能不可达，映射将依赖 TTL 自动过期）: {}", e.getMessage());
        }
    }

    // ============================================================
    // 私有方法：心跳
    // ============================================================

    /**
     * 启动心跳定时任务
     * <p>
     * 每 30 秒更新一次节点信息中的 lastHeartbeat 时间戳。
     * IM Router 通过检查 lastHeartbeat 判断节点是否存活。
     * </p>
     */
    private void startHeartbeat() {
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "im-heartbeat-" + nodeId);
            t.setDaemon(true);
            return t;
        });

        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                updateHeartbeat();
            } catch (Exception e) {
                log.error("心跳更新异常: nodeId={}", nodeId, e);
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);

        log.info("心跳定时任务已启动: interval={}s, nodeId={}", HEARTBEAT_INTERVAL_SECONDS, nodeId);
    }

    /**
     * 停止心跳定时任务
     */
    private void stopHeartbeat() {
        if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) {
            heartbeatExecutor.shutdown();
            try {
                if (!heartbeatExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    heartbeatExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                heartbeatExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("心跳定时任务已停止: nodeId={}", nodeId);
        }
    }

    /**
     * 更新心跳时间戳到 Redis
     * <p>
     * 更新 im:server:{nodeId} 中的 lastHeartbeatAt 字段，同时刷新 TTL
     * （防止节点信息意外过期），并重新 SADD 到 im:servers 活跃节点集合
     * （自愈：节点若被 Router 过期清理或 Redis 清空移出，心跳会重新加入）。
     * </p>
     */
    private void updateHeartbeat() {
        if (!registered.get()) {
            return;
        }
        try {
            String nodeKey = String.format(ImConstants.REDIS_SERVER_NODE, nodeId);
            String nodeInfo = buildNodeInfoJson();
            // 心跳时重新加入活跃节点集合（幂等，防止注册信息被误清理后无法自愈）
            redisTemplate.opsForSet().add(ImConstants.REDIS_SERVER_SET, nodeId);
            redisTemplate.opsForValue().set(nodeKey, nodeInfo, USER_MAPPING_TTL_SECONDS * 2, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("心跳更新失败（Redis 可能不可达）: nodeId={}, error={}", nodeId, e.getMessage());
            // 连续失败超过阈值时应进入降级模式（见需求 8.3）
        }
    }

    /**
     * 异步更新节点在线数（不阻塞主流程）
     */
    private void updateNodeOnlineCount() {
        if (!registered.get()) {
            return;
        }
        // 在线数更新随心跳一起刷新，此处仅标记
        // 实际心跳中会写入最新 onlineCount
    }

    // ============================================================
    // 私有方法：工具
    // ============================================================

    /**
     * 构建节点信息 JSON 字符串
     * <p>
     * 格式：{"nodeId":"...","grpcAddress":"192.168.1.10:9092","wsPort":9090,
     *        "onlineCount":100,"lastHeartbeatAt":1234567890,"status":1}
     * </p>
     * <p>
     * 字段名与 im-router model.ServerNode 对齐（grpcAddress / lastHeartbeatAt），
     * 避免 Router 无法解析节点信息而把在线用户误判为离线或误清理新节点。
     * </p>
     */
    private String buildNodeInfoJson() {
        try {
            Map<String, Object> nodeInfo = new LinkedHashMap<>();
            nodeInfo.put("nodeId", nodeId);
            // gRPC 地址（host:port），供 IM Router 建立 gRPC 连接
            nodeInfo.put("grpcAddress", getLocalHost() + ":" + grpcPort);
            nodeInfo.put("wsPort", wsPort);
            nodeInfo.put("onlineCount", getOnlineCount());
            nodeInfo.put("lastHeartbeatAt", System.currentTimeMillis());
            nodeInfo.put("status", 1); // 1=健康
            return objectMapper.writeValueAsString(nodeInfo);
        } catch (Exception e) {
            // 降级：手动拼接 JSON（避免 Jackson 序列化异常导致心跳失败）
            return String.format(
                    "{\"nodeId\":\"%s\",\"grpcAddress\":\"%s:%d\",\"wsPort\":%d," +
                    "\"onlineCount\":%d,\"lastHeartbeatAt\":%d,\"status\":1}",
                    nodeId, getLocalHost(), grpcPort, wsPort,
                    getOnlineCount(), System.currentTimeMillis());
        }
    }

    /**
     * 解析本机 IP 地址
     */
    private String resolveLocalHost() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    /**
     * 带指数退避重试的 Redis 操作执行器
     *
     * @param operation Redis 操作（返回 null 表示 Void 操作）
     * @param desc      操作描述（用于日志）
     * @return true=成功, false=重试耗尽后仍失败
     */
    private boolean executeWithRetry(java.util.concurrent.Callable<Void> operation, String desc) {
        long backoff = REDIS_RETRY_BASE_BACKOFF_MS;
        for (int i = 0; i < MAX_REDIS_RETRIES; i++) {
            try {
                operation.call();
                return true;
            } catch (Exception e) {
                if (i < MAX_REDIS_RETRIES - 1) {
                    log.warn("Redis 操作失败（第 {} 次重试）: {}, error={}", i + 1, desc, e.getMessage());
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                    backoff *= 2;
                } else {
                    log.error("Redis 操作重试耗尽（{} 次）: {}, error={}", MAX_REDIS_RETRIES, desc, e.getMessage());
                }
            }
        }
        return false;
    }
}
