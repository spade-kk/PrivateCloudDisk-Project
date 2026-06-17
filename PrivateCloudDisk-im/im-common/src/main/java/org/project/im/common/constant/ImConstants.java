package org.project.im.common.constant;

/**
 * IM 系统常量定义
 * <p>
 * 集中管理 IM 系统中所有魔法数字和配置常量，
 * 避免硬编码，提高可维护性。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
public final class ImConstants {

    private ImConstants() {
        throw new UnsupportedOperationException("常量类不允许实例化");
    }

    // ==================== Redis Key 前缀 ====================

    /** 用户在线状态 Hash Key：{@code im:online:users} */
    public static final String REDIS_ONLINE_USERS = "im:online:users";

    /** 用户 WebSocket 通道映射：{@code im:channel:{userId}} */
    public static final String REDIS_USER_CHANNEL = "im:channel:%s";

    /** 会话消息计数：{@code im:unread:{conversationId}:{userId}} */
    public static final String REDIS_UNREAD_COUNT = "im:unread:%s:%s";

    /** 离线消息队列：{@code im:offline:{userId}} */
    public static final String REDIS_OFFLINE_QUEUE = "im:offline:%s";

    /** 分布式锁前缀：{@code im:lock:} */
    public static final String REDIS_LOCK_PREFIX = "im:lock:";

    /** 用户 Token 缓存：{@code im:token:{userId}} */
    public static final String REDIS_USER_TOKEN = "im:token:%s";

    /** 消息 ID 自增序列：{@code im:msg_id_seq} */
    public static final String REDIS_MSG_ID_SEQ = "im:msg_id_seq";

    // ==================== RabbitMQ 配置 ====================

    /** 消息推送交换机 */
    public static final String MQ_EXCHANGE_MESSAGE = "im.message.exchange";

    /** 单聊消息路由键 */
    public static final String MQ_ROUTING_PRIVATE = "im.message.private";

    /** 群聊消息路由键 */
    public static final String MQ_ROUTING_GROUP = "im.message.group";

    /** 系统通知路由键 */
    public static final String MQ_ROUTING_SYSTEM = "im.message.system";

    /** 消息推送队列 */
    public static final String MQ_QUEUE_MESSAGE_PUSH = "im.message.push.queue";

    /** 消息持久化队列 */
    public static final String MQ_QUEUE_MESSAGE_PERSIST = "im.message.persist.queue";

    /** 离线消息队列 */
    public static final String MQ_QUEUE_OFFLINE = "im.message.offline.queue";

    // ==================== 业务限制 ====================

    /** 单条消息最大长度（字符） */
    public static final int MAX_MESSAGE_LENGTH = 5000;

    /** 消息撤回时限（秒） */
    public static final long RECALL_TIMEOUT_SECONDS = 120;

    /** 群组最大成员数 */
    public static final int MAX_GROUP_MEMBERS = 500;

    /** 单次拉取历史消息最大条数 */
    public static final int MAX_HISTORY_SIZE = 100;

    /** 会话列表默认分页大小 */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** 心跳间隔（秒） */
    public static final int HEARTBEAT_INTERVAL = 30;

    /** 心跳超时（秒），超过此时间未收到心跳视为离线 */
    public static final int HEARTBEAT_TIMEOUT = 90;

    /** 单用户最大同时连接数 */
    public static final int MAX_CONNECTIONS_PER_USER = 5;

    /** 离线消息最大保留条数 */
    public static final int MAX_OFFLINE_MESSAGES = 200;

    // ==================== 协议常量 ====================

    /** 协议版本 */
    public static final int PROTOCOL_VERSION = 1;

    /** 消息体最大长度（字节） */
    public static final int MAX_PAYLOAD_LENGTH = 65536;

    /** 协议魔数 */
    public static final int PROTOCOL_MAGIC = 0xABCDEF01;
}