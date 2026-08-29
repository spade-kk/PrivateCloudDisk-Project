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

    /** 用户连接节点映射：{@code im:user:{userId}} → server:{nodeId}（用于 Router 查询用户所在 IM Server） */
    public static final String REDIS_USER_SERVER = "im:user:%s";

    /** IM Server 节点注册：{@code im:server:{nodeId}} → JSON（节点信息，含地址、gRPC 端口） */
    public static final String REDIS_SERVER_NODE = "im:server:%s";

    /** IM Server 节点列表（Set）：{@code im:servers} */
    public static final String REDIS_SERVER_SET = "im:servers";

    /** 离线消息队列（Router 维护）：{@code im:offline:{userId}} → List<Protobuf bytes> */
    public static final String REDIS_OFFLINE_LIST = "im:offline:%s";

    // ==================== RabbitMQ 配置 — 命令队列（Command Queue） ====================
    // 命令队列表示"要做某件事"，由生产者发出指令，消费者执行。

    /** IM 命令交换机（direct 类型，精确路由） */
    public static final String MQ_EXCHANGE_COMMAND = "im.command.exchange";

    /** A. 消息发送命令队列（IM Server → IM Business） */
    public static final String MQ_QUEUE_SEND_COMMAND = "im.message.send.command";
    public static final String MQ_ROUTING_SEND_COMMAND = "im.message.send.command";

    /** B. 消息推送命令队列（IM Business → IM Router） */
    public static final String MQ_QUEUE_PUSH_COMMAND = "im.message.push.command";
    public static final String MQ_ROUTING_PUSH_COMMAND = "im.message.push.command";

    // ==================== RabbitMQ 配置 — 事件队列（Event Queue） ====================
    // 事件总线原则：生产者只发布事实，不关心谁消费；每个消费者拥有独立队列，各自处理失败。
    // 事件交换机使用 topic 类型，生产者按事件类型路由键发布，消费者队列按路由键订阅。

    /** IM 事件交换机（topic 类型，按路由键订阅，避免广播） */
    public static final String MQ_EXCHANGE_EVENT = "im.event.exchange";

    // --- 事件路由键（Producer 发布时使用，Consumer 队列绑定也使用此路由键） ---

    /** 消息送达事件路由键 */
    public static final String MQ_ROUTING_DELIVERED_EVENT = "im.message.delivered.event";

    /** 消息失败事件路由键 */
    public static final String MQ_ROUTING_FAILED_EVENT = "im.message.failed.event";

    /** 消息发送失败事件路由键 */
    public static final String MQ_ROUTING_SEND_FAILED_EVENT = "im.message.send.failed.event";

    /** 用户上线事件路由键 */
    public static final String MQ_ROUTING_USER_ONLINE_EVENT = "im.user.online.event";

    /** 用户离线事件路由键 */
    public static final String MQ_ROUTING_USER_OFFLINE_EVENT = "im.user.offline.event";

    /** 消息已读事件路由键 */
    public static final String MQ_ROUTING_MESSAGE_READ_EVENT = "im.message.read.event";

    // --- 事件消费者队列（命名格式：im.{event}.event.{consumer}） ---

    /** IM Platform 消费送达事件的队列 */
    public static final String MQ_QUEUE_DELIVERED_EVENT_PLATFORM = "im.message.delivered.event.platform";

    /** IM Platform 消费失败事件的队列 */
    public static final String MQ_QUEUE_FAILED_EVENT_PLATFORM = "im.message.failed.event.platform";

    /** IM Platform 消费用户上线事件的队列 */
    public static final String MQ_QUEUE_USER_ONLINE_EVENT = "im.user.online.event.platform";

    /** IM Platform 消费用户离线事件的队列 */
    public static final String MQ_QUEUE_USER_OFFLINE_EVENT = "im.user.offline.event.platform";

    /** IM Platform 消费消息已读事件的队列 */
    public static final String MQ_QUEUE_MESSAGE_READ_EVENT = "im.message.read.event.platform";

    /** IM Router 消费送达事件的队列 */
    public static final String MQ_QUEUE_DELIVERED_EVENT_ROUTER = "im.message.delivered.event.router";

    /** IM Router 消费失败事件的队列 */
    public static final String MQ_QUEUE_FAILED_EVENT_ROUTER = "im.message.failed.event.router";

    /** IM Router 消费发送失败事件的队列 */
    public static final String MQ_QUEUE_SEND_FAILED_EVENT_ROUTER = "im.message.send.failed.event.router";

    // ==================== RabbitMQ 配置 — 死信交换机（DLX） ====================
    // 所有 DLX/DLQ 命名统一加 im 前缀，与项目其他组件命名一致

    /** 命令死信交换机（direct 类型，统一处理命令消息死信） */
    public static final String MQ_DLX_COMMAND = "im.dlx.command";

    /** 事件死信交换机（direct 类型，按路由键分发到各消费者独立 DLQ） */
    public static final String MQ_DLX_EVENT = "im.dlx.event";

    // ==================== RabbitMQ 配置 — 命令死信队列（DLQ） ====================

    /** 消息发送命令死信队列 */
    public static final String MQ_DLQ_SEND_COMMAND = "im.dlq.command.send";

    /** 消息推送命令死信队列 */
    public static final String MQ_DLQ_PUSH_COMMAND = "im.dlq.command.push";

    // ==================== RabbitMQ 配置 — 命令重试队列（Retry Queue） ====================
    // 重试队列带有 TTL，消息过期后通过 DLX 重新投递到原队列

    /** 消息发送命令重试队列 */
    public static final String MQ_RETRY_SEND_COMMAND = "im.retry.command.send";

    /** 消息推送命令重试队列 */
    public static final String MQ_RETRY_PUSH_COMMAND = "im.retry.command.push";

    /** 命令消息重试 TTL（毫秒），5 秒后重新投递 */
    public static final int COMMAND_RETRY_TTL_MS = 5000;

    /** 命令消息最大重试次数 */
    public static final int COMMAND_MAX_RETRY_COUNT = 3;

    // ==================== RabbitMQ 配置 — 事件死信队列（DLQ，每个消费者独立） ====================

    /** 送达事件 - IM Platform 消费者死信队列 */
    public static final String MQ_DLQ_DELIVERED_PLATFORM = "im.dlq.event.delivered.platform";

    /** 失败事件 - IM Platform 消费者死信队列 */
    public static final String MQ_DLQ_FAILED_PLATFORM = "im.dlq.event.failed.platform";

    /** 用户上线事件死信队列 */
    public static final String MQ_DLQ_ONLINE = "im.dlq.event.online.platform";

    /** 用户离线事件死信队列 */
    public static final String MQ_DLQ_OFFLINE = "im.dlq.event.offline.platform";

    /** 消息已读事件死信队列 */
    public static final String MQ_DLQ_READ = "im.dlq.event.read.platform";

    /** 送达事件 - IM Router 消费者死信队列 */
    public static final String MQ_DLQ_DELIVERED_ROUTER = "im.dlq.event.delivered.router";

    /** 失败事件 - IM Router 消费者死信队列 */
    public static final String MQ_DLQ_FAILED_ROUTER = "im.dlq.event.failed.router";

    /** 发送失败事件 - IM Router 消费者死信队列 */
    public static final String MQ_DLQ_SEND_FAILED_ROUTER = "im.dlq.event.send.failed.router";

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

    /** 单次拉取离线消息最大条数 */
    public static final int MAX_OFFLINE_PULL_SIZE = 100;

    /** 离线消息默认拉取条数 */
    public static final int DEFAULT_OFFLINE_PULL_SIZE = 100;

    /** 历史消息默认拉取条数（游标分页） */
    public static final int DEFAULT_HISTORY_PULL_SIZE = 20;

    // ==================== 消息可见性状态（不参与投递生命周期） ====================

    /** 已撤回状态值（DB 保留值，供撤回功能使用） */
    public static final int RECALLED_STATUS = 5;

    /** 已删除状态值（DB 保留值，供删除可见性过滤使用） */
    public static final int DELETED_STATUS = 6;

    // ==================== 协议常量 ====================

    /** 协议版本 */
    public static final int PROTOCOL_VERSION = 1;

    /** 消息体最大长度（字节） */
    public static final int MAX_PAYLOAD_LENGTH = 65536;

    /** 协议魔数 */
    public static final int PROTOCOL_MAGIC = 0xABCDEF01;
}
