package org.project.im.platform.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 消息实体
 * <p>
 * 对应数据库表 im_message，存储所有聊天消息记录。
 * 消息 ID 使用雪花算法生成，全局唯一。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImMessage {

    /** 主键 ID */
    private Long id;

    /** 消息唯一 ID（雪花算法） */
    private String messageId;

    /** 会话 ID */
    private String conversationId;

    /** 会话类型：1-单聊 2-群聊 */
    private Integer conversationType;

    /** 消息类型：1-文本 2-图片 3-文件 4-语音 5-视频 6-位置 7-系统通知 8-自定义 */
    private Integer messageType;

    /** 发送者用户 ID */
    private String senderId;

    /** 接收者 ID（单聊为对方 userId，群聊为 groupId） */
    private String receiverId;

    /** 消息内容 */
    private String content;

    /** 扩展内容（JSON 格式） */
    private String extra;

    /** 消息状态：0-准备中(PREPARING) 1-已送达(DELIVERED) 2-已读(READ) 3-失败(FAILED)；5-已撤回 6-已删除（可见性状态） */
    private Integer status;

    /** 服务端消息序列号 */
    private Long serverSeq;

    /** 引用消息 ID */
    private String replyTo;

    /** 发送时间 */
    private LocalDateTime sendTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}