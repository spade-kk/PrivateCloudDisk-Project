package org.project.model.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件可获得事件消息
 * <p>文件完成合并+扫毒，正式成为可获得资源后发布。
 * 消费者：主业务服务 → 提交配额（released -= fileSize, used += fileSize）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileAvailableEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事件ID（幂等去重） */
    private String eventId;

    /** 文件ID */
    private String fileId;

    /** 文件名称 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件类型 */
    private String fileType;

    /** 用户ID */
    private String userId;

    /** 需求五-9：消费者恢复文件所属空间上下文。 */
    private String spaceId;

    /** 上传会话ID */
    private String uploadsSessionId;

    /** 事件发生时间 */
    private LocalDateTime eventTime;

    /*
     * 文件生命周期预处理需求：以下字段均为向后兼容的可选扩展。
     * 原消费者所依赖字段及行为不变；新字段用于审计插件是否修改内容，并为后续
     * Automation 的 file.available 元数据入口提供最终内容版本。
     */
    /** Storage Hash Worker 独立计算的最终 SHA-256 */
    private String checksum;

    /** 最终选定的存储定位符；不得透传给外部客户端 */
    private String storagePath;

    /** 内容版本；未被插件修改时保持 0 */
    private Long contentRevision;

    /** 是否选择了插件候选内容 */
    private Boolean contentModified;

    /** success/skipped/failed/timeout/fallback_unavailable */
    private String preprocessStatus;

    /** 跨服务流水线关联 ID */
    private String correlationId;
}
