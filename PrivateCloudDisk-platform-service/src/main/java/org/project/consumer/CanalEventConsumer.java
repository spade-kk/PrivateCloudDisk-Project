package org.project.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Canal MySQL CDC 事件消费者
 *
 * 企业级用法:
 *   - 监听 MySQL binlog 变更，实现数据实时同步
 *   - 场景:
 *     - 文件变更后刷新 OpenSearch 索引
 *     - 用户信息变更后清理 Redis 缓存
 *     - 审计日志实时采集
 *     - 数据变更通知推送
 *
 * Canal 通过 RabbitMQ 投递变更事件，消息格式为 JSON
 */
@Slf4j
@Component
public class CanalEventConsumer {

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    private static final String CANAL_QUEUE = "canal.file.change.queue";
    private static final String CANAL_EXCHANGE = "canal.exchange";
    private static final String CANAL_ROUTING_KEY = "canal.file.#";

    public CanalEventConsumer(ObjectMapper objectMapper, StringRedisTemplate redisTemplate) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 监听文件表 (pcd_node_table) 的数据变更
     * 变更后清理相关缓存并触发索引更新
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = CANAL_QUEUE, durable = "true"),
            exchange = @Exchange(value = CANAL_EXCHANGE, type = ExchangeTypes.TOPIC, durable = "true"),
            key = CANAL_ROUTING_KEY
    ))
    public void handleFileChange(byte[] message) {
        try {
            String json = new String(message);
            JsonNode event = objectMapper.readTree(json);

            String type = event.path("type").asText(); // INSERT / UPDATE / DELETE
            String database = event.path("database").asText();
            String table = event.path("table").asText();
            JsonNode data = event.path("data").get(0);

            if (data == null || data.isNull()) {
                return;
            }

            log.info("Canal CDC 事件: type={}, db={}, table={}", type, database, table);

            // 根据文件变更类型处理缓存
            String fileId = data.path("file_id").asText();
            String userId = data.path("user_id").asText();

            switch (table) {
                case "pcd_node_table":
                    handleNodeChange(type, fileId, userId);
                    break;
                case "pcd_user_table":
                    handleUserChange(type, userId);
                    break;
                case "pcd_file_table":
                    handleFileMetaChange(type, fileId, userId);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            log.error("Canal 事件处理异常", e);
            // 不抛出异常，避免死循环重试
        }
    }

    /**
     * 文件节点变更: 清理文件列表缓存
     */
    private void handleNodeChange(String type, String fileId, String userId) {
        // 清理用户文件列表缓存
        String cacheKey = "pcd:file:list:" + userId;
        redisTemplate.delete(cacheKey);

        // 缓存在 5 分钟内不重复刷新 (防止频繁 binlog 事件)
        String throttleKey = "pcd:canal:throttle:node:" + fileId;
        Boolean set = redisTemplate.opsForValue().setIfAbsent(throttleKey, "1", 5, TimeUnit.MINUTES);
        if (Boolean.TRUE.equals(set)) {
            log.info("文件变更已处理: type={}, fileId={}, userId={}", type, fileId, userId);
            // TODO: 触发 OpenSearch 索引更新
        }
    }

    /**
     * 用户信息变更: 清理用户缓存
     */
    private void handleUserChange(String type, String userId) {
        String cacheKey = "pcd:user:" + userId;
        redisTemplate.delete(cacheKey);
        log.info("用户缓存已清理: type={}, userId={}", type, userId);
    }

    /**
     * 文件元数据变更: 清理文件详情缓存
     */
    private void handleFileMetaChange(String type, String fileId, String userId) {
        String cacheKey = "pcd:file:detail:" + fileId;
        redisTemplate.delete(cacheKey);
        log.info("文件元数据缓存已清理: type={}, fileId={}", type, fileId);
    }
}