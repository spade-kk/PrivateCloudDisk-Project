package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.model.entity.NotificationSendLogEntity;

/**
 * 通知发送日志Mapper
 * <p>为消息消费者提供幂等性支持，记录发送状态。
 */
@Mapper
public interface NotificationSendLogMapper {

    /**
     * 根据eventId + channel + receiver查询记录（用于幂等性检查）
     *
     * @param eventId  事件ID
     * @param channel  通道 (EMAIL/SMS)
     * @param receiver 接收者
     * @return 日志记录（不存在返回null）
     */
    NotificationSendLogEntity findByEventIdAndChannelAndReceiver(
            @Param("eventId") String eventId,
            @Param("channel") String channel,
            @Param("receiver") String receiver
    );

    /**
     * 插入新记录（原子操作，受唯一索引保护）
     *
     * @param entity 日志实体
     * @return 影响行数（0表示唯一键冲突，即已存在）
     */
    int insert(NotificationSendLogEntity entity);

    /**
     * 标记为发送成功
     *
     * @param eventId  事件ID
     * @param channel  通道
     * @param receiver 接收者
     * @return 影响行数
     */
    int markSuccess(
            @Param("eventId") String eventId,
            @Param("channel") String channel,
            @Param("receiver") String receiver
    );

    /**
     * 标记为失败（记录错误信息，同时增加重试次数）
     *
     * @param eventId      事件ID
     * @param channel      通道
     * @param receiver     接收者
     * @param errorMessage 错误信息（会被截断至1000字符）
     * @return 影响行数
     */
    int markFailed(
            @Param("eventId") String eventId,
            @Param("channel") String channel,
            @Param("receiver") String receiver,
            @Param("errorMessage") String errorMessage
    );

    /**
     * 将FAILED状态重置为PENDING（用于人工触发重试场景）
     *
     * @param eventId  事件ID
     * @param channel  通道
     * @param receiver 接收者
     * @return 影响行数
     */
    int resetToPending(
            @Param("eventId") String eventId,
            @Param("channel") String channel,
            @Param("receiver") String receiver
    );
}
