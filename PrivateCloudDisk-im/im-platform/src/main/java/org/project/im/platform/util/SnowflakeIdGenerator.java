package org.project.im.platform.util;

import org.springframework.stereotype.Component;

/**
 * 雪花算法 ID 生成器
 * <p>
 * 基于 Twitter Snowflake 算法，生成全局唯一的 64 位 Long 型 ID。
 * <p>
 * 结构（64 bits）：
 * <pre>
 * 0 - 0000000000 0000000000 0000000000 0000000000 0 - 00000 - 00000 - 000000000000
 * 1bit符号位  41bit时间戳（毫秒）                       5bitDC  5bitWorker 12bit序列号
 * </pre>
 * <ul>
 *   <li>41 位时间戳：支持约 69.7 年（从 2024-01-01 开始）</li>
 *   <li>10 位机器标识：支持最多 1024 个节点</li>
 *   <li>12 位序列号：每毫秒最多 4096 个 ID</li>
 * </ul>
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Component
public class SnowflakeIdGenerator {

    /** 起始时间戳（2024-01-01 00:00:00） */
    private static final long START_TIMESTAMP = 1704067200000L;

    /** 机器 ID 占的位数 */
    private static final long WORKER_ID_BITS = 5L;

    /** 数据中心 ID 占的位数 */
    private static final long DATA_CENTER_ID_BITS = 5L;

    /** 序列号占的位数 */
    private static final long SEQUENCE_BITS = 12L;

    /** 最大机器 ID（31） */
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);

    /** 最大数据中心 ID（31） */
    private static final long MAX_DATA_CENTER_ID = ~(-1L << DATA_CENTER_ID_BITS);

    /** 机器 ID 左移位数 */
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;

    /** 数据中心 ID 左移位数 */
    private static final long DATA_CENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    /** 时间戳左移位数 */
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATA_CENTER_ID_BITS;

    /** 序列号掩码（4095） */
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    /** 工作机器 ID（0 ~ 31） */
    private final long workerId;

    /** 数据中心 ID（0 ~ 31） */
    private final long dataCenterId;

    /** 序列号（0 ~ 4095） */
    private long sequence = 0L;

    /** 上次生成 ID 的时间戳 */
    private long lastTimestamp = -1L;

    /**
     * 默认构造函数（workerId=1, dataCenterId=1）
     */
    public SnowflakeIdGenerator() {
        this(1, 1);
    }

    /**
     * 构造函数
     *
     * @param workerId     工作机器 ID（0 ~ 31）
     * @param dataCenterId 数据中心 ID（0 ~ 31）
     */
    public SnowflakeIdGenerator(long workerId, long dataCenterId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException(
                    "Worker ID 必须在 0 ~ " + MAX_WORKER_ID + " 之间");
        }
        if (dataCenterId > MAX_DATA_CENTER_ID || dataCenterId < 0) {
            throw new IllegalArgumentException(
                    "Data Center ID 必须在 0 ~ " + MAX_DATA_CENTER_ID + " 之间");
        }
        this.workerId = workerId;
        this.dataCenterId = dataCenterId;
    }

    /**
     * 生成下一个 ID（线程安全）
     *
     * @return 雪花 ID
     */
    public synchronized long nextId() {
        long currentTimestamp = timeGen();

        // 时钟回拨检测
        if (currentTimestamp < lastTimestamp) {
            throw new RuntimeException(
                    "时钟回拨 " + (lastTimestamp - currentTimestamp) + " 毫秒，拒绝生成 ID");
        }

        if (currentTimestamp == lastTimestamp) {
            // 同一毫秒内，序列号自增
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // 序列号已用完，等待下一毫秒
                currentTimestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            // 不同毫秒，序列号重置
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        // 组装 ID
        return ((currentTimestamp - START_TIMESTAMP) << TIMESTAMP_SHIFT)
                | (dataCenterId << DATA_CENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    /**
     * 生成下一个 ID（字符串形式）
     *
     * @return 雪花 ID 字符串
     */
    public String nextIdStr() {
        return String.valueOf(nextId());
    }

    /**
     * 等待直到下一毫秒
     */
    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    /**
     * 获取当前时间戳
     */
    private long timeGen() {
        return System.currentTimeMillis();
    }
}