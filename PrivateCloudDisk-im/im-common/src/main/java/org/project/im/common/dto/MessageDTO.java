package org.project.im.common.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息 DTO
 * <p>
 * IM 系统中消息的核心数据结构，贯穿消息的发送、接收、
 * 持久化、推送全链路。支持多种消息类型（文本、图片、文件等）。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** UUID 正则：标准 8-4-4-4-12 格式 */
    private static final String UUID_REGEX =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    /** 雪花 ID 正则：纯数字，最多 19 位 */
    private static final String SNOWFLAKE_REGEX = "^\\d{1,19}$";

    /** 消息唯一 ID（雪花算法生成，全局唯一，纯数字） */
    @Pattern(regexp = SNOWFLAKE_REGEX, message = "消息ID必须是纯数字格式的雪花ID")
    private String messageId;

    /** 所属会话 ID（UUID 格式） */
    @NotBlank(message = "会话ID不能为空")
    @Pattern(regexp = UUID_REGEX, message = "会话ID必须是有效的UUID格式")
    private String conversationId;

    /** 会话类型：1-单聊 2-群聊 3-系统 */
    @NotNull(message = "会话类型不能为空")
    @Min(value = 1, message = "会话类型最小为1")
    @Max(value = 3, message = "会话类型最大为3")
    private Integer conversationType;

    /** 消息类型：1-文本 2-图片 3-文件 4-语音 5-视频 6-位置 7-系统通知 8-自定义 */
    @NotNull(message = "消息类型不能为空")
    @Min(value = 1, message = "消息类型最小为1")
    @Max(value = 8, message = "消息类型最大为8")
    private Integer messageType;

    /** 发送者用户 ID（UUID 格式） */
    @NotBlank(message = "发送者ID不能为空")
    @Pattern(regexp = UUID_REGEX, message = "发送者ID必须是有效的UUID格式")
    private String senderId;

    /** 发送者昵称 */
    @Size(max = 50, message = "发送者昵称长度不能超过50个字符")
    private String senderName;

    /** 发送者头像 URL */
    @Size(max = 512, message = "头像URL长度不能超过512个字符")
    private String senderAvatar;

    /** 接收者 ID（单聊时为对方 userId，群聊时为 groupId） */
    @NotBlank(message = "接收者ID不能为空")
    @Pattern(regexp = UUID_REGEX, message = "接收者ID必须是有效的UUID格式")
    private String receiverId;

    /** 消息内容 */
    @Size(max = 5000, message = "消息内容长度不能超过5000个字符")
    private String content;

    /** 扩展内容（JSON 格式，存储附件信息、@提及等） */
    @Size(max = 10000, message = "扩展内容长度不能超过10000个字符")
    private String extra;

    /** 消息状态：0-发送中 1-已发送 2-已送达 3-已读 4-失败 5-已撤回 */
    @Min(value = 0, message = "消息状态最小为0")
    @Max(value = 5, message = "消息状态最大为5")
    private Integer status;

    /** 客户端消息序列号（用于去重和排序） */
    @PositiveOrZero(message = "客户端消息序列号必须为非负数")
    private Long clientSeq;

    /** 服务端消息序列号（全局递增） */
    @PositiveOrZero(message = "服务端消息序列号必须为非负数")
    private Long serverSeq;

    /** 引用消息 ID（回复消息时使用，雪花 ID 格式） */
    @Pattern(regexp = "^$|" + SNOWFLAKE_REGEX, message = "引用消息ID必须是纯数字格式的雪花ID")
    private String replyTo;

    /** 发送时间 */
    private LocalDateTime sendTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}