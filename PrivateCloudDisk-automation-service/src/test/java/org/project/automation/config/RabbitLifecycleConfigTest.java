package org.project.automation.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生命周期 RabbitMQ 拓扑声明回归测试。
 *
 * <p>聚焦死信队列 {@code x-message-ttl}：曾因 int 字面量乘法溢出
 * （{@code 30 * 24 * 60 * 60 * 1000} = {@code -1_702_967_296}）导致与 Broker 既有队列
 * {@code x-message-ttl=2592000000} 不等，触发 {@code PRECONDITION_FAILED}(406) 启动失败。
 * 本用例固定 DLQ TTL 必须为正且等于 30 天 Long 值，防止回归。</p>
 */
class RabbitLifecycleConfigTest {

    private final RabbitLifecycleConfig config = new RabbitLifecycleConfig();

    @Test
    void readyDlqTtlMustBePositiveThirtyDaysLong() {
        Queue queue = config.contentReadyDlq();
        assertDlqTtl(queue, "pcd.automation.file.content.ready.dlq");
        assertEquals("quorum", queue.getArguments().get("x-queue-type"));
    }

    @Test
    void availableDlqTtlMustBePositiveThirtyDaysLong() {
        Queue queue = config.automationAvailableDlq();
        assertDlqTtl(queue, "pcd.automation.file.available.dlq");
        assertEquals("quorum", queue.getArguments().get("x-queue-type"));
    }

    @Test
    void mainQueuesKeepSevenDayTtl() {
        assertEquals(604_800_000L, ttlOf(config.contentReadyQueue()));
        assertEquals(604_800_000L, ttlOf(config.automationAvailableQueue()));
    }

    private static void assertDlqTtl(Queue queue, String expectedName) {
        assertEquals(expectedName, queue.getName());
        Object ttl = queue.getArguments().get("x-message-ttl");
        assertTrue(ttl instanceof Long, "x-message-ttl 必须是 Long（避免 int 溢出），实际=" + ttl);
        long value = (Long) ttl;
        assertTrue(value > 0, "x-message-ttl 必须为正，实际=" + value);
        assertEquals(2_592_000_000L, value);
    }

    private static long ttlOf(Queue queue) {
        Object ttl = queue.getArguments().get("x-message-ttl");
        if (ttl instanceof Long) {
            return (Long) ttl;
        }
        return ((Number) ttl).longValue();
    }
}
