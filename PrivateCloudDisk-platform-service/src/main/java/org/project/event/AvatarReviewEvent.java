package org.project.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 头像审核事件
 * <p>
 * 用户上传新头像后发布此事件，异步进行：
 * - 文件格式验证
 * - 病毒扫描（如集成杀毒软件）
 * - 图片压缩处理
 * - 状态更新
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvatarReviewEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 事件唯一ID，用于幂等性检查
     * 格式示例：avatar-review:10001:20260609120000
     */
    private String eventId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 头像文件路径（本地文件系统路径或URL）
     */
    private String avatarPath;

    /**
     * 原始文件名
     */
    private String originalFileName;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件MIME类型
     */
    private String mimeType;

    /**
     * 事件发布时间
     */
    private LocalDateTime createdAt;
}
