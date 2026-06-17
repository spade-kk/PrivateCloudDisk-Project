package org.project.im.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息事件
 * <p>
 * 通过 Spring Event / RabbitMQ 在服务内部传递的消息事件对象。
 * 消息发送成功后发布此事件，各模块可监听并执行相应逻辑
 * （如持久化、推送、离线存储、搜索索引等）。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事件 ID */
    private String eventId;

    /** 事件类型：SEND / RECALL / READ / DELETE */
    private String eventType;

    /** 消息 ID */
    private String messageId;

    /** 会话 ID */
    private String conversationId;

    /** 会话类型 */
    private Integer conversationType;

    /** 消息类型 */
    private Integer messageType;

    /** 发送者 ID */
    private String senderId;

    /** 接收者 ID */
    private String receiverId;

    /** 消息内容 */
    private String content;

    /** 消息序列号 */
    private Long serverSeq;

    /** 事件时间 */
    private LocalDateTime eventTime;
}